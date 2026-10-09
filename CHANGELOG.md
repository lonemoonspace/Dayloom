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
