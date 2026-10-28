# Changelog / 更新日志

Each version has an English section followed by a Chinese one. The heading must be exactly `## X.Y.Z`: the release workflow
looks it up literally.
每个版本先写英文、再写中文。标题必须恰好是 `## X.Y.Z`：发版 workflow 按字面查找。

## Unreleased

- Train card: "Note crossing delays" now also warns while your train still shows on time. Bane NOR sometimes adds the
  crossing wait to the expected time only minutes before departure, too late to plan a change, so a late opposing train
  due after yours is named under the row too. The line is shorter: "Opposing L1 6 min late". Still off by default.
- Train, bus and departure board cards: a delay of a few seconds no longer strikes through the timetable time under the
  same minute ("08:24" over a struck "08:24").

- 火车卡片：「注明会车晚点」在本车还显示准点时也会提示。Bane NOR 有时到开车前几分钟才把会车等待算进预计时刻，来不及安排
  换乘，所以对向车晚点、而且比本车更晚才到时，也在那一行下面写出来。提示改短为「对向 L1 晚点 6 分钟」。仍默认关闭。
- 火车、公交与发车板卡片：只晚几秒时，不再在同一分钟下面划掉时刻表时间（「08:24」下面划掉「08:24」）。

## 0.2.4

- Train card: the "Opposing delay hint" becomes "Note crossing delays". Instead of predicting a wait, which almost never
  showed because Bane NOR's expected times already include it, a row that is already late now says why when its first
  train is waiting for a late train going the other way: "Held for the opposing L1 (4 min late)". Still off by default.

- 火车卡片：「对向晚点提示」改为「注明会车晚点」。不再预测等待（Bane NOR 的预计时刻已经算进了会车等待，所以以前的提示几乎
  不出现），改为在已经晚点的那一行注明原因：第一班车在等晚点的对向车会车时，写「在等对向 L1 会车（对向晚 4 分）」。仍默认关闭。

## 0.2.3

- Train card: a new "Opposing delay hint" switch in the train settings, off by default. When it is on and a train of the
  same line leaving the first stop the other way within 5 minutes is late, the row says what it may cost: "Opposing L1
  7 min late, may miss R12", "about 4 min left to change", or for a direct train "may arrive about 7 min late". It only
  guesses at single-track crossings, stays quiet without platforms or live data, and can be wrong on double track or
  when the crossing moves.

- 火车卡片：火车设置里新增「对向晚点提示」开关，默认关闭。打开后，起点站 5 分钟内同一条线反方向的车晚点时，那一行会提示可能的
  后果：「对向 L1 晚 7 分，可能赶不上 R12」「换乘还剩约 4 分」，直达则是「可能晚到约 7 分」。这只是对单线会车的推测，缺站台
  或实时数据时不提示，双线区段或调度改了会车点时可能误报。

## 0.2.2

- Train and bus cards: each route is packed on one line, "L1 (1) Asker R12 (3)": the first line and the platform it leaves
  from, then the stop, the line changed to and its platform; a direct route is just "L1 (1)" and no longer says "Direct".
  Platforms are small outlined numbers instead of "Pl. 3", and when a row runs short of room they go from the whole card
  before the stop name is shortened. The arrival has no arrow and is as large as the departure, in its own column. A late
  leg's line sits on the same line as its status in the pill, "L1 3 min late", or is dropped when that does not fit.

- 火车与公交卡片：每条路线紧挨着排成一行，如「L1 ① Asker R12 ③」：先坐的线路和出发站台，再是换乘站、换乘的线路和它的站台；
  直达只有「L1 ①」，不再写「直达」。站台改成描边的小数字胶囊，不再写「3站台」；某一行放不下时整张卡片先去掉站台胶囊，之后才
  省略站名。到达时刻不带箭头，和出发时刻一样大，单独一列。晚点那段的线路和状态在胶囊里写成一行，如「L1 晚 3 分」，放不下时
  去掉线路。

## 0.2.1

- Train card: transfers onto a faster train now show. Entur hides a change at a bigger station (for example a local train
  to a hub, then an express) as a near-copy of the direct train, so train commutes ask for those hidden options too and
  keep one only when it arrives earlier, or lets the user leave later and still arrive at the same time. Entur documents
  that switch as for testing only, so if it is ever refused the card falls back to the plain query.
