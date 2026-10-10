# Dayloom（织日）设计文档

> 状态：**v1.0，已审定**（2026-10-09）。之后的修改须同步更新两份文档，并在 §19 记录决策。
> 英文版见 [`design.en.md`](design.en.md)；两份内容必须一致，修改时同时更新。

---

## 1. 目标与非目标

**目标**

1. 一个开箱即用、谁都能配置的 Android 日常看板：天气、公共交通、路况、到期提醒、日历、足球、新闻。
2. **可扩展**：加一个新模块 = 新建一个功能包 + 在注册表加一行；首页、设置、导航、刷新、通知、后台任务都不用改。
3. **可精简**：用户可以关闭任意模块；关闭的模块不发请求、不占首页、不出现在导航与设置里。
4. **中英双语**：界面、通知、错误提示全部走资源；应用内可切换语言。
5. 不依赖任何自建后端；不内置任何 API Key；不收集任何数据。

**非目标（第一版不做）**

- 挪威以外的公共交通（只做接口，不做第二个实现）。
- iOS / 桌面 / 小组件（Widget）。
- 账号体系、云同步、从 PersonalAssistant 自动导入设置。
- Gradle 多模块（见 §3.3，以后需要时再拆）。

---

## 2. 已确定的决策

| 项目 | 决定 |
|---|---|
| 名称 | 英文 **Dayloom**，中文 **织日** |
| 仓库 | `lonemoonspace/dayloom`，全新初始提交、不带旧历史；自 M1 起公开（见 §19） |
| 许可证 | Apache-2.0 |
| applicationId / 包名 | `io.github.lonemoonspace.dayloom` |
| 工程结构 | 单 Gradle 模块 `:app` + 按包分层 + **架构测试**守边界 |
| 模块范围（第一版） | 天气、公共交通、路况、到期提醒、日历、足球、新闻，全部通用化 |
| 公共交通 | Entur Journey Planner 通用规划，仅挪威；经 `TransitProvider` 接口可插拔 |
| 天气 | MET Norway，全球可用 |
| 时区 | 默认跟随设备，可在设置里手动指定 |
| 界面语言 | 英文（默认资源）+ 中文；跟随系统或应用内手动切换 |
| 代码注释 / 提交信息 / CHANGELOG | 双语，英文在上、中文在下 |
| README | `README.md`（英文）+ `README.zh-CN.md`（中文），互相链接 |
| CI / 发版 | 沿用 PersonalAssistant 的门禁与 workflow，改名适配；门禁另外编译 Release 源码（§19） |
| minSdk | **33**（Android 13）；targetSdk 跟随 Google Play 当年要求 |
| 农历 | `cn.6tail:lunar`（lunar-java，MIT） |
| 冻结规则 | v1.0.0 之前可自由改；v1.0.0 起冻结存储格式等（§15） |

---

## 3. 总体架构

### 3.1 分层

```
┌──────────────────────────────────────────────────────────┐
│ app        组装层：DayloomApp、MainActivity、AppGraph、      │
│            ModuleRegistry、导航外壳、首页/设置外壳、首次启动引导 │
├──────────────────────────────────────────────────────────┤
│ feature/*  功能模块：weather、transit、traffic、reminders、   │
│            calendar、football、news（各自 data/domain/ui）    │
├──────────────────────────────────────────────────────────┤
│ core/*     基础设施：module(接口)、refresh、notify、storage、  │
│            secret、network、time、location、routine、i18n、  │
│            error、json、work、ui                            │
└──────────────────────────────────────────────────────────┘
依赖只能向下：app → feature → core；feature 之间互不依赖。
```

### 3.2 包结构

```
io.github.lonemoonspace.dayloom
├── DayloomApp.kt                // Application
├── MainActivity.kt
├── app/
│   ├── AppGraph.kt              // 手写依赖注入：共享的 OkHttp、时钟、存储、协调器
│   ├── ModuleRegistry.kt        // 唯一列出所有模块的地方
│   ├── nav/                     // 底部导航、路由（由注册表生成）
│   ├── home/                    // 首页外壳：按顺序渲染各模块卡片
│   ├── settings/                // 设置外壳：通用设置 + 各模块设置分区
│   └── onboarding/              // 首次启动引导
├── core/
│   ├── module/                  // FeatureModule、ModuleContext、HomeCard、ModuleTab…
│   ├── refresh/                 // CachedSource、RefreshCoordinator、刷新节奏
│   ├── notify/                  // NotificationRule、NotificationEngine、渠道、早间简报
│   ├── storage/                 // DataStore、快照、模块设置存储
│   ├── secret/                  // SecretStore、SecretBox
│   ├── network/                 // OkHttp、联网状态、凭据重定向防护
│   ├── time/                    // AppClock、时区
│   ├── location/                // 地点（Place）、地点搜索（Google / Entur / OpenStreetMap / 坐标）
│   ├── routine/                 // 日常时间窗（上班/下班）、常用地点（家/公司）
│   ├── i18n/                    // UiText、语言切换
│   ├── error/  json/  work/  ui/
└── feature/
    ├── weather/   transit/   traffic/   reminders/
    ├── calendar/  football/  news/
    └── <每个模块内>：data/（接口与数据源）domain/（模型与 Policy）ui/（卡片、页面、设置分区）
```

### 3.3 为什么是单模块 + 架构测试

