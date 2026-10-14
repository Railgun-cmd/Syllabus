package com.example.demo.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.demo.data.ClassTime
import com.example.demo.data.Course
import com.example.demo.data.ScheduleMeta
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

// ---------------- 视觉常量 ----------------

private val CELL_HEIGHT = 64.dp
private val PERIOD_COL_WIDTH = 48.dp
private const val MIN_WEEKS = 25

/** 第 period 节顶部的 y 偏移 */
private fun offsetOfPeriod(period: Int): Dp = CELL_HEIGHT * (period - 1)

/** 第 s..e 节课程块的高度 */
private fun heightOfBlock(s: Int, e: Int): Dp = CELL_HEIGHT * (e - s + 1)

/** 整个课表网格的高度 */
private fun gridHeight(): Dp = CELL_HEIGHT * ClassTime.PERIOD_COUNT

private val COURSE_COLORS = listOf(
    Color(0xFF4A7BD8), Color(0xFF3AA88A), Color(0xFFE0873F), Color(0xFF8070C8),
    Color(0xFFCC5A72), Color(0xFF3E9BC2), Color(0xFFB08A35), Color(0xFF5C9270),
    Color(0xFF7A6BB5), Color(0xFF4E8FA8),
)

private fun courseColor(name: String): Color = COURSE_COLORS[abs(name.hashCode()) % COURSE_COLORS.size]

private val DIM_COLOR = Color(0xFF9E9E9E)

private val DAY_SHORT = listOf("一", "二", "三", "四", "五", "六", "日")
private val DAY_FULL = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

// ---------------- 日期工具 ----------------

fun todayEpochDay(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis / 86_400_000L
}

/** 某天所在周的周一（epochDay） */
fun mondayEpochDayOf(epochDay: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = epochDay * 86_400_000L }
    val dow = cal.get(Calendar.DAY_OF_WEEK) // 1=周日 ... 7=周六
    val offset = if (dow == Calendar.SUNDAY) -6 else Calendar.MONDAY - dow
    return epochDay + offset
}

/** 1=周一 ... 7=周日 */
fun dayOfWeekOf(epochDay: Long): Int {
    val cal = Calendar.getInstance().apply { timeInMillis = epochDay * 86_400_000L }
    val d = cal.get(Calendar.DAY_OF_WEEK)
    return if (d == Calendar.SUNDAY) 7 else d - 1
}

/** 第 week 周的周一；未设开学日期时以「本周」为基准 */
fun weekStartOf(termStartEpochDay: Long?, week: Int): Long {
    val base = termStartEpochDay ?: mondayEpochDayOf(todayEpochDay())
    return base + (week - 1) * 7L
}

/** 今天属于第几周（未设置开学日期时返回 null） */
fun currentWeekOf(termStartEpochDay: Long?): Int? {
    if (termStartEpochDay == null) return null
    return (((todayEpochDay() - termStartEpochDay) / 7) + 1).toInt().coerceIn(1, MIN_WEEKS)
}

fun formatEpochDay(day: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = day * 86_400_000L }
    return "%d/%d/%d".format(
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
    )
}

fun dayOfMonthOf(day: Long): Int {
    val cal = Calendar.getInstance().apply { timeInMillis = day * 86_400_000L }
    return cal.get(Calendar.DAY_OF_MONTH)
}

private fun monthOf(day: Long): Int {
    val cal = Calendar.getInstance().apply { timeInMillis = day * 86_400_000L }
    return cal.get(Calendar.MONTH) + 1
}

private fun nowMinutesOfDay(): Int {
    val cal = Calendar.getInstance()
    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}

// ---------------- 主界面 ----------------