- Train and bus cards: each route is one line in aligned columns, "L1 Asker Pl. 3 R12 → 08:54" for a change or
  "L1 Direct Pl. 1 → 08:58" for a direct train, sized from what the card shows. When room runs short (12-hour clocks,
  larger fonts) the line changed to joins the stop name, or the platform column goes, whichever loses least, and only
  then is the stop name shortened; the arrival time keeps its column up to about 1.3× font size. A status too long for
  the pill drops its line name. When a change starts on the same train as a direct option, that direct option shows
  dimmed under it, so the two arrivals can be compared at a glance. The platform is the one to change at, or the one to
  leave from on a direct train. The duration is no longer shown, and a late leg's line goes above its status in the pill.
- Departures card: the time column is as wide as the card's widest time, so 12-hour times no longer shift some rows.
- Train and bus cards: a departure whose time has passed since the last update stays on the card until an update
  confirms it left, for up to 30 minutes, so a delay announced at the last minute no longer makes a train that is still
  coming disappear. This holds for transfers too, and such a train does not take one of the options to show. Until an
  update confirms it, the card refreshes every minute.

- 火车卡片：现在会显示换乘更快的车的方案。Entur 会把在大站换乘（比如先坐慢车到枢纽站，再换快车）当成直达车的近似重复
  而隐藏，所以火车通勤连这些被隐藏的方案一起要，只留下能早到、或能晚走又同时到的那些。Entur 的文档写明这个开关只供测试，哪天
  被拒绝了卡片就退回普通查询。
- 火车与公交卡片：每条路线一行、按列对齐，换乘写成「L1 Asker 3站台 R12 → 08:54」，直达写成「L1 直达 1站台 → 08:58」；
  列宽按卡片上实际显示的内容量出来。宽度不够时（12 小时制、大字号）按损失最少的方式让位：把换乘的线路并进站名，或去掉站台列，
  之后才省略站名；在约 1.3 倍字号以内，到达时刻始终保留自己的一列。状态在胶囊里放不下时去掉线路名。换乘方案和某个直达方案
  第一段是同一班车时，那个直达方案淡色显示在它下面，两个到达时刻一眼就能比较。站台是换乘的站台，直达则是出发站台。不再显示
  用时；晚点的那段线路写在胶囊里状态的上面。
- 发车板卡片：时刻列取整张卡片最宽的时刻，12 小时制时各行不再错开。
- 火车与公交卡片：上次更新之后才过了发车时刻的班次，会一直留在卡片上，直到有更新确认它已开走（最多 30 分钟）；临时才报的晚点
  不会再让一班还没来的车消失。换乘方案同样如此，而且这样的车不占「显示方案数」的名额。在有更新确认之前，卡片每分钟刷新一次。

## 0.2.0

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
    favourite stops have their own settings card. The train commute also finds rail replacement buses (during track work
    they are the only service), marked with a bus icon and "Replacement bus"; departure boards mark them too.
  - Places: no device location and no location permission. Search uses Google Places with your own Google Maps key (the
    same key as Traffic, now entered once under Places) only if you have one; without a key, street addresses come from
    Entur (Norway) and OpenStreetMap (elsewhere). Typed coordinates are accepted too.
  - Daily routine: gone altogether. Transport and traffic look to work before 12:00 and homewards from 12:00; the morning
    brief has its own time under Notifications (07:00 by default); disruption alerts cover the next departure within 45
    minutes and stay quiet 22:00–06:00. Quickly changing sources refresh more often from 06:00 to 22:00.
- New look (direction B): colours follow the wallpaper (Material You); the weather is one card in the theme colour with the
  hours, rain, clothing and tip pills and the next four days; train and bus cards have line icons, the route and the update
  time in their title row and one dense row per trip with lines, arrival, duration, transfers, platform and a status pill;
  the solar term is a pill beside the clock; expiry reminders take one row per item.
- Football module (M5): follow one team with your own free football-data.org key. Its own tab shows the match of the moment,
  fixtures, results with win/draw/loss and the league table; a home card appears around match time with a live score.
  Refreshing follows the schedule (every minute while a match is on). Opt-in kick-off reminders and final scores, and a
  morning-brief line on match days.
