# 扭趣 Android Gradle 导出工程

- 本仓库 PicoRoom/capsule-joy-VibeLab 只维护 Unity 导出的 Android Gradle 工程，根目录包含 launcher、unityLibrary。
- Unity 源工程由 PicoRoom/capsule-joy 承载；不再维护旧 Kotlin 原生版，不恢复 app/src/main 原生实现。
- 保留已处理的 ARM64 IL2CPP 运行库及其匹配资源，禁止使用不匹配的 metadata 或随意更新二进制。玩法修改应回到 Unity 工程，再重新导出。
- 所有产物和缓存存放 /Volumes/NewSSD；遵循上级 AGENTS.md。只在 PICO 真机验证，不使用模拟器。
- 不提交 local.properties、签名密钥、构建输出或缓存。保留资源来源和许可证。
