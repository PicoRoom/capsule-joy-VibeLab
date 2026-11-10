# 扭趣 · Unity Android Gradle 导出工程

本仓库 `PicoRoom/capsule-joy-VibeLab` 存放 **Unity 导出并处理后的 Android Gradle 工程**，不再维护旧 Kotlin 原生版。Unity 源工程在 [PicoRoom/capsule-joy](https://github.com/PicoRoom/capsule-joy)。

应用名：扭趣；包名：`com.bujiaban.capsulejoy.pico`；版本：1.0.0（4）；目标：PICO OS 6 Shared Space / ARM64。

## 独立构建

需要 Git LFS、JDK 17、Android SDK Platform 36 / Build Tools 36.0.0、NDK 27.2.12479018。不需要安装 Unity Editor。

```sh
git lfs pull
# 在本机 local.properties 设置 sdk.dir；NDK 可安装在 SDK/ndk/27.2.12479018
./gradlew :launcher:assembleRelease
```

输出：`launcher/build/outputs/apk/release/launcher-release.apk`。当前沿用 Unity 导出的 debug 签名配置，正式商店发布时应配置自己的发布签名；不要提交密钥。

- `launcher/`：Android 启动器、应用标识、图标和 Manifest。
- `unityLibrary/`：Unity Player、PICO SpatialAdapter、场景和资源、已编译的 IL2CPP 库。
- `shared/`：Unity 导出的 Gradle 公共规则。
- `export-info.json`：对应 Unity 源提交、版本及 APK 校验值。

已移除本机绝对路径、IL2CPP 编译工具及 C++ 中间输出。`libil2cpp.so` 来自同一源码版本的 Unity APK，已核对 global-metadata.dat 完全一致。运行库使用 Git LFS。玩法或场景变化必须先在 Unity 源工程修改、验证，再重新导出匹配的资源和运行库；不能仅替换 metadata。

旧原生实现保留在 Git 历史中，不在当前工程保留兼容层。缓存、APK 和本机 SDK 配置不提交。

设置面板提供「退出应用」；退出前保存收藏和玩具摆放状态。