- News module (M6): articles from your own Miniflux server in their own tab, with Unread / Starred / All filters and an
  article view. Read and starred states sync both ways and survive being offline; syncing runs every 30 minutes in the
  shared background round. AI summaries from any OpenAI-compatible endpoint, on request, in the app language. A home card
  shows the unread count and the newest headlines. Articles are kept in Room (`dayloom.db`, schema in `app/schemas/`).
- Polish (M7): the About card in Settings opens the data sources, a privacy statement and the open-source licenses
  (including lunar-java's MIT notice); the privacy statement is also in `PRIVACY.md`.
- Layout fixes found by rendering the home screen: card titles no longer cut the route short, the timetable time of a late
  departure sits under the expected one (12-hour clocks fit), the four-day strip uses weekday names, the English lunar
  line puts the date first, and the date keeps its colour in dark mode. README screenshots added.
- Weather card: the day is now a temperature curve with its high and low and rain bars underneath, rain spells shaded
  and labelled with amount and chance; the advice pills fit one row and the next four days are stacked columns.
- News settings: server and token are saved with one "Save and connect" button that shows the unread count or what went
  wrong (two separate save buttons made it easy to save only one); an address without a scheme gets https, and http://
  says why it cannot be used.
- Review fixes: a match moved to another day after its kick-off reminder is reminded again at the new time; AI summary
  requests ask for a single answer instead of a stream, and long titles are capped like the article text.
- Module tabs: opening News could show another module's page, or an empty one if that module had been turned off since.
  Every module tab now has a route of its own, so each tab keeps only its own state.
- New launcher icon: a sunrise over the sea, a sun sliced near its base rising from three swells with three threads woven
  through them, with a matching themed-icon layer and notification icon, generated by `scripts/icons/gen_icons.py`.
- Calendar header: the holiday countdown sits above the solar term beside the clock, in the same pill and size.
- Public transport: in the evening the train card could say there were no options when the next train was more than an hour
  or two away; Entur searched only its own short window. An empty answer for that reason is now asked again with a six-hour
  window.
- Settings: every module has one card with its on/off switch in the title row and its settings below; while a module is off
  its settings stay visible but greyed out and locked. The separate module list is gone (the onboarding keeps it).
- Pull to refresh on the home screen and every module tab.
- Football tab laid out like the original app: a switch between fixtures, the table and the top scorers. Fixtures open with a
  large card for the next match (crests, kick-off, countdown), then the coming matches and the recent results with a win,
  draw or loss edge; the table and the new top-scorers list show the matchday and update time and highlight the followed
  team. The team is no longer named at the top, and its crest is the tab's icon.
- News tab laid out like the original app: All, Unread and Starred with their counts, a search over titles, sources and
  text, and one card per article with an unread dot, a star to tap, a preview, source, time and "Full text saved". Articles
  that arrive as a feed's excerpt get their full text from Miniflux after each sync (ten at a time, a failed one waits half an
  hour) or when opened, and a later sync no longer replaces it with the excerpt.
- Fixes from the code review of 0.1.0-rc.9:
  - Notifications: each stage of an expiry reminder rings even while the previous one is still in the shade, and so do a
    new commute disruption and each day's morning brief (they replaced the earlier notification silently). Deleting a
    reminder, or giving it another date, withdraws its notification. A match's final score replaces only its own kick-off
    reminder.
  - News: switching to another Miniflux server drops the articles of the old one instead of sending their read and star
    changes to unrelated articles on the new one. When the server refuses changes made here (a read-only token, a proxy
    refusing PUT), the card and the tab say so. An article whose full text is as short as its excerpt is no longer fetched
    again on every sync, and failed attempts survive a restart. Lists are indexed and counted in SQL (`dayloom.db` version
    2, with a migration and a test of it). Quick read/unread taps reach the server in order. The "All" count matches the
    list it opens.
  - Public transport: a morning brief set for the afternoon shows the way home instead of nothing. The next train on the
    same line, late by as much, is a new disruption. A trip whose times cannot be read is dropped rather than shown with a
    leg missing; one failing stop no longer blanks a whole departure board.
  - Football: the refresh pace follows the stored schedule from the first round after a start; a missing competition is
    asked for by name.
  - Calendar: a Norwegian fixed holiday no longer hides a movable one on the same day (17 May 2012 is also Ascension Day).
  - Credentials typed into a field are no longer kept in the saved instance state; AI summaries are no longer cut off by
    the shared 90-second call limit.
  - Build: CI runs `verifyRelease`, the local gate plus the release R8 pass. Places are searched with Entur Geocoder and
    Nominatim (the Open-Meteo search mentioned for M1 was replaced in rc.4).
