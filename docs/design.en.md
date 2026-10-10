# Dayloom (织日) Design Document

> Status: **v1.0, approved** (2026-10-09). Later changes must update both documents and record the decision in §19.
> Chinese version: [`design.md`](design.md). The two must stay identical in content; update both in the same change.

---

## 1. Goals and non-goals

**Goals**

1. A ready-to-use Android daily dashboard that anyone can configure: weather, public transport, traffic, expiry reminders, calendar, football and news.
2. **Extensible**: adding a module = a new feature package + one line in the registry. The home screen, settings, navigation, refresh, notifications and background work need no changes.
3. **Trimmable**: users can turn off any module. A disabled module makes no requests and does not appear on the home screen, in navigation or in settings.
4. **Bilingual (English / Chinese)**: all UI text, notifications and error messages come from resources; the language can be switched inside the app.
5. No self-hosted backend, no bundled API keys, no data collection.

**Non-goals (not in the first version)**

- Public transport outside Norway (the interface exists, there is no second implementation).
- iOS, desktop, home-screen widgets.
- Accounts, cloud sync, automatic import of settings from PersonalAssistant.
- Multiple Gradle modules (see §3.3; split later if needed).

---

## 2. Decisions made

| Topic | Decision |
|---|---|
| Name | English **Dayloom**, Chinese **织日** |
| Repository | `lonemoonspace/dayloom`, fresh initial commit without old history; public since M1 (see §19) |
| License | Apache-2.0 |
| applicationId / package | `io.github.lonemoonspace.dayloom` |
| Project structure | Single Gradle module `:app`, layered by package, boundaries guarded by an **architecture test** |
| Module scope (first version) | Weather, public transport, traffic, expiry reminders, calendar, football, news — all generalized |
| Public transport | Generic trip planning via Entur Journey Planner, Norway only; pluggable through a `TransitProvider` interface |
| Weather | MET Norway, works worldwide |
| Time zone | Follows the device by default; can be overridden in settings |
| UI language | English (default resources) + Chinese; follow the system or switch in the app |
| Code comments / commit messages / CHANGELOG | Bilingual, English first, Chinese below |
| README | `README.md` (English) + `README.zh-CN.md` (Chinese), linking to each other |
| CI / release | Gate and workflows from PersonalAssistant, renamed and adapted; the gate also compiles the release sources (§19) |
| minSdk | **33** (Android 13); targetSdk follows Google Play's requirement for the year |
| Lunar calendar | `cn.6tail:lunar` (lunar-java, MIT) |
| Freeze rules | Anything may change before v1.0.0; storage formats etc. are frozen from v1.0.0 (§15) |

---

## 3. Overall architecture

### 3.1 Layers

```
┌──────────────────────────────────────────────────────────┐
│ app        Assembly: DayloomApp, MainActivity, AppGraph,   │
│            ModuleRegistry, navigation shell, home/settings │
│            shells, onboarding                              │
├──────────────────────────────────────────────────────────┤
│ feature/*  Modules: weather, transit, traffic, reminders,  │
│            calendar, football, news (each data/domain/ui)  │
├──────────────────────────────────────────────────────────┤
│ core/*     Infrastructure: module (interfaces), refresh,   │
│            notify, storage, secret, network, time,         │
│            location, routine, i18n, error, json, work, ui  │
└──────────────────────────────────────────────────────────┘
Dependencies point downwards only: app → feature → core; features never depend on each other.
```

### 3.2 Package layout

```
io.github.lonemoonspace.dayloom
├── DayloomApp.kt                // Application
├── MainActivity.kt
├── app/
│   ├── AppGraph.kt              // Hand-written DI: shared OkHttp, clock, storage, coordinator
│   ├── ModuleRegistry.kt        // The only place that lists all modules
│   ├── nav/                     // Bottom navigation and routes (generated from the registry)
│   ├── home/                    // Home shell: renders module cards in order
│   ├── settings/                // Settings shell: general settings + one section per module
│   └── onboarding/              // First-run onboarding
├── core/
│   ├── module/                  // FeatureModule, ModuleContext, HomeCard, ModuleTab…
│   ├── refresh/                 // CachedSource, RefreshCoordinator, refresh cadence
│   ├── notify/                  // NotificationRule, NotificationEngine, channels, morning brief
│   ├── storage/                 // DataStore, snapshots, module settings storage
│   ├── secret/                  // SecretStore, SecretBox
│   ├── network/                 // OkHttp, connectivity, credential redirect guard
│   ├── time/                    // AppClock, time zone
│   ├── location/                // Place, place search (Google / Entur / OpenStreetMap / coordinates)
│   ├── routine/                 // The day's rhythm: to work before noon, home from noon, day and night
│   ├── i18n/                    // UiText, language switching
│   ├── error/  json/  work/  ui/
└── feature/
    ├── weather/   transit/   traffic/   reminders/
    ├── calendar/  football/  news/
    └── <inside each module>: data/ (APIs and sources) domain/ (models and policies) ui/ (cards, screens, settings section)
```

### 3.3 Why a single module plus an architecture test

