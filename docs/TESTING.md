# Test record

There are no automated tests yet. Each version was checked by hand on the target phone over USB (`adb`): installing, driving the screen, reading logs, and taking screenshots.

**Device:** Google Pixel 9a, Android 17, on home Wi-Fi.

Threat-list checks used DNS lookups only: `ping` from the debug shell resolves the name, and Hodor doesn't relay ping packets, so nothing actually reached the listed hosts.

## 0.6.0 — Allow fix and light design (tested 22 Oct 2026)

| Check | Result |
|---|---|
| New design on the phone: header, bell, ⋮ menu, status pill, map, speed card, country strip, summary rows, floating dock | ✅ |
| Bell opens Shield → Recently held | ✅ |
| ⋮ menu → Start watching starts monitoring | ✅ |
| Shield → Threat lists page with "‹ Shield" back link | ✅ |
| **Allow takes effect straight away** | ✅ a gambling site held in Chrome (Gambling category); **Allow 15m** in Recently held; Chrome reloaded and showed the real site about 9 s later (well inside the old 60 s cache window) |
| Allow covers the whole site | ✅ after allowing a held site, a never-looked-up subdomain of it resolved to its real address |
| Chrome reconnects to the real server after Allow | ✅ new connections went to the site's real address instead of the block address. (One test site then reset the connection itself because it refuses that network; that is the site, not Hodor.) |
| The notification's own **Allow 15 min** button | ⬜ not reachable in testing: Do Not Disturb (Bedtime mode) hides Hodor's notifications from the shade; it calls the same allow function |
| The status pill's start button | ⬜ only the menu path was confirmed; early taps on the pill landed while the screen was scrolled or in use |

## 0.5.0 — site filtering (tested 21 Oct 2026)

| Check | Result |
|---|---|
| Shield shows Filter sites, Your rules, Recently held, Bypass protection | ✅ |
| Adding block rules (`neverssl.com`, `reddit.com`) | ✅ saved; the keyboard's Done key adds a Block rule |
| Rule covers the site and its subdomains | ✅ `reddit.com`, `www.reddit.com`, `old.reddit.com` and `neverssl.com` all resolve to the block address 10.111.0.80; `www.wikipedia.org` resolves normally |
| Plain-HTTP block page in Chrome | ✅ `http://neverssl.com` shows the "Hodor held the door" page: illustration, site, "Your rule: neverssl.com". The first build failed (relay went to IPv6 loopback, and the socket was bound to Wi-Fi); fixed during testing |
| HTTPS site held | ✅ `https://www.reddit.com` doesn't load; notification "Hodor held the door · www.reddit.com · Your rule · Chrome" |
| Notifications grouped and rate-limited | ✅ four notifications (neverssl, reddit, www., old.) grouped under Hodor |
| HTTPS to the block address is refused | ✅ connection refused on 443 |
| Encrypted-DNS bypass blocked | ✅ DoH list downloaded (59 KB); `dns.google`, `cloudflare-dns.com`, `dns.quad9.net` → 10.111.0.80 |
| Category: turning on **Social media** downloads its list and blocks | ✅ 137 KB downloaded; `tiktok.com`, `www.instagram.com`, `snapchat.com` → 10.111.0.80; Wikipedia unaffected |
| An allow rule beats a category, and covers only that name and below | ✅ allow `www.snapchat.com` → real address (34.149.46.130); `accounts.snapchat.com` and TikTok stay blocked |
| **Recently held** lists held sites; tapping one opens the Blocked screen | ✅ illustration, "HODOR HELD THE DOOR", `www.reddit.com`, "Your rule: reddit.com", "Chrome tried to open this site", three buttons |
| Blocked screen **Allow for 15 minutes** | ✅ `www.reddit.com` resolves again (146.75.89.140) after the 60 s cached answer; `old.reddit.com` stays blocked |
| DNS card shows held lookups | ✅ "⛔ 14 lookups held by site filtering", held names in red |
| Per-app **Skip site filter** | ✅ on for Shell: `www.tiktok.com` resolves normally; off again: `us.tiktok.com` → 10.111.0.80 |
| QUIC dropped while filtering | ✅ no UDP 443 (QUIC) connections recorded after filtering began; Chrome searches loaded over TCP. Before 0.5.0, Chrome used QUIC constantly |
| The notification's own **Allow 15 min** button | ⬜ not tapped (same allow function as the Blocked screen, which passed) |
| ECH suppression (empty answer to HTTPS records) | ⬜ not observed directly; no tool on the phone sends type-65 queries |

After testing, the phone was returned to its earlier settings: no rules, no categories, no exemptions. Notification permission was granted for the test and left on, so held sites are announced.

## 0.4.0 — ad blocking (tested 21 Oct 2026)

Method: each ad hostname below is a **real** host (the Mac resolves it through normal DNS) whose **parent** domain is on the list. Every test used a name not looked up before, so Android's DNS cache couldn't answer for it.