- 项目规模约两百个文件，多模块的构建提速体现不出来，反而多出 convention plugin、逐模块 lint/测试配置等维护负担。
- 边界由一组 JVM 单测（`ArchitectureTest`）保证，**违反即 CI 红**。它直接扫描 `src/main/java` 里每个文件的 `import`，不引入第三方库：
  1. `core/**` 不得 import `feature/**` 或 `app/**`；
  2. `feature/X/**` 不得 import `feature/Y/**`（X ≠ Y）或 `app/**`；
  3. 名为 `*Policy` 的文件不得 import `android.*` / `androidx.*`（保证可纯 JVM 单测）；
  4. 只有 `app/ModuleRegistry.kt` 可以引用各模块的 `XxxModule` 入口类。
- 以后要拆 Gradle 多模块时，边界已经干净，基本只是搬目录、加构建配置。

---

## 4. 模块系统（核心）

### 4.1 接口草图

```kotlin
/** 一个功能模块的静态描述；未启用时不创建实例，不产生任何开销。 */
interface FeatureModule {
    val id: ModuleId                    // 例："weather"；v1.0 起冻结
    @get:StringRes val title: Int
    @get:DrawableRes val icon: Int
    val defaultEnabled: Boolean
    fun create(ctx: ModuleContext): ModuleInstance
}

/** 启用后的模块实例：向外声明它贡献的一切。每一项都可以为空。 */
interface ModuleInstance {
    val sources: List<CachedSource<*, *>>          // 数据源，统一注册到 RefreshCoordinator
    val homeCards: List<HomeCard>                  // 首页卡片
    val tab: ModuleTab?                            // 独立标签页（足球、新闻）
    val settings: SettingsSection?                 // 设置页分区
    val notificationChannels: List<ChannelSpec>
    val notificationRules: List<NotificationRule<*>>
    val brief: BriefContributor?                   // 早间简报中的一行
    val configured: Flow<ConfigState>              // 是否已配置好；未配置时卡片显示引导
}
```

**ModuleContext**（core 提供给模块的能力，模块只能通过它拿依赖）：

| 能力 | 说明 |
|---|---|
| `http` | 共享的 OkHttpClient |
| `clock` / `zone` | 注入的时钟与当前时区（Flow） |
| `settings<T>(serializer, default)` | 本模块的设置存储（§6.2），只能读写自己的 |
| `snapshots<T>(sourceName, serializer)` | 本模块的快照存储，文件名自动加模块前缀 |
| `secret(name)` | 本模块的凭据，id 自动加模块前缀 |
| `notifyState(name)` | 本模块通知规则的状态存储，键自动加模块前缀 |
| `places` / `routine` | 共享的常用地点与日常时间窗（§8） |
| `connectivity` | 联网状态 |
| `appScope` | 进程级协程作用域 |

**命名空间**：模块拿到的所有存储键、快照文件名、凭据 id、通知状态键都由 core 自动加上 `<moduleId>.` 前缀，模块之间不可能撞名。

### 4.2 注册表

```kotlin
// app/ModuleRegistry.kt —— 加模块只改这一个文件
val allModules: List<FeatureModule> = listOf(
    CalendarModule, WeatherModule, TransitModule, TrafficModule,
    RemindersModule, FootballModule, NewsModule,
)
```

注册表自带单测：模块 id 唯一、通知 id 段不重叠（§7.2）、通知渠道 id 唯一、后台任务名唯一。

### 4.3 首页卡片与标签页

- `HomeCard`：`key`、默认排序、`placement: Flow<CardPlacement>`（如到期提醒「快到期」时置顶）、`@Composable Content()`。
- 首页外壳按「用户自定义顺序 → 默认顺序」渲染所有已启用模块的卡片。
- **排序**：首页进入编辑模式后长按卡片拖动排序；同时为每张卡片提供「上移 / 下移」无障碍操作，保证 TalkBack 用户也能排序。顺序存在 `app_settings`。
- `ModuleTab`：路由、图标、标签文字、`@Composable Content()`、可选深链。底部导航 = 首页 + 已启用模块的标签页 + 设置。
- 标签页的 ViewModel 只在用户首次进入时创建（沿用原项目做法，冷启动不请求足球/新闻接口）。

### 4.4 加一个新模块的完整步骤（验收标准）

1. 新建 `feature/<id>/`，实现 `FeatureModule` 与 `ModuleInstance`；
2. 在 `values/` 与 `values-zh/` 加该模块的字符串；
3. 在 `ModuleRegistry` 加一行；
4. 写该模块的 Policy 单测。

**不需要改**：首页、设置外壳、导航、刷新协调器、通知引擎、后台 Worker、任何其他模块。若做不到，视为架构缺陷。

---

## 5. 刷新机制

从 PersonalAssistant 移植 `CachedSource` 与 `RefreshCoordinator` 的核心语义（并行刷新、按来源 single-flight、离线跳过、静默触发、失败保留旧快照），以及它们的全部单测，改动如下：

| 原项目 | 新项目 |
|---|---|
| `enum class SourceId` | `SourceId(value: String)`，形如 `"weather.forecast"`，由模块 id + 来源名组成 |
| `paramsKey(settings: UserSettings)` | `CachedSource<P, T>`：`P` 是该来源自己的参数类型，由模块从自己的设置里算出，core 只看到泛型 |
| `commuteSources()` 写死天气/火车/路况/公交 | 每个来源声明 `RefreshCadence`（前台间隔、后台是否刷新、是否只在日常时间窗内高频）；协调器按节奏挑选来源 |
| 路况去程/回程两个来源 + `activeTraffic` 特判 | 路况模块内部按日常时间窗决定方向，对 core 只有一个来源 |
| `Trigger.LIVE_POLL` 为足球专设 | 保留为通用的「静默高频轮询」触发，任何模块可用 |

