# Hodor user guide

Hodor shows which apps on your phone connect to the internet, where they go, and on which ports. It can also block apps, known-bad sites, ads and trackers, and whole categories of sites.

This guide covers version 0.7.0. Your version is shown at the bottom of the **Map** screen.

---

## 1. Install

1. Get the file `Hodor.apk` onto your phone (AirDrop to a computer, Google Drive, email, a messaging app…).
2. Open it. Android asks you to **allow installs from this source** (your browser, Files or messaging app). Allow it.
3. You may see a **Play Protect** warning, because Hodor isn't from the Play Store. Tap **Install anyway**.

You need Android 10 or newer.

## 2. Start watching

1. Open Hodor.
2. Tap **LIVE** in the header, or the round **power button** on the Map screen.
3. The first time, Android asks to **allow Hodor to set up a VPN connection**. Tap **OK**.

When Hodor is watching:
- a **key icon** appears in the status bar;
- the header chip shows **● LIVE**;
- the status card shows **WATCHING** and the time since you started.

To stop, tap **LIVE** again or the power button. The chip changes to **OFF**.

> **Is my traffic sent somewhere?** No. Hodor's "VPN" runs entirely on your phone: your traffic still leaves through your own Wi-Fi or mobile data. See [What leaves your phone](#9-privacy-what-leaves-your-phone).

---

## 3. Finding your way around

| Control | What it does |
|---|---|
| **Dock** (dark bar at the bottom) | Home · Traffic · Apps · Shield. The selected screen's button is white with a lime dot. |
| **Bell** (top right) | Opens **Shield → Recently held**. A red dot means a site was held. |
| **⋮ menu** (top right) | Search, Ports, Threat lists, Clear closed connections, Start/Stop watching, **Appearance** (System / Light / Dark), About |
| **Status pill** (top of Home) | Shows Watching / Not watching and the uptime; the round button on its right starts or stops watching |
| **Search** (from the ⋮ menu) | Filters every screen by app, site, IP address, port, or list name |

**Shield** is a menu in three groups:
- **Filtering:** Site filter, Your rules, Recently held
- **Blocking:** Ads & trackers, Threat lists
- **Settings:** Bypass protection, Location lookups

Each row shows a one-line summary; tap it to open its page, and tap **‹ Shield** (or Back) to return.

---

## 4. The screens

### Map (home)

The first screen, top to bottom:

| Card | What it shows |
|---|---|
| **Traffic map** | Where your traffic goes. Countries you've connected to are shaded; each dot is a city, with the number of connections inside. **Red** dots include known-bad destinations. Pinch or use **+ / −** to zoom, drag to pan, and tap a dot for details. Tapping a dot that stands for several nearby cities zooms in to separate them. |
| **Status** | WATCHING or STOPPED, the time since you started, the DNS server in use, and how many apps and open connections there are. The power button starts and stops watching. |
| **Download / Upload** | Current speed, with a graph of the last 60 seconds |
| **Downloaded / Uploaded** | Total data since you last pressed Start |
| **Protection** | A score out of 100, explained below |
| **Ads blocked** | Ad and tracker requests blocked this session. Tap it to open Shield. |
| **Countries** | Each country your traffic reached, the apps involved, and how many connections. The **Look up IP locations** switch sits at the bottom. |

**The protection score** starts at 100 and loses points for:

| Reason | Points |
|---|---|
| Not watching | −20 |
| Each known-bad destination contacted | −15 (up to −60) |
| Flagged traffic not blocked | −10 |
| Threat lists missing or more than 3 days old | −15 |

80 or more is **Low risk**, 50 to 79 is **Medium**, and below 50 is **High**. Each deduction is listed under the bar. Tap the card to go to Shield.

### Traffic

Every connection, newest first. Each card shows:

- the **app** and the **site** (hostname, or the IP address if the name isn't known);
- a **badge** with its state (see [Badges and colours](#6-badges-and-colours));
- **Port**, with the service name when known (443 · HTTPS, 53 · DNS, 5228 · Google push…);
- **Location**, as a flag and city;
- **Data**: ↑ sent and ↓ received;
- when it started, the protocol (TCP/UDP), the IP address, and the local port on your phone.

**DNS cards:** an app's name lookups are grouped into one card, such as "Chrome · 156 DNS lookups · 21 names". Tap it to see every name it looked up. Violet names are ad or tracker domains; red names are known-bad.

### Apps

One card per app, sorted by data used:

- **Connections / Open / Data**: totals for the session.
- **Port chips**: every port the app used and how often, for example "TCP 443 · HTTPS ×12".
- **Block** switch: cuts the app off from the internet (see [Block an app](#block-an-app)).
- **Allow ads** switch, shown only while ad blocking is on: exempts this app from ad blocking, and shows how many of its ad requests were blocked.

### Ports

Each port in use, with its service name, how many connections used it, and which apps. Useful for spotting unusual ports. An "Unregistered port" isn't necessarily bad, just not a well-known service.

### Shield

| Card | What it's for |
|---|---|
| **Filter sites** | Switch whole categories on: Adult, Gambling, Social media, Dating, Piracy, Scams |
| **Your rules** | Block or always allow one site (and its subdomains) |
| **Recently held** | Sites Hodor stopped, which app tried, and why. Tap one for the Blocked screen, or **Allow 15m** |
| **Bypass protection** | **Block QUIC (HTTP/3)** and **Stop encrypted-DNS and ECH bypass**, which keep site names visible so filtering can't be dodged |
| **Block ads & trackers** | Turns ad blocking on or off; **Light** or **Normal** list; how many were blocked |
| **Block flagged destinations** | Refuses connections to anything on the threat lists |
| **Update schedule** | How often the lists refresh (**Daily / Weekly / Manual**) and **Only on Wi-Fi** |
| **Threat lists** | Each list, how many entries it has, its size, and when it was last updated. **Update now** refreshes them immediately. |
| **Flagged connections** | Everything that matched a threat list |

---

## 5. Common tasks

### Block an app
1. Go to **Apps**.
2. Turn on **Block** on the app's card. It turns red and says **Blocked**.

The app is cut off straight away: its open connections close and new ones are refused. Turn the switch off to undo it. The app has to have used the internet at least once while Hodor was watching to appear in the list.

### Turn on ad blocking
1. Go to **Shield**.
2. Turn on **Block ads & trackers**.
3. Pick **Normal** (recommended, ~3.4 MB download) or **Light** (~1.4 MB, blocks less).

The list downloads straight away. The card shows "Downloading the list…", then the number of domains. Ad blocking works in every app.

### An app or site broke after turning on ad blocking
1. Go to **Apps** and find the app.
2. Turn on **Allow ads** for it.

Only that app is exempted; everything else stays protected. If it still doesn't work, try **Light** instead of Normal in Shield.

### Block a category of sites (e.g. gambling)
1. Go to **Shield → Filter sites**.
2. Turn on the category. Its list downloads straight away (sizes are shown); "Dating" is built in.
3. On Android 13 and later, allow notifications when asked, so you hear when Hodor holds a site.

From then on, any app that tries a site in that category is stopped. You get a **"Hodor held the door"** notification; tap it to see the **Blocked screen** with the site, the reason, and **Allow** buttons.

### Block or allow one site
1. Go to **Shield → Your rules**.
2. Type the site, e.g. `tiktok.com` (pasting a full link works too), then tap **Block** or **Always allow**.

A rule covers the site and all its subdomains (`tiktok.com` also covers `www.tiktok.com`). **Always allow** beats every list, including ads and threats, so use it to fix a site that's wrongly blocked. Tap a rule to remove it.

### A site I need was held
Allowing works straight away (reload the page) and covers the whole site, e.g. allowing `www.instagram.com` allows everything on `instagram.com`.
- Tap the notification → **Allow for 15 minutes** or **Always allow**, or
- **Shield → Recently held** → **Allow 15m**, or
- For one whole app, **Apps → Skip site filter**.

Then reload the page.

### Block dangerous sites
1. Go to **Shield**.
2. Turn on **Block flagged destinations**.

Lookups and connections to sites on the threat lists (malware, phishing, botnets, attackers) are refused. Even with this off, Hodor still **flags** them with a red ⚠ so you can see them.

### Check what an app has been doing
- Tap 🔍 and type the app's name. Every screen now shows only that app.
- **Traffic** shows its connections and the names it looked up; **Apps** shows its ports and data; **Map** shows where it connected.

### Save mobile data
- In Shield, keep **Only on Wi-Fi** on (the default), and choose **Weekly** or **Manual** updates.
- On the Map screen, turn off **Look up IP locations** if you don't need the map.

### See where a specific connection went
- On **Map**, tap a dot to see each IP address there, its hostname, the apps involved, the ports, and the network owner.

---

## 6. Badges and colours

| Badge | Meaning |
|---|---|
| **open** (green) | Connection in progress |
| **closed** | Ended normally |
| **reset** | Ended abruptly; usually normal, apps often close connections this way |
| **failed** / **timeout** | Couldn't connect |
| **idle** | Closed by Hodor after a long time with no activity |
| **blocked** (red) | Refused by one of your blocking settings |
| **Flagged** (red) | The destination is on a threat list |
| **Ad** / **Ad blocked** (violet) | The destination is an ad, tracker or telemetry domain |
| **Held** (red) | Stopped by site filtering (a category or your rule) |
| **DNS** | A group of name lookups |

| Colour | Meaning |
|---|---|
| Mint | Normal activity, download, things that are on |
| Violet | Upload, ads and trackers |
| Red | Known-bad destinations, blocked items, high risk |

Lines starting with **⚠** name the threat list and entry that matched, for example "⚠ URLhaus: example.com". Lines starting with **◆** name the ad list match, and lines starting with **⛔** say why site filtering held something, for example "⛔ Gambling: bet.example".

---

## 7. Lists and updates

| List | What it contains |
|---|---|
| URLhaus | Sites spreading malware |
| Phishing Army | Phishing sites |
| Feodo Tracker | Botnet command servers |
| ET Compromised | Hacked hosts |
| FireHOL Level 1 | Known attack sources |
| HaGeZi (Normal/Light) | Ads, trackers and telemetry; only downloaded while ad blocking is on |
| Site-filter lists | Adult 1.4 MB, Gambling 2.3 MB, Social 0.1 MB, Piracy 1.1 MB, Scams 0.2 MB; only for categories you turn on. Dating is built in. |
| HaGeZi DoH | Encrypted-DNS servers (60 KB); used by bypass protection |

- Hodor checks every hour and downloads a list only when it's due under your schedule, and only if it has changed since last time.
- A full refresh of the threat lists is about 3.3 MB; the ads list adds 1.4 to 3.4 MB.
- **Update now** refreshes everything immediately, even on mobile data.

---

## 8. Things to know

- **One VPN at a time.** Android lets only one VPN app run. While Hodor is watching, you can't use another VPN or VPN-based ad blocker, and starting another VPN stops Hodor.
- **Some ads can't be blocked.** YouTube and Instagram serve ads from the same servers as the videos and posts, so blocking them would break the app. For websites, use Brave or Firefox with uBlock Origin alongside Hodor.
- **Private DNS.** Hodor works best with Android's Private DNS set to **Automatic** or **Off**. Set to a specific provider, the names your apps look up are hidden from Hodor.
- **History isn't saved.** The connection lists are kept only while Hodor runs. Stopping, restarting or reinstalling starts fresh. Your settings, blocked apps and lists are kept.
- **After installing an update,** tap **LIVE** again; installing stops watching.

---

## 9. Privacy: what leaves your phone

| What | Sent to | When |
|---|---|---|
| Threat lists (download only) | abuse.ch, phishing.army, emergingthreats.net, GitHub | On your update schedule |
| Ads list (download only) | GitHub (HaGeZi) | Only while ad blocking is on |
| Site-filter lists (download only) | GitHub (HaGeZi), blocklistproject.github.io | Only for categories you turn on |
| Public IP addresses your apps connect to | ip-api.com, over **unencrypted** HTTP | Once per new address, for the map. Turn off with **Look up IP locations**. |

Nothing else is sent: no app names, no browsing content, no account. The connection log never leaves the phone.

---

## 10. Troubleshooting

| Problem | Try this |
|---|---|
| No internet while Hodor is on | Tap **OFF** to check it's Hodor. Check you haven't blocked the app (**Apps**). If one app or site is the problem, turn on **Allow ads** for it, or switch the ads list to **Light**. |
| The key icon disappeared and Hodor says STOPPED | Another VPN took over, or Android stopped it. Tap **LIVE** again. |
| An app isn't in the Apps list | It hasn't connected since you started watching. Open it, then check again. |
| Hostnames missing (only IP addresses) | The app may use encrypted DNS of its own, or Private DNS is set to a specific provider. |
| The map shows nothing | Turn on **Look up IP locations**, and give it a few seconds. Local and private addresses never appear on the map. |
| A list shows "Last download failed" | Check your connection and tap **Update now**. With **Only on Wi-Fi** on, scheduled updates wait for Wi-Fi. |
| A site shows "This site can't be reached" | It may be held: check **Shield → Recently held**, or the notification. HTTPS sites can't show Hodor's page inside the browser, so Hodor uses a notification instead. |
| No "held" notifications | Bedtime mode or Do Not Disturb hides them: the bell's red dot and **Recently held** still show everything. |
| No "held" notifications (other causes) | Allow notifications for Hodor in Android settings. Repeats of the same site and app are silenced for 2 minutes. |
| Video calls or games slower with filtering on | **Block QUIC** makes apps use TCP. Turn it off in **Bypass protection** if needed; filtering still works on DNS and site names. |
| The Ads blocked count stays at 0 | Make sure **Block ads & trackers** is on, that the list finished downloading (Shield shows the domain count), and that the app doesn't have **Allow ads** on. |

---

## Glossary

| Term | Meaning |
|---|---|
| **Connection** | One conversation between an app and a server |
| **DNS lookup** | An app asking "what is the address of example.com?" before connecting |
| **Hostname** | The name of a server, such as `graph.facebook.com` |
| **IP address** | The numeric address of a server, such as `157.240.5.142` |
| **Port** | The number that says which service on a server is used, such as 443 for secure websites |
| **TCP / UDP** | The two main ways data is sent; QUIC (HTTP/3) runs over UDP port 443 |
| **VPN** | Here, the on-phone mechanism (`tun0`) that lets Hodor see every app's traffic; nothing is sent to a VPN company |