@Composable
fun ScheduleScreen(vm: ScheduleViewModel = viewModel()) {
    val meta by vm.meta.collectAsState()
    val courses by vm.courses.collectAsState()
    val importState by vm.importState.collectAsState()

    val today = remember { todayEpochDay() }
    val todayDow = remember { dayOfWeekOf(today) }
    // 当前时间（分钟）：每 30 秒刷新一次，让「已上」状态自动更新
    var nowMinutes by remember { mutableIntStateOf(nowMinutesOfDay()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowMinutes = nowMinutesOfDay()
        }
    }
    val scope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }
    val openPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importPdf(it) } }

    LaunchedEffect(importState) {
        when (val s = importState) {
            is ImportState.Done -> snackbarHostState.showSnackbar("导入成功：${s.count} 门课程")
            is ImportState.Error -> snackbarHostState.showSnackbar("导入失败：${s.message}")
            else -> {}
        }
    }

    // 周数上限：至少 25 周，课程周次更大时自动扩展
    val maxWeek = remember(courses) {
        maxOf(MIN_WEEKS, courses.maxOfOrNull { it.weekEnd } ?: 0)
    }

    // 当前周（未设置开学日期时为 null）
    val currentWeek = remember(meta?.termStartEpochDay) { currentWeekOf(meta?.termStartEpochDay) }
    // 首屏直接落在当周，避免先显示第 1 周再跳过去
    val initialPage = ((currentWeek ?: 1) - 1).coerceIn(0, maxWeek - 1)

    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { maxWeek })
    val selectedWeek = pagerState.currentPage + 1

    // 回到前台时自动回到当周（进入应用保持在当周课程）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, currentWeek, maxWeek) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && currentWeek != null) {
                scope.launch {
                    pagerState.scrollToPage((currentWeek - 1).coerceIn(0, maxWeek - 1))
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var detailCourse by remember { mutableStateOf<Course?>(null) }
    var editingCourse by remember { mutableStateOf<Course?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Course?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val todayInSelectedWeek = today in
        weekStartOf(meta?.termStartEpochDay, selectedWeek)..
        (weekStartOf(meta?.termStartEpochDay, selectedWeek) + 6)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (courses.isEmpty()) {
                EmptyState(
                    onImportPdf = { openPdf.launch(arrayOf("application/pdf")) },
                    onAddManual = {
                        editingCourse = null
                        showEditor = true
                    },
                )
            } else if (meta == null) {
                // 等元信息加载完再建 Pager，避免首屏闪一下第 1 周
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    ScheduleHeader(
                        week = selectedWeek,
                        todayDow = todayDow,
                        today = today,
                        weekStart = weekStartOf(meta?.termStartEpochDay, selectedWeek),
                        todayInSelectedWeek = todayInSelectedWeek,
                        importing = importState is ImportState.Parsing,
                        canPrev = pagerState.currentPage > 0,
                        canNext = pagerState.currentPage < maxWeek - 1,
                        onPrevWeek = {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    (pagerState.currentPage - 1).coerceAtLeast(0)
                                )
                            }
                        },
                        onNextWeek = {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    (pagerState.currentPage + 1).coerceAtMost(maxWeek - 1)
                                )
                            }
                        },
                        onAdd = {
                            editingCourse = null
                            showEditor = true
                        },
                        onMore = { showSettings = true },
                    )

                    // ---- 左右滑动切换周 ----
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        val week = page + 1
                        val pageWeekStart = weekStartOf(meta?.termStartEpochDay, week)
                        val pageTodayInWeek = today in pageWeekStart..(pageWeekStart + 6)
                        Column(Modifier.fillMaxSize()) {
                            WeekDateStrip(weekStart = pageWeekStart, today = today)
                            WeekGrid(
                                courses = courses,
                                selectedWeek = week,
                                todayDow = todayDow,
                                todayInSelectedWeek = pageTodayInWeek,
                                nowMinutes = nowMinutes,
                                currentWeek = currentWeek,
                                onCourseClick = { detailCourse = it },
                            )
                        }
                    }
                }
            }
        }
    }

    detailCourse?.let { c ->
        CourseDetailDialog(
            course = c,
            onEdit = {
                detailCourse = null
                editingCourse = c
                showEditor = true
            },
            onDelete = {
                detailCourse = null
                confirmDelete = c
            },
            onDismiss = { detailCourse = null },
        )
    }

    if (showEditor) {
        CourseEditDialog(
            initial = editingCourse,
            defaultDay = if (todayInSelectedWeek) todayDow else 1,
            onDismiss = { showEditor = false },
            onSave = { course ->
                vm.saveCourse(course)
                showEditor = false
                editingCourse = null
            },
        )
    }

    confirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除课程") },
            text = { Text("确定删除「${c.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCourse(c)
                    confirmDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("取消") }
            },
        )
    }

    if (showSettings) {
        SettingsDialog(
            meta = meta,
            onImportPdf = {
                showSettings = false
                openPdf.launch(arrayOf("application/pdf"))
            },
            onEditDate = { showSettings = false; showDatePicker = true },
            onClear = { showSettings = false; showClearConfirm = true },
            onDismiss = { showSettings = false },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空全部课程") },
            text = { Text("这会删除课表里的全部课程，且无法撤销。确定继续吗？") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAll()
                    showClearConfirm = false
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showDatePicker) {
        TermStartPicker(
            termStartEpochDay = meta?.termStartEpochDay,
            onConfirm = { d -> vm.setTermStart(d); showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }
}

// ---------------- 顶部标题栏 ----------------

@Composable
private fun ScheduleHeader(
    week: Int,
    todayDow: Int,
    today: Long,
    weekStart: Long,
    todayInSelectedWeek: Boolean,
    importing: Boolean,
    canPrev: Boolean,
    canNext: Boolean,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onAdd: () -> Unit,
    onMore: () -> Unit,
) {
    val title = if (todayInSelectedWeek) "第${week}周 ${DAY_FULL[todayDow - 1]}" else "第${week}周"
    val subtitle = if (todayInSelectedWeek) {
        formatEpochDay(today)
    } else {
        "${monthOf(weekStart)}/${dayOfMonthOf(weekStart)} - ${monthOf(weekStart + 6)}/${dayOfMonthOf(weekStart + 6)}"
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (importing) {
            CircularProgressIndicator(
                Modifier.padding(end = 4.dp).size(18.dp),
                strokeWidth = 2.dp,
            )
        }
        HeaderButton("◀", enabled = canPrev, onClick = onPrevWeek)
        HeaderButton("▶", enabled = canNext, onClick = onNextWeek)
        HeaderButton("+", enabled = true, onClick = onAdd)
        HeaderButton("⋯", enabled = true, onClick = onMore)
    }
}

@Composable
private fun HeaderButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 17.sp,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
        )
    }
}

