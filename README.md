# 狮课表 🦁

一款面向中国大学生的 **Android 课表 App**：把教务系统导出的 **PDF 课表**一键导入成漂亮的周视图课表。

> 山东师范大学 · Android 客户端（Kotlin + Jetpack Compose）

![应用图标](docs/icon.png)

---

## ✨ 功能特性

| 功能 | 说明 |
|------|------|
| 📄 **PDF 课表导入** | 支持**正方教务系统**导出的「班级课表」和「个人课表」PDF，自动解析课程名、教师、教室、周次、单双周 |
| 📅 **周视图课表** | 左侧节次 + 起止时间，右侧 7 天课程块，**跨节课程自动合并**显示 |
| 👉 **滑动换周** | 左右滑动切换周次，也支持 `◀` `▶` 按钮平滑滚动 |
| 🎨 **状态着色** | **非本周**课程灰显并标注；**今天已上完**的课自动变灰 |
| ✏️ **自定义增删改** | 顶部 `+` 手动添加课程；点击课程块可**编辑 / 删除**（补录教务系统没导出的课）|
| ⏰ **作息时间表** | 内置 10 节课起止时间 |
| 🏫 **校区教室** | 课程块直接显示 `@校区 教学楼 教室` |
| 💾 **纯本地存储** | 数据存本地 JSON，**不联网、不上传** |

## 📲 下载安装

直接下载仓库里的 [`release/狮课表-v0.23-测试版.apk`](release/)

- 系统要求：**Android 7.0（API 24）及以上**
- 安装：手机允许「安装未知来源应用」后点击安装

> ⚠️ 这是 **debug 签名**的测试版，适合自用和分发测试；若要上架商店需自行配置正式签名。

## 📄 支持的 PDF 格式

| 格式 | 说明 |
|------|------|
| 正方教务 · 班级课表 | 教务系统「班级课表」导出/打印为 PDF |
| 正方教务 · 个人课表 | 学生个人课表（含学分、考核方式、学时等）|

**解析思路**：不使用 `PDFTextStripper`（中文字体常缺 ToUnicode 表，提取出来是乱码），而是直接读取 PDF 内容流的 `COSString` 原始字节，按 **UniGB-UCS2-H** 解码；再按 x 坐标切分列、按 y 间距聚类成文本块，最后用正则抽取字段，并支持跨页续表。

## 🛠 编译构建

环境要求：**Android Studio 2026.2+**（AGP 9.4 / Kotlin 2.2 / Gradle 9.6 / JDK 17+ / compileSdk 37）

```bash
git clone <your-repo-url>
cd project

# 命令行构建
./gradlew assembleDebug          # 产物: app/build/outputs/apk/debug/app-debug.apk

# 或直接用 Android Studio 打开 → Sync → Run
```

> **国内网络提示**：若 Maven 下载缓慢，可在 `~/.gradle/init.d/` 放置阿里云镜像脚本，或改用腾讯/清华的 Gradle 发行版镜像。

## 🧱 技术栈

- **Kotlin 2.2** + **Jetpack Compose**（Material 3）
- **PDFBox-Android 2.0.27** —— PDF 内容流解析
- **org.json** —— 本地 JSON 持久化（刻意不用 Room/KSP，减少编译期依赖）
- 单 Activity + ViewModel + `StateFlow`

## 📁 目录结构

```
app/src/main/java/com/example/demo/
├── MainActivity.kt
├── data/
│   ├── Course.kt              # 课程数据模型（含 id 唯一性约束）
│   ├── ClassTime.kt           # 节次时间表（10 节）
│   ├── PdfScheduleParser.kt   # ★ PDF 课表解析器
│   └── ScheduleStore.kt       # JSON 本地存储
└── ui/
    ├── ScheduleScreen.kt      # 主界面（周视图 / 滑动换周）
    ├── ScheduleViewModel.kt   # 状态与业务逻辑
    └── CourseEditDialog.kt    # 添加 / 编辑课程
```

## 📝 版本历史

| 版本 | 说明 |
|------|------|
| **0.23** | 应用名改为「狮课表」，图标换成山东师大标志 |
| 0.22 | 去掉午休 / 大课间分段，课表恢复连续排列 |
| 0.21 | 作息时间改为 10 节 |
| 0.2 | 图标换成鲸鱼娘 |
| 0.1 | 首个测试版：PDF 导入 + 周视图课表 |

## ⚠️ 已知问题

- 目前仅适配**正方教务**导出的课表 PDF
- 节次固定 10 节，PDF 中第 11 节及以后的课程会被归到第 10 节
- 若同一格有多门课，暂只显示第一门

## 📄 声明

本项目仅供学习交流使用。校徽等标识的版权归**山东师范大学**所有。
