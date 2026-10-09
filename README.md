# Dayloom

**English** · [中文](README.zh-CN.md)

Dayloom is an extensible Android daily dashboard: weather, Norwegian public transport, traffic, expiry reminders, calendar,
football and news, each as a module you can turn on or off. The UI is available in English and Chinese.

> **Status: early development.** The module system, home screen, settings and build pipeline are in place (milestone M0),
> along with shared places and daily routine, weather and calendar (M1), expiry reminders and traffic (M2). See the [design document](docs/design.en.md) for the plan.

## Principles

- **Modules, not a monolith.** Every feature is a module in `feature/<id>/` registered with one line in `ModuleRegistry`.
  The home screen, settings, navigation, refresh and notifications pick it up automatically.
- **No backend, no bundled keys, no tracking.** Data comes from public APIs or services you configure yourself; API keys are
  entered in the app and encrypted with the Android Keystore.
- **Bilingual from the start.** All text comes from resources (`values/` English, `values-zh/` Chinese), and the language can be
  switched inside the app or in system settings.

## Requirements

- Android 13 (API 33) or newer.

## Building

JDK 17 and the bundled Gradle wrapper:

```bash
./gradlew verify          # unit tests + Android Lint + debug APK + release compile; must be green before every commit
./gradlew assembleDebug   # debug APK only
```

Debug builds include a small demo module that shows how a module plugs in; release builds do not.

Signed builds are produced by GitHub Actions only; see `CLAUDE.md` for the release process.

## Architecture

```
app/      assembly: module registry, navigation, home and settings shells
feature/  one package per module; features never depend on each other
core/     infrastructure: module API, refresh, notifications, storage, secrets, network, time, i18n, UI
```

Dependencies point downwards only (`app → feature → core`); `ArchitectureTest` fails the build otherwise.
Details: [docs/design.en.md](docs/design.en.md).

## License

[Apache License 2.0](LICENSE).
