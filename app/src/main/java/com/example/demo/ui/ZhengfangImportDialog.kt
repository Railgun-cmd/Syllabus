package com.example.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.demo.data.ZfConfig
import com.example.demo.data.ZhengfangImporter

/**
 * 从正方教务系统导入课表。
 * 各校系统略有差异，所以失败时把诊断日志一并展示，便于定位。
 */
@Composable
fun ZhengfangImportDialog(
    importing: Boolean,
    errorMessage: String?,
    diagnosticLog: String,
    onDismiss: () -> Unit,
    onImport: (ZfConfig) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    var year by remember { mutableStateOf(ZhengfangImporter.defaultSchoolYear()) }
    var term by remember { mutableStateOf(ZhengfangImporter.defaultTerm()) }
    var showLog by remember { mutableStateOf(false) }

    val canSubmit = !importing && url.isNotBlank() && user.isNotBlank() && pwd.isNotBlank()

    AlertDialog(
        onDismissRequest = { if (!importing) onDismiss() },
        title = { Text("从教务系统导入", fontWeight = FontWeight.Bold) },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = {
                    onImport(
                        ZfConfig(
                            baseUrl = url.trim(),
                            username = user.trim(),
                            password = pwd,
                            schoolYear = year.trim(),
                            term = term.trim(),
                        )
                    )
                },
            ) { Text("开始导入") }
        },
        dismissButton = {
            TextButton(enabled = !importing, onClick = onDismiss) { Text("取消") }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("教务系统地址") },
                    placeholder = { Text("http://jwxt.xxx.edu.cn") },
                    singleLine = true,
                    enabled = !importing,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("学号") },
                    singleLine = true,
                    enabled = !importing,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pwd,
                    onValueChange = { pwd = it },
                    label = { Text("密码") },
                    singleLine = true,
                    enabled = !importing,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it },
                        label = { Text("学年") },
                        placeholder = { Text("2026") },
                        singleLine = true,
                        enabled = !importing,
                        modifier = Modifier.weight(1f),
                    )
                    ChipPick(
                        label = "学期",
                        options = listOf("第一学期" to "3", "第二学期" to "12"),
                        selected = term,
                        enabled = !importing,
                        onSelect = { term = it },
                        modifier = Modifier.weight(1f),
                    )
                }

                Text(
                    "· 密码只用于本次登录，不会保存到手机\n" +
                        "· 很多学校的教务系统只能在校园网/VPN 下访问",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (importing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("正在登录并抓取课表…", fontSize = 12.sp)
                    }
                }

                if (errorMessage != null && !importing) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(8.dp),
                    ) {
                        Text(
                            errorMessage,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    TextButton(onClick = { showLog = !showLog }) {
                        Text(if (showLog) "收起诊断日志" else "查看诊断日志", fontSize = 12.sp)
                    }
                    if (showLog) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                diagnosticLog.ifEmpty { "(无日志)" },
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ChipPick(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (text, value) ->
                val on = value == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (on) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable(enabled = enabled) { onSelect(value) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        text,
                        fontSize = 12.sp,
                        color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
