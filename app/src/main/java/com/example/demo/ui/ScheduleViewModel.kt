package com.example.demo.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.demo.data.Course
import com.example.demo.data.PdfScheduleParser
import com.example.demo.data.ScheduleMeta
import com.example.demo.data.ScheduleStore
import com.example.demo.data.ZfConfig
import com.example.demo.data.ZfResult
import com.example.demo.data.ZhengfangImporter
import com.example.demo.data.withUniqueIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface ImportState {
    data object Idle : ImportState
    data object Parsing : ImportState
    data class Done(val count: Int) : ImportState
    data class Error(val message: String) : ImportState
}

class ScheduleViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ScheduleStore(app)

    private val _meta = MutableStateFlow<ScheduleMeta?>(null)
    val meta: StateFlow<ScheduleMeta?> = _meta

    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState

    /** 教务系统导入的诊断日志（失败时给用户看） */
    private val _zfLog = MutableStateFlow("")
    val zfLog: StateFlow<String> = _zfLog

    init {
        viewModelScope.launch(Dispatchers.IO) {
            store.load()?.let { snap ->
                // id 必须唯一，否则删除/编辑会作用到所有课程上
                val fixed = snap.courses.withUniqueIds()
                _meta.value = snap.meta
                _courses.value = fixed
                // 老数据（id 全为 0）规整后写回
                if (fixed != snap.courses) store.save(snap.meta, fixed)
            }
        }
    }

    /** 从系统文件选择器拿到的 PDF Uri 导入课表 */
    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            _importState.value = ImportState.Parsing
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val input = getApplication<Application>()
                        .contentResolver.openInputStream(uri)
                        ?: throw IOException("无法打开所选文件")
                    input.use { PdfScheduleParser.parse(it) }
                }
                val fixed = parsed.courses.withUniqueIds()
                withContext(Dispatchers.IO) {
                    store.save(parsed.meta, fixed)
                }
                _meta.value = parsed.meta
                _courses.value = fixed
                _importState.value = ImportState.Done(fixed.size)
            } catch (e: Exception) {
                _importState.value = ImportState.Error(
                    e.message ?: "解析失败，请确认这是正方教务导出的课表 PDF"
                )
            }
        }
    }

    /** 从正方教务系统直接导入课表（学号+密码登录后抓取） */
    fun importFromZhengfang(cfg: ZfConfig) {
        viewModelScope.launch {
            _importState.value = ImportState.Parsing
            _zfLog.value = ""
            when (val r = ZhengfangImporter.fetch(cfg)) {
                is ZfResult.Ok -> {
                    val fixed = r.courses.withUniqueIds()
                    val m = _meta.value ?: ScheduleMeta()
                    withContext(Dispatchers.IO) { store.save(m, fixed) }
                    _meta.value = m
                    _courses.value = fixed
                    _zfLog.value = r.log
                    _importState.value = ImportState.Done(fixed.size)
                }
                is ZfResult.Fail -> {
                    _zfLog.value = r.log
                    _importState.value = ImportState.Error(r.message)
                }
            }
        }
    }

    /** 设置开学日期（第 1 周周一的 epochDay） */
    fun setTermStart(epochDay: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val snap = store.load() ?: return@launch
            val newMeta = snap.meta.copy(termStartEpochDay = epochDay)
            store.save(newMeta, snap.courses.withUniqueIds())
            _meta.value = newMeta
        }
    }

    /** 新增或更新一门课程（id 为 0 表示新增） */
    fun saveCourse(course: Course) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = _courses.value.withUniqueIds().toMutableList()
            fun nextId(): Long = (list.maxOfOrNull { it.id } ?: 0L) + 1

            if (course.id == 0L) {
                list.add(course.copy(id = nextId()))
            } else {
                var idx = list.indexOfFirst { it.id == course.id }
                if (idx < 0) {
                    // id 对不上时按内容定位，避免重复添加
                    idx = list.indexOfFirst {
                        it.name == course.name && it.day == course.day &&
                            it.periodStart == course.periodStart
                    }
                }
                if (idx >= 0) {
                    list[idx] = course.copy(id = list[idx].id)
                } else {
                    list.add(course.copy(id = nextId()))
                }
            }
            val m = _meta.value ?: ScheduleMeta()
            store.save(m, list)
            _courses.value = list
        }
    }

    /** 删除一门课程（只删这一门，绝不牵连其它课程） */
    fun deleteCourse(course: Course) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = _courses.value.withUniqueIds().toMutableList()
            val byId = if (course.id > 0L) list.indexOfFirst { it.id == course.id } else -1
            val target = if (byId >= 0) {
                byId
            } else {
                // id 对不上时按内容只删第一门匹配的
                list.indexOfFirst {
                    it.name == course.name && it.day == course.day &&
                        it.periodStart == course.periodStart
                }
            }
            if (target >= 0) list.removeAt(target)
            val m = _meta.value ?: ScheduleMeta()
            store.save(m, list)
            _courses.value = list
        }
    }

    /** 清空已导入的课表 */
    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            store.clear()
            _meta.value = ScheduleMeta(importedAt = System.currentTimeMillis())
            _courses.value = emptyList()
        }
    }
}
