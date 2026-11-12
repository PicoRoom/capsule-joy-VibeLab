# 扭趣 · VibeLab Unity Android 导出工程

本仓库 `PicoRoom/capsule-joy-VibeLab` 存放 Unity 导出的 Android Gradle 工程。源工程为 [PicoRoom/capsule-joy](https://github.com/PicoRoom/capsule-joy)，本次基于提交 `ca74353a242e2dd2a4926a45b7091f05a0a32aad` 在独立工作副本重新导出。

应用名：扭趣；包名：`com.bujiaban.capsulejoy.pico`；版本：1.0.0 / code 4；PICO Shared Space / ARM64。

## 预编译与提交

已按 VibeLab v1.5 完成 `:unityLibrary:buildIl2Cpp --rerun` 强制预编译。保留 Unity 原任务体，在文件末尾禁用任务，并将 `jni.srcDirs` 清空；删除 C++ 中间源及构建缓存后，独立 Release 构建成功，`buildIl2Cpp SKIPPED`。清理前后 APK 的全部 6 个 native 库名称、ABI、SHA256 一致。

`.vibelab-prebuilt.json` 使用平台规定的 `schema: 1` / `il2cpp-prebuild-v6`，记录实际的 ARM64 库和 metadata 校验值。`export-info.json` 记录源版本及验证信息。玩法或资源修改必须回到 Unity 源工程，重新导出、预编译和校验完整配套内容。

Unity 工具链路径保持标准导出值，由平台构建时处理；`unityProjectPath` 与 `unity.projectPath` 已清空。保留 `gradle/wrapper/gradle-wrapper.properties`，不添加 gradlew 脚本或 wrapper JAR。APK 由 VibeLab 统一签名，本机沿用默认 debug 签名；不提交密钥、local.properties、APK、缓存或 Unity 源工程。

## 独立构建

Unity 6000.4.6f1、Gradle 9.1.0、AGP 9.0.0、Java 17、NDK 27.2.12479018、Build Tools 36.0.0；compile/target SDK 36，min SDK 29。仅 launcher 是 Application module；CapsuleBranding 是被引用的 library module。没有 CMake 链路。

使用与 wrapper 配置一致的 Gradle；也可以使用 Unity 安装目录中 `Tools/gradle/lib/gradle-launcher-9.1.0.jar` 启动 `org.gradle.launcher.GradleMain`。本机终验必须将 `JAVA_HOME` 设为工程声明的 JDK，将 `ANDROID_HOME` 与 `ANDROID_SDK_ROOT` 同时设为工程声明的 SDK，然后执行：

```sh
gradle assembleRelease --console=plain --no-daemon
```

所有本机缓存、临时目录和 APK 导出应配置在 `/Volumes/NewSSD`。APK 位于 `launcher/build/outputs/apk/release/launcher-release.apk`；保留到仓库外后清理根目录及各模块的 build、.gradle、.cxx 目录。

云端实际日志使用根目录 `assembleRelease`，会同时验证各 library 的资源；本机终验也使用此入口。应用名称、图标及两个 Activity 的显示名称由 launcher 清单定义，unityLibrary 清单不引用 launcher 的 `app_name` / `app_icon`，合并后的应用清单保持原有声明。否则仅运行 `:launcher:assembleRelease` 虽可成功，根目录构建仍会在 `:unityLibrary:verifyReleaseResources` 报资源不存在。Unity 重新导出后需复核这些清单声明。

2026-11-12 本机验证：`clean assembleRelease` 在禁用构建缓存、用本地 init script 模拟云端 `versionCode=1` 覆写后通过。APK 的 281 个运行内容条目（包括 6 个原生库、257 个资源文件）与上一版逐项 SHA256 一致；合并清单除版本号与节点顺序外一致，签名与普通 ZIP 对齐检查通过。此验证未使用平台私有 init script，也不代表云端审核或真机验证通过，详见 `export-info.json`。

## 无损资源优化

干净导出生成 135 个 GLB，旧导出中的 6 个残留自然消失。135 个文件在压缩前与原提交对应资源的字节完全一致。`tools/optimize_glb_lossless.py` 在提交前合并相同 bufferView 存储并无损重编码 PNG；135 个 GLB 从 104,076,072 字节降到 74,955,888 字节，保持几何、材质、变换、贴图像素和色彩元数据，不增加 glTF 扩展。云端 Gradle 不运行此 Python 工具；复现需要 Python 3.9+ 和 `tools/requirements.txt` 中的 Pillow。

资源来源见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 与 `licenses/`。原 Unity 工作区未被本次处理修改；独立工作副本的 SDK 自动重编号及生成资源已另行记录。

本机验证已通过；未做 PICO 云端或真机复测。默认 `page_size_policy: warn` 下，SpatialAdapter 的两个插件库有 4 KB LOAD 对齐警告，不能称整个 APK 已验证兼容 16 KB。
