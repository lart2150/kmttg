# Changelog

Release notes for the kmttg fork, newest first. Release candidates are rolled into the release they led up to.

## Unreleased (v2.12.0)

### Added
- Built-in MKV writer: remux recordings to Matroska in Java, with no ffmpeg copy step
- SkipMode segments written as MKV chapters, and the AutoSkip table filled from tivo.com
- SkipMode data that doesn't fit the recording is remembered, the recording's own airing is preferred, and mis-anchored data is recovered by reading the recording's stream
- Chapter 1 starts at the beginning of the file, and MKV metadata is filled in
- DRM badge on copy protected recordings in the NPL
- Plain-English search type using the TiVo voice search
- TiVo Defaults button in the recording and season pass option dialogs
- Copy My Shows recordings to another TiVo over MRV
- Help > Capture TiVo test data, plus a standalone capture jar so owners of models we don't have can send fixtures back
- `/rpcws` web endpoint that runs RPCs over pooled tivo.com websocket connections, renews expired tokens and closes after a minute idle
- `/rpc` and `/rpcws` can pass TiVo replies and errors through raw, report kmttg failures as typed JSON, and negotiate SchemaVersion per TiVo

### Fixed
- ffmpeg encode profiles work with current ffmpeg (tested on 8.1) and still work with the older bundled ffmpeg. The h264 high/med rate, PS3 and Xbox 360 profiles failed on newer ffmpeg
- Mobile encode profiles now apply their max bitrate, and the 1080p profile uses level 4.1
- AutoSkip.ini is keyed on the airing, so two airings of one episode keep their own cuts
- A half written AutoSkip.ini entry is dropped instead of writing "null" back out
- Existing cut points are kept when an import has none of its own
- A skip share is only reported as imported when the table was actually written
- The commercial in front of a recording with a single show segment is skipped
- A bad config.ini setting is skipped instead of stopping startup, and one bad value in auto.ini only loses that one setting
- Class defaults are restored for fields that an older queue file never carried
- NOT and OR keywords work in searches instead of matching nothing
- Web transcode cleanup no longer skips an entry after removing one
- Other pyTivo shares are kept when one has a blank path
- Show sort stays consistent when only one side has an episode number
- Captions: the last caption is kept, and findBefore works past the synced range
- Both streams are released when a file copy fails
- urlDecode and getTimeRemaining no longer throw on ordinary input
- Web server: half written EXTINF lines are ignored so the stream duration stops jumping, a new transcode no longer overwrites a cached one that shares its prefix, and a suffix range asking for more than the file holds serves the whole file
- Websockets: bounded header/body split so a body containing a blank line survives, and better timeouts

### Removed
- Zune, Creative Zen and PSP encode profiles

## v2.11.0-l (2026-09-19)

### Added
- Web UI rewritten in vanilla JS, dropping jQuery and DataTables
- Network connect progress shown in the web Info panel

### Fixed
- Security: closed file exposure, SSRF and injection holes in the web server
- Transcode and FILES job paths are confined to the configured video directories
- Web table filter/sort, one-pass reorder key, and double HTTP error responses
- The web server waits for in-flight connections when it stops
- Show information details and spacing, with async artwork and a per-series cache
- Dialogs resize to the GUI font size, and dialogs taller than the screen scroll
- Deleted and NPL table rows are removed by show identity, not by row number
- The JSON file handle is closed when a parse fails part way

### Build
- Uses tivolibre 0.8.0 from the fork's maven branch
- Large test additions: RPC replay, request shaping, JSON/XML parsing, GUI dialogs, table actions

## v2.10.0-l (2026-09-05)

### Changed
- **JavaFX UI replaced with Swing (FlatLaf)**. JavaFX is no longer needed
- **Java 11 minimum.** A Java 8 fallback lets older installs upgrade cleanly and checks for future Java version requirements
- Build migrated from Ant to Gradle, with Maven-managed dependencies updated to their latest versions

### Added
- `kmttg.vmoptions` file for JVM parameters
- Optional channel filter exclude list (thanks Ted Hess)
- Filter the Remote Deleted tab by show title
- `-rpcLog` flag logs RPC requests and responses with timing (JSONL)
- recordingId to MFS id cache to speed up NPL refresh
- THIRD-PARTY.md credits third-party libraries and ships in the release zip

### Fixed
- HTTP NPL: fixed an error, and it now streams instead of using a temp file
- configAuto layout under Swing (thanks Ted Hess)

## v2.9.5-l (2026-05-03)

- Updated RPC certificate
- JavaFX versions bumped, adding support for JRE 24

## v2.9.4-l (2025-08-03)

### Fixed
- Domain token fetching
- Metadata is overwritten when Overwrite is set
- kmttg.ps1 works when there is more than one JavaFX folder

## v2.9.3-l (2025-05-26)

- Warning shown when JavaFX is missing on older JRE versions

## v2.9.2-l (2025-05-17)

### Fixed
- Network connect ("phone home") was broken in 2.9
- Search
- Domain tokens refresh automatically, and websocket locks cleaned up
- kmttg shell script parameters (thanks thess)
- Tools download moved to GitHub

### Changed
- JavaFX versions bumped

## v2.9-l (2024-11-16)

### Added
- Websocket support for remote connections
- `[recordingId]`, `[contentId]` and `[collectionId]` filename keywords
- Deleted shows included in the disk usage graph
- Export to CSV on the deleted shows list
- Certificate information shown in Help
- Logging when the RPC certificate expires in less than 90 days, with an error under 14 days
- RPC certificate updated (valid to 11/17/2026)

### Fixed
- Resume offset for downloadPipedStream
- Permanently Delete runs in a new thread so it doesn't block the UI
- TS downloads use a .ts extension instead of .mpg
- Channel loading continues when one channel is missing metadata
- Websocket library

### Changed
- JavaFX versions bumped

## v2.8-l (2023-12-17)

### Added
- OpenJFX 20 for JRE 17+
- Updated mind certificate
- GitHub Actions build

### Fixed
- Deleted shows list only returned the first 1k entries. It now pages through all of them
- Permanently deleting a recording without a title threw an error
- Temp files go in a subdirectory, and the update zip is extracted in the temp dir
- Startup error about TooltipBehavior is silently caught
- Java deprecation cleanup

## v2.7-l (2023-02-19)

### Added
- JavaFX is downloaded and extracted if needed
- `kmttg.bat` and `kmttg.ps1` launch scripts for Windows, and an updated bash script

### Fixed
- Getting the domain token
- TLS 1.0 enabled for HTTPS requests to older TiVos
- File downloads on Java 11+
- CSS applied to all windows
- Removed dead code with `finalize` that caused compile warnings on JRE 9+
- More tasks work without JavaFX

## v2.6-l (2022-12-10)

- AutoSkip is more sensitive to another show starting
- JavaFX dependency removed for non-GUI actions
- Compatibility with JavaFX 11

## v2.5a-l (2022-06-05)

- The updater points at this fork on GitHub and uses an HTTP client, because the default HTTPS ciphers for URL openConnection are limited
- The release zip excludes some files to prevent accidental leaks
- Added the README

## v2.5-l (2022-06-04)

First release of the fork.

- Domain token authentication for tivo.com
- JavaFX availability check
- Export season passes from the command line
