/*
 * Progressive enhancement only: every form and link works without this file.
 */
(function () {
    "use strict";

    const $ = (sel, root = document) => root.querySelector(sel);
    const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

    // ---- Navigation drawer (mobile) & quick-log menu ---------------------------
    document.addEventListener("click", (event) => {
        if (event.target.closest("[data-nav-toggle]")) {
            document.body.classList.toggle("nav-open");
            return;
        }
        if (document.body.classList.contains("nav-open") && !event.target.closest(".rail")) {
            document.body.classList.remove("nav-open");
        }
        $$("details.quick-log[open]").forEach((d) => {
            if (!d.contains(event.target)) d.removeAttribute("open");
        });
    });
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape") {
            document.body.classList.remove("nav-open");
            $$("details.quick-log[open]").forEach((d) => d.removeAttribute("open"));
        }
    });

    // ---- Flash dismiss, confirmations, submit feedback ------------------------
    document.addEventListener("click", (event) => {
        const dismiss = event.target.closest("[data-dismiss]");
        if (dismiss) dismiss.closest(".flash")?.remove();

        const trigger = event.target.closest("[data-confirm]");
        if (trigger && !window.confirm(trigger.dataset.confirm)) {
            event.preventDefault();
            event.stopImmediatePropagation();
        }
    }, true);

    document.addEventListener("submit", (event) => {
        const form = event.target;
        const button = event.submitter || $("button[type=submit]", form);
        if (button && !button.hasAttribute("data-no-loading")) {
            // Defer so the submitter's name/value is still sent.
            setTimeout(() => button.classList.add("is-loading"), 0);
        }
    });

    // ---- Sortable tables: <th data-sort="num|text"> ---------------------------
    function sortTable(th) {
        const table = th.closest("table");
        const index = Array.from(th.parentNode.children).indexOf(th);
        const numeric = th.dataset.sort === "num";
        const asc = th.getAttribute("aria-sort") !== "ascending";
        $$("th[data-sort]", table).forEach((h) => h.removeAttribute("aria-sort"));
        th.setAttribute("aria-sort", asc ? "ascending" : "descending");
        const body = table.tBodies[0];
        const rows = Array.from(body.rows).filter((r) => !r.classList.contains("empty-row"));
        const key = (row) => {
            const cell = row.cells[index];
            const raw = cell ? (cell.dataset.value ?? cell.textContent.trim()) : "";
            return numeric ? parseFloat(raw.replace(/[^0-9.\-]/g, "")) || 0 : raw.toLowerCase();
        };
        rows.sort((a, b) => (key(a) > key(b) ? 1 : key(a) < key(b) ? -1 : 0) * (asc ? 1 : -1));
        rows.forEach((r) => body.appendChild(r));
    }
    document.addEventListener("click", (event) => {
        const th = event.target.closest("th[data-sort]");
        if (th) sortTable(th);
    });

    // ---- Client-side table filter: <input data-filter="#table-id"> ------------
    function applyFilter(table) {
        const inputs = $$(`[data-filter="#${table.id}"]`);
        const text = inputs.filter((i) => i.tagName === "INPUT").map((i) => i.value.trim().toLowerCase()).join(" ").trim();
        const selects = inputs.filter((i) => i.tagName === "SELECT");
        let visible = 0;
        $$("tbody tr[data-row]", table).forEach((row) => {
            const haystack = (row.textContent + " " + $$("input, select", row).map((f) => f.value).join(" ")).toLowerCase();
            const textOk = !text || text.split(/\s+/).every((t) => haystack.includes(t));
            const selectOk = selects.every((s) => !s.value || (row.dataset[s.dataset.key] || "") === s.value);
            const show = textOk && selectOk;
            row.classList.toggle("is-hidden", !show);
            if (show) visible++;
        });
        const counter = $(`[data-count-for="#${table.id}"]`);
        if (counter) counter.textContent = visible;
        table.dispatchEvent(new CustomEvent("filtered"));
    }
    $$("[data-filter]").forEach((input) => {
        const table = $(input.dataset.filter);
        if (!table) return;
        input.addEventListener(input.tagName === "SELECT" ? "change" : "input", () => applyFilter(table));
    });

    // ---- Nutrition ledger totals for visible rows -----------------------------
    const ledger = $("#ledger");
    if (ledger) {
        const total = () => {
            const sums = { calories: 0, protein: 0, carbs: 0, fat: 0 };
            $$("tbody tr[data-row]:not(.is-hidden)", ledger).forEach((row) => {
                Object.keys(sums).forEach((k) => { sums[k] += Number($(`[data-nutrient="${k}"]`, row)?.value || 0); });
            });
            Object.entries(sums).forEach(([k, v]) => { const out = $(`[data-total="${k}"]`); if (out) out.textContent = Math.round(v).toLocaleString("en-US"); });
        };
        ledger.addEventListener("filtered", total);
        ledger.addEventListener("input", total);
        total();
    }

    // ---- Meal form: calories from macros ---------------------------------------
    $("#calculateCalories")?.addEventListener("click", () => {
        const val = (id) => Number($("#" + id)?.value || 0);
        const kcal = Math.round(val("proteins") * 4 + val("carbohydrates") * 4 + val("fats") * 9);
        if (kcal > 0) $("#calories").value = kcal;
    });

    // ---- Unsaved-changes guard for long forms ---------------------------------
    $$("form[data-guard]").forEach((form) => {
        let dirty = false;
        form.addEventListener("input", () => { dirty = true; });
        form.addEventListener("submit", () => { dirty = false; });
        window.addEventListener("beforeunload", (e) => { if (dirty) { e.preventDefault(); e.returnValue = ""; } });
    });

    // ---- Workout builder -------------------------------------------------------
    // Blocks and rows are cloned from <template>s; field names are renumbered on
    // every change so Spring binds sets[i] / cardio[i] in order.
    const builder = $("#session-builder");
    if (builder) {
        const blocks = $("#blocks", builder);
        const cardioList = $("#cardio-rows", builder);
        const lastTimes = JSON.parse($("#last-times")?.textContent || "{}");
        let blockSeq = $$(".exercise-block", blocks).length;

        const renumber = () => {
            $$(".exercise-block", blocks).forEach((block, b) => {
                $(".block-no", block).textContent = b + 1;
                $$("tr.set-row", block).forEach((row, s) => { $(".set-no", row).textContent = s + 1; });
            });
            $$("tr.set-row", blocks).forEach((row, i) => {
                const block = row.closest(".exercise-block");
                $$("[data-field]", row).forEach((f) => { f.name = `sets[${i}].${f.dataset.field}`; });
                const ex = $("[data-block-exercise]", block).value;
                $("[data-hidden=exerciseId]", row).value = ex;
                $("[data-hidden=block]", row).value = block.dataset.block;
                $("[data-hidden=exerciseId]", row).name = `sets[${i}].exerciseId`;
                $("[data-hidden=block]", row).name = `sets[${i}].block`;
            });
            $$(".cardio-row", cardioList).forEach((row, i) => {
                $$("[data-field]", row).forEach((f) => { f.name = `cardio[${i}].${f.dataset.field}`; });
            });
            const count = $$("tr.set-row", blocks).filter((r) => $("[data-field=reps]", r).value || $("[data-field=weightLb]", r).value).length;
            const summary = $("#builder-summary");
            if (summary) summary.textContent = count + (count === 1 ? " set" : " sets");
        };

        const showLast = (block) => {
            const id = $("[data-block-exercise]", block).value;
            const out = $(".last-time", block);
            out.replaceChildren();
            if (id && lastTimes[id]) {
                const strong = document.createElement("strong");
                strong.textContent = "Last time: ";
                out.append(strong, document.createTextNode(lastTimes[id]));
            }
        };

        const addSet = (block, copyFrom) => {
            const row = $("#tpl-set").content.firstElementChild.cloneNode(true);
            const source = copyFrom || $$("tr.set-row", block).pop();
            if (source) {
                ["reps", "weightLb", "rpe"].forEach((f) => { $(`[data-field=${f}]`, row).value = $(`[data-field=${f}]`, source).value; });
            }
            $("tbody", block).appendChild(row);
            renumber();
            $("[data-field=reps]", row).focus();
        };

        const addBlock = (exerciseId) => {
            const block = $("#tpl-block").content.firstElementChild.cloneNode(true);
            block.dataset.block = String(blockSeq++);
            blocks.appendChild(block);
            if (exerciseId) $("[data-block-exercise]", block).value = exerciseId;
            addSet(block);
            showLast(block);
            $("[data-block-exercise]", block).focus();
        };

        builder.addEventListener("click", (event) => {
            const t = event.target.closest("[data-action]");
            if (!t) return;
            const block = t.closest(".exercise-block");
            switch (t.dataset.action) {
                case "add-block": addBlock(); break;
                case "add-set": addSet(block); break;
                case "remove-set": {
                    const rows = $$("tr.set-row", block);
                    if (rows.length > 1) t.closest("tr").remove(); else $$("input", t.closest("tr")).forEach((i) => { if (i.type === "checkbox") i.checked = false; else if (i.type !== "hidden") i.value = ""; });
                    break;
                }
                case "remove-block": block.remove(); break;
                case "add-cardio": {
                    const row = $("#tpl-cardio").content.firstElementChild.cloneNode(true);
                    cardioList.appendChild(row);
                    $("select", row).focus();
                    break;
                }
                case "remove-cardio": t.closest(".cardio-row").remove(); break;
                default: return;
            }
            event.preventDefault();
            renumber();
        });
        builder.addEventListener("change", (event) => {
            if (event.target.matches("[data-block-exercise]")) {
                showLast(event.target.closest(".exercise-block"));
            }
            renumber();
        });
        builder.addEventListener("input", renumber);
        // Enter in the last set of a block adds another set instead of submitting.
        builder.addEventListener("keydown", (event) => {
            if (event.key !== "Enter" || !event.target.matches("tr.set-row input")) return;
            const block = event.target.closest(".exercise-block");
            const rows = $$("tr.set-row", block);
            if (event.target.closest("tr") === rows[rows.length - 1]) {
                event.preventDefault();
                addSet(block);
            }
        });

        $$(".exercise-block", blocks).forEach(showLast);
        if (!$$(".exercise-block", blocks).length && !$$(".cardio-row", cardioList).length) addBlock();
        renumber();
    }
})();
