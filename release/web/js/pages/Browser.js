// Share Browser - lists the file shares the server exposes

document.addEventListener("DOMContentLoaded", async () => {
   const browser = document.getElementById("BROWSER");
   try {
      const shares = await getJSON("/getBrowserShares");
      if (shares.length === 0) {
         browser.textContent = "No shares are configured.";
         return;
      }
      for (const share of shares) {
         const a = document.createElement("a");
         a.href = "/" + share;
         a.textContent = share;
         browser.appendChild(a);
      }
   } catch (e) {
      util_handleError("/getBrowserShares", e);
   }
});
