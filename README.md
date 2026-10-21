# TubeHaus

**Your own YouTube, on your own server: fast, private, and it doesn't get blocked.**

It looks and works exactly like YouTube (same layout, same URLs, same keyboard shortcuts),
with no ads and no tracking. It also plays live streams with real-time captions, keeps one
library for all your devices, and keeps working even from a cheap cloud VPS.

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

**🌍 11 languages:** 한국어 · English · 日本語 · 简体中文 · Español · Português · Français · Deutsch · Русский · हिन्दी · Bahasa Indonesia —
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

## Quick start (Docker)

One command brings up everything: Caddy, the TubeHaus server with Cloudflare WARP built in,
the PO-token provider for live streams, whisper.cpp for live captions, and the official
Invidious + companion + PostgreSQL images. Works on x86-64 and ARM64.

```sh
git clone https://github.com/biolend83/tubehaus && cd tubehaus
cp .env.example .env        # set your domain and three secrets
docker compose up -d        # first build takes a few minutes (whisper.cpp)
```

Then open your domain. WARP registers itself on first start and keeps its registration in a
volume; the companion reaches YouTube through it as well. Add a login to
`deploy/Caddyfile.docker` before you expose it (see *Caddy* below).

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
