# Privacy

**English** · [中文](PRIVACY.zh-CN.md)

Dayloom has no server of its own and collects nothing: no analytics, no crash reports, no ads, no accounts.

## What stays on your phone

- Settings, saved places, your daily routine, expiry reminders, cached data and news articles.
- API keys and tokens, encrypted with the Android Keystore. They are only ever sent to the service they belong to.
- There is no cloud backup. Moving to a new phone copies your settings but not your keys, which cannot be decrypted on
  another device.

## What is sent, and to whom

The app only contacts the services of the modules you turn on, and sends each only what it needs:

| Module | Service | What is sent |
|---|---|---|
| Weather | MET Norway (api.met.no) | The coordinates of Home, rounded to four decimals |
| Public transport | Entur | The stops you chose; what you type into stop search |
| Places | Google Places API with your key, otherwise Open-Meteo | What you type into place search |
| Traffic | Google Routes API with your key | The coordinates of the two places you chose |
| Football | football-data.org with your key | The competition and team you follow |
| News | Your own Miniflux server | Your token; read and starred changes |
| News, AI summaries | The endpoint you configure | An article's title and text, only when you tap Summarize |

Every request identifies the app as `Dayloom/<version> (+https://github.com/lonemoonspace/dayloom)`, as MET Norway asks.

## Permissions

- Internet and network state.
- Notifications, requested only when you turn on a notification switch.
- No location permission.