- Status pills (traffic congestion, transit status, expiry reminders) show only their wording, without a leading "!", tick or
  other symbol. On the departure board the countdown sits in a fixed column at the right edge with the platform before it,
  so times line up across rows and stops; the expiry reminders card lines up its dates the same way.
- The weather card's four-day strip stays full in the evening, when the forecast above it has moved on to tomorrow; if the
  forecast runs short, the remaining days share the row. The holiday and solar-term countdown pills in the header share one
  width and one right edge.
- Train, Bus and Departures are now separate modules, each with its own switch in Settings, instead of all three under one
  Public transport switch. After the update each starts from the old switch and keeps its stops, favourites and place on
  the home screen. A module switched on without stops or favourites shows a card asking for them; train and bus
  disruptions have separate notification channels, and the morning brief gives each its own line.
- Fixes from the independent review of 0.2.0-rc.3:
  - News: a mistyped server address, a refused token or a page that is not Miniflux no longer deletes the articles of the
    server before; they are dropped only once the new server has answered. Unread and starred lists are fetched page by
    page, up to 1,000 each instead of the newest 200; a list cut off at that limit never marks the rest as read. Opening
    an excerpt does not ask for its full text again when the server says the article is that short, or when it failed in
    the last 30 minutes.
  - Storage: a damaged settings, keys or notification-state file starts over empty instead of crashing the app on every
    start, and a failing file read is retried instead of crashing it. If the settings still cannot be read after a few
    seconds at start, a message says so and offers the system's app info instead of a blank screen.
  - Notifications: a commute disruption alert is taken down once the delay is over or that trip has left, also when no
    other trip is due yet (the next one is far off, none are left today, or it is night), and a module's alerts are taken
    down when the module is switched off.
  - Settings: a swipe that starts on the dimmed settings of a module that is off scrolls the page again. The notification
    switch drops its "permission denied" note once permission is granted in the system settings. Saving an empty time zone
    turns "Follow device" back on.
  - Home: the football card leaves no gap when there is no match to show, and an old snapshot no longer shows a match as
    live hours after it ended. Pulling to refresh right after start-up refreshes instead of doing nothing.
  - Public transport: trips cancelled in the timetable itself are shown struck through, like real-time cancellations.
  - Smaller: team crests are decoded at the size they are shown, very wide or tall ones too; a damaged "options" setting
    no longer crashes the commute card; the traffic key note says how many background requests a month to expect and when
    that stays free.
- Departure board rows now look like the train and bus cards: the departure time large on the left (the timetable time
  struck through under it when late), the line and destination with the countdown within the hour, the platform under
  them, a status pill on the right of every row (on time included), and dividers between rows.

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
  - 公共交通：火车与公交通勤分开，各有自己的站点、卡片、设置与异常提醒；收藏站点有单独的设置卡片。火车通勤也会找到铁路替代巴士
    （线路施工时只有它在跑），用巴士图标和「替代巴士」标出；发车板上同样标出。
  - 地点：不再使用设备定位，也不申请定位权限。搜索用你自己的 Google Maps Key 调 Google Places（与路况共用一个 Key，现在只需在
    「地点」里填一次），但只是可选项；没有 Key 时街道地址来自 Entur（挪威）与 OpenStreetMap（其他地方）。也可以直接输入坐标。
  - 日常作息：整个去掉。公共交通与路况 12:00 前看去程、12:00 起看返程；早间简报在「通知」里有自己的发送时间（默认 07:00）；
    异常提醒只看 45 分钟内发车的下一班，22:00–06:00 不提醒。变化快的来源在 06:00–22:00 刷新得更勤。
- 新外观（方向 B）：颜色跟随壁纸（Material You）；天气是一整块主题色的主卡，包含几个时间点、降雨、穿衣与提示胶囊以及之后四天；
  火车与公交卡片的标题行有线条图标、路线与更新时间，每个方案一行，含线路、到达、用时、换乘、站台与状态胶囊；节气是时钟旁的
  小胶囊；到期提醒每个条目一行。
