// Shared page navigation, injected so the markup lives in one place.
// Collapses behind a toggle on narrow screens.

const PAGES = [
   { href: "Stream.html", label: "Streaming" },
   { href: "Browser.html", label: "Shares" },
   { href: "Remote.html", label: "Remote" },
   { href: "ToDo.html", label: "ToDo" },
   { href: "SeasonPasses.html", label: "Season Passes" },
   { href: "Info.html", label: "Info" },
   { href: "Jobs.html", label: "Job Monitor" }
];

function buildNav() {
   const current = window.location.pathname.split("/").pop() || "index.html";

   const nav = document.createElement("nav");
   nav.className = "knav";

   const toggle = document.createElement("button");
   toggle.className = "knav-toggle";
   toggle.setAttribute("aria-label", "Menu");
   toggle.setAttribute("aria-expanded", "false");
   toggle.innerHTML = "<span></span><span></span><span></span>";

   const list = document.createElement("ul");
   list.className = "knav-list";
   for (const page of PAGES) {
      const li = document.createElement("li");
      const a = document.createElement("a");
      a.href = page.href;
      a.textContent = page.label;
      if (page.href === current)
         a.className = "current";
      li.appendChild(a);
      list.appendChild(li);
   }

   toggle.addEventListener("click", () => {
      const open = nav.classList.toggle("open");
      toggle.setAttribute("aria-expanded", String(open));
   });

   nav.append(toggle, list);
   document.body.prepend(nav);
}

document.addEventListener("DOMContentLoaded", buildNav);
