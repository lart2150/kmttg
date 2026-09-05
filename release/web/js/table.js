// Minimal sortable/filterable/selectable table.
// Replaces DataTables 1.10.3 + the Flash-based TableTools extension, which
// between them were only providing row selection, expandable child rows, a
// filter box and sorting on a single table.
//
// A cell value may be a string (inserted as text), a DOM node, or
// {html: "..."} when markup is genuinely needed.

class KTable {
   // options: columns, select ("single"|"multi"), sortable, filter, details, emptyText
   constructor(el, options) {
      this.el = el;
      this.options = options;
      this.columns = options.columns;
      this.rows = [];
      this.sortIndex = -1;
      this.sortAsc = true;
      this.filterText = "";
      this.onSelect = null;
      this.build();
   }

   build() {
      this.el.classList.add("ktable", "pure-table");

      this.toolbar = document.createElement("div");
      this.toolbar.className = "ktable-toolbar";
      if (this.options.filter !== false) {
         const label = document.createElement("label");
         label.className = "ktable-filter";
         label.textContent = "Filter:";
         this.filterInput = document.createElement("input");
         this.filterInput.type = "search";
         this.filterInput.addEventListener("input", () => {
            this.filterText = this.filterInput.value.toLowerCase();
            this.applyFilter();
         });
         label.appendChild(this.filterInput);
         this.toolbar.appendChild(label);
      }
      this.countEl = document.createElement("span");
      this.countEl.className = "ktable-count";
      this.toolbar.appendChild(this.countEl);
      this.el.parentNode.insertBefore(this.toolbar, this.el);

      const thead = document.createElement("thead");
      const tr = document.createElement("tr");
      this.columns.forEach((col, i) => {
         const th = document.createElement("th");
         if (col.details) {
            th.className = "details-control-header";
         } else {
            th.textContent = col.label || "";
            if (this.options.sortable && col.orderable !== false) {
               th.classList.add("sortable");
               th.addEventListener("click", () => this.sortBy(i));
            }
         }
         if (col.width)
            th.style.width = col.width;
         tr.appendChild(th);
      });
      thead.appendChild(tr);
      this.tbody = document.createElement("tbody");
      this.el.replaceChildren(thead, this.tbody);

      this.tbody.addEventListener("click", e => this.clicked(e));
      this.updateCount();
   }

   clicked(e) {
      const td = e.target.closest("td");
      const tr = e.target.closest("tr");
      if (!tr || !this.tbody.contains(tr) || tr.classList.contains("details-row"))
         return;
      const row = this.rows.find(r => r.el === tr);
      if (!row)
         return;
      if (td && td.classList.contains("details-control")) {
         this.toggleDetails(row);
         return;
      }
      // Links and buttons inside a cell act on their own
      if (e.target.closest("a, button"))
         return;
      this.select(row, e);
   }

   select(row, e) {
      const mode = this.options.select;
      if (!mode)
         return;
      const additive = mode === "multi" && (e.ctrlKey || e.metaKey);
      if (!additive)
         for (const r of this.rows)
            if (r !== row)
               this.setSelected(r, false);
      this.setSelected(row, additive ? !row.selected : true);
      if (this.onSelect)
         this.onSelect(row);
   }

   setSelected(row, selected) {
      row.selected = selected;
      row.el.classList.toggle("selected", selected);
   }

   selectedRows() {
      return this.rows.filter(r => r.selected);
   }

   // cells must line up with the columns; data is the payload kept off-screen
   add(cells, data) {
      const tr = document.createElement("tr");
      this.columns.forEach((col, i) => {
         const td = document.createElement("td");
         if (col.details) {
            td.className = "details-control";
         } else {
            setCell(td, cells[i]);
            if (col.className)
               td.className = col.className;
         }
         tr.appendChild(td);
      });
      const row = { cells: cells, data: data, el: tr, selected: false, detailsRow: null };
      this.rows.push(row);
      this.tbody.appendChild(tr);
      this.updateCount();
      if (this.filterText)
         this.applyFilterRow(row);
      return row;
   }

   toggleDetails(row) {
      if (row.detailsRow) {
         row.detailsRow.remove();
         row.detailsRow = null;
         row.el.classList.remove("shown");
         return;
      }
      const tr = document.createElement("tr");
      tr.className = "details-row";
      const td = document.createElement("td");
      td.colSpan = this.columns.length;
      setCell(td, this.options.details(row));
      tr.appendChild(td);
      row.el.after(tr);
      row.detailsRow = tr;
      row.el.classList.add("shown");
   }

   clear() {
      this.rows = [];
      this.tbody.replaceChildren();
      this.updateCount();
   }

   removeRow(row) {
      if (row.detailsRow)
         row.detailsRow.remove();
      row.el.remove();
      this.rows.splice(this.rows.indexOf(row), 1);
      this.updateCount();
   }

   // Move a row by the given offset, keeping it selected
   moveRow(row, offset) {
      const from = this.rows.indexOf(row);
      const to = from + offset;
      if (from < 0 || to < 0 || to >= this.rows.length)
         return false;
      this.rows.splice(from, 1);
      this.rows.splice(to, 0, row);
      this.render();
      row.el.scrollIntoView({ block: "nearest" });
      return true;
   }

   sortBy(index) {
      this.sortAsc = this.sortIndex === index ? !this.sortAsc : true;
      this.sortIndex = index;
      const dir = this.sortAsc ? 1 : -1;
      this.rows.sort((a, b) => dir * String(cellText(a.cells[index]))
         .localeCompare(String(cellText(b.cells[index])), undefined, { numeric: true }));
      this.el.querySelectorAll("th").forEach((th, i) => {
         th.classList.toggle("sort-asc", i === index && this.sortAsc);
         th.classList.toggle("sort-desc", i === index && !this.sortAsc);
      });
      this.render();
   }

   render() {
      for (const row of this.rows) {
         this.tbody.appendChild(row.el);
         if (row.detailsRow)
            this.tbody.appendChild(row.detailsRow);
      }
   }

   applyFilter() {
      for (const row of this.rows)
         this.applyFilterRow(row);
      this.updateCount();
   }

   applyFilterRow(row) {
      const hit = !this.filterText ||
         row.cells.some(c => String(cellText(c)).toLowerCase().includes(this.filterText));
      row.el.hidden = !hit;
      if (row.detailsRow)
         row.detailsRow.hidden = !hit;
   }

   get visibleCount() {
      return this.rows.filter(r => !r.el.hidden).length;
   }

   updateCount() {
      const total = this.rows.length;
      const shown = this.visibleCount;
      if (total === 0)
         this.countEl.textContent = this.options.emptyText || "No data";
      else if (shown === total)
         this.countEl.textContent = total + (total === 1 ? " entry" : " entries");
      else
         this.countEl.textContent = shown + " of " + total + " entries";
   }
}

function setCell(td, value) {
   if (value == null)
      td.textContent = "";
   else if (value instanceof Node)
      td.appendChild(value);
   else if (typeof value === "object" && value.html !== undefined)
      td.innerHTML = value.html;
   else
      td.textContent = value;
}

// Text used for filtering and sorting
function cellText(value) {
   if (value == null)
      return "";
   if (value instanceof Node)
      return value.textContent;
   if (typeof value === "object" && value.html !== undefined)
      return value.html.replace(/<[^>]*>/g, " ");
   return value;
}