快照文件：`snapshot_<sourceId>`（点号替换为下划线），快照带 `schemaVersion`，不兼容时直接作废重拉。

---

## 6. 设置与存储

### 6.1 存储文件一览

| 文件 | 类型 | 内容 |
|---|---|---|
| `app_settings` | DataStore（JSON） | 全局设置：语言、时区覆盖、已启用模块、卡片顺序、引导是否完成、早间简报开关 |
| `module_settings` | DataStore Preferences | 每个模块一个键（= 模块 id），值是该模块设置的 JSON |
| `shared_data` | DataStore（JSON） | 常用地点（家/公司/自定义）、日常时间窗 |
| `secrets` | DataStore Preferences | 所有凭据，`SecretBox` 加密；键 = `<moduleId>.<name>` |
| `notify_state` | DataStore Preferences | 通知规则状态；键 = `<moduleId>.<rule>` |
| `snapshot_*` | DataStore（JSON） | 各数据源快照 |
| `dayloom.db` | Room | 只有新闻模块使用，schema 从 v1 开始导出到 `app/schemas/` |

### 6.2 模块设置

- 每个模块一个 `@Serializable` 设置类，**所有字段必须有默认值**；带 `version: Int` 字段，为 v1.0 之后的迁移预留。
- 解码失败：v1.0 前直接回退默认值并记日志；v1.0 起必须写迁移并有测试。
- 不再有一个装下所有功能的 `UserSettings`。

### 6.3 凭据

沿用 `SecretStore` / `SecretBox`（`v1:` 密文格式、Android Keystore），规则不变：发请求只用 `usable(id)`，输入框只回填 `display`，永远不把密文当凭据发出或显示。
第一版的凭据：`core.google_maps`（地点搜索与路况共用的一个 Google Maps Platform Key，在「地点」里填写；模块通过 `ModuleContext.googleMapsKey` 取用）、`football.football_data`、`news.miniflux_token`、`news.llm_api_key`。

---

## 7. 通知

### 7.1 规则

- `NotificationRule<S>` 移到各模块内部；规则只能读本模块的快照与设置（通过 `RuleInput` 提供的、按本模块收窄的读取器），不再有全局的 `SnapshotsView`。
- 通知文案在发送时按**当前应用语言**从资源取（Worker 里用带语言的 `Context`，见 §10.4）。规则返回结构化的 `NotificationContent`，不返回拼好的字符串。

### 7.2 渠道与 id

- 渠道 id：`<moduleId>.<name>`，由模块声明，`NotificationChannels` 统一创建。
- 通知 id：每个模块在注册时声明一个号段（如天气 1000–1999、交通 2000–2999…），注册表单测保证不重叠。
- 1–999 留给外壳自己（早间简报：id 1、渠道 `core.brief`、状态键 `core.morning_brief`），模块号段不能落进去，同样由注册表检查。

### 7.3 早间简报

- 由 core 实现，是唯一跨模块的通知：每个启用了 `BriefContributor` 的模块提供一行结构化内容（如「今天有雨，带伞」「首班车准点」「路况畅通，22 分钟」），按模块顺序拼成一条通知。
- 新模块只要实现 `BriefContributor` 就会自动出现在简报里。
- 发送时机：每天上班时间窗内的第一轮后台刷新，按时间窗所属日期每天一条。没有任何模块有话说时（数据太旧或都没内容）当天的机会不算用掉，同一时间窗里稍后的一轮还能发。某个模块出错只少它那一行。

### 7.4 第一版的通知

| 模块 | 通知 | 默认 |
|---|---|---|
| 公共交通 | 通勤行程取消/大晚点 | 关 |
| 到期提醒 | 快到期 / 已到期 | 关 |
| 足球 | 开赛提醒、终场比分 | 关 |
| 新闻 | 无（后台同步不发通知） | — |
| core | 早间简报 | 关 |

所有通知默认关闭（选择加入），理由同原项目：不替用户做打扰型决定。

---

## 8. 共享概念：地点与日常时间窗

天气、公共交通、路况、早间简报都需要「家在哪、公司在哪、什么时候出门」。为避免每个模块各问一遍，放在 core 里共享：

- **常用地点（`core/location`）**：`Place(id, label, name, lat, lon, countryCode?)`。预置「家」「公司」两个槽位，可加自定义地点。
  - 搜索：有用户自己的 Key 时用 **Google Places API（新版）文本搜索**（街道地址与具名地点）；没有 Key 时先用 **Entur Geocoder**（挪威的街道地址、地名、地标与站点，免费、无需 Key，地址来自 Kartverket），找不到或出错时用 **Nominatim**（OpenStreetMap，全球；使用政策要求可识别的 User-Agent、每秒最多约一次、不能边输入边搜索，所以只在按「搜索」时调用）。Google 只是可选项：用户不必为了填地址去申请 Key（rc.4 测试后决定）。手动输入的坐标（「59.9139, 10.7522」）总是直接采用。
  - 不用设备定位：App 不申请任何定位权限（rc.1 测试后决定，见 §19）。
- **日常时间窗（`core/routine`）**：上班窗口、下班窗口（支持跨午夜，沿用原项目校验规则）。每天都生效，没有通勤开关，也不选工作日。

各模块的设置里引用这些共享项，比如天气默认「家」、路况默认「家 → 公司」，也可以改成别的地点。

---

## 9. 时间与时区

