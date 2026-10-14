package com.example.demo.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.RSAPublicKeySpec
import java.util.Calendar
import javax.crypto.Cipher
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** 教务系统导入配置 */
data class ZfConfig(
    /** 教务系统地址，如 http://jwxt.sdnu.edu.cn 或 https://jw.xxx.edu.cn/jwglxt */
    val baseUrl: String,
    val username: String,
    val password: String,
    /** 学年，如 "2026" 表示 2026-2027 学年 */
    val schoolYear: String,
    /** 学期：3=第一学期，12=第二学期 */
    val term: String,
)

sealed interface ZfResult {
    data class Ok(val courses: List<Course>, val log: String) : ZfResult
    data class Fail(val message: String, val log: String) : ZfResult
}

/**
 * 正方教务系统导入器（适配新版正方 jwglxt）
 *
 * 流程：取登录页拿 csrftoken -> 取 RSA 公钥 -> RSA 加密密码并登录 -> 调课表接口拿 JSON -> 转成 Course
 * 各校路径/字段略有差异，所以全程记录日志，失败时把日志展示出来便于排查。
 */
object ZhengfangImporter {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private val log = StringBuilder()
    /** 简化 cookie 罐 */
    private val cookies = mutableMapOf<String, String>()

    /** 默认学年：9 月及以后算新学年 */
    fun defaultSchoolYear(): String {
        val cal = Calendar.getInstance()
        val y = cal.get(Calendar.YEAR)
        return if (cal.get(Calendar.MONTH) + 1 >= 9) "$y" else "${y - 1}"
    }

    /** 默认学期：9~1 月为第一学期(3)，其余为第二学期(12) */
    fun defaultTerm(): String {
        val m = Calendar.getInstance().get(Calendar.MONTH) + 1
        return if (m >= 9 || m <= 1) "3" else "12"
    }

