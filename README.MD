This is a fork of [KMTTG by moyekj](https://sourceforge.net/p/kmttg/wiki/Home/)

For more information read the wiki pages on sourceforge for the orginal version by moyekj.

See the [changelog](CHANGELOG.md) for what's changed in each release of this fork.

## Requirements

kmttg v2.10.0-l and later require **Java 11 or newer** (Java 8 and 10 are no longer
supported). Java 21 or Java 25 is recommended - free builds at https://adoptium.net.

If you updated from an older kmttg and it now shows a "Java 11 or newer required"
message, install a newer Java and start kmttg again. Your config.ini, auto.ini,
queue and downloads are untouched.


## Native TiVo recording HLS playback (experimental)

The Video Streaming web page offers **native HLS** for completed, copy-freely
recordings on compatible TiVos. This uses the TiVo's built-in transcoder instead
of transferring the recording and running FFmpeg on the kmttg host. The existing
transcoding links remain available as a fallback.

The local TiVo must allow HTTP streaming on port 49152 and authenticated RPC
on its configured port. The native playback page proxies its session-specific
playlists, AES keys and MPEG-TS segments through the kmttg web server, using a
random per-session token. The browser page stops the session when closed, and
the server expires idle sessions after five minutes or on shutdown. A private,
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
