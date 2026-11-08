This is a fork of [KMTTG by moyekj](https://sourceforge.net/p/kmttg/wiki/Home/)

For more information read the wiki pages on sourceforge for the orginal version by moyekj.

See the [changelog](CHANGELOG.md) for what's changed in each release of this fork.

## Requirements

kmttg v2.10.0-l and later require **Java 11 or newer** (Java 8 and 10 are no longer
supported). Java 21 or Java 25 is recommended - free builds at https://adoptium.net.

If you updated from an older kmttg and it now shows a "Java 11 or newer required"
message, install a newer Java and start kmttg again. Your config.ini, auto.ini,
queue and downloads are untouched.


## Stream recordings directly from TiVo (experimental)

The Video Streaming web page offers **stream directly from TiVo** for completed,
copy-freely recordings on compatible TiVos. Its links appear in this order:
**transcode & play**, **stream directly from TiVo**, **transcode**.
Direct streaming plays in your browser using the TiVo's built-in transcoder,
without downloading and running FFmpeg on the kmttg computer. By contrast,
**transcode & play** downloads, decodes and converts the recording on the kmttg
computer as you watch it; **transcode** creates a cached converted file.

In the desktop GUI, select one recording in a TiVo's Now Playing tab,
right-click and choose **Stream to This Computer [h]** (or press H).
The existing **Play [p]** command starts playback on the TiVo's television
output. The kmttg web server must be enabled in Web Server settings.
The existing transcoding options remain available as a fallback.

The local TiVo must allow HTTP streaming on port 49152 and authenticated RPC
on its configured port. The native playback page proxies its session-specific
playlists, AES keys and MPEG-TS segments through the kmttg web server, using a
random per-session token. The browser page stops the session when closed, and
the browser sends a session heartbeat once a minute while the player is open,
so paused recordings are not mistaken for abandoned sessions. The server expires
sessions with no heartbeat/media activity after five minutes or on shutdown. A private,
durable session journal also allows kmttg to reclaim its own abandoned sessions
after a hard crash: recovery first checks that the TiVo still reports the exact
client UUID and session ID, and never releases a session belonging to another
live kmttg process. Native playback is offered only when the TiVo's streaming
service reports enabled and Ready. Only
copy-freely recordings with the expected TiVo DRM permissions are offered
this option; protected content is never forced or decrypted against policy.

The included hls.js player supports browsers with Media Source Extensions,
while Safari can use its native HLS player. Native playback is experimental
and may not be available on every TiVo model.