- `AppClock` 提供 `now()` 与 `zone: Flow<ZoneId>`；`zone` = 设置里的覆盖值，否则跟随设备（监听系统时区变化）。
- 所有 Policy 把 `now: ZonedDateTime` 当参数，测试里传固定时间与固定时区。
- 新代码禁止直接调用 `ZonedDateTime.now()` / `ZoneId.systemDefault()`；由架构测试或 lint 规则检查。
- 外部数据（Entur、football-data.org）返回带偏移的时间，统一换算到当前时区再展示。

---

## 10. 国际化（中英双语）

### 10.1 资源

- `values/strings.xml` = 英文（默认），`values-zh/strings.xml` = 中文。
- 每个模块的字符串用前缀分组：`weather_*`、`transit_*`…，便于查找和以后拆分。
- 复数用 `plurals`；带参数的一律用位置参数 `%1$s`。

### 10.2 规则

1. **Policy / domain 层只返回结构化结果**（枚举、密封类、数字、时间），不拼任何界面文案。
2. ViewModel 需要传文字给界面时用 `UiText`（`Res(id, args)` / `Plural(id, n, args)` / `Raw(string)`），界面层再解析。
3. 错误文案集中在 `core/ui/ErrorText`，按 `AppError` 类型映射到资源。
4. 节假日名、天气描述、星期、农历月日名全部走资源。
5. 不把任何拼好的文案存进快照或设置。

### 10.3 语言切换

- 设置里「跟随系统 / English / 中文」，直接用系统的按应用语言 API（`LocaleManager`，Android 13 起提供），不需要 AppCompat；声明 `locales_config.xml`，系统设置里也能给织日单独选语言。

### 10.4 后台与通知

- 按应用语言由系统作用于整个进程，Worker 与通知取到的字符串应与界面一致；M0 时实测确认，若有不一致，再用 `context.createConfigurationContext(带应用语言的 Configuration)` 兜底。

### 10.5 检查

- 单测：解析两份 `strings.xml`，**键集合必须完全一致**，占位符个数与类型一致。
- lint：`MissingTranslation`、`ExtraTranslation` 设为 error。

---

## 11. 各模块设计

> 每个模块列出：功能、数据源、设置、来源、通知、从原项目移植什么。

### 11.1 天气 `weather`

- **功能**：家所在地现在的天气（体感、风与阵风），然后是这一天：18:00 前是今天，18:00 起是明天（`DayOutlookPolicy`，白天时段 06:00–22:00）：最低/最高与体感温度、逐小时的温度曲线（标出最高与最低）及其下方的雨量柱（降雨时段加底色并标注雨量与概率，每三小时标一个钟点）、穿衣建议（按最低体感温度）与温馨提示（带伞、大雨、雷暴、雨雪、路滑、大风、炎热、防晒、温差）；最后是四天预报横排。
- **数据源**：MET Norway Locationforecast 2.0 `complete`（全球；用 `complete` 是为了体感温度、阵风、紫外线与降水概率）。
- **设置**：无——固定为家。
- **来源**：`weather.forecast`。
- **移植**：`MetApi`、`WeatherPointPicker`、`DailyForecastBuilder`、天气图标（原项目自绘的 `ic_wx_*` 矢量图）。
- **注意**：MET 要求 User-Agent 带联系方式，改为 `Dayloom/<版本> (+https://github.com/lonemoonspace/dayloom)`；界面需注明数据来源（CC BY 4.0）。

### 11.2 公共交通 `transit`（第一版最大的一块）

- **功能**
  1. **通勤行程**，**火车**通勤与**公交**通勤分开，各有自己的站点、卡片、设置与异常开关：起点站 → 终点站（该类车辆的任意线路；Entur `trip` 查询限定为 `rail` 加铁路替代巴士（`bus` 的 `railReplacementBus` 子类型，线路施工时只有它在跑；替代巴士沿用火车线路号，只能靠子类型认出，卡片上加巴士图标与「替代巴士」），或 `bus` 与 `coach`），在上班窗口显示去程、下班窗口显示回程，窗口外显示双向最近一班。列出接下来 N 个方案：出发/到达时间、换乘次数与换乘站、每段的实时状态（准点/晚点 N 分/取消）。
  2. **收藏站点发车板**：任意站点的实时发车，可按线路、终点、方向筛选（例：某站只看某条公交线路、开往某个终点的班次，可还原原项目「只显示全程车」的效果）。
- **数据源**：Entur Journey Planner v3（GraphQL）`trip` 与 `stopPlace.estimatedCalls`；站点搜索用 Entur Geocoder。请求头 `ET-Client-Name: lonemoonspace-dayloom`。
- **可插拔**：`TransitProvider` 接口（`searchStops`、`planTrips`、`departures`），第一版只有 `EnturProvider`。设置里地点不在挪威时提示「暂不支持该地区」。
- **设置**：火车、公交（起点站、终点站、显示方案数、异常提醒）与收藏站点（站点 + 筛选条件）各一张卡片。
- **来源**：`transit.train`、`transit.bus`、`transit.boards`。
- **通知**：通勤窗口内，接下来的方案出现取消或大晚点时提醒（移植 `CommuteDisruptionPolicy` 的指纹去重思路）。
- **移植**：`EnturApi` 的请求与解析基础、`StationMatcher`/`TransferMatcher` 中通用的部分、状态标签的判定规则（准点/晚点/取消/实时未知）。
- **不移植**：`L1Stations`、L1/R14 专用的换乘对比、`UpcomingL1Policy`、`Bus280*`。
- **接口验证（2026-10-09，实现之前）**：`trip` 返回的每一段都带线路、起止站、计划与预计时刻以及自己的 `realtime` 标记；换乘时的步行是单独的 `foot` 段，所以换乘站可知。延误能实时看到（实测一班区域列车晚点 12 分钟）。取消：每个班次有 `cancellation`，`includeRealtimeCancellations`（行程）与 `includeCancelledTrips`（发车板）参数可用；验证时恰好没有正在发生的取消可供观察。很多班次根本没有实时数据（某繁忙市区站 300 班中有 88 班），所以「实时未知」是单独的状态，绝不显示成准点。发车板的线路过滤 `whiteListed: {lines}` 在服务端又能用了，但过滤仍在客户端做，因为设置里用的是线路号与终点文字。站点搜索用 Geocoder v3（`q`、`limit`、`layers=stopPlace`）。被取消的方案照样显示并标出，而不是隐藏；严重延误指下一个方案任一段已知延误至少 5 分钟。