| Check | Result |
|---|---|
| Turning on **Block ads & trackers** downloads the list on the phone | ✅ HaGeZi Multi Normal, 168,161 domains, 3.36 MB; the Shield card shows "HaGeZi Normal · 168,161 domains · 3.2 MB" |
| Subdomains of listed domains are blocked | ✅ `www.google-analytics.com` (216.58.205.110 on the Mac) → `unknown host`; `stats.g.doubleclick.net` (74.125.133.154) → `unknown host`; `pagead2.googlesyndication.com` (142.251.140.226) → `unknown host` |
| Normal sites still resolve | ✅ `www.wikipedia.org` → 185.15.59.224 |
| An ad-heavy page still loads and works | ✅ dailymail.co.uk and theguardian.com loaded normally |
| Blocked counts | ✅ the home **ADS BLOCKED** card showed 339 of 339 ad/tracker requests blocked after the Daily Mail visit; the Guardian visit showed "135 ad and tracker lookups blocked" for Chrome; an analytics tracker (`api2.amplitude.com`) in another app was blocked |
| Per-app count on the Apps screen | ✅ the Shell card showed "4 ad and tracker requests blocked", matching the 4 test lookups |
| **Allow ads** exempts one app | ✅ with it on for Shell: `ssl.google-analytics.com` → 142.250.151.97 and `googleads.g.doubleclick.net` → 142.250.129.155 resolved |
| Turning **Allow ads** off again restores blocking | ✅ `securepubads.g.doubleclick.net` → `unknown host` |
| Ad names highlighted, and "N ad and tracker lookups blocked" in DNS cards | ✅ violet chips; allowed ad lookups still marked as ads but not counted as blocked |
| Version shown in the app | ✅ "HODOR v0.4.0 · BUILD 4" at the bottom of the map screen |
| Connection-card fields no longer run together with long city names | ✅ fixed during testing ("New Haven" / "↑4.8 KB ↓5.0 KB") |
| **Light** list level | ⬜ not tested (only Normal was downloaded) |
| Cutting a TCP connection when its SNI reveals an ad domain | ⬜ not exercised directly; in practice blocking happened at the DNS stage |


## 0.3.0 — dark map-first design

| Check | Result |
|---|---|
| Home opens on the map; countries reached are shaded mint; dots show counts | ✅ two countries shown for Chrome traffic |
| Status card: WATCHING, uptime counting, DNS server, app and open counts | ✅ "DNS <router> · 2 APPS · 31 OPEN" |
| Speed graphs and totals update live | ✅ e.g. 7.3 KB/s down / 5.2 KB/s up; 1.9 MB / 172 KB this session |
| LIVE chip starts monitoring with one tap | ✅ |
| Power button starts monitoring | ✅ in its first position; not re-checked after the map card was made shorter |
| Traffic and Threats screens in the dark style, with the bottom bar | ✅ |
| Fade behind the bottom bar; shorter map card | ✅ |
| Launcher icon on the home screen | ⬜ not checked on the device |

## 0.2.x — blocking, threats, map, refresh

| Check | Result |
|---|---|
| All five threat lists download on the phone | ✅ 437 / 156,024 / 5 / 481 / 4,633 entries (3.3 MB) |
| A lookup of a URLhaus domain (`alarmline.com.br`) is flagged | ✅ "⚠ URLhaus: alarmline.com.br"; counted on the status card |
| With **Block flagged destinations** on, a listed domain (`blankeyeo.com`) doesn't resolve | ✅ `ping: unknown host`; record shows `blocked` |
| **Block** on Chrome: a new site fails | ✅ nasa.gov → ERR_NAME_NOT_RESOLVED |
| Unblocking Chrome restores access | ✅ (settings file confirmed empty) |
| Map locates IPs | ✅ 23/23, then 54/54 IPs located; country list correct |
| Nearby dots merge; country shading includes small countries | ✅ France, Portugal and Ireland shaded under a merged dot |

## 0.1.x — first version

| Check | Result |
|---|---|
| Install over USB and start the VPN | ✅ after fixing the missing `ACCESS_NETWORK_STATE` permission (crashed on first start) |
| Connections attributed to the right apps, with hostnames and ports | ✅ Chrome, Brave, WhatsApp (TCP 5222), Google Services (TCP 5228), Play Store |
| Browsing works normally while monitoring | ✅ BBC and Reddit loaded, including images and video |
| DNS lookups grouped per app | ✅ "Chrome · 150 DNS lookups · 68 names" in one row |

## Testing notes

- Through `adb`, the first tap after the app comes to the front is sometimes ignored. Tests check the result after every tap rather than tapping blindly; blind tapping once toggled monitoring off by accident.
- Reinstalling the app stops the VPN; it has to be started again after every install.
