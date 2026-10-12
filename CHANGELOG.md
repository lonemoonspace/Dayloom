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
