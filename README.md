# TubeHaus

[![Release](https://img.shields.io/github/v/release/biolend83/tubehaus)](https://github.com/biolend83/tubehaus/releases)
[![License: AGPL-3.0](https://img.shields.io/badge/license-AGPL--3.0-blue)](LICENSE)
[![Check](https://github.com/biolend83/tubehaus/actions/workflows/check.yml/badge.svg)](https://github.com/biolend83/tubehaus/actions/workflows/check.yml)
![Docker: amd64 | arm64](https://img.shields.io/badge/docker-amd64%20%7C%20arm64-2496ED?logo=docker&logoColor=white)
[![Built on Invidious](https://img.shields.io/badge/built%20on-Invidious-orange)](https://invidious.io)

**Your own YouTube, on your own server: fast, private, and it doesn't get blocked.**

It looks and works exactly like YouTube (same layout, same URLs, same keyboard shortcuts),
with no ads and no tracking. It also plays live streams with real-time captions, keeps one
library for all your devices, and keeps working even from a cheap cloud VPS.

TubeHaus is built on top of [Invidious](https://invidious.io) and
[invidious-companion](https://github.com/iv-org/invidious-companion), which do the hard work of talking to
YouTube. TubeHaus adds its own front end and a small server next to them. All credit to the Invidious team.

![TubeHaus home screen](docs/screenshot-home.jpg)

## Why TubeHaus

**⚡ Fast**
- **Instant clicks.** Video pages come from a local cache that refreshes in the background, and the videos you are most likely to open next are fetched before you click (measured: about 1-2 s cold, a few milliseconds warm)
- **Live streams that don't stall.** The server pulls segments ahead of the player over kept-alive connections and serves a longer, gap-free playlist with exact segment timing, so the timeline never jumps
- **Always the best quality.** It never lowers resolution on its own. It prefers 60 fps and the codec your device decodes in hardware (checked with MediaCapabilities), switches codec instead of resolution when frames drop, and sizes the buffer from the bitrate so even 8K fits the browser's media buffer

**🔒 Private**
- **Your devices never talk to Google.** Video, thumbnails and pages are streamed through your own server; no Google account, cookies or YouTube scripts in your browser
- **YouTube never sees your home IP.** Everything your server asks YouTube goes out through Cloudflare WARP
- **No accounts, no telemetry.** Subscriptions, history, resume positions and playlists are files on your server, shared by your household and nobody else

**🛡️ Doesn't get blocked**
- YouTube refuses most VPS and cloud addresses ("Sign in to confirm you're not a bot"). TubeHaus routes YouTube traffic through **Cloudflare WARP in proxy mode**, and when YouTube flags the address anyway it **reconnects WARP for a fresh one and replays the request automatically** (rate-limited). Playback, live streams and downloads keep working on a cheap cloud VM

**🎙️ Live captions**
- YouTube gives no captions for live streams. TubeHaus transcribes them on the fly with [whisper.cpp](https://github.com/ggml-org/whisper.cpp), a few seconds ahead of playback, on a single CPU core

**🌍 12 languages:** 한국어 · English · 日本語 · 简体中文 · Español · Português · Français · Deutsch · Русский · हिन्दी · Bahasa Indonesia · Türkçe —
chosen on the first visit (the browser's language is preselected) and changeable any time under **Language** in the
account menu or Settings. View counts and dates follow each language's own style.

## Everything else

- YouTube-identical UI: home, search, channels, playlists, subscriptions, history, library, trending
- Custom player on [Shaka Player](https://github.com/shaka-project/shaka-player) (DASH/HLS): chapters, storyboard seek previews, SponsorBlock, speed, sleep timer, stats, mini player, theater mode, ambient mode, Media Session (lock screen / Bluetooth keys)
- **One library for every device**: subscriptions (with an RSS-based feed), watch history, resume positions, Watch Later, likes, playlists
- **Send to TV**: pick a video on your phone and it starts on the TV (no Chromecast; open screens long-poll the server)
- **High-quality offline save**: 1080p/4K come as separate video and audio; the server fetches both and merges them with ffmpeg
- **Translated subtitles** for videos that have none in your language
- **TV mode**: full remote-control navigation (arrows / OK / Back) for TV browsers
- Dislike counts (Return YouTube Dislike), comments, transcripts

Optional lookups the browser makes itself: SponsorBlock (by a hash prefix, not the video ID) and Return YouTube Dislike.

*(Formerly HomeTube, renamed to avoid a clash with an unrelated project of the same name.)*

## How it fits together

```
browser ──► Caddy ─┬─ /y/*, page navigations ─────────► web/ (the app)
                   ├─ /api/p/*, /api/v1/videos/* ─────► TubeHaus server ──┐
                   ├─ /companion/* ───────────────────► invidious-companion ─┤
                   └─ everything else ────────────────► Invidious          │
                                                                           ▼
                                                          Cloudflare WARP (proxy mode)
                                                                           │
                                                                        YouTube
```

TubeHaus uses [Invidious](https://github.com/iv-org/invidious) as its YouTube backend and adds its own server next to it. Invidious' companion `private_url` points at the TubeHaus server, which passes everything
through and patches in what is missing (live HLS URLs, an empty `storyboards` key some old
videos lack, WARP rotation when YouTube blocks).

## Requirements

- **Server:** Linux with Docker and docker compose, x86-64 or ARM64 (a free Oracle Cloud ARM VM works fine)
- **CPU / RAM:** 2+ cores and about 4 GB RAM for the whole stack. Live captions use about one more core, only while someone watches a live stream with captions on
- **Disk:** about 10-20 GB, mostly Docker images. Videos are streamed, not stored, so this doesn't grow with use
- **A hostname** for HTTPS (a domain, or a free wildcard DNS name such as sslip.io), or plain HTTP on a LAN

## Quick start (Docker)

One command brings up everything: Caddy, the TubeHaus server with Cloudflare WARP built in,
the PO-token provider for live streams, whisper.cpp for live captions, and the official
Invidious + companion + PostgreSQL images. Works on x86-64 and ARM64.

```sh
git clone https://github.com/biolend83/tubehaus && cd tubehaus
cp .env.example .env        # set your domain and three secrets
docker compose pull         # prebuilt images, amd64 and arm64
docker compose up -d
```

To build the TubeHaus image yourself instead (for example after changing the code), use
`docker compose up -d --build`. The first build takes a few minutes because of whisper.cpp.

Then open your domain. WARP registers itself on first start and keeps its registration in a
volume; the companion reaches YouTube through it as well.

**Add a login before you expose it.** TubeHaus has no accounts of its own. The simplest option is the built-in
password: set `TUBEHAUS_AUTH=basic`, `TUBEHAUS_USER` and `TUBEHAUS_PASSWORD_HASH` in `.env` (the hash comes from
`docker compose run --rm caddy caddy hash-password`; write every `$` as `$$`). Or put Authelia, Authentik or a
passkey gate in front with `forward_auth`.

**Check that everything works:**

```sh
docker compose exec tubehaus python3 /app/server/tubehaus_server.py --doctor
```

It tests WARP, YouTube, Invidious, a real video lookup and the optional tools, one line each.
If something fails, see [Troubleshooting](docs/TROUBLESHOOTING.md). To update or back up, see
[Updating and backups](docs/UPGRADING.md).

## Manual setup

You need a Linux server with Python 3.10+, [Caddy](https://caddyserver.com), and Docker for Invidious.

### 1. Cloudflare WARP in proxy mode

Install the official client ([pkg.cloudflareclient.com](https://pkg.cloudflareclient.com)), then —
**set proxy mode before connecting**, or the whole server's routing moves into WARP:

```sh
warp-cli --accept-tos registration new
warp-cli --accept-tos mode proxy        # listens on 127.0.0.1:40000
warp-cli --accept-tos connect
curl -x http://127.0.0.1:40000 https://www.cloudflare.com/cdn-cgi/trace   # warp=on
```

Containers can't reach 127.0.0.1 on the host, so re-expose it on the Docker bridge for the companion:

```sh
socat TCP-LISTEN:18080,bind=172.17.0.1,fork,reuseaddr TCP:127.0.0.1:40000   # run it as a service
```

(Unofficial WARP clients such as `wgcf` get rate-limited by Cloudflare; use the official one.)

### 2. Invidious + companion

Follow the [Invidious installation guide](https://docs.invidious.io/installation/) with
[invidious-companion](https://github.com/iv-org/invidious-companion). Then:

- companion: `PROXY=socks5://172.17.0.1:18080` (WARP) and publish it on `127.0.0.1:8282`
- Invidious config: point the companion's `private_url` at TubeHaus, e.g.
  `invidious_companion: [{ private_url: "http://172.17.0.1:3010/companion", public_url: "https://tube.example.com/companion" }]`
  (then bind TubeHaus to an address the container can reach, e.g. `HT_BIND=172.17.0.1`)

### 3. TubeHaus server

```sh
git clone https://github.com/biolend83/tubehaus /opt/tubehaus
cd /opt/tubehaus && cp .env.example .env    # edit it
sudo cp deploy/tubehaus.service /etc/systemd/system/ && sudo systemctl enable --now tubehaus
```

Optional extras:
- **Live streams:** [yt-dlp](https://github.com/yt-dlp/yt-dlp) with the
  [bgutil-ytdlp-pot-provider](https://github.com/Brainicism/bgutil-ytdlp-pot-provider) plugin (PO tokens keep live URLs alive)
- **Live captions:** build whisper.cpp and download `ggml-base-q5_1.bin`; set `HT_WHISPER_BIN` / `HT_WHISPER_MODEL`
- **Offline save:** `ffmpeg`

### 4. Caddy

Copy `web/` to `/srv/tubehaus/web` and adapt [`deploy/Caddyfile.example`](deploy/Caddyfile.example).

**TubeHaus has no accounts.** Anyone who can open it shares the same library. Put your own
login in front (Caddy `basic_auth`, or `forward_auth` to Authelia / Authentik / a passkey gate)
before exposing it. Set `window.TUBEHAUS_LOGIN_URL` in `index.html` if expired sessions should
be sent to your login page.

## FAQ

**Is this a replacement for Invidious?**
No, it runs alongside it. Invidious and its companion fetch everything from YouTube; TubeHaus is a
YouTube-style front end plus a server that adds live streams, live captions, a shared library, send to TV,
downloads and WARP egress. The docker compose file runs the official Invidious images for you.

**Can I use my existing Invidious instance (or keep using Yattee, FreeTube, ...)?**
Yes, follow *Manual setup* and point TubeHaus at it. Apps that talk to Invidious keep working, and if the
companion goes out through WARP they benefit from it too. Live streams and WARP rotation work best when
TubeHaus sits in front of the companion as described.

**Where does the content come from? Does it store videos?**
Everything comes from YouTube. Nothing is stored except your library (subscriptions, history, playlists) and
short-lived files for offline downloads.

**Is there a recommendation algorithm?**
Not a personal one: no Google account is involved, so nothing learns from what you watch. You get your
subscriptions feed, trending and category tabs, and YouTube's per-video related list.

**Age-restricted videos?**
They usually don't play, since there's no signed-in account to pass the age check. That is the same as Invidious.

**Does anything phone home?**
No. There are no accounts and no telemetry. The only outside requests are YouTube (through Invidious and WARP)
and, from the browser, the optional SponsorBlock and Return YouTube Dislike lookups.

**How do phones and TVs connect?**
It's a web app: open your address in the browser, or use "Add to Home Screen" to install it like an app.
TV browsers get a TV mode with remote-control navigation.

## Configuration

All settings are environment variables — see [`.env.example`](.env.example).

| Variable | Default | |
|---|---|---|
| `HT_BIND`, `HT_PORT` | `127.0.0.1`, `3010` | where the API listens |
| `HT_STATE` | `./state` | library and subtitle cache |
| `HT_INVIDIOUS` | `http://127.0.0.1:3000` | Invidious |
| `HT_COMPANION` | `http://127.0.0.1:8282` | invidious-companion |
| `HT_PUBLIC_URL` | `http://localhost:8080` | the address browsers use |
| `HT_YT_PROXY` | `127.0.0.1:40000` | WARP proxy (empty = direct) |
| `HT_WARP_ROTATE` | `1` | reconnect WARP when YouTube blocks |
| `HT_YTDLP` | `yt-dlp` | for live streams |
| `HT_WHISPER_BIN`, `HT_WHISPER_MODEL`, `HT_WHISPER_LANG` | empty, empty, `auto` | live captions |
| `HT_SUB_LANG`, `HT_LIBRETRANSLATE` | `en`, empty | default subtitle translation target (the app asks for its own language), optional fallback |
| `HT_DOWNLOAD_DIR` | `/var/tmp/tubehaus-dl` | offline save workspace |

## Translations

Interface text lives in `web/y/lang/<code>.js` (the key is the original Korean text). To add a language, copy
`en.js`, translate the values, and add the code to `LANGS` in `web/y/i18n.js`. Corrections are very welcome.

## Community

- **Questions and setup help:** [Discussions](https://github.com/biolend83/tubehaus/discussions) (please include the `--doctor` output)
- **Bugs and feature requests:** [Issues](https://github.com/biolend83/tubehaus/issues/new/choose)
- **Contributing:** see [CONTRIBUTING.md](CONTRIBUTING.md). Translation fixes are a great first contribution
- **Security problems:** report them privately, see [SECURITY.md](SECURITY.md)
- **What changed:** [CHANGELOG.md](CHANGELOG.md) and [Releases](https://github.com/biolend83/tubehaus/releases)

## Credits

- [Invidious](https://github.com/iv-org/invidious) and [invidious-companion](https://github.com/iv-org/invidious-companion) (AGPL-3.0) — the backend this sits on
- [Shaka Player](https://github.com/shaka-project/shaka-player) (Apache-2.0) — bundled in `web/y/lib/`
- Layout after [varun-jasti/Youtube-UI-UX](https://github.com/varun-jasti/Youtube-UI-UX) (MIT); progress bar and volume after [pncsoares/youtube-video-player-clone](https://github.com/pncsoares/youtube-video-player-clone) (MIT)
- Icons: Material Symbols (Apache-2.0)
- [whisper.cpp](https://github.com/ggml-org/whisper.cpp) (MIT), [SponsorBlock](https://sponsor.ajay.app), [Return YouTube Dislike](https://returnyoutubedislike.com)
- Cloudflare WARP

## License

AGPL-3.0, like Invidious.

This project is not affiliated with YouTube or Google. Use it for your own household, and
respect the terms of the services you connect to.
