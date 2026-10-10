package com.example.demo.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 用 JSON 文件持久化课表（替代 Room，避免 KSP 注解处理器依赖）。
 *
 * 数据量很小（几十门课），每次读写整个文件即可，无需数据库。
 * 文件位置：应用私有目录 files/schedule.json
 */
class ScheduleStore(context: Context) {

    private val file = File(context.filesDir, "schedule.json")

    data class Snapshot(val meta: ScheduleMeta, val courses: List<Course>)

    fun load(): Snapshot? {
        if (!file.exists()) return null
        return try {
            val root = JSONObject(file.readText())

            val m = root.optJSONObject("meta") ?: JSONObject()
            val meta = ScheduleMeta(
                semester = m.optString("semester"),
                className = m.optString("className"),
                studentId = m.optString("studentId"),
                major = m.optString("major"),
                printTime = m.optString("printTime"),
                termStartEpochDay = optLongOrNull(m, "termStartEpochDay"),
                importedAt = m.optLong("importedAt", System.currentTimeMillis()),
            )

            val arr = root.optJSONArray("courses") ?: JSONArray()
            val courses = ArrayList<Course>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                courses.add(
                    Course(
                        id = o.optLong("id"),
                        name = o.optString("name"),
                        type = o.optString("type"),
                        day = o.optInt("day"),
                        periodStart = o.optInt("periodStart"),
                        periodEnd = o.optInt("periodEnd"),
                        weekStart = o.optInt("weekStart"),
                        weekEnd = o.optInt("weekEnd"),
                        parity = o.optString("parity"),
                        campus = o.optString("campus"),
                        room = o.optString("room"),
                        teacher = o.optString("teacher"),
                        classNo = o.optString("classNo"),
                        composition = o.optString("composition"),
                        nature = o.optString("nature"),
                        enrollment = optIntOrNull(o, "enrollment"),
                        assessment = o.optString("assessment"),
                        note = o.optString("note"),
                        hourComposition = o.optString("hourComposition"),
                        weeklyHours = o.optString("weeklyHours"),
                        totalHours = o.optString("totalHours"),
                        credits = o.optString("credits"),
                        isOther = o.optBoolean("isOther"),
                        totalWeeks = optIntOrNull(o, "totalWeeks"),
                    )
                )
            }
            Snapshot(meta, courses)
        } catch (e: Exception) {
            null
        }
    }

    fun save(meta: ScheduleMeta, courses: List<Course>) {
        val root = JSONObject()

        root.put("meta", JSONObject().apply {
            put("semester", meta.semester)
            put("className", meta.className)
            put("studentId", meta.studentId)
            put("major", meta.major)
            put("printTime", meta.printTime)
            put("termStartEpochDay", meta.termStartEpochDay ?: JSONObject.NULL)
            put("importedAt", meta.importedAt)
        })

        val arr = JSONArray()
        courses.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("type", c.type)
                put("day", c.day)
                put("periodStart", c.periodStart)
                put("periodEnd", c.periodEnd)
                put("weekStart", c.weekStart)
                put("weekEnd", c.weekEnd)
                put("parity", c.parity)
                put("campus", c.campus)
                put("room", c.room)
                put("teacher", c.teacher)
                put("classNo", c.classNo)
                put("composition", c.composition)
                put("nature", c.nature)
                put("enrollment", c.enrollment ?: JSONObject.NULL)
                put("assessment", c.assessment)
                put("note", c.note)
                put("hourComposition", c.hourComposition)
                put("weeklyHours", c.weeklyHours)
                put("totalHours", c.totalHours)
                put("credits", c.credits)
                put("isOther", c.isOther)
                put("totalWeeks", c.totalWeeks ?: JSONObject.NULL)
            })
        }
        root.put("courses", arr)

        file.writeText(root.toString())
    }

    fun updateMeta(meta: ScheduleMeta) {
        val snap = load() ?: return
        save(meta, snap.courses)
    }

    fun clear() {
        if (file.exists()) file.delete()
    }

    private fun optIntOrNull(o: JSONObject, key: String): Int? =
        if (o.has(key) && !o.isNull(key)) o.optInt(key) else null

    private fun optLongOrNull(o: JSONObject, key: String): Long? =
        if (o.has(key) && !o.isNull(key)) o.optLong(key) else null
}