// ---------------- 周日期条 ----------------

@Composable
private fun WeekDateStrip(weekStart: Long, today: Long) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(PERIOD_COL_WIDTH), contentAlignment = Alignment.Center) {
            Text(
                "${monthOf(weekStart)}月",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (i in 0..6) {
            val day = weekStart + i
            val isToday = day == today
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    DAY_SHORT[i],
                    fontSize = 11.sp,
                    color = if (isToday) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (isToday) Color(0xFF303540) else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${dayOfMonthOf(day)}",
                        fontSize = 13.sp,
                        color = if (isToday) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

// ---------------- 周视图网格 ----------------

@Composable
private fun WeekGrid(
    courses: List<Course>,
    selectedWeek: Int,
    todayDow: Int,
    todayInSelectedWeek: Boolean,
    nowMinutes: Int,
    currentWeek: Int?,
    onCourseClick: (Course) -> Unit,
) {
    val totalHeight = gridHeight()

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth()) {
            // ---- 左侧：节次 + 时间 ----
            Column(Modifier.width(PERIOD_COL_WIDTH)) {
                for (p in 1..ClassTime.PERIOD_COUNT) {
                    Column(
                        Modifier.height(CELL_HEIGHT).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "$p",
                            fontSize = 14.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            ClassTime.start(p),
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            ClassTime.end(p),
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        )
                    }
                }
            }

            // 节次列与课表之间的竖分隔线
            Box(
                Modifier
                    .width(0.5.dp)
                    .height(totalHeight)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )

            // ---- 7 天 ----
            for (day in 1..7) {
                val dayCourses = courses
                    .filter { it.day == day && !it.isOther && it.periodStart >= 1 }
                    .sortedBy { it.periodStart }

                Box(Modifier.weight(1f).height(totalHeight)) {
                    // 每节底部的分隔线
                    for (p in 1..ClassTime.PERIOD_COUNT) {
                        Box(
                            Modifier
                                .offset(y = offsetOfPeriod(p) + CELL_HEIGHT)
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                    dayCourses.forEach { c ->
                        val s = c.periodStart.coerceIn(1, ClassTime.PERIOD_COUNT)
                        val e = c.periodEnd.coerceIn(s, ClassTime.PERIOD_COUNT)
                        val inWeek = c.activeInWeek(selectedWeek)
                        // 今天这一节是否已经下课
                        val todayEnded = todayInSelectedWeek && c.day == todayDow &&
                            nowMinutes > ClassTime.endMinutes(c.periodEnd)
                        // 「过往」判定：过去的周次 / 本周更早的星期 / 今天已下课
                        val past = when {
                            !inWeek -> false
                            currentWeek != null && selectedWeek < currentWeek -> true
                            !todayInSelectedWeek -> false
                            c.day < todayDow -> true
                            else -> todayEnded
                        }
                        val tag = when {
                            !inWeek -> "非本周"
                            todayEnded -> "已上"
                            else -> null
                        }
                        CourseBlock(
                            course = c,
                            tag = tag,
                            dim = !inWeek || past,
                            modifier = Modifier
                                .offset(y = offsetOfPeriod(s))
                                .fillMaxWidth()
                                .height(heightOfBlock(s, e) - 2.dp),
                            onClick = { onCourseClick(c) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CourseBlock(
    course: Course,
    tag: String?,
    dim: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val base = if (dim) DIM_COLOR else courseColor(course.name)
    Column(
        modifier
            .padding(horizontal = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(base.copy(alpha = if (dim) 0.5f else 0.92f))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 3.dp),
    ) {
        Text(
            course.name,
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        val location = buildString {
            if (course.campus.isNotEmpty() || course.room.isNotEmpty()) {
                append("@")
                append(course.campus)
                append(course.room)
            }
        }
        if (location.isNotEmpty()) {
            Text(
                location,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 8.sp,
                lineHeight = 10.sp,
            )
        }
        if (course.teacher.isNotEmpty()) {
            Text(
                course.teacher,
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 8.sp,
                lineHeight = 10.sp,
            )
        }
        if (tag != null) {
            Text(
                tag,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 8.sp,
                lineHeight = 10.sp,
            )
        }
    }
}

// ---------------- 空状态 ----------------

@Composable
private fun EmptyState(onImportPdf: () -> Unit, onAddManual: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🐳", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text("还没有课表哦", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "导入教务系统导出的 PDF，或者手动添加课程",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = onImportPdf) { Text("导入课表 PDF") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onAddManual) { Text("手动添加课程") }
    }
}

// ---------------- 弹窗 ----------------

@Composable
private fun CourseDetailDialog(
    course: Course,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(course.displayTitle(), fontWeight = FontWeight.Bold) },
        confirmButton = { TextButton(onClick = onEdit) { Text("编辑") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val rows = buildList {
                    if (!course.isOther) {
                        val d = DAY_FULL[course.day.coerceIn(1, 7) - 1]
                        add("时间" to "$d 第${course.periodStart}-${course.periodEnd}节")
                        add(
                            "节次时间" to
                                "${ClassTime.start(course.periodStart)} - ${ClassTime.end(course.periodEnd)}"
                        )
                    }
                    add(
                        "周次" to buildString {
                            append("${course.weekStart}-${course.weekEnd}周")
                            if (course.parity.isNotEmpty()) append("（${course.parity}）")
                            course.totalWeeks?.let { append("（共${it}周）") }
                        }
                    )
                    if (course.room.isNotEmpty()) add("教室" to course.room)
                    if (course.teacher.isNotEmpty()) add("教师" to course.teacher)
                    if (course.classNo.isNotEmpty()) add("教学班" to course.classNo)
                    if (course.nature.isNotEmpty()) add("性质" to course.nature)
                    if (course.assessment.isNotEmpty()) add("考核方式" to course.assessment)
                    if (course.credits.isNotEmpty()) add("学分" to course.credits)
                    course.enrollment?.let { add("选课人数" to "$it 人") }
                    if (course.note.isNotEmpty()) add("备注" to course.note)
                    if (course.composition.isNotEmpty()) add("班组成" to course.composition)
                    if (course.campus.isNotEmpty()) add("校区" to course.campus)
                }
                rows.forEach { (k, v) ->
                    Row {
                        Text(
                            "$k：",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(v)
                    }
                }
            }
        },
    )
}

@Composable
private fun SettingsDialog(
    meta: ScheduleMeta?,
    onImportPdf: () -> Unit,
    onEditDate: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text("设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoRow("学期", meta?.semester)
                InfoRow("班级", meta?.className)
                InfoRow("学号", meta?.studentId)
                InfoRow("专业", meta?.major)
                InfoRow("打印时间", meta?.printTime)
                InfoRow("版本", "0.1 测试版")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("开学日期：", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(meta?.termStartEpochDay?.let { formatEpochDay(it) } ?: "未设置")
                    TextButton(onClick = onEditDate) { Text("修改") }
                }
                Text(
                    "开学日期用于计算「第几周」和单双周",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onImportPdf) { Text("重新导入课表 PDF") }
                TextButton(onClick = onClear) {
                    Text("清空全部课程", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String?) {
    Row {
        Text("$label：", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.takeIf { it.isNotEmpty() } ?: "—")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TermStartPicker(
    termStartEpochDay: Long?,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = termStartEpochDay?.times(86_400_000L)
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onConfirm(it / 86_400_000L) } ?: onDismiss()
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}
