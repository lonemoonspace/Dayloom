# Changelog / 更新日志

Each version has an English section followed by a Chinese one. The heading must be exactly `## X.Y.Z`: the release workflow
looks it up literally.
每个版本先写英文、再写中文。标题必须恰好是 `## X.Y.Z`：发版 workflow 按字面查找。

## Unreleased

- Project skeleton (M0): module system with registry, host and namespaced storage; generic refresh coordinator and
  notification engine ported from PersonalAssistant; home screen with drag-to-reorder cards; settings for language,
  time zone and modules; English and Chinese resources; architecture and string-parity tests; CI and release workflows.
- Places and daily routine (M1): saved places (Home, Work, custom) found with Open-Meteo search or one-shot current
  location; to-work and back-home windows with working days, shared by every module.
- Weather module (M1): MET Norway forecast for a saved place, umbrella advice for each commute window, when rain starts
  or stops, and a four-day strip; ported from PersonalAssistant.
- Calendar module (M1): clock, date and ISO week; optional Chinese lunar date, stem-branch year and solar terms
  (lunar-java); holidays of China and Norway with a seven-day countdown. `scripts/verify_lunar.py` checks the lunar
  data against the Hong Kong Observatory tables on a developer machine.
- Expiry reminders module (M2): any number of items with their own warning period; the card moves to the top while one
  is about to expire or has expired. Reminder rule (warning period, last day, fresh expiry) ported from
  PersonalAssistant's ticket logic; delivery is switched on with the notification wiring in M4.
- Traffic module (M2): driving time and congestion between two saved places with the user's own Google Routes key,
  direction following the daily windows; off by default. Ported from PersonalAssistant.
- Public transport module (M3): Entur trip options between two commute stops (any line, with transfers) following the
  daily windows, with the real-time status of every leg (on time, late, cancelled, no live data); real-time departure
  boards for favourite stops, filtered by line and destination; disruption rule for the commute windows. Entur API
  validated first; request handling, status rules and disruption fingerprints ported from PersonalAssistant.
- Background work and notifications (M4): a periodic background refresh (every 15 minutes, each source throttled to its
  own cadence) that then runs every notification rule; the home screen also refreshes due sources every minute while it is
  visible. Opt-in switches for expiry reminders and commute disruptions; the notification permission is asked for only
  when one is turned on, and a switch says when the permission or its system channel is off. Morning brief: on working
  days, one notification at the start of the to-work window with a line each from weather, public transport, traffic and
  expiry reminders. First-run onboarding: language, Home, modules.
- Changed after testing 0.1.0-rc.1:
  - Calendar header: the solar term sits on the clock row; the stem-branch year and lunar date form one line level with
    the date.
  - Weather always shows Home and has no settings. Instead of umbrella advice per commute window, the card shows the day:
    low and high, feels-like, a three-hourly timeline, rain spells with amount and chance, what to wear and tips (umbrella,
    heavy rain, thunder, snow, icy roads, strong wind, heat, sunscreen, layers); from 18:00 it shows tomorrow. Data now
    comes from MET's `complete` forecast.
  - Public transport: separate train and bus commutes, each with its own stops, card, settings and disruption alerts;
    favourite stops have their own settings card.
  - Places: no device location and no location permission. Search uses Google Places with your own Google Maps key (the
    same key as Traffic, now entered once under Places), falls back to Open-Meteo without a key, and accepts typed
    coordinates.
  - Daily routine: the to-work and back-home times apply every day; the commute switch and working-day selection are gone,
    and the time buttons line up.

- 项目骨架（M0）：带注册表、宿主与命名空间存储的模块系统；从 PersonalAssistant 移植并泛化的刷新协调器与通知引擎；
  卡片可拖动排序的首页；语言、时区与模块开关设置；中英文资源；架构测试与字符串一致性测试；CI 与发版 workflow。
- 地点与日常作息（M1）：已保存的地点（家、公司、自定义），可用 Open-Meteo 搜索或单次读取当前位置；带工作日的去程、返程时间窗，
  所有模块共用。
- 天气模块（M1）：已保存地点的挪威气象局预报、每段通勤时间窗要不要带伞、雨何时开始或停止，以及四天预报横排；移植自 PersonalAssistant。
- 日历模块（M1）：时钟、日期与 ISO 周数；可选的中国农历日期、干支年与节气（lunar-java）；中国与挪威节日及七天内倒计时。
  `scripts/verify_lunar.py` 在开发机上把农历数据与香港天文台对照表逐日比对。
- 到期提醒模块（M2）：任意多个条目，各自设定提前提醒天数；有条目快到期或已过期时卡片置顶。提醒规则（进入提醒期、
  截止当天、刚过期）移植自 PersonalAssistant 的车票逻辑；实际发送在 M4 接上通知后开启。
- 路况模块（M2）：用用户自己的 Google Routes Key 查两个已保存地点之间的驾车时间与拥堵，方向随日常时间窗切换；默认关闭。
  移植自 PersonalAssistant。
- 公共交通模块（M3）：用 Entur 查两个通勤站点之间的出行方案（任意线路，可换乘），跟随日常时间窗，并显示每一段的实时状态
  （准点、晚点、取消、实时未知）；收藏站点的实时发车板，可按线路与终点过滤；通勤时段的异常通知规则。先验证了 Entur 接口；
  请求处理、状态规则与异常指纹移植自 PersonalAssistant。
- 后台刷新与通知（M4）：周期性后台刷新（每 15 分钟一次，每个来源再按自己的节奏节流），刷新后运行所有通知规则；首页可见时
  也每分钟刷新到期的来源。到期提醒与通勤异常各有一个需要主动打开的开关；只在打开开关时才请求通知权限，权限或对应的系统渠道
  被关掉时开关会提示。早间简报：工作日上班时间窗开始时发一条，天气、公共交通、路况与到期提醒各一行。首次启动引导：语言、家、模块。
- 0.1.0-rc.1 测试后的修改：
  - 日历页头：节气放在时钟那一行；干支年与农历日期写成一行，与公历日期同高。
  - 天气固定显示家所在地，没有设置项。卡片不再按通勤时段给带伞建议，改为显示这一天的情况：最低与最高温度、体感、每三小时一点的
    时间线、降雨时段及雨量与概率、穿衣建议与温馨提示（带伞、大雨、雷暴、雨雪、路滑、大风、炎热、防晒、温差）；18:00 起显示明天。
    数据改用 MET 的 `complete` 预报。
  - 公共交通：火车与公交通勤分开，各有自己的站点、卡片、设置与异常提醒；收藏站点有单独的设置卡片。
  - 地点：不再使用设备定位，也不申请定位权限。搜索用你自己的 Google Maps Key 调 Google Places（与路况共用一个 Key，现在只需在
    「地点」里填一次）；没有 Key 时退回 Open-Meteo；也可以直接输入坐标。
  - 日常作息：去程与返程时间每天都生效；去掉了通勤开关与工作日选择，时间按钮上下对齐。
