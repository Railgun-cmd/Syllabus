package com.example.demo.data

/**
 * 节次时间表（按学校作息，共 10 节）
 * 数组索引 0 对应第 1 节
 */
object ClassTime {

    /** 每节的 (开始, 结束) 时间 */
    val TIMES: List<Pair<String, String>> = listOf(
        "08:20" to "09:05",   // 1
        "09:10" to "09:55",   // 2
        "10:10" to "10:55",   // 3
        "11:00" to "11:45",   // 4
        "14:00" to "14:45",   // 5
        "14:50" to "15:35",   // 6
        "15:50" to "16:35",   // 7
        "16:40" to "17:25",   // 8
        "19:00" to "19:45",   // 9
        "19:50" to "20:35",   // 10
    )

    /** 总节数 */
    val PERIOD_COUNT: Int get() = TIMES.size

    fun start(period: Int): String = TIMES.getOrNull(period - 1)?.first ?: ""

    fun end(period: Int): String = TIMES.getOrNull(period - 1)?.second ?: ""

    /** "08:20" -> 从 0 点起的分钟数 */
    fun toMinutes(hhmm: String): Int {
        val parts = hhmm.split(":")
        if (parts.size != 2) return 0
        return (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
    }

    /** 第 period 节的结束时间（分钟） */
    fun endMinutes(period: Int): Int = toMinutes(end(period))
}
