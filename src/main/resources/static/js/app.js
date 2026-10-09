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

    // ---- Usage timing: how long the page was open when something was saved -------
    const pageOpened = Date.now();
    document.addEventListener("submit", (event) => {
        const form = event.target;
        if (event.defaultPrevented || (form.method || "").toLowerCase() !== "post") return;
        let field = form.querySelector("input[name=_elapsed]");
        if (!field) {
            field = document.createElement("input");
            field.type = "hidden";
            field.name = "_elapsed";
            form.appendChild(field);
        }
        field.value = String(Date.now() - pageOpened);
        const source = form.querySelector("input[name=_source]");
        if (source && form.querySelector("tr.set-row.is-done")) source.value = "live";
    });

    // ---- Installable app ---------------------------------------------------------
    if ("serviceWorker" in navigator) {
        window.addEventListener("load", () => navigator.serviceWorker.register("/sw.js").catch(() => {}));
    }
    (function installCard() {
        const card = $("[data-install]");
        const standalone = window.matchMedia("(display-mode: standalone)").matches || navigator.standalone;
        let dismissed = false;
        try { dismissed = localStorage.getItem("install-dismissed") === "1"; } catch (e) { /* ignore */ }
        if (!card || standalone || dismissed) return;
        const ios = /iphone|ipad|ipod/i.test(navigator.userAgent) && !/crios|fxios/i.test(navigator.userAgent);
        let deferred = null;
        if (ios) {
            $("[data-install-how]", card).textContent = "Tap the Share button, then \u201cAdd to Home Screen\u201d. It opens like an app.";
            card.hidden = false;
        }
        window.addEventListener("beforeinstallprompt", (event) => {
            event.preventDefault();
            deferred = event;
            $("[data-install-go]", card).hidden = false;
            card.hidden = false;
        });
        $("[data-install-go]", card).addEventListener("click", async () => {
            if (!deferred) return;
            deferred.prompt();
            await deferred.userChoice.catch(() => null);
            deferred = null;
            card.hidden = true;
        });
        $("[data-install-dismiss]", card).addEventListener("click", () => {
            card.hidden = true;
            try { localStorage.setItem("install-dismissed", "1"); } catch (e) { /* ignore */ }
        });
        window.addEventListener("appinstalled", () => { card.hidden = true; });
    })();

    // ---- One-tap food: chips add to whichever meal is picked on the form ----------
    document.addEventListener("change", (event) => {
        const select = event.target.closest("select#mealType");
        if (!select) return;
        $$(".quick-food input[name=type], .same-as-yesterday input[name=type]").forEach((i) => { i.value = select.value; });
        const label = select.options[select.selectedIndex].text.toLowerCase();
        $$(".quick-head .small").forEach((n) => { n.textContent = "Adds to " + label; });
        const say = $(".same-as-yesterday");
        if (say) say.hidden = true; // that suggestion was for the original meal
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
            const filledRows = $$("tr.set-row", blocks).filter((r) => $("[data-field=reps]", r).value || $("[data-field=weightLb]", r).value);
            const count = filledRows.length;
            const doneCount = filledRows.filter((r) => r.classList.contains("is-done")).length;
            const summary = $("#builder-summary");
            if (summary) summary.textContent = doneCount ? `${doneCount} of ${count} done` : count + (count === 1 ? " set" : " sets");
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
                case "done-set": {
                    const row = t.closest("tr");
                    const done = row.classList.toggle("is-done");
                    t.setAttribute("aria-pressed", String(done));
                    t.setAttribute("aria-label", done ? "Set done (tap to undo)" : "Mark this set done");
                    if (done) { rest.start(); live.begin(); } else { rest.stop(); }
                    break;
                }
                case "bump":
                    $$("[data-field=weightLb]", block).forEach((i) => { if (i.value) i.value = Math.round((Number(i.value) + 5) * 2) / 2; });
                    t.disabled = true;
                    t.textContent = "+5 lb added";
                    break;
                case "rest-add": rest.add(30); break;
                case "rest-skip": rest.stop(); break;
                case "save-cancel": choice.hidden = true; break;
                case "save-done":
                case "save-all": {
                    if (t.dataset.action === "save-done") {
                        $$("tr.set-row:not(.is-done)", blocks).forEach((r) => {
                            ["reps", "weightLb", "rpe"].forEach((f) => { $(`[data-field=${f}]`, r).value = ""; });
                        });
                    }
                    choice.hidden = true;
                    skipChoice = true;
                    renumber();
                    builder.requestSubmit();
                    return;
                }
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
        // ---- Live workout: tick sets, rest timer, screen stays on ---------------------
        const hasValues = (r) => $("[data-field=reps]", r).value || $("[data-field=weightLb]", r).value;
        const rest = (() => {
            const box = $("[data-rest]", builder);
            const out = $("[data-rest-time]", builder);
            let left = 0, timer = null;
            let base = 90;
            try { base = Number(localStorage.getItem("rest-seconds")) || 90; } catch (e) { /* default */ }
            const show = () => {
                out.textContent = left > 0 ? `${Math.floor(left / 60)}:${String(left % 60).padStart(2, "0")}` : "Go!";
                box.classList.toggle("is-done", left <= 0);
            };
            const stop = () => { clearInterval(timer); timer = null; if (box) box.hidden = true; };
            const tick = () => {
                left -= 1;
                show();
                if (left === 0) {
                    if (navigator.vibrate) navigator.vibrate([200, 100, 200]);
                    setTimeout(() => { if (left <= 0) stop(); }, 4000);
                }
            };
            return {
                start() {
                    if (!box) return;
                    clearInterval(timer);
                    left = base;
                    box.hidden = false;
                    show();
                    timer = setInterval(tick, 1000);
                },
                add(seconds) {
                    left = Math.max(left, 0) + seconds;
                    base = Math.min(600, base + seconds); // remember a longer rest for next time
                    try { localStorage.setItem("rest-seconds", String(base)); } catch (e) { /* ignore */ }
                    if (!timer) timer = setInterval(tick, 1000);
                    show();
                },
                stop,
            };
        })();
        // Keep the screen on and the sign-in alive while a workout is in progress.
        const live = (() => {
            let lock = null, ping = null;
            const wake = () => {
                if (!("wakeLock" in navigator) || document.visibilityState !== "visible") return;
                navigator.wakeLock.request("screen").then((l) => { lock = l; }).catch(() => {});
            };
            return {
                begin() {
                    if (ping) return;
                    wake();
                    document.addEventListener("visibilitychange", () => { if (ping && document.visibilityState === "visible") wake(); });
                    ping = setInterval(() => fetch("/ping", { credentials: "same-origin" }).catch(() => {}), 10 * 60 * 1000);
                },
                end() { if (lock) lock.release().catch(() => {}); clearInterval(ping); },
            };
        })();
        if (builder.classList.contains("is-new")) {
            // Any open workout form keeps the session alive, ticked or not.
            setInterval(() => fetch("/ping", { credentials: "same-origin" }).catch(() => {}), 10 * 60 * 1000);
        }
        // Some sets ticked, others not: ask which to save instead of guessing.
        const choice = $("[data-save-choice]", builder);
        let skipChoice = false;
        builder.addEventListener("submit", (event) => {
            if (event.defaultPrevented || skipChoice || !choice) return;
            const filled = $$("tr.set-row", blocks).filter(hasValues);
            const done = filled.filter((r) => r.classList.contains("is-done"));
            if (!done.length || done.length === filled.length) return;
            event.preventDefault();
            $("[data-save-choice-text]", choice).textContent =
                `You ticked ${done.length} of ${filled.length} sets. Save only the ${done.length} you did, or every set?`;
            $("[data-action=save-done]", choice).textContent = `Save the ${done.length} ticked`;
            $("[data-action=save-all]", choice).textContent = `Save all ${filled.length}`;
            choice.hidden = false;
            $("[data-action=save-done]", choice).focus();
            setTimeout(() => $$("button.is-loading", builder).forEach((b) => b.classList.remove("is-loading")), 0);
        }, true);
        builder.addEventListener("submit", (event) => { if (!event.defaultPrevented) { rest.stop(); live.end(); } });

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
