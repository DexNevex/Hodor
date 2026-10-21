# Changelog

All notable changes to Hodor. Format based on [Keep a Changelog](https://keepachangelog.com); versions follow [semantic versioning](https://semver.org).

The git repository was created at 0.3.0. Earlier versions are recorded here from the development history but have no commits or tags.

## [0.6.0] — 2026-10-22

### Fixed
- **Allow 15 min / Always allow didn't work straight away.** A blocked site was answered with Hodor's block address, which the phone and browser cached for 60 seconds, so they kept hitting the block after you allowed it. Now:
  - connections that still arrive at the block address are accepted, their site name is read, and if the site has been allowed Hodor looks up the real address and connects through;
  - block answers are cached for only 10 seconds;
  - Allow covers the whole site (e.g. `www.instagram.com` allows `instagram.com` and all its subdomains), so the page's other parts load too.

### Changed
- **New light design** based on a reference you supplied: light grey background, white cards, lime accent, dark pills.
  - Header with the Hodor logo, a **bell** (red dot when a site was held; opens Recently held) and a **⋮ menu** (Search, Ports, Threat lists, Clear closed, Start/Stop, About).
  - Home: a dark **status pill** with a start/stop button; the map (dark dots on light land); one combined **speed card**; a scrolling **"Where it goes"** country strip; compact **Protection / Ads blocked / Sites held** rows.
  - A floating dark **dock** with four round icon buttons: Home, Traffic, Apps, Shield. Ports moved to the menu.
  - **Shield is a menu** grouped into Filtering (Site filter, Your rules, Recently held), Blocking (Ads & trackers, Threat lists) and Settings (Bypass protection, Location lookups); each row shows a one-line summary and opens its own page.

## [0.5.0] — 2026-10-21

### Added
- **Site filtering** by domain (with HTTPS, only the hostname is visible, so filtering works on sites, not full URLs).
  - **Categories**, each list downloaded only when switched on: Adult (HaGeZi NSFW), Gambling (HaGeZi Gambling mini), Social media (HaGeZi Social), Dating (built in), Piracy (HaGeZi Anti-Piracy), Scams (Block List Project).
  - **Your rules:** block or always allow a site and its subdomains. Allow beats every list, including ads and threats.
  - **Per-app "Skip site filter"** on the Apps screen.
  - **Enforced at several points:** DNS (a filtered name resolves to Hodor's block address), the TLS/HTTP hostname of each connection, and QUIC.
- **Bypass protection** (while filtering): QUIC/HTTP/3 is dropped so browsers fall back to TCP, where the hostname is visible; known encrypted-DNS (DoH) servers are blocked (HaGeZi DoH); "HTTPS" DNS records get an empty answer, which turns off Encrypted Client Hello.
- **"Hodor held the door"**:
  - a notification for each held site (at most once per app and site every 2 minutes), with **Allow 15 min**;
  - a full-screen **Blocked** screen with an original illustration of a giant holding a door, and **Allow for 15 minutes** / **Always allow** / **Close**;
  - a **block page** for plain-HTTP sites, served from the phone.
- **Recently held** list on Shield; tap an entry for the Blocked screen.
- Red "Held" badges and "⛔ reason" lines on connections and DNS cards.
- User guide (`docs/USER_GUIDE.md`).

### Changed
- List downloads share one code path, schedule and Wi-Fi rule across threat, ad and filter lists.

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

### Fixed
- On connection cards, long city names no longer run into the data figures.

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