- At roughly two hundred files the build-speed benefit of multiple Gradle modules does not show, while convention plugins and per-module lint/test setup add maintenance cost.
- Boundaries are enforced by a set of JVM unit tests (`ArchitectureTest`); **a violation turns CI red**. It scans the `import`s of every file under `src/main/java`, with no third-party library:
  1. `core/**` must not import `feature/**` or `app/**`;
  2. `feature/X/**` must not import `feature/Y/**` (X ≠ Y) or `app/**`;
  3. files named `*Policy` must not import `android.*` / `androidx.*` (so they stay testable on the plain JVM);
  4. only `app/ModuleRegistry.kt` may reference a module's `XxxModule` entry class.
- If we split into Gradle modules later, the boundaries are already clean; it is mostly moving directories and adding build configuration.

---

## 4. Module system (the core)

### 4.1 Interface sketch

```kotlin
/** Static description of a feature module; no instance (and no cost) while disabled. */
interface FeatureModule {
    val id: ModuleId                    // e.g. "weather"; frozen from v1.0
    @get:StringRes val title: Int
    @get:DrawableRes val icon: Int
    val defaultEnabled: Boolean
    fun create(ctx: ModuleContext): ModuleInstance
}

/** An enabled module: declares everything it contributes. Every item may be empty. */
interface ModuleInstance {
    val sources: List<CachedSource<*, *>>          // Data sources, registered with RefreshCoordinator
    val homeCards: List<HomeCard>                  // Home screen cards
    val tab: ModuleTab?                            // Own tab (football, news)
    val settings: SettingsSection?                 // Section on the settings screen
    val notificationChannels: List<ChannelSpec>
    val notificationRules: List<NotificationRule<*>>
    val brief: BriefContributor?                   // One line in the morning brief
    val configured: Flow<ConfigState>              // Whether it is set up; if not, cards show a setup prompt
}
```

**ModuleContext** (what core provides to a module; modules get dependencies only through it):

| Capability | Description |
|---|---|
| `http` | Shared OkHttpClient |
| `clock` / `zone` | Injected clock and current time zone (Flow) |
| `settings<T>(serializer, default)` | This module's settings store (§6.2); a module can only read and write its own |
| `snapshots<T>(sourceName, serializer)` | This module's snapshot store; file names get the module prefix automatically |
| `secret(name)` | This module's credentials; ids get the module prefix automatically |
| `notifyState(name)` | State store for this module's notification rules; keys get the module prefix automatically |
| `places` | Shared saved places (§8) |
| `connectivity` | Network status |
| `appScope` | Process-wide coroutine scope |

**Namespacing**: every storage key, snapshot file name, secret id and notification state key a module receives is prefixed with `<moduleId>.` by core, so modules cannot collide.

### 4.2 Registry

```kotlin
// app/ModuleRegistry.kt — adding a module only changes this file
val allModules: List<FeatureModule> = listOf(
    CalendarModule, WeatherModule, TransitModule, TrafficModule,
    RemindersModule, FootballModule, NewsModule,
)
```

The registry has its own unit tests: module ids unique, notification id ranges non-overlapping (§7.2), notification channel ids unique, background work names unique.

### 4.3 Home cards and tabs

- `HomeCard`: `key`, default order, `placement: Flow<CardPlacement>` (e.g. an expiry reminder moves to the top when it is about to expire), `@Composable Content()`.
- The home shell renders the cards of all enabled modules in "user-defined order, then default order".
- **Ordering**: in the home screen's edit mode, long-press a card and drag it. Each card also offers "move up / move down" accessibility actions so TalkBack users can reorder too. The order is stored in `app_settings`.
- `ModuleTab`: route, icon, label, `@Composable Content()`, optional deep link. Bottom navigation = Home + tabs of enabled modules + Settings.
- A tab's ViewModel is created only when the user first opens it (as in the original project: a cold start does not call the football or news APIs).

### 4.4 Full steps to add a module (acceptance criterion)

1. Create `feature/<id>/` implementing `FeatureModule` and `ModuleInstance`;
2. add the module's strings to `values/` and `values-zh/`;
3. add one line to `ModuleRegistry`;
4. write unit tests for the module's policies.

**No changes needed** to the home screen, settings shell, navigation, refresh coordinator, notification engine, background worker or any other module. If that does not hold, it is an architecture defect.

---

## 5. Refresh

Port the core semantics of `CachedSource` and `RefreshCoordinator` from PersonalAssistant (parallel refresh, per-source single-flight, skip when offline, silent triggers, keep the old snapshot on failure) together with all their unit tests, with these changes:

| Original project | New project |
|---|---|
| `enum class SourceId` | `SourceId(value: String)`, e.g. `"weather.forecast"`, made of module id + source name |
| `paramsKey(settings: UserSettings)` | `CachedSource<P, T>`: `P` is the source's own parameter type, derived by the module from its own settings; core only sees generics |
| `commuteSources()` hard-codes weather/train/traffic/bus | Each source declares a `RefreshCadence` (foreground interval, whether to refresh in background, whether to poll more often only in the daytime); the coordinator picks sources by cadence |
| Two traffic sources (outbound/return) plus an `activeTraffic` special case | The traffic module picks the direction from the time of day (12:00) internally; core sees one source |
| `Trigger.LIVE_POLL` exists just for football | Kept as a generic "silent high-frequency polling" trigger any module can use |

