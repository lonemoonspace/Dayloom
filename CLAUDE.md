# Dayloom（织日）

> Status: **draft**, pending review together with `docs/design.md`. No code exists yet.
> 状态：**草稿**，与 `docs/design.md` 一起待审。目前还没有任何代码。

An extensible, bilingual (English / Chinese) Android daily dashboard: weather, Norwegian public transport,
traffic, expiry reminders, calendar, football and news. Kotlin + Jetpack Compose, single Gradle module `:app`,
hand-written DI, DataStore + WorkManager (+ Room for news only). Design: `docs/design.md`.

可扩展的中英双语 Android 日常看板：天气、挪威公共交通、路况、到期提醒、日历、足球、新闻。
Kotlin + Jetpack Compose，单 Gradle 模块 `:app`，手写依赖注入，DataStore + WorkManager（仅新闻用 Room）。设计见 `docs/design.md`。

## Language / 语言

- UI text: English in `values/`, Chinese in `values-zh/`; every key exists in both.
- Code comments, commit messages and CHANGELOG are bilingual: **English first, Chinese below**, updated together in the same change. A mismatch between the two is a bug.
- README: `README.md` (English) and `README.zh-CN.md` (Chinese).

- 界面文案：英文放 `values/`，中文放 `values-zh/`；两边的键必须完全一致。
- 代码注释、提交信息、CHANGELOG 用双语：**英文在上，中文在下**，同一次修改里一起改；两种语言不一致视为 bug。
- README：`README.md`（英文）与 `README.zh-CN.md`（中文）。

Comment format / 注释格式：

```kotlin
/**
 * Snapshots older than this are marked as cached in the UI.
 * 超过这个时长的快照在界面上标注「缓存」。
 */
```

Commit format / 提交格式：

```
<English subject>

<English body, optional>

<中文标题>

<中文正文，可选>
```

## Architecture / 架构

- Packages: `core/*` (infrastructure), `feature/*` (one package per module), `app/*` (assembly: registry, navigation, home/settings shells, onboarding).
- Dependencies point downwards only: `app → feature → core`. `core` never imports `feature` or `app`; a feature never imports another feature. `ArchitectureTest` enforces this — never weaken it to make a change pass.
- Every module implements `FeatureModule` and is listed once in `app/ModuleRegistry.kt`. Adding a module must not require edits to the home screen, settings shell, navigation, refresh coordinator, notification engine or other modules; if it does, fix the architecture instead.
- Each data source is a `CachedSource` with its own snapshot; all refreshes go through `RefreshCoordinator`; UI only observes snapshots.
- Storage keys, snapshot names, secret ids and notification state keys are namespaced `<moduleId>.<name>` by `ModuleContext`; modules never build these names by hand.

- 包分三层：`core/*`（基础设施）、`feature/*`（每个模块一个包）、`app/*`（组装：注册表、导航、首页/设置外壳、首次启动引导）。
- 依赖只能向下：`app → feature → core`。`core` 不引用 `feature` 或 `app`；功能模块之间互不引用。由 `ArchitectureTest` 保证——不许为了让改动通过而放宽它。
- 每个模块实现 `FeatureModule`，在 `app/ModuleRegistry.kt` 里登记一次。加模块不应该需要改首页、设置外壳、导航、刷新协调器、通知引擎或其他模块；如果需要，先修架构。
- 每个数据来源是一个带独立快照的 `CachedSource`；刷新一律经 `RefreshCoordinator`；界面只观察快照。
- 存储键、快照名、凭据 id、通知状态键由 `ModuleContext` 自动加 `<moduleId>.` 前缀；模块不得手工拼这些名字。

## Build and gate / 构建与门禁

JDK 17; use the Gradle wrapper. / JDK 17，用仓库自带的 Gradle wrapper。

```powershell
.\gradlew.bat verify -q --console=plain 2>&1 | Select-Object -Last 40   # test + lintDebug + assembleDebug; must be green before every commit / 每次提交前必须全绿
.\gradlew.bat :app:testDebugUnitTest --tests "io.github.lonemoonspace.dayloom.feature.weather.*"
.\gradlew.bat :app:lintDebug --rerun-tasks                              # standalone lint needs --rerun-tasks / 单独跑 lint 必须加 --rerun-tasks
```

