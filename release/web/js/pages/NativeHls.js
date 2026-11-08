// The TiVo's own transcoder serves H.264/AAC HLS; kmttg proxies only this session.
// The existing ffmpeg web player remains available on Stream.html.
(() => {
   "use strict";
   const status = document.getElementById("status");
   const video = document.getElementById("video");
   const stopButton = document.getElementById("stop");
   const params = new URLSearchParams(window.location.search);
   const tivo = params.get("tivo");
   const recordingId = params.get("recordingId");
   let token = null;
   let player = null;
   let stopping = false;

   function endpoint(path, values) {
      const url = new URL(path, window.location.origin);
      for (const [key, value] of Object.entries(values))
         url.searchParams.set(key, value);
      return url;
   }

   function stop() {
      if (stopping) return;
      stopping = true;
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

   stopButton.addEventListener("click", () => {
      stop();
      status.textContent = "Playback stopped.";
   });
   window.addEventListener("pagehide", stop, { once: true });

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
         if (stopping) {
            // User navigated away while startup was in progress.
            stopping = false;
            stop();
            return;
         }
         if (video.canPlayType("application/vnd.apple.mpegurl")) {
            video.src = data.playlist; // Safari and native Apple HLS
         } else if (typeof Hls !== "undefined" && Hls.isSupported()) {
            player = new Hls({ enableWorker: true });
            player.loadSource(data.playlist);
            player.attachMedia(video);
         } else {
            stop();
            throw new Error("This browser cannot play HLS.");
         }
         status.textContent = "Streaming directly from TiVo.";
         video.play().catch(() => {}); // user can press Play if autoplay is blocked
      } catch (error) {
         status.textContent = error.message || "Native playback failed.";
      }
   }

   start();
})();