Snapshot files: `snapshot_<sourceId>` (dots replaced by underscores). Snapshots carry a `schemaVersion`; incompatible snapshots are discarded and refetched.

---

## 6. Settings and storage

### 6.1 Storage files

| File | Type | Contents |
|---|---|---|
| `app_settings` | DataStore (JSON) | Global settings: language, time zone override, enabled modules, card order, onboarding done, morning brief switch |
| `module_settings` | DataStore Preferences | One key per module (= module id); the value is that module's settings as JSON |
| `shared_data` | DataStore (JSON) | Saved places (home / work / custom) |
| `secrets` | DataStore Preferences | All credentials, encrypted with `SecretBox`; key = `<moduleId>.<name>` |
| `notify_state` | DataStore Preferences | Notification rule state; key = `<moduleId>.<rule>` |
| `snapshot_*` | DataStore (JSON) | Data source snapshots |
| `dayloom.db` | Room | Used only by the news module; schema exported to `app/schemas/` from v1 |

### 6.2 Module settings

- Each module has one `@Serializable` settings class; **every field has a default value**; it carries a `version: Int` field reserved for migrations after v1.0.
- Decoding failure: before v1.0, fall back to defaults and log; from v1.0, a migration with tests is required.
- There is no longer one `UserSettings` holding every feature's settings.

### 6.3 Credentials

Keep `SecretStore` / `SecretBox` (`v1:` ciphertext format, Android Keystore) with the same rules: requests use only `usable(id)`, input fields only show `display`, ciphertext is never sent or displayed as a credential.
First-version credentials: `core.google_maps` (one Google Maps Platform key shared by place search and traffic, entered under Places; modules reach it through `ModuleContext.googleMapsKey`), `football.football_data`, `news.miniflux_token`, `news.llm_api_key`.

---

## 7. Notifications

### 7.1 Rules

- `NotificationRule<S>` moves into each module. A rule can only read its own module's snapshots and settings (through a reader in `RuleInput` scoped to that module); the global `SnapshotsView` is gone.
- Notification text is resolved from resources in the **current app language** at send time (the worker uses a localized `Context`, see §10.4). Rules return a structured `NotificationContent`, never pre-built strings.

### 7.2 Channels and ids

- Channel ids: `<moduleId>.<name>`, declared by the module and created centrally by `NotificationChannels`.
- Notification ids: each module declares an id range at registration (e.g. weather 1000–1999, transit 2000–2999…); a registry test ensures the ranges do not overlap.
- 1–999 are kept for the shell itself (morning brief: id 1, channel `core.brief`, state key `core.morning_brief`); module ranges must stay out of it, also checked with the registry.

### 7.3 Morning brief

- Implemented in core; it is the only cross-module notification. Each enabled module with a `BriefContributor` supplies one structured line (e.g. "Rain today, take an umbrella", "First departure on time", "Traffic is light, 22 min"), combined in module order into one notification.
- A new module appears in the brief automatically just by implementing `BriefContributor`.
- When it goes out: on the first background round at or after the time the user chose (07:00 by default, Settings → Notifications), once a day; three hours late it is no longer morning news and the day is skipped. When no module has anything to say (data too old, or nothing to report) the day is not used up, so a later round can still send it. A module that fails only loses its own line.

### 7.4 Notifications in the first version

| Module | Notification | Default |
|---|---|---|
| Public transport | Commute trip cancelled / heavily delayed | Off |
| Expiry reminders | About to expire / expired | Off |
| Football | Kick-off reminder, final score | Off |
| News | None (background sync sends no notifications) | — |
| core | Morning brief | Off |

All notifications are off by default (opt-in), for the same reason as in the original project: do not make interruptive choices on the user's behalf.

---

## 8. Shared concepts: places and the day's rhythm

Weather, public transport, traffic and the morning brief all need "where is home, where is work, when do I leave". To avoid every module asking separately, these live in core:

- **Saved places (`core/location`)**: `Place(id, label, name, lat, lon, countryCode?)`. Two preset slots, "Home" and "Work", plus custom places.
  - Search: **Google Places API (New) Text Search** with the user's own key (street addresses and named places); without a key, **Entur Geocoder** first (Norwegian street addresses, places, points of interest and stops; free, no key, addresses from Kartverket), and **Nominatim** (OpenStreetMap, worldwide) when it finds nothing or fails; Nominatim's policy asks for an identifying User-Agent, at most about one request a second and no search-as-you-type, so it is only called when Search is pressed. Google is optional: nobody should need a key just to enter an address (decided after the rc.4 test). Coordinates typed by hand ("59.9139, 10.7522") are always accepted as they are.
  - No device location: the app asks for no location permission at all (decided after the rc.1 test, §19).
- **The day's rhythm (`core/routine`)**: before 12:00 the cards look to work (home → work), from 12:00 homewards; 06:00–22:00 is daytime, when quickly changing sources refresh more often and commute alerts may go out. The same for everyone and every day, with no settings (replaced user-set commute windows after the rc.6 test).

Module settings refer to these shared items — weather defaults to "Home", traffic to "Home → Work" — and can point elsewhere.

---

## 9. Time and time zone

