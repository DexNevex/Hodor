# Test record

There are no automated tests yet. Each version was checked by hand on the target phone over USB (`adb`): installing, driving the screen, reading logs, and taking screenshots.

**Device:** Google Pixel 9a, Android 17, on home Wi-Fi.

Threat-list checks used DNS lookups only: `ping` from the debug shell resolves the name, and Hodor doesn't relay ping packets, so nothing actually reached the listed hosts.

## 0.4.0 — ad blocking

*To be filled in once tested on the phone.*

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
