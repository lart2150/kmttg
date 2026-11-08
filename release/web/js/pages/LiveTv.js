(() => {
   "use strict";
   const tivo = document.getElementById("tivo");
   const channel = document.getElementById("channel");
   const watch = document.getElementById("watch");
   const status = document.getElementById("status");
   const requestedTivo = new URLSearchParams(location.search).get("tivo");

   async function loadChannels() {
      channel.disabled = true;
      watch.disabled = true;
      channel.replaceChildren();
      status.textContent = "Loading received channels…";
      try {
         const url = new URL("/nativeHls/channels", location.origin);
         url.searchParams.set("tivo", tivo.value);
         const response = await fetch(url, { cache: "no-store" });
         const data = await response.json();
         if (!response.ok) throw new Error(data.error || "Could not load channels");
         for (const entry of data.channel) {
            const option = document.createElement("option");
            option.value = entry.stbChannelId;
            option.textContent = entry.channelNumber + " · " + entry.callSign;
            channel.append(option);
         }
         channel.disabled = !channel.options.length;
         watch.disabled = !channel.options.length;
         status.textContent = channel.options.length
            ? "Select a channel to stream from your TiVo."
            : "This TiVo has no received channels available for streaming.";
      } catch (error) {
         status.textContent = error.message || "Could not load TiVo channels.";
      }
   }

   watch.addEventListener("click", () => {
      if (watch.disabled || channel.disabled) return;
      const url = new URL("/web/NativeHls.html", location.origin);
      url.searchParams.set("tivo", tivo.value);
      url.searchParams.set("stbChannelId", channel.value);
      window.open(url.toString(), "_blank", "noopener");
   });
   tivo.addEventListener("change", loadChannels);

   async function initialize() {
      try {
         const response = await fetch("/getRpcTivos", { cache: "no-store" });
         if (!response.ok) throw new Error("Could not load configured TiVos");
         const available = await response.json();
         for (const name of available) {
            const option = document.createElement("option");
            option.textContent = name;
            option.value = name;
            tivo.append(option);
         }
         if (!available.length) throw new Error("No RPC-enabled TiVos configured");
         if (available.includes(requestedTivo)) tivo.value = requestedTivo;
         await loadChannels();
      } catch (error) {
         status.textContent = error.message || "Live TV is unavailable.";
      }
   }
   initialize();
})();