- `AppClock` provides `now()` and `zone: Flow<ZoneId>`; `zone` = the override from settings, otherwise the device zone (listening for system time zone changes).
- Every policy takes `now: ZonedDateTime` as a parameter; tests pass a fixed time and fixed zone.
- New code must not call `ZonedDateTime.now()` / `ZoneId.systemDefault()` directly; checked by the architecture test or a lint rule.
- External data (Entur, football-data.org) arrives with offsets and is converted to the current zone for display.

---

## 10. Internationalization (English / Chinese)

### 10.1 Resources

- `values/strings.xml` = English (default), `values-zh/strings.xml` = Chinese.
- Strings are grouped by module prefix — `weather_*`, `transit_*`… — for findability and future splitting.
- Use `plurals` for plurals; always positional arguments `%1$s` for parameters.

### 10.2 Rules

1. **Policy / domain code returns structured results only** (enums, sealed types, numbers, times), never user-facing text.
2. When a ViewModel needs to hand text to the UI it uses `UiText` (`Res(id, args)` / `Plural(id, n, args)` / `Raw(string)`); the UI layer resolves it.
3. Error text lives in `core/ui/ErrorText`, mapped from `AppError` types to resources.
4. Holiday names, weather descriptions, weekday names and lunar month/day names all come from resources.
5. Never store rendered text in snapshots or settings.

### 10.3 Language switching

- Settings offer "Follow system / English / 中文", implemented directly with the system per-app language API (`LocaleManager`, available from Android 13); no AppCompat needed. A declared `locales_config.xml` also lets users pick Dayloom's language in system settings.

### 10.4 Background work and notifications

- The per-app language is applied by the system to the whole process, so strings resolved by workers and notifications should match the UI. Verify this in M0; if they differ, fall back to `context.createConfigurationContext(a Configuration with the app language)`.

### 10.5 Checks

- Unit test: parse both `strings.xml` files; **the key sets must be identical**, and placeholder counts and types must match.
- Lint: `MissingTranslation` and `ExtraTranslation` set to error.

---

## 11. Module designs

> For each module: features, data source, settings, sources, notifications, and what is ported from the original project.

### 11.1 Weather `weather`

