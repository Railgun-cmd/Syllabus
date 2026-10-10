package com.example.demo.data

import android.util.Log
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import java.io.InputStream

/**
 * 正方教务「班级课表」PDF 解析器。
 *
 * 与 Python 原型 parse_schedule.py 一一对应：
 *  1. 用 PDFStreamParser 拿到内容流的原始 COSString 字节（绕开 ToUnicode 缺失的问题）
 *  2. 按 UniGB-UCS2-H 编码把字节流解码成 UCS-2 文本
 *  3. 按 x 坐标分列（时间段/节次/周一~周日），按 y 间距把文字聚成课程块
 *  4. 跨页续接被截断的块，最后用正则抽取字段
 */
object PdfScheduleParser {

    private const val TAG = "PdfScheduleParser"

    /** 课表列边界（PDF user space x 坐标，来自表格矩形） */
    private val COL_BOUNDS = floatArrayOf(
        16f, 62.73f, 99.08f, 202.92f, 306.77f, 410.62f, 514.46f, 618.31f, 722.15f, 826f
    )

    /** 课程类型标记（导出 PDF 的图例: ★理论 ☆实验 □上机 ■实践） */
    private val MARKER_TYPE = mapOf("★" to "理论", "☆" to "实验", "□" to "上机", "■" to "实践")

    private data class RawRun(
        val text: String, val x: Float, val y: Float,
        val fontSize: Float, val renderMode: Int,
    )

    private class Block {
        val names = mutableListOf<String>()
        val details = mutableListOf<String>()
        var topY = 0f
        var lastY = 0f
    }

    /** 解析过程中暂存的课程（detail 可能跨页续接） */
    private class CourseDraft(
        val name: String,
        val day: Int,
        var detail: String,
        var clipped: Boolean,
    )

    data class ParsedResult(
        val meta: ScheduleMeta,
        val courses: List<Course>,
    )

    // ---------------- 入口 ----------------

    fun parse(input: InputStream): ParsedResult {
        val document = PDDocument.load(input)
        try {
            val pagesRuns = mutableListOf<List<RawRun>>()
            for (page in document.pages) {
                pagesRuns.add(extractRuns(page))
            }
            Log.i(TAG, "提取完成: ${pagesRuns.size} 页")
            return parsePages(pagesRuns)
        } finally {
            document.close()
        }
    }

    // ---------------- 第 1 步: 内容流 -> 带坐标的文字 ----------------

    private fun extractRuns(page: PDPage): List<RawRun> {
        val runs = mutableListOf<RawRun>()
        val parser = PDFStreamParser(page)
        // 注意: pdfbox-android 的 parse() 返回 void，token 要从 getTokens() 取
        parser.parse()
        val tokens = parser.getTokens()

        var tx = 0f
        var ty = 0f
        var fontSize = 0f
        var renderMode = 0

        for (i in tokens.indices) {
            val tok = tokens[i]
            if (tok !is Operator) continue
            when (tok.name) {
                "BT" -> { tx = 0f; ty = 0f }
                "Td", "TD" -> {
                    val nums = prevNumbers(tokens, i, 2)
                    if (nums.size == 2) { tx += nums[0]; ty += nums[1] }
                }
                "Tm" -> {
                    val nums = prevNumbers(tokens, i, 6)
                    if (nums.size == 6) { tx = nums[4]; ty = nums[5] }
                }
                "Tf" -> {
                    val prev = tokens.getOrNull(i - 1)
                    if (prev is COSNumber) fontSize = prev.floatValue()
                }
                "Tr" -> {
                    val prev = tokens.getOrNull(i - 1)
                    if (prev is COSNumber) renderMode = prev.intValue()
                }
                "Tj" -> {
                    val prev = tokens.getOrNull(i - 1)
                    if (prev is COSString) {
                        runs.add(RawRun(decodeUcs2(prev.bytes), tx, ty, fontSize, renderMode))
                    }
                }
                "TJ" -> {
                    val prev = tokens.getOrNull(i - 1)
                    if (prev is COSArray) {
                        val sb = StringBuilder()
                        for (el in prev) {
                            if (el is COSString) sb.append(decodeUcs2(el.bytes))
                        }
                        runs.add(RawRun(sb.toString(), tx, ty, fontSize, renderMode))
                    }
                }
            }
        }
        return runs
    }

