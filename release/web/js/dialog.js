// Modal dialogs built on the native <dialog> element.
// Replaces dialog/dialog_box.js, which faded a hand-positioned <div> with
// setInterval("...") string timers and needed a #content wrapper on every page.

let dialogEl = null;

function ensureDialog() {
   if (dialogEl)
      return dialogEl;
   dialogEl = document.createElement("dialog");
   dialogEl.className = "kdialog";
   dialogEl.innerHTML =
      '<form method="dialog">' +
      '<header><h2 class="kdialog-title"></h2>' +
      '<button class="kdialog-close" value="close" aria-label="Close">&times;</button></header>' +
      '<div class="kdialog-content"></div>' +
      '<footer class="kdialog-buttons"></footer>' +
      '</form>';
   document.body.appendChild(dialogEl);
   return dialogEl;
}

// showDialog(title, message, type, autohide)
// type is one of error/warning/success/prompt; autohide is seconds.
function showDialog(title, message, type, autohide) {
   const el = ensureDialog();
   el.className = "kdialog " + (type || "error");
   el.querySelector(".kdialog-title").textContent = title;
   // Messages are server text or JSON dumps, never trusted markup
   el.querySelector(".kdialog-content").textContent = message;
   el.querySelector(".kdialog-buttons").replaceChildren();
   el.querySelector(".kdialog-close").hidden = !!autohide;

   if (el.open)
      el.close();
   el.showModal();

   clearTimeout(el.autohideTimer);
   if (autohide)
      el.autohideTimer = setTimeout(() => el.open && el.close(), autohide * 1000);
   return el;
}

// Promise-based replacement for window.confirm, which blocks the whole page
function confirmDialog(title, message) {
   const el = showDialog(title, message, "prompt");
   const buttons = el.querySelector(".kdialog-buttons");
   const ok = document.createElement("button");
   ok.className = "pure-button pure-button-primary";
   ok.value = "ok";
   ok.textContent = "OK";
   const cancel = document.createElement("button");
   cancel.className = "pure-button";
   cancel.value = "cancel";
   cancel.textContent = "Cancel";
   buttons.append(cancel, ok);
   ok.focus();
   return new Promise(resolve => {
      el.addEventListener("close", () => resolve(el.returnValue === "ok"), { once: true });
   });
}

// Pick one value from a list. Replaces the inline "select a file" panels that
// SeasonPasses hid and unhid in place.
function selectDialog(title, label, items) {
   const el = showDialog(title, "", "prompt");
   const content = el.querySelector(".kdialog-content");
   content.textContent = "";
   const select = document.createElement("select");
   select.className = "kdialog-select";
   fillSelect(select, items);
   const labelEl = document.createElement("label");
   labelEl.textContent = label;
   labelEl.appendChild(select);
   content.appendChild(labelEl);

   const buttons = el.querySelector(".kdialog-buttons");
   const ok = document.createElement("button");
   ok.className = "pure-button pure-button-primary";
   ok.value = "ok";
   ok.textContent = "OK";
   const cancel = document.createElement("button");
   cancel.className = "pure-button";
   cancel.value = "cancel";
   cancel.textContent = "Cancel";
   buttons.append(cancel, ok);
   select.focus();
   return new Promise(resolve => {
      el.addEventListener("close", () => resolve(el.returnValue === "ok" ? select.value : null), { once: true });
   });
}
