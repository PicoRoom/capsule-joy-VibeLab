# PICO OS 6 分层图标

图标由奶油色圆形底板和独立小熊扭蛋前景组成，系统负责层间阴影和动态光影。

- 原始前景：`design/icon/foreground-source.png`。
- 最终资源：`app/src/main/res/drawable/icon_3d_layer_*.png` 与 `icon_3d_sdf_*.png`。
- 普通启动图标：`app/src/main/res/mipmap-xxxhdpi/ic_spatial_launcher.png`。
- `values/drawables_3d.xml` 按前景到背景排列图层与 SDF；Manifest 引用 `icon.3d.list` 和 `icon.sdf.list`。

两层均为 1024 × 1024 PNG；圆外透明，背景内切圆完全不透明。原始前景由 imagegen 生成。

`tools/BuildSpatialIcon.java` 用于本地更新资源，调用已安装 PICO Spatial Plugin 的 `SdfGenerator`（1024px / 32f / 0f / 0f / Distance / 200）。编译和运行需要插件 JAR 及其 Kotlin 标准库，类输出与临时文件放在外接盘任务的 `work/` 中，从项目根目录运行。此工具不参与 Gradle 构建；云端直接使用已提交的图标资源。

参考：[应用图标与分层设计](https://developer-cn.picoxr.com/document/spatial-design/app-icon/)、[App Identity Configurator](https://developer-cn.picoxr.com/document/spatial-toolkit/manage-app-icon/)。