    suspend fun fetch(cfg: ZfConfig): ZfResult = withContext(Dispatchers.IO) {
        log.clear()
        cookies.clear()
        try {
            trustAllCerts()
            val raw = cfg.baseUrl.trim()
            if (raw.isEmpty()) return@withContext fail("请先填写教务系统地址")
            note("学年/学期: ${cfg.schoolYear} / ${cfg.term}")

            // ---- 1. 登录页（兼容：站点根 / 带 jwglxt / 直接粘登录页地址 / 带参数的首页地址）----
            var root = ""
            var page = ""
            var lastCode = 0
            for (cand in candidateRoots(raw)) {
                val (code, html) = get("$cand/xtgl/login_slogin.html")
                lastCode = code
                note("尝试 $cand -> HTTP $code (${html.length} 字符)")
                if (code == 200 && html.contains("login_slogin")) {
                    root = cand
                    page = html
                    break
                }
            }
            if (root.isEmpty()) {
                return@withContext fail(
                    "打不开登录页（最后一次 HTTP $lastCode）。请确认地址正确，" +
                        "且手机能访问教务系统（部分学校只能在校园网/VPN 下访问）"
                )
            }
            note("使用地址: $root")
            val title = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
                .find(page)?.groupValues?.get(1)
            note("页面标题: ${title ?: "?"}")
            val csrf = Regex("""name="csrftoken"\s+value="([^"]*)"""").find(page)?.groupValues?.get(1)
                ?: Regex("""csrftoken["']?\s*[:=]\s*["']([^"']+)""").find(page)?.groupValues?.get(1)
                ?: ""
            note("csrftoken: ${if (csrf.isEmpty()) "未取到（可能该系统不需要）" else "已取到"}")

            // ---- 2. RSA 公钥 ----
            val (c2, keyJson) = get("$root/xtgl/login_getPublicKey.html")
            note("取公钥 -> HTTP $c2")
            val modulus = Regex(""""modulus"\s*:\s*"([^"]*)"""").find(keyJson)?.groupValues?.get(1)
            val exponent = Regex(""""exponent"\s*:\s*"([^"]*)"""").find(keyJson)?.groupValues?.get(1)
            if (modulus == null || exponent == null) {
                return@withContext fail("取不到登录公钥（HTTP $c2）。该系统可能不是新版正方，或地址不完整")
            }

            // ---- 3. 登录（把登录页的所有隐藏字段一起提交，各校要求不同）----
            val encrypted = rsaEncrypt(cfg.password, modulus, exponent)
            val hidden = parseHiddenInputs(page)
            note("登录页隐藏字段: ${hidden.keys.joinToString(", ")}")
            val form = LinkedHashMap<String, String>()
            form.putAll(hidden)
            form["csrftoken"] = csrf.ifEmpty { hidden["csrftoken"] ?: "" }
            form["yhm"] = cfg.username
            form["mm"] = encrypted
            if (form["language"].isNullOrEmpty()) form["language"] = "zh_CN"

            val (c3, res3) = postForm("$root/xtgl/login_slogin.html", form)
            note("登录提交 -> HTTP $c3, ${res3.length} 字符")

            // 登录失败时服务器会重新渲染登录页（含 yhm 输入框）
            val backToLogin = res3.contains("id=\"yhm\"") || res3.contains("name=\"yhm\"")
            if (backToLogin) {
                val hint = when {
                    res3.contains("用户名或密码不正确") || res3.contains("密码错误") -> "用户名或密码不正确"
                    res3.contains("id=\"yzm\"") -> "登录需要验证码（多次输错后触发，请先用浏览器登录一次或稍后再试）"
                    else -> "登录没有成功（服务器仍返回登录页）"
                }
                return@withContext fail(hint)
            }

            // ---- 4. 抓课表 ----
            val kbUrl = "$root/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151&su=${enc(cfg.username)}"
            val (c4, kb) = postForm(
                kbUrl,
                mapOf("xnm" to cfg.schoolYear, "xqm" to cfg.term),
            )
            note("课表接口 -> HTTP $c4, ${kb.length} 字符")
            val body = kb.trim()
            if (!body.startsWith("{")) {
                return@withContext fail("课表接口没返回 JSON（多半是没登录成功）。请确认学校地址、学年学期是否正确")
            }
            val obj = JSONObject(body)
            val arr = obj.optJSONArray("kbList")
                ?: return@withContext fail("返回里没有 kbList 字段，可能是老版正方或学期参数不对")

            // ---- 5. 转成 Course ----
            val courses = mutableListOf<Course>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                courses += toCourses(o)
            }
            val real = courses.filter { !it.isOther }
            note("解析到 ${real.size} 条课程记录")
            if (real.isEmpty()) return@withContext fail("解析到 0 门课，可能学期参数不对（试试把学年/学期改成上学期或下学期）")

            var meta = ScheduleMeta()
            ZfResult.Ok(real, log.toString())        } catch (e: Exception) {
            note("异常: ${e.javaClass.simpleName}: ${e.message}")
            fail("请求失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    // ---------------- 内部实现 ----------------

    private fun fail(msg: String): ZfResult.Fail = ZfResult.Fail(msg, log.toString())

    /** 解析登录页里的所有 hidden 输入，登录时原样带上（各校字段不同） */
    private fun parseHiddenInputs(html: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        Regex("""<input[^>]*type\s*=\s*["']hidden["'][^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { m ->
                val tag = m.value
                val name = Regex("""name\s*=\s*["']([^"']*)""").find(tag)?.groupValues?.get(1)
                if (name.isNullOrEmpty()) return@forEach
                val value = Regex("""value\s*=\s*"([^"]*)"""").find(tag)?.groupValues?.get(1)
                    ?: Regex("""value\s*=\s*'([^']*)'""").find(tag)?.groupValues?.get(1)
                    ?: Regex("""value\s*=\s*([^\s>]+)""").find(tag)?.groupValues?.get(1)
                    ?: ""
                out[name] = value
            }
        return out
    }

    private fun note(s: String) {
        log.append(s).append('\n')
    }

    /**
     * 用户可能填：
     *   http://jwxt.sdnu.edu.cn
     *   http://jwxt.sdnu.edu.cn/jwglxt
     *   http://jwxt.sdnu.edu.cn/jwglxt/xtgl/login_slogin.html
     *   http://jwxt.sdnu.edu.cn/jwglxt/xtgl/index_initMenu.html?jsdm=xs&_t=...
     * 这里统一归一成可拼 /xtgl/... 的根地址（必要时再补一个带 /jwglxt 的候选）
     */
    private fun candidateRoots(raw: String): List<String> {
        var u = raw.trim()
        if (!u.startsWith("http")) u = "http://$u"
        u = u.substringBefore('#').substringBefore('?').trimEnd('/')
        for (marker in listOf("/xtgl/", "/kbcx/", "/xtgl", "/kbcx", "login_slogin", "index_initMenu")) {
            val i = u.indexOf(marker)
            if (i > 0) {
                u = u.substring(0, i)
                break
            }
        }
        u = u.trimEnd('/')
        val list = mutableListOf(u)
        if (!u.endsWith("/jwglxt")) list += "$u/jwglxt"
        return list
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    /** 各校证书常有问题，这里放行（仅本 App 生效） */
    private fun trustAllCerts() {
        try {
            val tm = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, tm, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(ctx.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        } catch (_: Exception) {
        }
    }

    private fun cookieHeader(): String =
        cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

    private fun saveCookies(conn: HttpURLConnection) {
        conn.headerFields?.forEach { (k, v) ->
            if (k != null && k.equals("Set-Cookie", ignoreCase = true)) {
                v?.forEach { raw ->
                    val kv = raw.substringBefore(';')
                    val idx = kv.indexOf('=')
                    if (idx > 0) cookies[kv.substring(0, idx).trim()] = kv.substring(idx + 1).trim()
                }
            }
        }
    }

    private fun get(url: String): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            if (cookies.isNotEmpty()) setRequestProperty("Cookie", cookieHeader())
        }
        val code = conn.responseCode
        val text = readBody(conn)
        saveCookies(conn)
        conn.disconnect()
        return code to text
    }

    private fun postForm(url: String, form: Map<String, String>): Pair<Int, String> {
        val body = form.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
            if (cookies.isNotEmpty()) setRequestProperty("Cookie", cookieHeader())
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val text = readBody(conn)
        saveCookies(conn)
        conn.disconnect()
        return code to text
    }

    private fun readBody(conn: HttpURLConnection): String {
        val stream = try {
            conn.inputStream
        } catch (_: Exception) {
            conn.errorStream
        } ?: return ""
        return stream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
    }

    private fun rsaEncrypt(password: String, modulusB64: String, exponentB64: String): String {
        val modulus = BigInteger(1, Base64.decode(modulusB64, Base64.DEFAULT))
        val exponent = BigInteger(1, Base64.decode(exponentB64, Base64.DEFAULT))
        val key = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return Base64.encodeToString(cipher.doFinal(password.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    /** 正方的一条 kbList -> 一到多条 Course（周次可能是多段） */
    private fun toCourses(o: JSONObject): List<Course> {
        val name = o.optString("kcmc").trim()
        if (name.isEmpty()) return emptyList()

        val day = parseDay(o.optString("xqj"))
        val (ps, pe) = parsePeriods(o.optString("jcs"))
        val zcd = o.optString("zcd", "")
        val parity = when {
            zcd.contains("单") -> "单周"
            zcd.contains("双") -> "双周"
            else -> ""
        }
        val ranges = parseWeeks(zcd).ifEmpty { listOf(1 to 25) }

        val place = o.optString("cdmc").trim()
        val teacher = o.optString("xm").trim()
        val campus = o.optString("xqmc").trim()

        return ranges.map { (ws, we) ->
            Course(
                name = name,
                day = day,
                periodStart = ps,
                periodEnd = pe,
                weekStart = ws,
                weekEnd = we,
                parity = parity,
                campus = campus,
                room = place,
                teacher = teacher,
                credits = o.optString("xf").trim(),
                assessment = o.optString("khfs").trim(),
            )
        }
    }

    /** "1"~"7"，也兼容 "星期一" 之类 */
    private fun parseDay(raw: String): Int {
        val s = raw.trim()
        s.toIntOrNull()?.let { return it.coerceIn(1, 7) }
        val map = mapOf(
            "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7,
        )
        map.forEach { (k, v) -> if (s.contains(k)) return v }
        return 1
    }

    /** "1-2" / "3" / "1-2,3-4" -> (起, 止) */
    private fun parsePeriods(raw: String): Pair<Int, Int> {
        val nums = Regex("""\d+""").findAll(raw).map { it.value.toInt() }.toList()
        if (nums.isEmpty()) return 1 to 2
        val a = nums.first().coerceIn(1, 12)
        val b = (nums.getOrNull(1) ?: a).coerceIn(a, 12)
        return a to b
    }

    /** "1-16周" / "1-16周(单)" / "1,3,5周" / "1-8,10-16周" -> 段列表 */
    private fun parseWeeks(zcd: String): List<Pair<Int, Int>> {
        val cleaned = zcd.replace("周", "").replace("（", "(").replace("）", ")")
        val out = mutableListOf<Pair<Int, Int>>()
        Regex("""(\d+)\s*-\s*(\d+)""").findAll(cleaned).forEach {
            val a = it.groupValues[1].toInt()
            val b = it.groupValues[2].toInt()
            if (a in 1..30 && b in a..30) out += a to b
        }
        if (out.isEmpty()) {
            // 单个数字或逗号分隔
            Regex("""\d+""").findAll(cleaned).forEach {
                val a = it.value.toInt()
                if (a in 1..30) out += a to a
            }
        }
        return out.distinct()
    }
}