    /** 读取运算符前 count 个数字操作数（与 Python 版一致） */
    private fun prevNumbers(tokens: List<Any>, opIndex: Int, count: Int): List<Float> {
        val out = mutableListOf<Float>()
        var j = opIndex - 1
        while (j >= 0 && out.size < count && tokens[j] is COSNumber) {
            out.add(0, (tokens[j] as COSNumber).floatValue())
            j--
        }
        return out
    }

    /**
     * 解码 UCS-2 BE 字节流。
     * PDFBox 解析 COSString 时已经展开了 \( \) \\ \n \t 等转义，
     * 这里直接按 2 字节大端读出 Unicode 码点（UniGB-UCS2-H 编码约定）。
     */
    private fun decodeUcs2(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size / 2)
        var i = 0
        while (i + 1 < bytes.size) {
            val code = ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
            if (code != 0) sb.append(code.toChar())
            i += 2
        }
        return sb.toString()
    }

    // ---------------- 第 2 步: 聚类 + 字段抽取 ----------------

    private fun colOf(x: Float): Int? {
        for (ci in 0 until COL_BOUNDS.size - 1) {
            if (x >= COL_BOUNDS[ci] && x < COL_BOUNDS[ci + 1]) return ci
        }
        return null
    }

    /** 一列文字按 y 聚类成块（课程名 9pt、详情 8pt；块间距 > 20 视为新块） */
    private fun clusterColumn(runs: List<RawRun>): List<Block> {
        val sorted = runs.sortedByDescending { it.y }
        val blocks = mutableListOf<Block>()
        var prevFs = -1f
        for (r in sorted) {
            if (r.text.isBlank()) continue
            var newBlock = false
            if (blocks.isEmpty()) {
                newBlock = true
            } else {
                val last = blocks.last()
                val gap = last.lastY - r.y
                if (r.fontSize == 9f && prevFs == 8f) newBlock = true
                else if (gap > 20f) newBlock = true
            }
            if (newBlock) {
                blocks.add(Block().also { it.topY = r.y; it.lastY = r.y })
            }
            val b = blocks.last()
            if (r.fontSize == 9f) b.names.add(r.text) else b.details.add(r.text)
            b.lastY = r.y
            prevFs = r.fontSize
        }
        return blocks
    }

    private fun match1(pattern: String, text: String): String? =
        Regex(pattern).find(text)?.groupValues?.getOrNull(1)

    /** 把块的文字抽成 Course 字段 */
    private fun buildCourse(draft: CourseDraft): Course? {
        var name = draft.name
        if (name.isBlank()) return null
        var type = ""
        for ((marker, t) in MARKER_TYPE) {
            if (name.contains(marker)) {
                type = t
                name = name.replace(marker, "")
            }
        }
        val d = draft.detail

        val pm = Regex("""\((\d+)-(\d+)节\)""").find(d)
        val periodStart = pm?.groupValues?.get(1)?.toIntOrNull()
        val periodEnd = pm?.groupValues?.get(2)?.toIntOrNull()

        val wm = Regex("""(\d+)-(\d+)周""").find(d)
        val weekStart = wm?.groupValues?.get(1)?.toIntOrNull()
        val weekEnd = wm?.groupValues?.get(2)?.toIntOrNull()

        val parityRaw = match1("""周\((单|双)\)""", d)
        val parity = when (parityRaw) {
            "单" -> "单周"
            "双" -> "双周"
            else -> ""
        }

        val classM = Regex("""教学班:(?:\((\d{4}-\d{4}-\d)\)-)?([^/]+)""").find(d)
        val classNo = classM?.let { g ->
            val v = g.groupValues
            if (v[1].isNotEmpty()) "${v[1]}-${v[2]}" else v[2]
        } ?: ""

        return Course(
            name = name.trim(),
            type = type,
            day = draft.day,
            periodStart = periodStart ?: 0,
            periodEnd = periodEnd ?: 0,
            weekStart = weekStart ?: 0,
            weekEnd = weekEnd ?: 0,
            parity = parity,
            campus = match1("""校区:([^/]+)""", d) ?: "",
            room = match1("""场地:([^/]+)""", d) ?: "",
            teacher = match1("""教师:([^/]+)""", d) ?: "",
            classNo = classNo,
            composition = match1("""教学班组成:([^/]+)""", d) ?: "",
            nature = match1("""课程性质[^:]*:([^/]+)""", d) ?: "",
            enrollment = match1("""选课人数:(\d+)""", d)?.toIntOrNull(),
            // 个人课表独有字段(班级课表没有, 匹配不到就是空串)
            assessment = match1("""考核方式:([^/]+)""", d) ?: "",
            note = match1("""选课备注:([^/]+)""", d) ?: "",
            hourComposition = match1("""课程学时组成:([^/]+)""", d) ?: "",
            weeklyHours = match1("""周学时:([\d.]+)""", d) ?: "",
            totalHours = match1("""总学时:([\d.]+)""", d) ?: "",
            credits = match1("""学分:([\d.]+)""", d) ?: "",
        )
    }

    private fun parsePages(pagesRuns: List<List<RawRun>>): ParsedResult {
        val openBlocks = arrayOfNulls<CourseDraft>(9)  // 每列上一页被截断的块
        val drafts = mutableListOf<CourseDraft>()
        val others = mutableListOf<Course>()

        var semester = ""
        var className = ""
        var studentId = ""
        var major = ""
        var printTime = ""
        val othersRaw = mutableListOf<String>()

        for (pageRuns in pagesRuns) {
            val cols = Array(9) { mutableListOf<RawRun>() }

            for (r in pageRuns) {
                val ci = colOf(r.x) ?: continue
                cols[ci].add(r)

                // 表头与杂项信息
                if (r.text.contains("学年") && r.text.contains("学期") && r.fontSize == 8f) {
                    semester = r.text.trim()
                }
                if (r.text.trim().endsWith("课表") && r.fontSize == 24f) {
                    className = r.text.trim().dropLast(2)
                }
                if (r.text.trim().startsWith("专业") && r.fontSize == 8f) {
                    major = r.text.trim().substringAfter("：")
                }
                if (r.text.trim().startsWith("学号") && r.fontSize == 8f) {
                    studentId = r.text.trim().substringAfter("：").trim()
                }
                if (r.text.contains("打印时间")) {
                    printTime = r.text.trim().substringAfter(":")
                }
                if (r.text.trim().startsWith("其他课程")) {
                    othersRaw.add(r.text.trim())
                }
            }

            for (dayIdx in 2..8) {
                val dayRuns = cols[dayIdx].filter { it.fontSize == 8f || it.fontSize == 9f }
                val blocks = clusterColumn(dayRuns)
                val day = dayIdx - 1

                for (b in blocks) {
                    if (b.names.isEmpty()) {
                        // 续接上一页被截断的块
                        val prev = openBlocks[dayIdx]
                        if (prev != null && prev.clipped) {
                            prev.detail += b.details.joinToString("")
                            prev.clipped = b.lastY < 60f
                        }
                        continue
                    }
                    val draft = CourseDraft(
                        name = b.names.joinToString(""),
                        day = day,
                        detail = b.details.joinToString(""),
                        clipped = b.lastY < 60f,
                    )
                    openBlocks[dayIdx] = draft
                    drafts.add(draft)
                }
                // 块完整结束时清除 open 状态，避免误续接
                openBlocks[dayIdx]?.let { if (!it.clipped) openBlocks[dayIdx] = null }
            }
        }

        // 其他课程（无固定节次，如军事技能）
        for (raw in othersRaw) {
            val body = raw.substringAfter("：", raw)
            for (seg in body.split(";")) {
                val s = seg.trim()
                if (s.isEmpty()) continue
                val m = Regex("""^(.+?)([★☆□■])(.+?)\(共(\d+)周\)/(\d+)-(\d+)周$""").find(s) ?: continue
                val g = m.groupValues
                others.add(
                    Course(
                        name = g[1],
                        type = MARKER_TYPE[g[2]] ?: "",
                        day = 0,
                        isOther = true,
                        teacher = g[3],
                        totalWeeks = g[4].toIntOrNull(),
                        weekStart = g[5].toIntOrNull() ?: 0,
                        weekEnd = g[6].toIntOrNull() ?: 0,
                    )
                )
            }
        }

        val courses = drafts.mapNotNull { buildCourse(it) } + others
        val meta = ScheduleMeta(
            semester = semester,
            className = className,
            studentId = studentId,
            major = major,
            printTime = printTime,
            termStartEpochDay = null, // 用户在 App 里设置
            importedAt = System.currentTimeMillis(),
        )
        Log.i(TAG, "解析完成: ${courses.size} 门课程")
        return ParsedResult(meta, courses)
    }
}