- **Features**: the weather at Home now (feels-like, wind and gusts), then the day: until 18:00 today, from 18:00 tomorrow (`DayOutlookPolicy`, daytime hours 06:00–22:00): low/high and feels-like, an hourly temperature curve (high and low marked) with rain bars underneath (rain spells shaded and labelled with amount and chance, the hour marked every three hours), what to wear (from the lowest feels-like temperature) and tips (umbrella, heavy rain, thunder, snow, icy roads, strong wind, heat, sunscreen, layers); then a four-day strip.
- **Data source**: MET Norway Locationforecast 2.0 `complete` (worldwide; `complete` for feels-like, gusts, UV and rain probability).
- **Settings**: none — always Home.
- **Sources**: `weather.forecast`.
- **Ported**: `MetApi`, `WeatherPointPicker`, `DailyForecastBuilder`, weather icons (the original project's own `ic_wx_*` vector drawables).
- **Note**: MET requires contact information in the User-Agent; it becomes `Dayloom/<version> (+https://github.com/lonemoonspace/dayloom)`. The UI must credit the data source (CC BY 4.0).

### 11.2 Public transport `transit` (the largest piece of the first version)

- **Features**
  1. **Commute trips**, a **train** commute and a **bus** commute kept apart, each with its own stops, card, settings and disruption switch: origin stop → destination stop (any line of that kind; the Entur `trip` query is limited to `rail` plus rail replacement buses (the `railReplacementBus` submode of `bus`, the only service during track work; they keep the train's line code and only the submode identifies them, so the card adds a bus icon and "Replacement bus"), or to `bus` and `coach`). Shows the outbound trips before 12:00 and the return trips from 12:00. Lists the next N options: departure/arrival times, number of transfers and transfer stops, real-time status of each leg (on time / N min late / cancelled).
  2. **Favourite stop departure boards**: real-time departures from any stop, filterable by line, destination and direction (e.g. at one stop, show only a given bus line heading to a given terminus — this reproduces the original project's "full-route buses only" behaviour).
- **Data source**: Entur Journey Planner v3 (GraphQL) `trip` and `stopPlace.estimatedCalls`; stop search via the Entur Geocoder. Request header `ET-Client-Name: lonemoonspace-dayloom`.
- **Pluggable**: `TransitProvider` interface (`searchStops`, `planTrips`, `departures`); the first version has only `EnturProvider`. If the saved place is outside Norway, settings say the region is not supported yet.
- **Settings**: one card each for train, bus (origin stop, destination stop, number of options shown, disruption alerts) and favourite stops (stop + filters).
- **Sources**: `transit.train`, `transit.bus`, `transit.boards`.
- **Notifications**: alert when the next option leaves within 45 minutes and is cancelled or heavily delayed, never 22:00–06:00 (port the fingerprint de-duplication idea of `CommuteDisruptionPolicy`).
- **Ported**: the request/parsing foundation of `EnturApi`, the generic parts of `StationMatcher` / `TransferMatcher`, the status-label rules (on time / late / cancelled / no real-time data).
- **Not ported**: `L1Stations`, the L1/R14-specific transfer comparison, `UpcomingL1Policy`, `Bus280*`.
- **API validation (2026-10-09, before implementation)**: `trip` returns every leg with line, from/to stop, aimed and expected times and its own `realtime` flag; walking between rides is a separate `foot` leg, so transfer stops are known. Delays showed live (a 12-minute late regional train). Cancellations: `cancellation` per call, `includeRealtimeCancellations` (trip) and `includeCancelledTrips` (boards) are accepted; there was no live cancellation to observe. Many departures have no real-time data at all (88 of 300 at a busy city stop), so "no real-time data" is a status of its own and never shown as on time. The board line filter `whiteListed: {lines}` works server-side again, but filters stay on the client because the settings use public line codes and destination text. Stop search uses Geocoder v3 (`q`, `limit`, `layers=stopPlace`). Cancelled options are shown, flagged, rather than hidden; heavy delay means a known delay of at least 5 minutes on any leg of the next option.

### 11.3 Traffic `traffic`

- **Features**: estimated travel time, free-flow time, distance and congestion level from origin to destination; outbound before 12:00, return from 12:00.
- **Data source**: Google Routes API with **the user's own key** (settings clearly state it must be enabled in Google Cloud and may incur charges).
- **Settings**: origin, destination (default "Home → Work"), the shared Google key (`core.google_maps`, with the Routes API enabled).
- **Sources**: `traffic.route`.
- **Off by default**: users without a key never see an error card.

### 11.4 Expiry reminders `reminders`

- **Features**: a user-defined list of items, each with a name, an expiry time and how many days ahead to remind; items about to expire or expired move the card to the top.
- **Settings**: add, edit and delete items.
- **Sources**: none (local data only).
- **Notifications**: about to expire, expired; each item notifies once per stage.
- **Ported**: the decision logic of `TicketPolicy`, generalized to any item.

### 11.5 Calendar `calendar`

- **Features**: the date header at the top of the home screen: time, date, ISO week number; optional Chinese lunar calendar (stem-branch year, zodiac, solar terms); holidays of the selected countries with countdowns.
- **Lunar data source**: `cn.6tail:lunar` (lunar-java, MIT), wrapped behind a `LunarProvider` interface so the calendar module depends only on the interface and the implementation can be swapped later. The Hong Kong Observatory table bundled in the original project is not reused (its terms do not allow it, see §19).
- **Local verification**: `scripts/verify_lunar.py` runs only on a developer machine; it temporarily downloads the Observatory's 1901–2100 tables and compares them day by day with lunar-java (lunar date, leap month, solar terms). The Observatory data is never committed or shipped in the APK. Result on 2026-10-09: 42 of 73,029 days differ, none in 1980–2056 — six solar terms a day apart (1912–1979) and the 9th month of 2057, whose new moon falls minutes from midnight China Standard Time; these are listed as known differences in `LunarReferenceTest`.
- **Holidays**: `HolidayProvider` interface; the first version ships **China** (statutory holidays + traditional lunar festivals) and **Norway** (including Easter-based movable holidays). Multiple countries can be selected; same-name, same-day holidays are merged (same rule as the original project). More countries can be added later, or open data such as Nager.Date.
- **Settings**: show lunar calendar (toggle), holiday countries (multi-select).
- **Sources**: none (all computed locally).
- **Ported**: the decision parts of `Holidays`, `NorwayHolidays` and `CountdownText`; all names moved to resources. **Not ported**: `LunarData` and `scripts/generate_lunar_data.py` (data licensing).

### 11.6 Football `football`

- **Features**: its own tab with the followed team's recent results, upcoming fixtures and league table; silent polling of the score while a match is live.
- **Data source**: football-data.org v4 with **the user's own key** (the free tier is enough).
- **Settings**: followed team (pick a competition first, then a team from that competition, because the free tier has no global team search), key.
- **Sources**: `football.matches`, `football.standings`.
- **Notifications**: kick-off reminder, final score.
- **Ported**: `FootballDataOrgApi`, `FootballStatusBuilder`, score / penalty shoot-out logic, live polling policy; `isRealMadrid` becomes `isFollowedTeam`.
- **Not ported**: the Real Madrid crest image (trademark); crests are loaded from the URLs the API returns.
- **Implementation (M5)**: the original code was not available at implementation time, so it was rewritten from the API documentation and tested against a mock server. `football.matches` fetches four weeks back to five weeks ahead, with a cadence that follows the schedule (`FootballPolicy.cadence`: every minute while live, every 15 minutes around a match, every three hours otherwise), so scores on the home card and in the tab are live; `football.standings` refreshes every six hours, overall tables only. v4 counts a shoot-out into `fullTime`, so the score of play is regular plus extra time (or full time minus the shoot-out). The home card appears only around match time (live, kick-off within 24 hours, finished within 12 hours) and moves to the top while live. Two rules, `kickoff` (within an hour before kick-off) and `result` (only from a snapshot refreshed in this round, up to 11 hours after kick-off), share one notification id, so the final score replaces the kick-off reminder. Crests: Android cannot draw SVG and the same host serves a PNG next to each SVG, so the PNG is used, with the three-letter code as fallback. The twelve free competitions have bilingual names in resources.

### 11.7 News `news`

- **Features**: its own tab with the Miniflux article list, article detail, read/starred state, and AI summaries via an OpenAI-compatible API.
- **Data source**: the user's own Miniflux server and LLM endpoint.
- **Settings**: Miniflux URL and token; LLM URL, model and key.
- **Storage**: Room `dayloom.db` (schema exported from v1).
- **Background work**: `news.sync` is an ordinary source that runs in the shared refresh round (see §12).
- **Implementation (M6)**: the original code was not available at implementation time, so it was rewritten against the Miniflux API v1 and the OpenAI-compatible chat API and tested against a mock server. A sync first downloads the unread and starred entries (up to 200 each, newest first), then pushes local changes (read/unread in one request; the bookmark call toggles, so a star is only sent when the server's state differs from the wanted one), then merges into Room. A local change stays pending, and wins, until a push succeeds, so reading offline is not undone; an article that left a complete unread list without a local change was read elsewhere; a list cut off at 200 proves nothing; a star is only toggled when the server's state is known; read, unstarred articles are deleted 14 days after they were read. Network calls run outside the lock and the merge only clears a pending change whose value was the one sent, so a tap during a sync survives it. Opening an unread article marks it read and pushes read states right away; the home card counts come straight from Room, so reading updates them at once. AI summaries are made only when the user taps the button (they may cost money), from the text without markup cut to 12,000 characters, with a prompt in the app language, and kept in Room. Addresses must be https, because the token must not travel in clear text; an address typed without a scheme gets https, and http:// is refused with that reason. Server and token are saved with one "Save and connect" button that syncs once through the coordinator and shows the unread count or the error. Room comes through `ModuleContext.database()`; only one module may own `dayloom.db`. A small home card shows the unread count and the three newest headlines.
- **Summary language**: follows the app language (prompts in both languages).
- **Ported**: almost entirely (it is already generic); the main work is bilingual text and plugging into the module interface.

---

## 12. Background work

- One generic periodic refresh worker (unique work name `dayloom.refresh`): picks the sources due according to their `RefreshCadence` → refreshes them → hands the result to `NotificationEngine` to evaluate the rules of all enabled modules.
- No network constraint: rules that only depend on the clock (expiry reminders) must run offline too, and offline sources are skipped without a request. WorkManager is asked to retry only when every source actually attempted failed.
- While the home screen is visible it checks every minute with the same cadences and refreshes the due sources; a source that failed waits an interval before the next try, so a broken service is not hit every minute.
- Modules get no WorkManager work of their own: work like the news sync is a `CachedSource` too (`news.sync`), run in the same round on its own `RefreshCadence`, with the same offline skip, error display and failure throttling; a disabled module is simply no longer refreshed. That leaves one worker to freeze.
- Worker class names and unique work names are frozen from v1.0 (WorkManager instantiates scheduled work by class name).

---

## 13. First-run onboarding

1. Welcome + language choice;
2. set "Home" (search or coordinates), skippable;
3. choose which modules to enable (defaults: calendar, weather, expiry reminders; modules that need a key or only work in Norway are unchecked, with the reason shown);
4. go to the home screen. Cards of modules that are not fully set up show a "Set up" prompt.

---

## 14. Security, privacy and data sources

- The APK contains no API keys; credentials stay on the phone, encrypted with the Keystore.
- No analytics, no crash reporting, no ads; network requests only go to the services of modules the user enabled.
- Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS` (runtime permission, requested only when the user turns on a notification switch). No location permission.
- The About page also lists every third-party library and its license (MIT and similar licenses require the copyright notice to ship with the software).
- Credentials are only sent to the service they belong to; `CredentialRedirectGuard` is kept to prevent leaks through redirects.
- Settings gain an "About / Data sources" section (M7: dialogs for data sources, privacy and open-source licenses; the privacy statement is also in `PRIVACY.md`):

| Data | Source | License / requirements |
|---|---|---|
| Weather | MET Norway | CC BY 4.0, attribution required; User-Agent with contact information |
| Public transport | Entur | NLOD, attribution required; `ET-Client-Name` header |
| Place search | Google Places API (New) with the user's key; Entur Geocoder and Nominatim without | Google Maps attribution shown with the results; Entur under NLOD; OpenStreetMap under ODbL, "© OpenStreetMap contributors" required |
| Traffic | Google Routes | User's own key, subject to Google's terms |
| Football | football-data.org | User's own key, subject to its terms |
| Lunar calendar | lunar-java (`cn.6tail:lunar`) | MIT, copyright notice required; not the Hong Kong Observatory table |

---

## 15. Freeze rules

**Before v1.0.0**: everything below may change freely, without migrations.

**Frozen from v1.0.0** (changing it loses user data or causes duplicate notifications; a change requires a migration with tests):

- applicationId `io.github.lonemoonspace.dayloom`;
- module ids, source ids, secret ids, notification channel ids, notification id ranges, notification state keys;
- storage file names (§6.1) and the JSON format of `@Serializable` types (polymorphic subclasses must have `@SerialName`);
- the Room database name and schema;
- WorkManager unique work names and Worker fully qualified class names;
- fully qualified class names of `MainActivity` / `DayloomApp`; deep-link scheme `dayloom://`;
- the `SecretBox` ciphertext format and Keystore alias.

---

## 16. Milestones

Each milestone is one PR (or a few); `verify` must be green before merging into `main`.

| Milestone | Contents | Done when |
|---|---|---|
| **M0 Skeleton** | Gradle project, package layout, port and generalize core (time/json/error/network/storage/secret/refresh/notify); module interfaces and registry; navigation/home/settings shells; i18n infrastructure and language switching; `ArchitectureTest`, string-parity test; CI and release workflows; LICENSE, README, CLAUDE.md | The empty app runs and switches language; a test-only "sample module" proves the §4.4 steps hold; `verify` is green |
| **M1 Places + weather + calendar** | `core/location`, `core/routine`; weather module; calendar module | Both modules work, bilingual |
| **M2 Expiry reminders + traffic** | Both modules | Same as above |
| **M3 Public transport** | Step one: validate the Entur API (cancellations, delays, transfer details); then commute trips, favourite stops, disruption notifications | With your own configuration it can replace the original project's train and bus cards |
| **M4 Background work and notifications** | Background refresh worker, notification engine wiring, morning brief, onboarding | First test build (`0.1.0-rc.1`) |
| **M5 Football** | Football module | Works, bilingual |
| **M6 News** | News module (including Room v1) | Works, bilingual |
| **M7 Polish** | "About / Data sources" page, README screenshots, license review, privacy statement | **First usable version** |
| v1.0.0 | Released after a period of stability | Freeze rules take effect |

After M3 you can start using it on your phone alongside the original app; after M6 it reaches parity and the original app can be retired.

---

## 17. Testing strategy

- **Plain JVM tests for policies**: all decision logic (weather point picking, trip status, expiry, holidays, lunar calendar, scores…) with `now` as a parameter.
- **Port the existing tests too**: `RefreshCoordinatorTest`, `SecretBox` / `SecretStore`, existing policy tests, rewritten against the new interfaces.
- **Architecture test**: the four rules in §3.3.
- **Registry test**: unique ids, non-overlapping notification ranges.
- **String-parity test**: §10.5.
- **Robolectric**: only for DataStore / Room migration tests (which will only exist after v1.0).

---

## 18. Porting list from PersonalAssistant

| Category | Contents |
|---|---|
| **Ported mostly as is** | `SecretBox`, `SecretStore`, `AppError`, `AppJson`, `HttpCalls`, `SharedHttpClient`, `CredentialRedirectGuard`, `ConnectivityMonitor`, `AppClock`, UI components (`InfoCard`, `Glass`, `Skeleton`, `StatusWidgets`, theme) |
| **Ported after generalizing** | `CachedSource`, `RefreshCoordinator`, `RefreshCadencePolicy`, `BackgroundRefreshPolicy`, `NotificationEngine`, `NotificationRule`, all of weather, calendar (holiday parts), all of football, all of news, `TicketPolicy` |
| **Rewritten** | Home, settings, navigation (now registry-driven); public transport (now generic trip planning) |
| **Not ported** | `L1Stations`, the L1/R14 transfer comparison, `Bus280*`, the Real Madrid crest, `LunarData` and its generator script, `LegacySecretsMigration`, all of the original project's data migration code, personal defaults and addresses in preview data |

Check every file before porting: remove personal information from defaults, test data, preview data and comments.

---

## 19. Decision log and open questions

### Decided

| Question | Decision |
|---|---|
| Card ordering | Drag to reorder, plus accessible move up / move down (§4.3) |
| English design document | Add `docs/design.en.md`, kept identical to the Chinese version |
| Device location | None. First one-shot only; removed after the rc.1 test in favour of Google search and typed coordinates (§8) |
| Lunar data | Use lunar-java (MIT), accepting the slight risk that its upstream (sxwnl) license is not explicit; verify locally against the Observatory data (§11.5) |
| minSdk | 33 (Android 13). The intended users all have recent phones; in return, blur and dynamic color work on every device, notification permission has a single flow, and per-app language uses the native system implementation |
| Repository visibility | Public from M1 (2026-10-09) instead of M7: GitHub Actions minutes are free for public repositories. Code ported from PersonalAssistant (weather, holidays, icons) is published with the owner's consent; personal data is removed when porting (§18) |
| Release compile in the gate | `verify` and CI also run `compileReleaseKotlin`: in M1 the release source set turned out to be missing a file that only debug had, which a debug-only gate cannot see. About a minute per CI run; R8 stays in the release workflows |
| Expiry reminders | Reminded once per item and expiry time when the warning period starts, on the last day, and on expiring — the last only within a day of expiry, so an old date typed in stays quiet. The rule exists from M2; the opt-in switch arrives in M4 with the notification wiring and permission flow, so no switch exists that does nothing |
| Public transport | Entur only for now; validated before implementation (§11.2). The commute card switches direction at 12:00; favourite-stop boards filter by line code and destination on the client. The disruption rule exists from M3; its opt-in switch arrives with the notification wiring in M4 |
| The day's rhythm | The working-day selection and commute switch went after the rc.1 test; after the rc.6 test the commute windows went too, replaced by a direction switch at 12:00 (§8). The cards only need to know which way to look, and two time pickers asked more than that was worth. The two other jobs the windows did have their own replacements: the morning brief has its own time, and disruption alerts are limited to trips leaving within 45 minutes and are quiet 22:00–06:00 |
| Notification switches | No master switch: each kind of notification has its own switch, off by default, and "turn everything off" is left to the system settings. The notification permission is requested only when the user turns a switch on; when the permission or the channel is off the switch says so and links to the system settings |
| Background refresh | WorkManager every 15 minutes without a network constraint, each source throttled by its own `RefreshCadence`; the visible home screen checks every minute (§12) |
| Morning brief | Sent on the first background round at or after the chosen time (07:00 by default), every day, with a line each from weather, public transport, traffic and expiry reminders; modules whose data is too old are left out (§7.3) |
| rc.1 test feedback (2026-10-09) | Calendar header: solar term on the clock row, stem-branch year and lunar date on one line beside the date. Weather: always Home, no settings; the commute windows on the card are replaced by the day's outlook with clothing advice and tips, tomorrow's from 18:00. Public transport: train and bus commutes apart (sources, cards, settings, rules). Places: no device location; Google search with a shared key, typed coordinates, Open-Meteo as the keyless fallback. Daily windows apply every day. Settings may now have several cards per module (`ModuleInstance.settingsSections`). The overall look is being discussed separately |
| Visual design (2026-10-09) | Direction B of three mockups: Material 3 Expressive with colours from the wallpaper (Material You), 20 dp cards, one weather card in the scheme's primary colour that does not change with the weather, cards with a line icon, title, route and "updated" time in one row, one dense row per trip option (departure, lines, arrival, duration, transfers, platform, status pill), three options per commute window by default |
| News sync (M6) | No separate `news.sync` WorkManager work; it runs as a source in the shared refresh round (§12). Room comes through `ModuleContext.database()`, and only one module may own `dayloom.db` |
| Football and news implementation (M5, M6) | The PersonalAssistant code was not available at implementation time; both were rewritten from the public API documentation and tested against a mock server. They need one real test with real keys |

### Rationale (archived)

**1. Lunar calendar data source (decided: option A)**

Findings: the original project's `LunarData.kt` is generated by a script from the Hong Kong Observatory website's 1901–2100 conversion tables (`hko.gov.hk/.../T{year}e.txt`).

- The Observatory website's conditions allow **non-commercial use only**, require its IP notice and conditions to be reproduced with any copy, absolutely prohibit sale or exchange for any benefit, and reserve the right to withdraw permission at any time. Apache-2.0 lets anyone use the work commercially, so the two conflict: **this data cannot go into the open-source repository or the APK**.
- On DATA.GOV.HK the same dataset is offered under terms that allow commercial use and redistribution (with attribution), but the CSV files there **only cover 2023–2028**; there is also an online per-date lookup API, which needs a network connection and does not suit an offline calendar.

Options:

| Option | Description | Assessment |
|---|---|---|
| **A. Use `cn.6tail:lunar` (lunar-java, MIT)** (recommended) | Dependency-free Java library; solar terms and new moons use the Shouxing astronomical calendar (sxwnl) algorithms with high precision; supports lunar dates, stem-branch, zodiac, solar terms | License compatible with Apache-2.0; no data table to maintain. Concern: it is ported from sxwnl, whose original project has no explicit license (it only says "all source code is open for enthusiasts to study"), a slight upstream licensing ambiguity; it does far more than we need, so size relies on R8 shrinking |
| B. The same author's Kotlin library `tyme4kt` (MIT) | Newer API, Kotlin-native | Newer, with less ecosystem and documentation than lunar-java; same upstream concern as A |
| C. Implement a high-precision algorithm ourselves | Write our own from published astronomical algorithms (solar longitude + new moons) | A lot of work; the original project showed low-precision algorithms are off by a day near midnight, and matching the Observatory's precision is not easy |
| D. Ask the Hong Kong Observatory for written permission | Email mailbox@hko.gov.hk | Unknown turnaround, and permission can be withdrawn at any time; unsuitable for an open-source project |

Whichever option is chosen, the Observatory data can still be used for **local verification**: a script that runs only on the developer machine downloads the tables temporarily and compares every day; the data itself is never committed or shipped in the APK.

**2. minSdk (decided: 33)**

Options that were compared:

| minSdk | Android version | Benefit | Cost |
|---|---|---|---|
| 24 | Android 7.0 | Covers a few more old devices | `java.time` needs core library desugaring; notification channels and adaptive icons need compatibility branches |
| 26 | Android 8.0 | Same as the original project; `java.time`, notification channels and adaptive icons are all native | No notable cost |
| 28 / 29 | Android 9 / 10 | Almost no compatibility code to remove | Loses some devices for nothing |
| 31 | Android 12 | Dynamic color (Material You) needs no version check | Notable cost; dynamic color can be enabled after a runtime version check anyway |
| **33** (chosen) | Android 13 | Native per-app language and notification permission need no version check; includes every benefit of 31 | Covers the fewest devices (about 58% per Android Studio data, December 2025); not a problem for the intended users |

targetSdk follows Google Play's requirement for the year; to be checked at implementation time.
