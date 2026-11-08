// The TiVo's own transcoder serves H.264/AAC HLS; kmttg proxies only this session.
// The existing ffmpeg web player remains available on Stream.html.
(() => {
   "use strict";
   const status = document.getElementById("status");
   const video = document.getElementById("video");
   const streamOptions = document.getElementById("stream-options");
   const qualityControl = document.getElementById("quality-control");
   const audioControl = document.getElementById("audio-control");
   const quality = document.getElementById("quality");
   const audio = document.getElementById("audio");
   const params = new URLSearchParams(window.location.search);
   const tivo = params.get("tivo");
   const recordingId = params.get("recordingId");
   let token = null;
   let player = null;
   let stopping = false;
   let keepAliveTimer = null;

   function endpoint(path, values) {
      const url = new URL(path, window.location.origin);
      for (const [key, value] of Object.entries(values))
         url.searchParams.set(key, value);
      return url;
   }

   function option(value, label) {
      const item = document.createElement("option");
      item.value = String(value);
      item.textContent = label;
      return item;
   }

   function showOptions() {
      streamOptions.hidden = qualityControl.hidden && audioControl.hidden;
   }

   function refreshQuality() {
      const levels = player?.levels || [];
      if (levels.length < 2) {
         qualityControl.hidden = true;
         showOptions();
         return;
      }
      const items = [option(-1, "Auto")];
      for (const [index, level] of levels.entries()) {
         const dimensions = level.height ? level.height + "p" : "Quality " + (index + 1);
         const speed = level.bitrate ? " (" + (level.bitrate / 1_000_000).toFixed(1) + " Mbps)" : "";
         items.push(option(index, dimensions + speed));
      }
      quality.replaceChildren(...items);
      quality.value = String(player.autoLevelEnabled ? -1 : player.currentLevel);
      qualityControl.hidden = false;
      showOptions();
   }

   function refreshAudio() {
      const tracks = player ? player.audioTracks : video.audioTracks;
      if (!tracks || tracks.length < 2) {
         audioControl.hidden = true;
         showOptions();
         return;
      }
      const options = Array.from(tracks, (track, index) =>
         option(index, track.name || track.label || track.lang || track.language || "Track " + (index + 1)));
      audio.replaceChildren(...options);
      const selected = player ? player.audioTrack : Array.from(tracks).findIndex(track => track.enabled);
      audio.value = String(selected >= 0 ? selected : 0);
      audioControl.hidden = false;
      showOptions();
   }

   quality.addEventListener("change", () => {
      if (player) player.currentLevel = Number(quality.value);
   });
   audio.addEventListener("change", () => {
      if (player) {
         player.audioTrack = Number(audio.value);
      } else if (video.audioTracks) {
         for (let i = 0; i < video.audioTracks.length; i++)
            video.audioTracks[i].enabled = i === Number(audio.value);
      }
   });

   // Safari handles HLS natively. Its audio-track list is exposed only on
   // browsers that implement HTMLMediaElement.audioTracks.
   if (video.audioTracks) {
      video.audioTracks.addEventListener("addtrack", refreshAudio);
      video.audioTracks.addEventListener("removetrack", refreshAudio);
      video.audioTracks.addEventListener("change", refreshAudio);
   }

   function stop() {
      if (stopping) return;
      stopping = true;
      if (keepAliveTimer !== null) {
         clearInterval(keepAliveTimer);
         keepAliveTimer = null;
      }
      if (player) {
         player.destroy();
         player = null;
      }
      video.pause();
      video.removeAttribute("src");
      video.load();
      if (token) {
         // keepalive survives navigation/tab closure; the server also expires idle sessions.
         fetch(endpoint("/nativeHls/stop", { token }), {
            method: "POST",
            headers: { "X-Kmttg-Playback": "1" },
            keepalive: true
         }).catch(() => {});
         token = null;
      }
   }

   window.addEventListener("pagehide", stop, { once: true });
   // Reaching the end is different from pausing. A finished VOD stream should
   // release its hardware transcoder even if the browser tab stays open.
   video.addEventListener("ended", () => {
      stop();
      status.textContent = "Recording finished.";
   });

   async function start() {
      if (!tivo || !recordingId) {
         status.textContent = "Missing TiVo or recording identifier.";
         return;
      }
      try {
         const response = await fetch(endpoint("/nativeHls/start", { tivo, recordingId }), {
            method: "POST",
            headers: { "X-Kmttg-Playback": "1" },
            cache: "no-store"
         });
         const data = await response.json();
         if (!response.ok) throw new Error(data.error || "TiVo streaming unavailable");
         token = data.token;
         // Playback may be paused or fully buffered for minutes without media GETs.
         // The server must not confuse an open player with a crashed/closed tab.
         keepAliveTimer = setInterval(async () => {
            if (stopping || !token) return;
            try {
               const ping = await fetch(endpoint("/nativeHls/ping", { token }), {
                  method: "POST",
                  headers: { "X-Kmttg-Playback": "1" },
                  cache: "no-store"
               });
               if (ping.status === 404) {
                  stop();
                  status.textContent = "TiVo playback session expired.";
               }
            } catch (e) {
               // Transient LAN interruption: let the server's five-minute grace
               // period handle it; do not stop playback on one dropped ping.
            }
         }, 60_000);
         if (stopping) {
            // User navigated away while startup was in progress.
            stopping = false;
            stop();
            return;
         }
         if (video.canPlayType("application/vnd.apple.mpegurl")) {
            video.src = data.playlist; // Safari and native Apple HLS
            refreshAudio();
         } else if (typeof Hls !== "undefined" && Hls.isSupported()) {
            player = new Hls({ enableWorker: true });
            player.on(Hls.Events.MANIFEST_PARSED, refreshQuality);
            player.on(Hls.Events.LEVELS_UPDATED, refreshQuality);
            player.on(Hls.Events.LEVEL_SWITCHED, () => {
               if (!qualityControl.hidden)
                  quality.value = String(player.autoLevelEnabled ? -1 : player.currentLevel);
            });
            player.on(Hls.Events.AUDIO_TRACKS_UPDATED, refreshAudio);
            player.on(Hls.Events.AUDIO_TRACK_SWITCHED, refreshAudio);
            player.loadSource(data.playlist);
            player.attachMedia(video);
         } else {
            stop();
            throw new Error("This browser cannot play HLS.");
         }
         status.textContent = "Streaming from TiVo without downloading or transcoding on kmttg.";
         video.play().catch(() => {}); // user can press Play if autoplay is blocked
      } catch (error) {
         status.textContent = error.message || "Could not stream from TiVo.";
      }
   }

   start();
})();
