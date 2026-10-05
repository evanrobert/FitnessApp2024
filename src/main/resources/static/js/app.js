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

    // ---- Dark / light theme ------------------------------------------------------
    function themeLabel() {
        const light = document.documentElement.getAttribute("data-theme") === "light";
        $$("[data-theme-toggle] [data-theme-label]").forEach((n) => { n.textContent = light ? "Dark mode" : "Light mode"; });
        const meta = $('meta[name="theme-color"]');
        if (meta) meta.setAttribute("content", light ? "#f3f5f8" : "#0a0c0f");
    }
    themeLabel();
    document.addEventListener("click", (event) => {
        if (!event.target.closest("[data-theme-toggle]")) return;
        const light = document.documentElement.getAttribute("data-theme") !== "light";
        if (light) document.documentElement.setAttribute("data-theme", "light");
        else document.documentElement.removeAttribute("data-theme");
        try { localStorage.setItem("theme", light ? "light" : "dark"); } catch (e) { /* not saved, still switches */ }
        themeLabel();
        if (window.LedgerCharts) window.LedgerCharts.renderAll();
    });

    // ---- Flash dismiss, confirmations, submit feedback ------------------------
    document.addEventListener("click", (event) => {
        const back = event.target.closest("[data-back]");
        if (back && window.history.length > 1) {
            event.preventDefault();
            window.history.back();
            return;
        }
        const dismiss = event.target.closest("[data-dismiss]");
        if (dismiss) dismiss.closest(".flash")?.remove();

        const trigger = event.target.closest("[data-confirm]");
        if (trigger && !window.confirm(trigger.dataset.confirm)) {
            event.preventDefault();
            event.stopImmediatePropagation();
        }
    }, true);

    document.addEventListener("submit", (event) => {
        if (event.defaultPrevented) return; // a check stopped the submit: don't leave the button spinning
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
    // every change so Spring binds sets[i] / cardio[i] in order. Each block names
    // its exercise in a searchable text box: pick from the list or type anything.
    const builder = $("#session-builder");
    if (builder) {
        const blocks = $("#blocks", builder);
        const cardioList = $("#cardio-rows", builder);
        const lastTimes = JSON.parse($("#last-times")?.textContent || "{}");
        const known = new Map($$("#exercise-options option").map((o) => [o.value.trim().toLowerCase(), o.dataset.group]));
        let blockSeq = $$(".exercise-block", blocks).length;

        const nameOf = (block) => $("[data-block-exercise]", block).value.trim().replace(/\s+/g, " ");

        const renumber = () => {
            $$(".exercise-block", blocks).forEach((block, b) => {
                $(".block-no", block).textContent = b + 1;
                $$("tr.set-row", block).forEach((row, s) => { $(".set-no", row).textContent = s + 1; });
            });
            $$("tr.set-row", blocks).forEach((row, i) => {
                const block = row.closest(".exercise-block");
                $$("[data-field]", row).forEach((f) => { f.name = `sets[${i}].${f.dataset.field}`; });
                const isNew = !known.has(nameOf(block).toLowerCase());
                const fields = { exerciseName: nameOf(block), muscleGroup: isNew ? $("[data-block-muscle]", block).value : "", block: block.dataset.block };
                Object.entries(fields).forEach(([key, value]) => {
                    const input = $(`[data-hidden=${key}]`, row);
                    input.value = value;
                    input.name = `sets[${i}].${key}`;
                });
            });
            $$(".cardio-row", cardioList).forEach((row, i) => {
                $$("[data-field]", row).forEach((f) => { f.name = `cardio[${i}].${f.dataset.field}`; });
            });
            const count = $$("tr.set-row", blocks).filter((r) => $("[data-field=reps]", r).value || $("[data-field=weightLb]", r).value).length;
            const summary = $("#builder-summary");
            if (summary) summary.textContent = count + (count === 1 ? " set" : " sets");
        };

        // "Last time" hint for known exercises; muscle-group picker for new names.
        const describe = (block) => {
            const name = nameOf(block).toLowerCase();
            const out = $(".last-time", block);
            out.replaceChildren();
            if (name && lastTimes[name]) {
                const strong = document.createElement("strong");
                strong.textContent = "Last time: ";
                out.append(strong, document.createTextNode(lastTimes[name]));
            }
            $(".new-exercise", block).hidden = !name || known.has(name);
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

        const addBlock = () => {
            const block = $("#tpl-block").content.firstElementChild.cloneNode(true);
            block.dataset.block = String(blockSeq++);
            blocks.appendChild(block);
            addSet(block);
            describe(block);
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
                case "toggle-set-details": {
                    const on = builder.classList.toggle("show-set-details");
                    t.textContent = on ? "Hide effort and warm-up" : "Add effort or mark warm-ups (optional)";
                    break;
                }
                default: return;
            }
            event.preventDefault();
            renumber();
        });
        builder.addEventListener("input", (event) => {
            if (event.target.matches("[data-block-exercise]")) describe(event.target.closest(".exercise-block"));
            renumber();
        });
        builder.addEventListener("change", renumber);
        builder.addEventListener("keydown", (event) => {
            // Enter in the exercise box moves to the first set instead of submitting.
            if (event.key === "Enter" && event.target.matches("[data-block-exercise]")) {
                event.preventDefault();
                $("tr.set-row [data-field=weightLb]", event.target.closest(".exercise-block"))?.focus();
                return;
            }
            // Enter in the last set of a block adds another set instead of submitting.
            if (event.key !== "Enter" || !event.target.matches("tr.set-row input")) return;
            const block = event.target.closest(".exercise-block");
            const rows = $$("tr.set-row", block);
            if (event.target.closest("tr") === rows[rows.length - 1]) {
                event.preventDefault();
                addSet(block);
            }
        });
        // A set without an exercise would be silently dropped: say so before submitting.
        builder.addEventListener("submit", (event) => {
            const unnamed = $$(".exercise-block", blocks).find((block) => !nameOf(block)
                && $$("tr.set-row", block).some((r) => $("[data-field=reps]", r).value || $("[data-field=weightLb]", r).value));
            if (unnamed) {
                event.preventDefault();
                $("[data-block-exercise]", unnamed).focus();
                $("[data-block-exercise]", unnamed).setCustomValidity("Name the exercise for these sets");
                $("[data-block-exercise]", unnamed).reportValidity();
                setTimeout(() => $$("button.is-loading", builder).forEach((b) => b.classList.remove("is-loading")), 0);
            }
        }, true);
        // Out-of-range numbers (e.g. 75 for rate of perceived exertion) would come back as a server error: point at the field now.
        const rangeHint = { rpe: "Rate of perceived exertion is 1–10 (10 = nothing left)", avgHr: "Heart rate is 30–230" };
        builder.addEventListener("submit", (event) => {
            if (event.defaultPrevented) return;
            const bad = $$("input[data-field][type=number]", builder)
                .find((i) => i.validity.rangeOverflow || i.validity.rangeUnderflow || i.validity.badInput);
            if (!bad) return;
            event.preventDefault();
            if (bad.closest(".detail-col")) builder.classList.add("show-set-details");
            bad.classList.add("is-invalid");
            bad.setCustomValidity(rangeHint[bad.dataset.field] || `Use a value between ${bad.min || "0"} and ${bad.max || "any"}`);
            bad.reportValidity();
            setTimeout(() => $$("button.is-loading", builder).forEach((b) => b.classList.remove("is-loading")), 0);
        }, true);
        builder.addEventListener("input", (event) => {
            if (event.target.matches("[data-block-exercise]")) event.target.setCustomValidity("");
            if (event.target.matches("input[data-field]")) {
                event.target.setCustomValidity("");
                event.target.classList.remove("is-invalid");
                event.target.removeAttribute("title");
            }
        });

        $$(".exercise-block", blocks).forEach(describe);
        if (!$$(".exercise-block", blocks).length && !$$(".cardio-row", cardioList).length) addBlock();
        renumber();
    }
})();