### 11.3 路况 `traffic`

- **功能**：起点 → 终点的预计用时、畅通用时、距离、拥堵等级；按日常时间窗自动切换去程/回程。
- **数据源**：Google Routes API，**用户自己的 Key**（设置页明确提示需在 Google Cloud 开通、可能产生费用）。
- **设置**：起点、终点（默认「家 → 公司」）、共用的 Google Key（`core.google_maps`，需启用 Routes API）。
- **来源**：`traffic.route`。
- **默认关闭**：没有 Key 的用户不会看到一张报错的卡片。

### 11.4 到期提醒 `reminders`

- **功能**：用户自定义条目列表，每条有名称、截止时间、提前提醒天数；快到期/已到期时卡片置顶。
- **设置**：条目增删改。
- **来源**：无（纯本地数据）。
- **通知**：快到期、已到期，每条每个阶段只提醒一次。
- **移植**：`TicketPolicy` 的判定逻辑，改为对任意条目生效。

### 11.5 日历 `calendar`

- **功能**：首页顶部的日期头：时间、日期、ISO 周数；可选农历（干支生肖、节气）；所选国家的节假日与倒计时。
- **农历数据来源**：`cn.6tail:lunar`（lunar-java，MIT），由 `LunarProvider` 接口包一层，日历模块只依赖接口，以后换实现不影响其他代码。不沿用原项目内置的香港天文台对照表（许可不允许，见 §19）。
- **本地校验**：`scripts/verify_lunar.py` 只在开发机运行，临时下载天文台 1901–2100 年对照表，与 lunar-java 的结果逐日比对（农历日期、闰月、节气）；天文台数据不入库、不进 APK。2026-10-09 的结果：73,029 天中有 42 天不一致，1980–2056 年一天都没有——六个节气差一天（1912–1979 年），以及 2057 年九月（那次朔日距北京时间零点只有几分钟）；这些在 `LunarReferenceTest` 里列为已知差异。
- **节假日**：`HolidayProvider` 接口，第一版内置**中国**（法定节假日 + 农历传统节日）与**挪威**（含复活节浮动假日）两个实现，可多选、同名同日合并（沿用原项目规则）。以后可加更多国家，或接 Nager.Date 等开放数据。
- **设置**：显示农历（开关）、节假日国家（多选）。
- **来源**：无（全部本地计算）。
- **移植**：`Holidays`、`NorwayHolidays`、`CountdownText` 的判定部分；所有名称改为资源。**不移植** `LunarData` 与 `scripts/generate_lunar_data.py`（数据许可问题）。

### 11.6 足球 `football`

- **功能**：独立标签页，显示所关注球队的最近赛果、未来赛程、所在联赛积分榜；比赛进行中静默轮询比分。
- **数据源**：football-data.org v4，**用户自己的 Key**（免费档即可）。
- **设置**：关注的球队（先选赛事、再从该赛事的球队列表里选，因为免费档没有全局球队搜索）、Key。
- **来源**：`football.matches`、`football.standings`。
- **通知**：开赛提醒、终场比分。
- **移植**：`FootballDataOrgApi`、`FootballStatusBuilder`、比分/点球大战判定、实时轮询策略；`isRealMadrid` 改为 `isFollowedTeam`。
- **不移植**：皇马队徽图片（商标）；队徽改为从接口返回的 URL 加载。
- **实现（M5）**：原项目代码在实现时不可用，按接口文档重写，并用模拟服务器测试。`football.matches` 取今天往前四周、往后五周的比赛，刷新节奏随赛程变化（`FootballPolicy.cadence`：进行中每分钟、比赛前后每 15 分钟、其余每三小时），所以首页卡片与标签页的比分是实时的；`football.standings` 每六小时一次，只取总表。v4 把点球大战计入 `fullTime`，比赛比分取常规时间加加时（或全场减点球）。首页卡片只在比赛前后出现（进行中、24 小时内开赛、12 小时内刚结束），进行中时置顶。两条通知规则 `kickoff`（开球前一小时内）与 `result`（只看本轮刷新成功的快照、开球后 11 小时内）共用一个通知 id，终场比分替换开赛提醒。队徽：Android 画不了 SVG，同一主机上有并列的 PNG，所以改取 PNG，失败时显示三字母代码。赛事名称（十二项免费赛事）放在资源里双语显示。

### 11.7 新闻 `news`

