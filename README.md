# Capsule Joy · 扭趣

本仓库为 **capsule-joy-VibeLab**，独立维护 PICO 原生 Android Gradle 工程。

- 独立 Gradle 仓库：[PicoRoom/capsule-joy-VibeLab](https://github.com/PicoRoom/capsule-joy-VibeLab)
- Unity 工程：[PicoRoom/capsule-joy · codex/unity-migration](https://github.com/PicoRoom/capsule-joy/tree/codex/unity-migration)（已完成编辑器回归和 APK 构建，真机完整玩法待验收）

本工程直接使用 Kotlin 和 PICO Spatial SDK 开发，不依赖 Unity 编辑器或 Unity 导出目录。克隆后以仓库根目录打开 Android Studio，配置 Android SDK 即可通过 Gradle 构建。

```sh
git clone git@github.com:PicoRoom/capsule-joy-VibeLab.git
cd capsule-joy-VibeLab
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

扭趣是一款面向 PICO 的原生 AR 扭蛋与收藏游戏。在立体空间里投币、转动手柄、操纵抓爪，再亲手打开胶囊，收集属于你的动物玩具。

## 玩法

- **三种机型**：体验转柄出蛋与抓爪取蛋两类操作，也可以观察机器内部机构。
- **十八款动物**：探索三个收藏系列，每个系列包含六款动物。
- **双手开蛋**：拿起胶囊，用双手分离上下半球，揭晓本次获得的玩具。
- **空间收藏**：通过珍藏册取出和归还玩具，自由移动、旋转、缩放并摆放收藏。
- **实操引导**：首次体验提供摆放、投币、转柄、取蛋、开蛋和收纳六步教程。

应用在 PICO 共享空间的立体窗口中运行，支持调整窗口位置与尺寸。主菜单可以暂停和继续当前轮次，音乐与音效可独立开关；收藏、摆放、教程进度及声音设置会保存在设备上。

## 技术与目录

项目使用 Kotlin 和 PICO Spatial SDK 6.1.9，采用原生 Android 工程组织。模型、配置和音频随应用打包。

```text
app/src/main/         玩法、空间场景、界面与运行资源
app/src/test/         单元测试
app/src/androidTest/  原生场景与交互测试
gradle/               Gradle Wrapper 与依赖版本目录
design/icon/          应用图标源素材
tools/                图标制作工具
docs/                 架构与视觉实现说明
licenses/             第三方资源许可
```

## 项目文档

- [应用架构](docs/DESIGN.md)
- [揭晓特效](docs/REVEAL_EFFECTS.md)
- [分层图标](docs/SPATIAL_ICON.md)
- [第三方资源与许可](THIRD_PARTY_NOTICES.md)
