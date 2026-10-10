package com.example.demo.data

/**
 * 一门课程（纯数据类，不依赖 Room —— 由 ScheduleStore 用 JSON 持久化）
 *
 * day: 1=周一 ... 7=周日, 0=无固定时间的其他课程
 * parity: "" / "单周" / "双周"
 */
data class Course(
    val id: Long = 0,
    val name: String,
    val type: String = "",            // 理论/实验/上机/实践, 空=无标记
    val day: Int = 0,
    val periodStart: Int = 0,
    val periodEnd: Int = 0,
    val weekStart: Int = 0,
    val weekEnd: Int = 0,
    val parity: String = "",
    val campus: String = "",
    val room: String = "",
    val teacher: String = "",
    val classNo: String = "",
    val composition: String = "",
    val nature: String = "",
    val enrollment: Int? = null,
    /** 以下字段仅个人课表(学生课表)导出带 */
    val assessment: String = "",      // 考核方式: 考试/考查
    val note: String = "",            // 选课备注
    val hourComposition: String = "", // 课程学时组成: 理论:36,实践:12
    val weeklyHours: String = "",     // 周学时
    val totalHours: String = "",      // 总学时
    val credits: String = "",         // 学分
    val isOther: Boolean = false,     // 无固定节次(如军事技能)
    val totalWeeks: Int? = null,
) {
    /** 该课程在第 week 周是否真的上课（周次范围 + 单双周都要匹配） */
    fun activeInWeek(week: Int): Boolean {
        if (week !in weekStart..weekEnd) return false
        if (parity.isEmpty()) return true
        val odd = week % 2 == 1
        return if (parity == "单周") odd else !odd
    }

    /** 单双周过滤: filter = 全部/单周/双周 */
    fun matchesParity(filter: String, week: Int): Boolean {
        if (parity.isEmpty()) return true
        val odd = week % 2 == 1
        return when (filter) {
            "单周" -> odd && parity == "单周"
            "双周" -> !odd && parity == "双周"
            else -> true
        }
    }

    /** 课程块标题行（类型标记拼回来用于显示） */
    fun displayTitle(): String = if (type.isEmpty()) name else "$name[$type]"
}

/** 导入的课表元信息 */
data class ScheduleMeta(
    val semester: String = "",
    val className: String = "",
    val studentId: String = "",
    val major: String = "",
    val printTime: String = "",
    /** 第 1 周周一的 epochDay，用于算当前周次与单双周 */
    val termStartEpochDay: Long? = null,
    val importedAt: Long = System.currentTimeMillis(),
)

/**
 * 保证每门课程都有唯一 id。
 *
 * PDF 导入的课程 id 全是 0，如果直接按 id 删除/编辑，会一次命中所有课程
 * （表现为「删除一门课却把整个课表删光」）。加载与导入后都必须过一遍这里。
 */
fun List<Course>.withUniqueIds(): List<Course> {
    val used = HashSet<Long>()
    var next = 1L
    return map { course ->
        if (course.id > 0L && used.add(course.id)) {
            course
        } else {
            while (used.contains(next)) next++
            used.add(next)
            course.copy(id = next++)
        }
    }
}
