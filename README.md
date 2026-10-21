# Hodor

*Hold the door.* An Android app that shows which apps on your phone connect to the internet, where they go, and on which ports. It can also block apps, known-bad destinations, ads and trackers, and whole categories of sites. It needs no root.

Built for and tested on a Pixel 9a running Android 17 (minimum Android 10).

**Using the app?** Start with the [user guide](docs/USER_GUIDE.md).

## What it does

| Area | What you get |
|---|---|
| **Map** (home) | A world map of where your traffic goes, with countries you've reached shaded and dots sized by connection count; live download and upload speed graphs; session totals; a protection score; ads blocked; and a country list. |
| **Traffic** | Every connection: app, hostname, IP, protocol, port and service name, location, bytes in and out, and state. Each app's DNS lookups are grouped into one card. |
| **Apps** | Per app: connections, data used, ports used, a **Block** switch, and (with ad blocking on) an **Allow ads** switch. |
| **Ports** | Each port in use, its service name, and which apps use it. |
| **Shield** | **Site filtering** (categories, your block/allow rules, recently held sites, bypass protection), ad and tracker blocking (Light or Normal list), threat lists with an update schedule, **Block flagged destinations**, and the list of flagged connections. |

When site filtering stops something, Hodor shows a **"Hodor held the door"** notification. Tapping it opens a full-screen Blocked screen with Allow buttons. Plain-HTTP sites get a block page in the browser; HTTPS sites can't, because the browser would show a certificate error instead.

## How it works (short version)

Hodor runs a VPN that stays **on the phone**. Android sends every app's traffic into it, and Hodor:

1. asks Android which app owns each connection (`ConnectivityManager.getConnectionOwnerUid`);
2. reads the hostname from DNS answers and from the first bytes of each connection (TLS SNI / HTTP Host);
3. checks the destination against the threat lists and the ads list;
4. either refuses the connection or relays it through a normal socket that bypasses the VPN.

Nothing goes to a VPN server: traffic leaves through your normal Wi-Fi or mobile connection. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the details.

## Privacy: what leaves the phone

| What | Where | When |
|---|---|---|
| Threat lists | abuse.ch, phishing.army, emergingthreats.net, GitHub (FireHOL) | When due (Daily/Weekly/Manual), Wi-Fi only by default, skipped when unchanged |
| Ads list | GitHub (HaGeZi) | Only while ad blocking is on, same schedule |
| Site-filter lists | GitHub (HaGeZi), blocklistproject.github.io | Only for categories you turn on (and the DoH list while bypass protection is on), same schedule |
| Public IP addresses | ip-api.com, **plain HTTP** (their free tier has no HTTPS) | Each new public IP, once; results are cached. Turn off with **Look up IP locations** on the map screen |

Nothing else is sent. The connection log is kept in memory only, and is gone when the app stops. Settings, lists and the IP-location cache are stored in the app's private storage.

## Install on the phone

From the project folder, with the phone connected and USB debugging on:

```bash
~/Library/Android/sdk/platform-tools/adb install -r Hodor.apk
```

Then open Hodor and tap **LIVE** (or the power button on the map screen). The first time, Android asks you to allow the VPN.

## Build from source

Requirements: JDK 17 and the Android SDK, installed on this Mac at:

- JDK: `/opt/homebrew/opt/openjdk@17`
- SDK: `~/Library/Android/sdk` (platform 36, build-tools 36.0.0); its path is in `local.properties`

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug
cp ~/Library/Caches/HodorBuild/app/outputs/apk/debug/app-debug.apk Hodor.apk
```

Build output goes to `~/Library/Caches/HodorBuild`, not `app/build`. The Desktop is synced by iCloud, and sync made `… 2.dex` copies mid-build that broke the build (see the end of `build.gradle.kts`).

## Versioning

- [Semantic versioning](https://semver.org): `MAJOR.MINOR.PATCH`. The app is pre-1.0, so a minor bump means new features.
- The version lives in `app/build.gradle.kts`: `versionName` (shown at the bottom of the map screen) and `versionCode` (increases by 1 each release).
- Every release is a git tag (`v0.3.0`, `v0.4.0`, …) and has an entry in [CHANGELOG.md](CHANGELOG.md).
- The repository starts at 0.3.0; earlier versions are described in the changelog but have no commits.

To release:
1. Bump `versionName` and `versionCode`.
2. Add a CHANGELOG entry.
3. Commit, then run `git tag -a vX.Y.Z -m "Hodor X.Y.Z"`.

## Project layout

```
app/src/main/java/com/portwatch/
  HodorApp.kt          app start-up: settings, lists, IP-location cache
  MainActivity.kt      VPN consent, start/stop, system bars
  vpn/                 the VPN service and the packet relay engine
  data/                connection log, app lookup, hostnames, lists, settings, stats
  ui/                  Compose screens and components
app/src/main/assets/countries.geojson   world map (Natural Earth, public domain)
app/src/main/res/font/                  Outfit and Oxanium (SIL Open Font License)
docs/                  user guide, architecture and test records
```

The Kotlin package and application ID are still `com.portwatch` (the app's original name). Changing them would install Hodor as a separate app and ask for VPN consent again.

## Known limitations

- It holds Android's single VPN slot, so it can't run alongside another VPN or VPN-based ad blocker.
- DNS-level ad blocking can't remove ads served from the same servers as the content (YouTube, Instagram) or hide empty ad boxes on web pages. Use Brave or uBlock Origin for websites.
- Site filtering works on hostnames, not full URLs. Blocking one page of a site (e.g. one subreddit) needs a browser-level tool.
- HTTPS sites can't show an in-browser block page without installing a Hodor certificate authority, which isn't done; Hodor's own Blocked screen is used instead.
- IPv4 threat lists only. IPv6 traffic is still monitored when the network has IPv6.
- Fragmented IP packets and IPv6 extension headers are dropped (both rare).
- There are no automated tests yet; see [docs/TESTING.md](docs/TESTING.md) for what was checked on the device.
