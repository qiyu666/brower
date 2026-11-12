# Qiyu Browser

一款基于 WebView 的轻量级 Android 浏览器，功能对标 Chrome / Edge，使用 Jetpack Compose 构建。

## 功能

### 核心浏览

- **多标签管理** — 无限制开标签，随时切换、关闭
- **标签切换器** — 2 列卡片布局，显示网页缩略图 + 网站 favicon + 域名，当前标签蓝色边框
- **新标签页** — 原生 Compose 页面，搜索框 + 浏览器图标 + 最近访问站点
- **地址栏** — 键盘回车直接提交，无多余按钮；域名（如 `github.com`、`baidu.com/x`）自动识别为 URL，含空格自动搜索
- **下拉刷新** — 页面滚到最顶部时才触发，中间滚动不会误触
- **内存优化** — 切走标签自动 `onPause` 释放后台 WebView 内存，切回恢复

### 隐私与安全

- **隐私模式** — 无痕标签禁用 DOM 存储和数据库，关闭时自动清除访问域名的 cookies
- **拦截弹窗** — 设置中可开关
- **自动播放控制** — 视频自动播放开关

### 外观与定制

- **深色模式** — 跟随系统 / 浅色 / 深色
- **字号调节** — 75% ~ 150%
- **桌面模式** — 切换为桌面版 Chrome UA，显示桌面版网页
- **多搜索引擎** — Google / Bing / DuckDuckGo / Baidu

### 下载与数据

- **自定义下载目录** — 预设（默认 / 图片 / 视频 / 音乐）或任意目录（SAF 选择器）
- **下载管理** — 进度条、暂停、重试，前台服务保证后台继续下载
- **书签** — 收藏网页，快速打开
- **浏览历史** — 搜索、按时间排序、单条删除、全部清除

### 界面

- **启动闪屏** — 黑底 + 白色 N 图标，无空白闪屏
- **底部导航** — Chrome 风格：后退 / 前进 / 新标签 / 标签数 / 菜单
- **中英双语** — 完整 i18n 支持

## 技术栈

| 层 | 技术 |
|---|---|
| UI | Jetpack Compose (Material3) |
| 语言 | Kotlin |
| 数据 | Room + Kotlin Coroutines/Flow |
| 最低版本 | Android 8.0 (API 26) |
| 目标版本 | Android 14 (API 34) |

## 安装

### 从 Release 下载

前往 [Releases](https://github.com/qiyu666/brower/releases) 下载最新 APK，直接安装。

### 从源码构建

```bash
# 需要 Android SDK + JDK 17
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

或使用 GitHub Actions 自动构建（见 `.github/workflows/build-apk.yml`）。

## 许可证

MIT