- 足球模块（M5）：用你自己的免费 football-data.org Key 关注一支球队。独立标签页显示当下的比赛、赛程、带胜平负的赛果与联赛积分榜；
  比赛前后首页出现一张带实时比分的卡片。刷新节奏随赛程变化（比赛进行中每分钟一次）。需要主动打开的开赛提醒与终场比分，比赛日的
  早间简报里有一行。
- 新闻模块（M6）：来自你自己的 Miniflux 服务器的文章，独立标签页，可按未读 / 收藏 / 全部筛选，并有文章阅读页。已读与收藏状态
  双向同步，离线时的修改不会丢；同步每 30 分钟在共用的后台轮次里运行一次。可用任意兼容 OpenAI 的接口按需生成 App 语言的 AI 摘要。
  首页卡片显示未读数与最新标题。文章存于 Room（`dayloom.db`，schema 在 `app/schemas/`）。
- 打磨（M7）：设置里的「关于」卡片可以查看数据来源、隐私声明与开源许可（含 lunar-java 的 MIT 声明）；隐私声明另见 `PRIVACY.zh-CN.md`。
- 渲染首页时发现并修正的排版问题：卡片标题不再过早截断路线；晚点班次的时刻表时间放到预计时间下面（12 小时制也放得下）；四天预报
  一律用星期；英文农历一行先写日期；深色模式下日期颜色正确。README 加入截图。
- 天气卡片：这一天改为温度曲线（标出最高与最低）加下方的雨量柱，降雨时段加底色并标注雨量与概率；建议胶囊排成一行，
  之后四天改为上下排列的四列。
- 新闻设置：服务器与令牌用同一个「保存并连接」按钮保存，并显示未读数或出错原因（原来两个保存按钮，很容易只存了一个）；
  没写协议的地址自动补 https，http:// 会说明为什么不能用。
- 审查修正：开赛提醒发出后又改期的比赛，会按新时间再提醒一次；AI 摘要请求明确要求一次性返回而不是流式，过长的标题与正文
  一样有长度上限。
- 模块标签页：打开「新闻」可能显示另一个模块的页面，若那个模块已被关闭则是空白页。现在每个模块标签页都有自己的路由，
  各自只保留自己的状态。
- 新启动器图标：海上日出，底部切片的太阳从三道涌浪里升起，三缕细线与浪一压一挑；配套主题图标层和通知图标，
  由 `scripts/icons/gen_icons.py` 生成。
- 日历页头：节日倒计时放到时钟旁节气的上面，用同样的胶囊和字号。
- 公共交通：晚上下一班火车在一两个小时以后时，火车卡片会显示查不到方案，因为 Entur 只在它自己选的短时间窗里找。现在因此查空时
  会用六小时的时间窗再查一次。
- 设置：每个模块一张卡片，标题行里是开关，下面是它的设置；模块关闭时设置仍然可见，但变灰且不能修改。单独的模块列表去掉了
  （首次启动引导里保留）。
- 首页和各模块标签页都可以下拉刷新。
- 足球标签页改用原 App 的布局：在赛程、积分榜与射手榜之间切换。赛程最上面是下一场的大卡片（队徽、开球时间、倒计时），
  然后是近期赛程和最近赛果（左边色条标出胜平负）；积分榜和新增的射手榜显示轮次与更新时间，并高亮关注球队。顶部不再写队名，
  标签图标改为关注球队的队徽。
- 新闻标签页改用原 App 的布局：带数量的「全部 / 未读 / 收藏」，可搜索标题、来源与正文，每篇文章一张卡片（未读圆点、可点的
  星标、预览、来源、时间与「已缓存全文」）。只发摘要的订阅源的文章，会在每次同步后（每次十篇，失败的等半小时再试）或打开时从
  Miniflux 取回全文，之后的同步不会再用摘要把它覆盖。
