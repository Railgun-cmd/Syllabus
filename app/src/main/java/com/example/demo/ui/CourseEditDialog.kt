package com.example.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.demo.data.ClassTime
import com.example.demo.data.Course

private val DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
private val PARITY_LABELS = listOf("每周", "单周", "双周")
private val PARITY_VALUES = listOf("", "单周", "双周")

/**
 * 添加 / 编辑课程弹窗。
 * initial == null 表示新增。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditDialog(
    initial: Course?,
    defaultDay: Int = 1,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var day by remember { mutableIntStateOf(initial?.day?.takeIf { it in 1..7 } ?: defaultDay) }
    var start by remember { mutableIntStateOf(initial?.periodStart?.takeIf { it >= 1 } ?: 1) }
    var end by remember { mutableIntStateOf(initial?.periodEnd?.takeIf { it >= 1 } ?: 2) }
    var weekStart by remember { mutableIntStateOf(initial?.weekStart?.takeIf { it >= 1 } ?: 1) }
    var weekEnd by remember { mutableIntStateOf(initial?.weekEnd?.takeIf { it >= 1 } ?: 18) }
    var parity by remember { mutableStateOf(initial?.parity ?: "") }
    var campus by remember { mutableStateOf(initial?.campus ?: "") }
    var room by remember { mutableStateOf(initial?.room ?: "") }
    var teacher by remember { mutableStateOf(initial?.teacher ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) "添加课程" else "编辑课程",
                fontWeight = FontWeight.Bold,
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        Course(
                            id = initial?.id ?: 0L,
                            name = name.trim(),
                            type = initial?.type ?: "",
                            day = day,
                            periodStart = start,
                            periodEnd = if (end < start) start else end,
                            weekStart = weekStart,
                            weekEnd = if (weekEnd < weekStart) weekStart else weekEnd,
                            parity = parity,
                            campus = campus.trim(),
                            room = room.trim(),
                            teacher = teacher.trim(),
                            composition = initial?.composition ?: "",
                            nature = initial?.nature ?: "",
                            enrollment = initial?.enrollment,
                            assessment = initial?.assessment ?: "",
                            note = initial?.note ?: "",
                            hourComposition = initial?.hourComposition ?: "",
                            weeklyHours = initial?.weeklyHours ?: "",
                            totalHours = initial?.totalHours ?: "",
                            credits = initial?.credits ?: "",
                        )
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课程名（必填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                ChipRow("星期", DAY_NAMES, day - 1) { day = it + 1 }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("节次", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NumberPicker("第", start, 1..ClassTime.PERIOD_COUNT) {
                        start = it
                        if (end < it) end = it
                    }
                    Text("~", fontSize = 13.sp)
                    NumberPicker("", end, 1..ClassTime.PERIOD_COUNT) {
                        end = it
                        if (start > it) start = it
                    }
                    Text("节", fontSize = 13.sp)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("周次", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NumberPicker("第", weekStart, 1..25) {
                        weekStart = it
                        if (weekEnd < it) weekEnd = it
                    }
                    Text("~", fontSize = 13.sp)
                    NumberPicker("", weekEnd, 1..25) {
                        weekEnd = it
                        if (weekStart > it) weekStart = it
                    }
                    Text("周", fontSize = 13.sp)
                }

                ChipRow("单双周", PARITY_LABELS, PARITY_VALUES.indexOf(parity)) {
                    parity = PARITY_VALUES[it]
                }

                OutlinedTextField(
                    value = campus,
                    onValueChange = { campus = it },
                    label = { Text("校区（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("教室（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("教师（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun ChipRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, text ->
                val selected = i == selectedIndex
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onSelect(i) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text,
                        fontSize = 12.sp,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberPicker(
    prefix: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("$prefix$value", fontSize = 13.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            range.forEach { v ->
                DropdownMenuItem(
                    text = { Text("$prefix$v", fontSize = 13.sp) },
                    onClick = {
                        onChange(v)
                        expanded = false
                    },
                )
            }
        }
    }
}