- **功能**：独立标签页，Miniflux 订阅的文章列表、详情、已读/收藏，以及 OpenAI 兼容接口的 AI 摘要。
- **数据源**：用户自己的 Miniflux 服务器与 LLM 接口。
- **设置**：Miniflux 地址与 Token、LLM 地址/模型/Key。
- **存储**：Room `dayloom.db`（schema v1 起导出）。
- **后台任务**：`news.sync` 是一个普通来源，在共用的刷新轮次里运行（见 §12）。
- **实现（M6）**：原项目代码在实现时不可用，按 Miniflux API v1 与 OpenAI 兼容接口重写，并用模拟服务器测试。同步：先下载未读与已收藏（各最多 200 条，按发布时间倒序），再把本地修改推送上去（已读/未读一次请求；收藏接口是切换式的，只在服务器状态与想要的不同时才发），最后合并进 Room。本地修改在推送成功前保持「待发送」并优先，离线时读过的文章不会被改回去；从完整的未读列表里消失、本地又没改过的文章视为在别处读过；被 200 条截断的列表不做这种推断；收藏只在确知服务器状态时才切换；已读且未收藏的文章在读过 14 天后删除。网络请求在锁外进行，合并时只清除值与已发送值相同的待发送修改，同步期间的点按不会丢。打开未读文章即标为已读并尽快推送已读状态；首页卡片的数字直接取自 Room，读完立即更新。AI 摘要只在用户点按钮时生成（可能产生费用），正文去掉标签后截到 12000 字符，提示词按 App 语言选择，摘要存进 Room。地址只接受 https（令牌不能明文传输）；没写协议的地址自动补 https，http:// 会被拒绝并说明原因。服务器与令牌用同一个「保存并连接」按钮保存，随后经协调器同步一次，显示未读数或错误。Room 通过 `ModuleContext.database()` 获得，`dayloom.db` 只能有一个模块持有。首页有一张小卡片：未读数与最新三条标题。
- **摘要语言**：跟随应用语言（提示词中英两套）。
- **移植**：基本整体移植（它本来就是通用的），主要工作是文案双语化与接入模块接口。

---

## 12. 后台任务

- 一个通用的周期刷新 Worker（唯一任务名 `dayloom.refresh`）：按各来源的 `RefreshCadence` 挑选需要刷新的来源 → 刷新 → 交给 `NotificationEngine` 评估所有已启用模块的规则。
- 不加联网约束：只依赖时间的规则（到期提醒）离线时也要运行，离线的来源直接跳过、不发请求。只有实际尝试过的来源全部失败时才让 WorkManager 重试。
- 首页可见时每分钟按同样的节奏检查一次，刷新到期的来源；失败的来源要再等一个间隔才重试，不会每分钟敲一次坏掉的服务。
- 模块不另设 WorkManager 任务：新闻同步这类工作也是一个 `CachedSource`（`news.sync`），在同一轮里按自己的 `RefreshCadence` 运行，沿用离线跳过、错误显示与失败节流；模块关闭时它自然不再被刷新。这样只有一个 Worker 需要冻结。
- Worker 的类名与唯一任务名从 v1.0 起冻结（WorkManager 按类名实例化已排期的任务）。

---

## 13. 首次启动引导

1. 欢迎 + 语言选择；
2. 设置「家」（搜索或输入坐标），可跳过；
3. 勾选要启用的模块（默认：日历、天气、到期提醒；需要 Key 或仅限挪威的模块默认不勾，并注明原因）；
4. 进入首页。未配置完的模块卡片显示「去设置」引导。

---

## 14. 安全、隐私与数据来源

- APK 不含任何 API Key；凭据只存在手机上，用 Keystore 加密。
- 无统计、无崩溃上报、无广告；网络请求只发往用户启用的模块对应的服务。
- 权限：`INTERNET`、`ACCESS_NETWORK_STATE`、`POST_NOTIFICATIONS`（运行时申请，用户打开任一通知开关时才请求）。不申请定位权限。
- 「关于」页同时列出所有第三方库及其许可证（MIT 等许可要求随软件附上版权声明）。
- 凭据只随请求发往其所属服务；沿用 `CredentialRedirectGuard` 防止重定向泄露。
- 设置页增加「关于 / 数据来源」部分（M7：数据来源、隐私与开源许可三个对话框；隐私声明另见 `PRIVACY.zh-CN.md`）：

| 数据 | 来源 | 许可 / 要求 |
|---|---|---|
| 天气 | MET Norway | CC BY 4.0，需注明来源；User-Agent 带联系方式 |
| 公共交通 | Entur | NLOD，需注明来源；请求头 `ET-Client-Name` |
| 地点搜索 | 有 Key 时用 Google Places API（新版），没有时用 Entur Geocoder 与 Nominatim | 结果旁注明来自 Google 地图；Entur 为 NLOD；OpenStreetMap 为 ODbL，需注明「© OpenStreetMap 贡献者」 |
| 路况 | Google Routes | 用户自己的 Key，受 Google 服务条款约束 |
| 足球 | football-data.org | 用户自己的 Key，受其条款约束 |
| 农历 | lunar-java（`cn.6tail:lunar`） | MIT，需附版权声明；不使用香港天文台对照表 |

---

## 15. 冻结规则

**v1.0.0 之前**：以下内容都可以自由改，不需要迁移。

**v1.0.0 起冻结**（改了会丢用户数据或产生重复通知；要改必须写迁移并有测试）：

- applicationId `io.github.lonemoonspace.dayloom`；
- 模块 id、来源 id、凭据 id、通知渠道 id、通知 id 号段、通知状态键；
- 存储文件名（§6.1）与 `@Serializable` 类型的 JSON 格式（多态子类必须有 `@SerialName`）；
- Room 数据库名与 schema；
- WorkManager 唯一任务名与 Worker 全限定类名；
- `MainActivity` / `DayloomApp` 全限定类名；深链 scheme `dayloom://`；
- `SecretBox` 的密文格式与 Keystore alias。

---