```bash
./gradlew verify   # Linux / macOS / CI
```

- If a global `~/.gradle/gradle.properties` caps Metaspace at 320m, lint/KSP may hit `OutOfMemoryError: Metaspace`; pass `"-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8"`.
- Unit tests run on the Debug variant only. CI (`.github/workflows/ci.yml`) runs the same three checks on every push.

- 若全局 `~/.gradle/gradle.properties` 把 Metaspace 限在 320m，lint/KSP 可能报 `OutOfMemoryError: Metaspace`；命令加上 `"-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8"`。
- 单元测试只跑 Debug 变体。CI（`.github/workflows/ci.yml`）对每次 push 运行同样三项。

## Frozen from v1.0.0 / 从 v1.0.0 起冻结

Before v1.0.0 everything below may change freely without migrations. From v1.0.0 on, changing any of it loses user data or
causes duplicate notifications, so it requires a migration with tests:

v1.0.0 之前，下列内容都可以自由修改，不需要迁移。从 v1.0.0 起，改动其中任何一项都会丢用户数据或产生重复通知，必须写迁移并有测试：

- applicationId `io.github.lonemoonspace.dayloom`; FQCNs of `MainActivity` and `DayloomApp`; deep-link scheme `dayloom://`.
- Module ids, source ids, secret ids, notification channel ids, notification id ranges, notification state keys.
- Storage file names (`app_settings`, `module_settings`, `shared_data`, `secrets`, `notify_state`, `snapshot_*`) and the JSON format of every `@Serializable` type stored in them (sealed/polymorphic subclasses must have `@SerialName`).
- Room database `dayloom.db` and its schema (export to `app/schemas/`, add a `Migration` and a migration test).
- WorkManager unique work names and Worker FQCNs.
- `SecretBox` `v1:` ciphertext format and Keystore alias.

- applicationId `io.github.lonemoonspace.dayloom`；`MainActivity` 与 `DayloomApp` 的全限定类名；深链 scheme `dayloom://`。
- 模块 id、来源 id、凭据 id、通知渠道 id、通知 id 号段、通知状态键。
- 存储文件名（`app_settings`、`module_settings`、`shared_data`、`secrets`、`notify_state`、`snapshot_*`）及其中每个 `@Serializable` 类型的 JSON 格式（密封/多态子类必须有 `@SerialName`）。
- Room 数据库 `dayloom.db` 及其 schema（导出到 `app/schemas/`，写 `Migration` 与迁移测试）。
- WorkManager 唯一任务名与 Worker 全限定类名。
- `SecretBox` 的 `v1:` 密文格式与 Keystore alias。

## Coding conventions / 编码约定

- Coroutines: before any `catch (e: Exception)`, add `catch (e: CancellationException) { throw e }`.
- Secrets: always via `core/secret/SecretStore`; requests use only `usable(id)`; input fields only show `SecretState.display`; never send or display ciphertext.
- Time: never call `ZonedDateTime.now()` / `ZoneId.systemDefault()` directly; use the injected `AppClock` and its zone (device zone unless overridden in settings).
- Decision logic lives in pure functions named `XxxPolicy` inside the owning feature package, takes `now` as a parameter, has no Android imports, and is covered by plain JVM tests.
- i18n: policies and domain code return structured results (enums, sealed types, numbers), never user-facing text. ViewModels pass `UiText` to the UI. Never store rendered text in snapshots or settings.
- Anything that depends on the Android framework sits behind an interface and is faked in tests; Robolectric only for DataStore/Room migration tests.
- Serialized types: every field has a default value.
- Comments explain *why* and non-obvious constraints in one or two sentences per language; no version history or review ids (those belong in git log / CHANGELOG).
- Never put personal data (addresses, real stop ids tied to a person, keys) in defaults, tests or previews.

