# 织日（Dayloom）

[English](README.md) · **中文**

织日是一个可扩展的 Android 日常看板：天气、挪威公共交通、路况、到期提醒、日历、足球与新闻，每一项都是可以单独开关的模块。
界面支持中英双语。

> **状态：早期开发中。**模块系统、首页、设置与构建流程已经就位（里程碑 M0），共用的地点与日常作息、天气与日历也已加入（M1）。
> 计划见[设计文档](docs/design.md)。

## 原则

- **模块化，而不是一整块。**每个功能都是 `feature/<id>/` 下的一个模块，在 `ModuleRegistry` 里登记一行即可；
  首页、设置、导航、刷新与通知会自动接入它。
- **没有后端、不内置 Key、不做追踪。**数据来自公开接口或你自己配置的服务；API Key 在 App 里填写，用 Android Keystore 加密保存。
- **从第一天起就是双语。**所有文字都来自资源文件（`values/` 英文、`values-zh/` 中文），可以在 App 内或系统设置里切换语言。

## 系统要求

- Android 13（API 33）及以上。

## 构建

使用 JDK 17 与仓库自带的 Gradle wrapper：

```bash
./gradlew verify          # 单元测试 + Android Lint + Debug 包；每次提交前必须全绿
./gradlew assembleDebug   # 只打 Debug 包
```

Debug 版包含一个小的演示模块，展示模块如何接入；Release 版不包含。

签名包只由 GitHub Actions 构建，发版流程见 `CLAUDE.md`。

## 架构

```
app/      组装层：模块注册表、导航、首页与设置外壳
feature/  每个模块一个包；功能模块之间互不依赖
core/     基础设施：模块接口、刷新、通知、存储、凭据、网络、时间、国际化、界面组件
```

依赖只能向下（`app → feature → core`），否则 `ArchitectureTest` 会让构建失败。详见 [docs/design.md](docs/design.md)。

## 许可证

[Apache License 2.0](LICENSE)。
