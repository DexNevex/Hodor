# Third-party notices

Hodor's own code is under the [MIT licence](LICENSE). It includes or uses the following third-party work.

## Bundled in the app

| Component | Use | Licence |
|---|---|---|
| [Inter](https://rsms.me/inter/) by Rasmus Andersson | App font (`app/src/main/res/font/inter_*.ttf`) | SIL Open Font License 1.1, see [LICENSES/Inter-OFL-1.1.txt](LICENSES/Inter-OFL-1.1.txt) |
| [Natural Earth](https://www.naturalearthdata.com/) 1:110m Admin 0 countries | World map (`app/src/main/assets/countries.geojson`, simplified) | Public domain |
| AndroidX, Jetpack Compose, Kotlin standard library and coroutines | Libraries, fetched by Gradle at build time | Apache License 2.0 |

## Downloaded by the app at run time

These lists aren't part of this repository. The app downloads them onto the phone when the matching feature is on, and each keeps its own licence and terms.

| Source | Used for | Terms |
|---|---|---|
| [URLhaus](https://urlhaus.abuse.ch/) data via [malware-filter](https://gitlab.com/malware-filter/urlhaus-filter) | Threat list (malware sites) | URLhaus data: CC0; malware-filter: see its repository |
| [Phishing Army](https://phishing.army/) | Threat list (phishing) | See the project's site |
| [Feodo Tracker](https://feodotracker.abuse.ch/) (abuse.ch) | Threat list (botnet servers) | CC0 |
| [Emerging Threats](https://rules.emergingthreats.net/) compromised IPs (Proofpoint) | Threat list | See the source |
| [FireHOL Level 1](https://github.com/firehol/blocklist-ipsets) | Threat list (attack sources) | See the repository |
| [HaGeZi DNS blocklists](https://github.com/hagezi/dns-blocklists) | Ads/trackers, site-filter categories, DoH servers | GPL-3.0 |
| [Block List Project](https://github.com/blocklistproject/Lists) | Site filter: scams | Unlicense |
| [ip-api.com](https://ip-api.com/) | IP locations for the map (optional) | Free tier is for **non-commercial use only**, see [their terms](https://ip-api.com/docs/legal) |

## Not affiliated

"Hodor" and "hold the door" refer to a character from *Game of Thrones*. This project isn't affiliated with or endorsed by HBO or George R. R. Martin. The app's artwork is original and doesn't depict the character.