## 16. 里程碑

每个里程碑一个 PR（或几个），合入 `main` 前 `verify` 必须全绿。

| 里程碑 | 内容 | 完成标准 |
|---|---|---|
| **M0 骨架** | Gradle 工程、包结构、移植 core（time/json/error/network/storage/secret/refresh/notify）并泛化；模块接口与注册表；导航/首页/设置外壳；双语基础设施与语言切换；`ArchitectureTest`、字符串一致性测试；CI 与发版 workflow；LICENSE、README、CLAUDE.md | 空壳 App 能运行、能切换语言；加一个测试用的「示例模块」验证 §4.4 的步骤成立；`verify` 全绿 |
| **M1 地点 + 天气 + 日历** | `core/location`、`core/routine`；天气模块；日历模块 | 两个模块可用、双语 |
| **M2 到期提醒 + 路况** | 两个模块 | 同上 |
| **M3 公共交通** | 第一步：Entur 接口验证（能否拿到取消、晚点、换乘细节）；然后通勤行程、收藏站点、中断通知 | 用你自己的配置能替代原项目的火车与公交卡片 |
| **M4 后台与通知** | 后台刷新 Worker、通知引擎接入、早间简报、首次启动引导 | 第一个测试包（`0.1.0-rc.1`） |
| **M5 足球** | 足球模块 | 可用、双语 |
| **M6 新闻** | 新闻模块（含 Room v1） | 可用、双语 |
| **M7 打磨** | 「关于/数据来源」页、README 截图、许可核查、隐私说明 | **第一个可用版本** |
| v1.0.0 | 稳定一段时间后发布 | 冻结规则生效 |

你在 M3 之后就可以开始在手机上和原 App 并行使用；M6 完成后功能追平，可以考虑让原 App 退役。

---

## 17. 测试策略

- **Policy 纯 JVM 单测**：所有判定逻辑（天气点挑选、行程状态、到期判定、节假日、农历、比分…），`now` 当参数。
- **移植的单测一起带过来**：`RefreshCoordinatorTest`、`SecretBox`/`SecretStore`、各 Policy 的现有测试，按新接口改写。
- **架构测试**：§3.3 的四条规则。
- **注册表测试**：id 唯一、通知号段不重叠。
- **字符串一致性测试**：§10.5。
- **Robolectric**：只用于 DataStore / Room 迁移测试（v1.0 之后才会有）。

---

## 18. 从 PersonalAssistant 移植清单

| 分类 | 内容 |
|---|---|
| **基本原样移植** | `SecretBox`、`SecretStore`、`AppError`、`AppJson`、`HttpCalls`、`SharedHttpClient`、`CredentialRedirectGuard`、`ConnectivityMonitor`、`AppClock`、UI 组件（`InfoCard`、`Glass`、`Skeleton`、`StatusWidgets`、主题） |
| **泛化后移植** | `CachedSource`、`RefreshCoordinator`、`RefreshCadencePolicy`、`BackgroundRefreshPolicy`、`NotificationEngine`、`NotificationRule`、天气全套、日历（节假日部分）、足球全套、新闻全套、`TicketPolicy` |
| **重写** | 首页、设置、导航（改为注册表驱动）；公共交通（改为通用规划） |
| **不移植** | `L1Stations`、L1/R14 换乘对比、`Bus280*`、皇马队徽、`LunarData` 与其生成脚本、`LegacySecretsMigration`、原项目的所有数据迁移代码、个人默认值与预览数据里的地址 |

移植前逐个文件检查：默认值、测试数据、预览数据、注释里的个人信息一律清除。

---

## 19. 决策记录与待定问题

### 已决

