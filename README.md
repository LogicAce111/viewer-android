# Viewer Android 1.2.0

1.2.0包含沉浸阅读、历史位置提示与返回顶部、漫画目录分类、持久目录索引，以及四类目录点击反馈圆角。沿用原应用ID和正式签名，支持覆盖安装，跳过1.1。详情见 [更新说明](CHANGELOG.md)，安装包见 [GitHub Releases](https://github.com/LogicAce111/viewer-android/releases)。

“搜索”功能将在1.3版本推出。

Viewer 是一个目录驱动、完全本地的 Android 全媒体播放器。用户分别指定视频、音乐、文本和漫画目录，应用只读取所选目录，并按分类独立过滤文件。

Windows 版本请访问 [LogicAce111/viewer-windows](https://github.com/LogicAce111/viewer-windows)。

## 发布信息

- 应用 ID：`com.legion.viewer`
- 版本：`1.2.0`（versionCode 4）
- 最低系统：Android 8.0（API 26）
- 目标系统：Android API 37
- CPU 架构：仅 `arm64-v8a`
- 网络权限：无
- 存储方式：Storage Access Framework（系统目录选择器）

详细操作请阅读 [使用说明.md](使用说明.md)，工程结构和构建方式请阅读 [开发文档.md](开发文档.md)。第三方组件许可见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。

## 构建

Debug：

```powershell
.\scripts\Build-Debug.ps1
```

签名 Release：

```powershell
.\scripts\Build-Release.ps1
```

发布脚本默认从项目外的 `E:\Viewer\signing\viewer-android-signing.properties` 读取签名，不会把密钥或密码写入项目。APK 按工程版本号输出到独立目录，当前为 `artifacts\release\1.2.0`。
