# 扭趣项目范围

- 本地工程对应 `git@github.com:PicoRoom/capsule-joy-VibeLab.git`，专门维护独立原生 Gradle 工程；Unity 工程由 `PicoRoom/capsule-joy` 单独承载，不向本仓库加入 Unity 工程或导出目录。

- 本项目只开发和交付 PICO AR 原生版；项目根目录就是 Android Gradle 工程根目录，产品代码位于 `app/src/main/`。
- 不维护网页版。早期网页模型工具、导出副本和历史开发产物已移除，不再恢复这些路径或增加兼容层。
- 运行资源以 `app/src/main/assets/` 和 `app/src/main/res/` 为准；原生测试位于 `app/src/test/` 和 `app/src/androidTest/`。
- 保留资源来源与许可证。`design/`、`tools/` 仅保存当前原生图标的源素材与制作工具，不参与 Gradle 构建。
- 继续遵循上级 `../AGENTS.md` 的共享模拟器与数据保护要求，以及用户对 `/Volumes/NewSSD` 的产物存储要求。
- 本机缓存、构建日志与交付包使用 `/Volumes/NewSSD/Codex/workspaces/niudanji-maintenance/`，临时文件放 `work/`，交付放 `outputs/`；不要将历史导出和缓存提交进工程。
