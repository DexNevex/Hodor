# Changelog

All notable changes to Hodor. Format based on [Keep a Changelog](https://keepachangelog.com); versions follow [semantic versioning](https://semver.org).

The git repository was created at 0.3.0. Earlier versions are recorded here from the development history but have no commits or tags.

## [0.4.0] — 2026-10-21

### Added
- **Ad and tracker blocking.** DNS lookups for listed domains (and their subdomains) get a "no such domain" answer, in every app.
  - Uses the HaGeZi DNS blocklists: **Normal** (Multi Normal, ~168,000 domains, ~3.4 MB) or **Light** (~1.4 MB).
  - The list is downloaded and held in memory only while ad blocking is on, and refreshes on the threat-list schedule.
  - Per-app **Allow ads** switch on the Apps screen, with that app's blocked count.
  - Violet "Ad" / "Ad blocked" badges on connections, ad names highlighted in DNS cards, and an **ADS BLOCKED** card on the map screen.
  - Connections recognised as ads only from their TLS/HTTP hostname are cut when that hostname arrives.
- Version and build number at the bottom of the map screen.
- Project documentation: README, this changelog, `docs/ARCHITECTURE.md`, `docs/TESTING.md`.
- Git repository with tagged releases.

### Changed
- The Threats tab is now **Shield**, covering both ads and threats.

## [0.3.0] — 2026-10-21

### Added
- New dark design based on a user-supplied mockup: near-black cards, mint glow, violet for upload, Oxanium type for numbers and labels.
- **Map-first home screen**: traffic map, status card (watching/stopped, uptime, DNS server, a plain power button), live download/upload speed graphs (last 60 s), session totals with segmented bars, a protection score, and a country list.
- **"Hold the door" logo**: an arched doorway whose posts and barring beam form an H, with a door panel and knob. Used for the launcher icon, the Android 13+ themed icon, and the in-app header.
- Bottom navigation bar (Map, Traffic, Apps, Ports, Threats) and a **LIVE/OFF** chip in the header.
- Session traffic counters feeding the speed and total cards.

### Changed
- Build output moved to `~/Library/Caches/HodorBuild`, because iCloud sync of the Desktop was corrupting builds.

## [0.2.1] — 2026-10-21

### Added
- Grey-and-yellow card design with an Overview screen (security gauge, stat pills, recent activity). Replaced in 0.3.0.
- Threat-list refresh is checked hourly. A list downloads only when due, under a **Daily / Weekly / Manual** schedule, and is skipped when unchanged on the server (ETag / Last-Modified). **Only on Wi-Fi** is on by default.
- Map: countries you connect to are shaded; nearby dots merge, and tapping a merged dot zooms in.

## [0.2.0] — 2026-10-21

### Added
- Renamed **PortWatch → Hodor** (display name only).
- **Per-app blocking**: new connections are refused, open ones are cut, and DNS gets NXDOMAIN so the app fails fast.
- **Threat lists**: URLhaus, Phishing Army, Feodo Tracker, ET Compromised and FireHOL Level 1. Matching connections and DNS lookups are flagged, with an optional **Block flagged destinations** switch.
- **Map tab**: an offline world map (Natural Earth) with IP locations from ip-api.com, which you can turn off.

## [0.1.1] — 2026-10-20

### Added
- Each app's DNS lookups are grouped into one row, showing the names it looked up.

### Fixed
- The app crashed on Start because the `ACCESS_NETWORK_STATE` permission was missing.

## [0.1.0] — 2026-10-20

### Added
- First version, as **PortWatch**. A local VPN with its own TCP/UDP relay that records every connection's owning app, destination IP, hostname (DNS / TLS SNI / HTTP Host), port and service name, and bytes in and out.
- Connections, Apps and Ports screens.