- 协程：任何 `catch (e: Exception)` 前先 `catch (e: CancellationException) { throw e }`。
- 凭据：一律经 `core/secret/SecretStore`；发请求只用 `usable(id)`；输入框只回填 `SecretState.display`；永远不要发出或显示密文。
- 时间：不直接调用 `ZonedDateTime.now()` / `ZoneId.systemDefault()`；用注入的 `AppClock` 及其时区（默认设备时区，设置里可覆盖）。
- 判定逻辑写成所属功能包里的纯函数 `XxxPolicy`，`now` 当参数，不引用 Android，用纯 JVM 单测覆盖。
- 国际化：Policy 与 domain 层只返回结构化结果（枚举、密封类、数字），不返回界面文案；ViewModel 用 `UiText` 把文字交给界面；不在快照或设置里存拼好的文案。
- 依赖 Android 框架的东西抽接口，测试用 fake；只有 DataStore/Room 迁移测试才用 Robolectric。
- 序列化类型：每个字段都有默认值。
- 注释写「为什么」和不显然的约束，每种语言一两句；不写版本历史、审查编号（这些进 git log / CHANGELOG）。
- 默认值、测试、预览数据里不放任何个人数据（地址、能对应到个人的站点、Key）。

## Release / 打包与发版

All signed builds come from GitHub Actions. Signing material lives in repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`. The APK is public: **never embed any API key**. APK, mapping, `local.properties` and
`keystore.properties` are never committed.

签名包全部由 GitHub Actions 构建。签名材料在仓库 Secrets：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。
APK 公开可下载，**不往里写任何 API Key**。APK、mapping、`local.properties`、`keystore.properties` 永不入库。

**Internal test build (no Release) / 内部测试包（不建 Release）**
1. Set `versionName = "X.Y.Z-rc.N"` (must contain `-`) and bump `versionCode` (`YYYYMMDDNN`) in `app/build.gradle.kts`.
2. Commit, push, then push the same commit to `testbuild/X.Y.Z-rc.N` (`git push origin HEAD:refs/heads/testbuild/X.Y.Z-rc.N`). Branches, not tags: cloud sessions cannot push tags.
3. `.github/workflows/internal-build.yml` runs the gate and stores the signed APK as an artifact for 14 days.

1. 改 `app/build.gradle.kts`：`versionName = "X.Y.Z-rc.N"`（必须带 `-`），`versionCode` 按 `YYYYMMDDNN` 递增。
2. 提交并推送，再把同一提交推到 `testbuild/X.Y.Z-rc.N`。用分支而非 tag：云端会话不能推 tag。
3. `.github/workflows/internal-build.yml` 跑门禁并构建签名 APK，作为 artifact 保存 14 天。

**Release / 正式版**
1. Set `versionName` (`X.Y.Z`) and `versionCode`; update `CHANGELOG.md` (heading exactly `## X.Y.Z`, bilingual body).
2. Commit `Release vX.Y.Z` / `发布 vX.Y.Z`, push `main`, then trigger: push annotated tag `vX.Y.Z`, or from a cloud session push `HEAD:refs/heads/release/vX.Y.Z` (commit must already be on `main`). `.github/workflows/release.yml` runs the gate, checks tag = `versionName`, builds the signed APK and creates the Release with APK + SHA256.

1. 改 `versionName`（`X.Y.Z`）与 `versionCode`；更新 `CHANGELOG.md`（标题恰好是 `## X.Y.Z`，正文双语）。
2. 提交 `Release vX.Y.Z` / `发布 vX.Y.Z` 并推送 `main`，然后触发：推附注 tag `vX.Y.Z`；云端会话改推 `HEAD:refs/heads/release/vX.Y.Z`（提交必须已在 `main` 上）。`release.yml` 跑门禁、校验 tag 与 `versionName` 一致、构建签名 APK 并建 Release（附 APK 与 SHA256）。

Day-to-day work happens on `main`; large changes get their own branch and are merged back before a release.
日常开发在 `main` 上进行；大的改造另开分支，合回 `main` 后再发版。