- 0.1.0-rc.9 代码审查后的修正：
  - 通知：到期提醒的每一档都会响铃，即使上一档还留在通知栏；新的通勤异常和每天的早间简报也一样（原来会静默替换前一条）。删除提醒
    或改日期时撤下它的通知。比赛的终场比分只替换同一场的开赛提醒。
  - 新闻：换到另一台 Miniflux 服务器时清掉旧服务器的文章，不再把它们的已读与收藏修改发到新服务器上不相干的文章。服务器拒收本机的
    修改时（只读令牌、代理拒绝 PUT），卡片与标签页会提示。全文和摘要一样短的文章不再每次同步都重抓，失败记录重启后仍在。列表加了
    索引并在 SQL 里计数（`dayloom.db` 第 2 版，带迁移及其测试）。快速的已读/未读点按按顺序到达服务器。「全部」的数量与打开的列表一致。
  - 公共交通：设在下午的早间简报显示回家方向，而不是空着。同一线路的下一班车晚点同样多，也算新的异常。时刻读不出的方案整个丢掉，
    不再少一段显示；某一个站点出错不再让整个发车板空白。
  - 足球：刷新节奏从启动后的第一轮起就按已存的赛程；缺赛事时提示的是赛事。
  - 日历：挪威的固定节日不再盖住同一天的浮动节日（2012-05-17 也是耶稣升天节）。
  - 输入框里的凭据不再存进保存的实例状态；AI 摘要不再被共享的 90 秒调用上限截断。
  - 构建：CI 运行 `verifyRelease`，即本地门禁再加 Release 的 R8 处理。地点搜索用 Entur Geocoder 与 Nominatim（M1 提到的 Open-Meteo
    搜索已在 rc.4 换掉）。
- 状态胶囊（路况拥堵、公共交通状态、到期提醒）只显示文字，前面不再加「!」、对勾等符号。发车板的倒计时放在靠右的固定列，
  站台在它前面，各行、各站点的时刻上下对齐；到期提醒卡片的日期也同样对齐。
- 天气卡片的四天横排在傍晚（上方已切到明天）时也保持排满；预报天数不够时，剩下的几天平分整行。页头的节日与节气倒计时胶囊等宽且右侧对齐。
- 火车、公交与发车板拆成三个模块，在设置里各有自己的开关，不再共用一个「公共交通」开关。升级后三个开关都沿用原来的状态，
  站点、收藏站点与首页卡片位置都保留。开启了但没设置站点或收藏站点时，卡片会提示去设置；火车与公交的异常通知分成两个渠道，
  早间简报里也各占一行。
- 0.2.0-rc.3 独立审查后的修正：
  - 新闻：服务器地址打错、令牌被拒或对方不是 Miniflux 时，不再删掉上一台服务器的文章；只在新服务器成功应答后才清空。未读与收藏
    列表分页获取，各最多 1,000 条，不再只取最新的 200 条；在上限处被截断的列表不会把其余文章标成已读。打开只有摘要的文章时，
    服务器已表明本来就这么短、或 30 分钟内刚失败过的，不再请求全文。
  - 存储：设置、Key 或通知状态文件损坏时清空重来，而不是每次启动都崩溃；读文件失败会重试，而不是让 App 崩溃。启动几秒后仍读不出设置时，显示提示并提供系统「应用信息」入口，
    而不是一片空白。
  - 通知：延误结束或那班车已开走后，通勤异常通知会被撤下，没有下一班可提醒时（下一班还早、今天没车了或已到夜间）也一样；
    关闭模块时撤下它的通知。
  - 设置：从关闭模块的灰色设置区开始滑动，页面又能滚动了。在系统设置里授予通知权限后，开关下的「权限被拒绝」提示会消失。
    保存空白时区时，「跟随设备」开关会重新打开。
  - 首页：没有比赛可显示时，足球卡片不再留下空隙；过时的快照不会在比赛结束几小时后仍显示「进行中」。刚启动时下拉刷新会真正刷新，
    而不是什么都不做。
  - 公共交通：时刻表里就已停运的班次也会划线显示，与实时取消一样。
  - 其他：队徽按显示尺寸解码，极宽或极高的也一样；损坏的「方案数」设置不再让通勤卡片崩溃；路况 Key 的说明写明后台刷新每月大约多少次
    请求、什么情况下仍免费。
- 发车板的每一行改成和火车、公交卡片一样：左边大字是发车时刻（晚点时下面划掉时刻表时间），中间是线路、终点与一小时内的倒计时，
  下面是站台，右边每行都有状态胶囊（准点也显示），行与行之间有分隔线。