| 问题 | 决定 |
|---|---|
| 卡片排序 | 直接做拖动排序，并提供无障碍的上移/下移（§4.3） |
| 英文版设计文档 | 补 `docs/design.en.md`，与中文版保持一致 |
| 设备定位 | 不用。最初只做一次性定位；rc.1 测试后去掉，改为 Google 搜索与手动输入坐标（§8） |
| 农历数据 | 用 lunar-java（MIT），接受其上游（寿星天文历）许可不够明确的轻微风险；用天文台数据做本地校验（§11.5） |
| minSdk | 33（Android 13）。使用者的手机都是新机型；换来：毛玻璃模糊与动态取色在所有设备上都可用、通知权限只有一种流程、按应用语言用系统原生实现 |
| 仓库公开时间 | 从 M1（2026-10-09）起公开，而不是等到 M7：公开仓库的 GitHub Actions 分钟数免费。从 PersonalAssistant 移植的代码（天气、节日、图标）经所有者同意公开；移植时去掉个人数据（§18） |
| 门禁编译 Release | `verify` 与 CI 另外运行 `compileReleaseKotlin`：M1 时发现 Release 源集缺了一个只有 Debug 才有的文件，只编 Debug 的门禁看不出来。CI 每次多约一分钟；R8 仍只在发版 workflow 里跑 |
| 到期提醒 | 每个条目、每个截止时间在进入提醒期、截止当天、过期时各提醒一次——过期提醒只在过期后一天内发，录入的旧日期不会打扰人。规则从 M2 起就有；选择加入的开关在 M4 随通知接线与权限流程一起加上，不会出现一个按了没用的开关 |
| 公共交通 | 目前只有 Entur；实现前已验证接口（§11.2）。通勤卡片跟随日常时间窗（时间窗内只看一个方向，时间窗外两个方向各显示下一个方案）；收藏站点发车板在客户端按线路号与终点过滤。异常通知规则从 M3 起就有，选择加入的开关在 M4 随通知接线一起加上 |
| 日常时间窗 | 所有模块共用（§8），每天都一样（rc.1 测试后去掉了工作日选择与通勤开关）；起止都是墙上时间，夏令时切换日的时间窗会短一小时或长一小时，与原项目一致 |
| 通知开关 | 不设通知总开关：每类通知各有一个默认关闭的开关，「全部关掉」交给系统设置。通知权限只在用户打开某个开关时请求；权限或对应渠道被关掉时开关上直接提示并给出系统设置入口 |
| 后台刷新 | WorkManager 每 15 分钟一轮，不加联网约束，每个来源按自己的 `RefreshCadence` 节流；首页可见时每分钟检查一次（§12） |
| 早间简报 | 每天上班时间窗内第一轮后台刷新时发送，天气、公共交通、路况、到期提醒各一行；数据过旧的模块不出现在简报里（§7.3） |
| rc.1 测试反馈（2026-10-09） | 日历页头：节气放在时钟那一行，干支年与农历日期写成一行、与公历日期同高。天气：固定为家，没有设置；卡片上的通勤时间窗换成当天概况、穿衣建议与温馨提示，18:00 起改看明天。公共交通：火车与公交通勤分开（来源、卡片、设置、规则）。地点：不用设备定位；Google 搜索（共用 Key）、手动输入坐标，没有 Key 时退回 Open-Meteo。日常时间窗每天生效。一个模块现在可以有多张设置卡片（`ModuleInstance.settingsSections`）。整体视觉另行讨论 |
| 视觉设计（2026-10-09） | 三个样稿中选定方向 B：Material 3 Expressive，颜色取自壁纸（Material You），卡片 20 dp 圆角；天气是一整块主题主色的主卡，不随天气变色；卡片标题行一行放下线条图标、标题、路线与「更新于」；每个出行方案一行（出发、线路、到达、用时、换乘、站台、状态胶囊）；每段通勤时间窗默认显示 3 个方案 |
| 新闻同步（M6） | 不另设 `news.sync` WorkManager 任务，而是作为来源在共用的刷新轮次里运行（§12）；Room 经 `ModuleContext.database()` 提供，`dayloom.db` 只能由一个模块持有 |
| 足球与新闻的实现（M5、M6） | PersonalAssistant 代码在实现时不可用，按公开接口文档重写，用模拟服务器测试；拿到真实 Key 后需要实测一次 |

### 决策依据（存档）

**1. 农历数据来源（已决：方案 A）**

调查结论：原项目的 `LunarData.kt` 由脚本从香港天文台网站的 1901–2100 年对照表（`hko.gov.hk/.../T{年份}e.txt`）生成。

- 天文台网站的使用条件只允许**非商业用途**，要求复制时附上其知识产权声明与使用条件，明确禁止任何形式的出售或换取利益，并保留随时撤回许可的权利。Apache-2.0 允许任何人商用，两者冲突，**不能把这份数据放进开源仓库或 APK**。
- DATA.GOV.HK 上同一数据集的条款允许商用与再分发（需注明来源），但那里的 CSV **只有 2023–2028 年**；另有按日期查询的在线接口，要联网，不适合离线日历。

可选方案：

| 方案 | 说明 | 评价 |
|---|---|---|
| **A. 用 `cn.6tail:lunar`（lunar-java，MIT）**（推荐） | 无第三方依赖的 Java 库，节气与朔望用寿星天文历（sxwnl）算法，精度高；支持农历、干支、生肖、节气 | 许可兼容 Apache-2.0；省掉自己维护数据表。顾虑：它移植自寿星天文历，而寿星原项目没有明确的许可证（只写「开放所有源代码，供爱好者学习参考」），存在轻微的上游许可模糊；功能远多于我们所需，靠 R8 裁剪体积 |
| B. 同作者的 Kotlin 版 `tyme4kt`（MIT） | API 更新、Kotlin 原生 | 较新，生态与资料少于 lunar-java；上游顾虑同 A |
| C. 自己实现高精度算法 | 按公开的天文算法（太阳黄经 + 朔望月）自己写 | 工作量大；原项目已验证低精度算法在零点附近会错一天，要做到天文台级别精度并不容易 |
| D. 向香港天文台申请书面授权 | 发邮件到 mailbox@hko.gov.hk | 周期不确定，且授权可随时撤回，不适合开源项目 |

不论选哪个方案，都可以用天文台数据做**本地校验**：写一个只在开发机运行的脚本，临时下载对照表、逐日比对结果，数据本身不入库、不进 APK。

**2. minSdk（已决：33）**

当时比较过的选项：

| minSdk | 对应系统 | 收益 | 代价 |
|---|---|---|---|
| 24 | Android 7.0 | 多覆盖少量老设备 | `java.time` 要开 core library desugaring；通知渠道、自适应图标都要写兼容分支 |
| 26 | Android 8.0 | 与原项目一致；`java.time`、通知渠道、自适应图标都是原生支持 | 无明显代价 |
| 28 / 29 | Android 9 / 10 | 几乎没有可以删掉的兼容代码 | 白白少覆盖一部分设备 |
| 31 | Android 12 | 动态取色（Material You）不必判断版本 | 代价明显；而且动态取色本来就可以运行时判断版本后启用 |
| **33**（选定） | Android 13 | 系统原生的按应用语言、通知权限不必判断版本；包含 31 的全部收益 | 覆盖设备最少（2025 年 12 月 Android Studio 数据约 58%）；对目标用户不构成问题 |

targetSdk 跟随 Google Play 当年的要求设置，实施时再核对。
