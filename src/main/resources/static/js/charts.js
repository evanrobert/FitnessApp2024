/*
 * Ledger charts: small dependency-free SVG charts.
 *
 * Markup:  <div class="chart" data-chart="chart-id"></div>
 *          <script type="application/json" id="chart-id">{ ...spec... }</script>
 *
 * Spec types
 *   line     { labels[], series:[{name, values[], color, style:"line"|"dots"}], target:{value,label}, unit, decimals, height }
 *   bar      { labels[], values[], unit, target:{value,label}, decimals, height, highlight:index }
 *   calendar { start:"YYYY-MM-DD", values[], unit, max }
 *   spark    { values[], color }
 *
 * Every chart gets a hover/focus tooltip and a "View as table" twin, so no value
 * is reachable only by hovering or only through color.
 */
(function () {
    "use strict";

    const NS = "http://www.w3.org/2000/svg";
    const COLORS = { s1: "#1e90ff", s2: "#b8792a", s3: "#4a3aa7", ink: "#0b0b0b" };
    const SEQ = ["#f0e7d8", "#b7d3f6", "#6da7ec", "#2a78d6", "#184f95"]; // sequential blue, 0 -> most
    const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

    function el(name, attrs, parent) {
        const node = document.createElementNS(NS, name);
        Object.entries(attrs || {}).forEach(([k, v]) => node.setAttribute(k, v));
        if (parent) parent.appendChild(node);
        return node;
    }

    function color(key) {
        return COLORS[key] || key || COLORS.s1;
    }

    function fmt(value, decimals) {
        if (value === null || value === undefined || Number.isNaN(value)) return "—";
        const d = decimals === undefined ? (Math.abs(value) < 100 && value % 1 !== 0 ? 1 : 0) : decimals;
        return Number(value).toLocaleString("en-US", { maximumFractionDigits: d, minimumFractionDigits: 0 });
    }

    function parseDate(iso) {
        const [y, m, d] = iso.split("-").map(Number);
        return new Date(y, m - 1, d);
    }

    function shortDate(label) {
        if (!/^\d{4}-\d{2}-\d{2}$/.test(label)) return label;
        const d = parseDate(label);
        return MONTHS[d.getMonth()] + " " + d.getDate();
    }

    /** Round axis maximum/minimum to clean numbers (0 / 50 / 100...). */
    function niceScale(min, max, ticks) {
        if (min === max) { min = min - 1; max = max + 1; }
        const span = max - min;
        const raw = span / ticks;
        const mag = Math.pow(10, Math.floor(Math.log10(raw)));
        const step = [1, 2, 2.5, 5, 10].map((m) => m * mag).find((s) => s >= raw) || raw;
        return { min: Math.floor(min / step) * step, max: Math.ceil(max / step) * step, step };
    }

    function tooltip(container) {
        const tip = document.createElement("div");
        tip.className = "chart-tooltip";
        tip.setAttribute("role", "status");
        container.appendChild(tip);
        return {
            show(x, y, title, rows) {
                tip.replaceChildren();
                const head = document.createElement("div");
                head.className = "tt-date";
                head.textContent = title;
                tip.appendChild(head);
                rows.forEach((r) => {
                    const row = document.createElement("div");
                    row.className = "tt-row";
                    const key = document.createElement("span");
                    key.className = "tt-key";
                    key.style.background = r.color;
                    const val = document.createElement("span");
                    val.className = "tt-val";
                    val.textContent = r.value;
                    const name = document.createElement("span");
                    name.className = "tt-name";
                    name.textContent = r.name;
                    row.append(key, val, name);
                    tip.appendChild(row);
                });
                const box = container.getBoundingClientRect();
                const w = tip.offsetWidth || 140;
                let left = x + 14;
                if (left + w > box.width) left = x - w - 14;
                tip.style.left = Math.max(0, left) + "px";
                tip.style.top = Math.max(0, y - 20) + "px";
                tip.classList.add("is-visible");
            },
            hide() { tip.classList.remove("is-visible"); }
        };
    }

    function tableTwin(container, header, rows) {
        const foot = document.createElement("div");
        foot.className = "chart-foot";
        const toggle = document.createElement("button");
        toggle.type = "button";
        toggle.className = "btn btn-ghost btn-xs";
        toggle.textContent = "View as table";
        toggle.setAttribute("aria-expanded", "false");
        foot.appendChild(toggle);
        const wrap = document.createElement("div");
        wrap.className = "chart-table";
        wrap.hidden = true;
        const table = document.createElement("table");
        table.className = "table table-compact";
        const thead = table.createTHead().insertRow();
        header.forEach((h, i) => {
            const th = document.createElement("th");
            th.textContent = h;
            if (i > 0) th.className = "num";
            thead.appendChild(th);
        });
        const body = table.createTBody();
        rows.forEach((r) => {
            const tr = body.insertRow();
            r.forEach((cell, i) => {
                const td = tr.insertCell();
                td.textContent = cell;
                if (i > 0) td.className = "num";
            });
        });
        wrap.appendChild(table);
        toggle.addEventListener("click", () => {
            wrap.hidden = !wrap.hidden;
            toggle.setAttribute("aria-expanded", String(!wrap.hidden));
            toggle.textContent = wrap.hidden ? "View as table" : "Hide table";
        });
        container.after(foot, wrap);
    }

    function legend(container, items) {
        const box = document.createElement("div");
        box.className = "chart-legend";
        items.forEach((it) => {
            const span = document.createElement("span");
            const key = document.createElement("i");
            if (it.kind) key.className = it.kind;
            key.style.background = it.color;
            span.append(key, document.createTextNode(it.name));
            box.appendChild(span);
        });
        container.before(box);
    }

    function empty(container, message) {
        const box = document.createElement("div");
        box.className = "chart-empty";
        box.textContent = message || "Not enough data yet.";
        container.replaceChildren(box);
    }

    // ---- Line ------------------------------------------------------------

    function line(container, spec) {
        const labels = spec.labels || [];
        const series = spec.series || [];
        const all = series.flatMap((s) => s.values).filter((v) => v !== null && v !== undefined);
        if (labels.length < 2 || all.length < 2) return empty(container, spec.empty);

        const W = Math.max(container.clientWidth, 280), H = spec.height || 220;
        const pad = { t: 14, r: 56, b: 26, l: 44 };
        const values = spec.target ? all.concat([spec.target.value]) : all;
        const lo = Math.min(...values), hi = Math.max(...values);
        const headroom = (hi - lo) * 0.12 || 1;
        const scale = niceScale(spec.zero ? 0 : lo - headroom, hi + headroom, 4);
        const x = (i) => pad.l + (i / (labels.length - 1)) * (W - pad.l - pad.r);
        const y = (v) => pad.t + (1 - (v - scale.min) / (scale.max - scale.min)) * (H - pad.t - pad.b);

        const svg = el("svg", { viewBox: `0 0 ${W} ${H}`, role: "img", "aria-label": spec.title || "Trend chart" });
        for (let v = scale.min; v <= scale.max + 1e-9; v += scale.step) {
            el("line", { class: "grid-line", x1: pad.l, x2: W - pad.r, y1: y(v), y2: y(v) }, svg);
            el("text", { class: "tick", x: pad.l - 8, y: y(v) + 4, "text-anchor": "end" }, svg).textContent = fmt(v, 0);
        }
        const every = Math.max(1, Math.ceil(labels.length / Math.max(2, Math.floor((W - pad.l - pad.r) / 70))));
        labels.forEach((l, i) => {
            if (i % every === 0 || i === labels.length - 1) {
                el("text", { class: "tick", x: x(i), y: H - 6, "text-anchor": i === 0 ? "start" : i === labels.length - 1 ? "end" : "middle" }, svg).textContent = shortDate(l);
            }
        });
        if (spec.target) {
            el("line", { class: "target-line", x1: pad.l, x2: W - pad.r, y1: y(spec.target.value), y2: y(spec.target.value) }, svg);
            el("text", { class: "target-label", x: W - pad.r + 6, y: y(spec.target.value) + 4 }, svg).textContent = spec.target.label || "Target";
        }

        series.forEach((s) => {
            const c = color(s.color);
            if (s.style === "dots") {
                s.values.forEach((v, i) => {
                    if (v === null || v === undefined) return;
                    el("circle", { cx: x(i), cy: y(v), r: 3.5, fill: c, "fill-opacity": 0.35 }, svg);
                });
                return;
            }
            let d = "", started = false;
            s.values.forEach((v, i) => {
                if (v === null || v === undefined) { started = false; return; }
                d += (started ? "L" : "M") + x(i).toFixed(1) + " " + y(v).toFixed(1);
                started = true;
            });
            if (s.area) {
                const idx = s.values.map((v, i) => (v === null || v === undefined ? null : i)).filter((i) => i !== null);
                const area = d + `L${x(idx[idx.length - 1])} ${y(scale.min)}L${x(idx[0])} ${y(scale.min)}Z`;
                el("path", { d: area, fill: c, "fill-opacity": 0.1 }, svg);
            }
            el("path", { d, fill: "none", stroke: c, "stroke-width": 2, "stroke-linejoin": "round", "stroke-linecap": "round" }, svg);
            const lastIdx = s.values.map((v, i) => (v === null || v === undefined ? -1 : i)).reduce((a, b) => Math.max(a, b), -1);
            if (lastIdx >= 0 && !s.hideEnd) {
                el("circle", { cx: x(lastIdx), cy: y(s.values[lastIdx]), r: 4.5, fill: c, stroke: "#fff", "stroke-width": 2 }, svg);
                if (series.filter((q) => q.style !== "dots").length === 1) {
                    el("text", { class: "end-label", x: x(lastIdx) + 8, y: y(s.values[lastIdx]) + 4 }, svg).textContent = fmt(s.values[lastIdx], spec.decimals);
                }
            }
        });

        const cross = el("line", { class: "crosshair", y1: pad.t, y2: H - pad.b, x1: 0, x2: 0 }, svg);
        const hit = el("rect", { x: pad.l, y: pad.t, width: W - pad.l - pad.r, height: H - pad.t - pad.b, fill: "transparent", tabindex: 0 }, svg);
        container.replaceChildren(svg);
        const tip = tooltip(container);
        const unit = spec.unit ? " " + spec.unit : "";
        const showAt = (i) => {
            const cx = x(i);
            cross.setAttribute("x1", cx);
            cross.setAttribute("x2", cx);
            container.classList.add("is-hovering");
            const rows = series.map((s) => ({ color: color(s.color), value: fmt(s.values[i], spec.decimals) + unit, name: s.name }))
                .filter((r, k) => series[k].values[i] !== null && series[k].values[i] !== undefined);
            if (spec.target) rows.push({ color: COLORS.ink, value: fmt(spec.target.value, spec.decimals) + unit, name: spec.target.label || "Target" });
            const scaleX = container.clientWidth / W;
            tip.show(cx * scaleX, pad.t, shortDate(labels[i]), rows);
        };
        let focusIndex = labels.length - 1;
        hit.addEventListener("pointermove", (e) => {
            const box = svg.getBoundingClientRect();
            const px = (e.clientX - box.left) * (W / box.width);
            focusIndex = Math.round(((px - pad.l) / (W - pad.l - pad.r)) * (labels.length - 1));
            focusIndex = Math.max(0, Math.min(labels.length - 1, focusIndex));
            showAt(focusIndex);
        });
        hit.addEventListener("focus", () => showAt(focusIndex));
        hit.addEventListener("keydown", (e) => {
            if (e.key === "ArrowLeft") { focusIndex = Math.max(0, focusIndex - 1); showAt(focusIndex); e.preventDefault(); }
            if (e.key === "ArrowRight") { focusIndex = Math.min(labels.length - 1, focusIndex + 1); showAt(focusIndex); e.preventDefault(); }
        });
        const leave = () => { container.classList.remove("is-hovering"); tip.hide(); };
        hit.addEventListener("pointerleave", leave);
        hit.addEventListener("blur", leave);

        if (series.length > 1 || spec.target) {
            legend(container, series.map((s) => ({ name: s.name, color: color(s.color), kind: s.style === "dots" ? "box" : "" }))
                .concat(spec.target ? [{ name: spec.target.label || "Target", color: COLORS.ink, kind: "dash" }] : []));
        }
        tableTwin(container, ["Date"].concat(series.map((s) => s.name + (spec.unit ? ` (${spec.unit})` : ""))),
            labels.map((l, i) => [shortDate(l)].concat(series.map((s) => fmt(s.values[i], spec.decimals)))).reverse());
    }

    // ---- Bar (columns) -----------------------------------------------------

    function bar(container, spec) {
        const labels = spec.labels || [], values = spec.values || [];
        if (!values.some((v) => v)) return empty(container, spec.empty);
        const W = Math.max(container.clientWidth, 280), H = spec.height || 200;
        const pad = { t: 18, r: spec.target ? 56 : 12, b: 26, l: 44 };
        const hi = Math.max(...values, spec.target ? spec.target.value : 0);
        const scale = niceScale(0, hi * 1.08, 4);
        const band = (W - pad.l - pad.r) / values.length;
        const bw = Math.min(24, band * 0.62);
        const y = (v) => pad.t + (1 - v / scale.max) * (H - pad.t - pad.b);
        const svg = el("svg", { viewBox: `0 0 ${W} ${H}`, role: "img", "aria-label": spec.title || "Bar chart" });
        for (let v = 0; v <= scale.max + 1e-9; v += scale.step) {
            el("line", { class: v === 0 ? "axis-line" : "grid-line", x1: pad.l, x2: W - pad.r, y1: y(v), y2: y(v) }, svg);
            el("text", { class: "tick", x: pad.l - 8, y: y(v) + 4, "text-anchor": "end" }, svg).textContent = fmt(v, 0);
        }
        const every = Math.max(1, Math.ceil(values.length / Math.max(2, Math.floor((W - pad.l - pad.r) / 48))));
        const tip = tooltip(container);
        const unit = spec.unit ? " " + spec.unit : "";
        values.forEach((v, i) => {
            const cx = pad.l + band * i + band / 2;
            if (i % every === 0 || i === values.length - 1) {
                el("text", { class: "tick", x: cx, y: H - 6, "text-anchor": "middle" }, svg).textContent = shortDate(labels[i]);
            }
            const top = y(v || 0), base = y(0), h = Math.max(0, base - top);
            const r = Math.min(4, h, bw / 2);
            const x0 = cx - bw / 2;
            const d = h <= 0 ? "" : `M${x0} ${base}V${top + r}Q${x0} ${top} ${x0 + r} ${top}H${x0 + bw - r}Q${x0 + bw} ${top} ${x0 + bw} ${top + r}V${base}Z`;
            const fill = spec.highlight === undefined || spec.highlight === i ? color(spec.color) : "#b7d3f6";
            if (d) el("path", { d, fill, class: "bar" }, svg);
            const hit = el("rect", { x: pad.l + band * i, y: pad.t, width: band, height: H - pad.t - pad.b, fill: "transparent", tabindex: 0, "aria-label": `${shortDate(labels[i])}: ${fmt(v, spec.decimals)}${unit}` }, svg);
            const show = () => tip.show(cx * (container.clientWidth / W), top, shortDate(labels[i]), [{ color: fill, value: fmt(v, spec.decimals) + unit, name: spec.name || "" }]);
            hit.addEventListener("pointerenter", show);
            hit.addEventListener("focus", show);
            hit.addEventListener("pointerleave", tip.hide);
            hit.addEventListener("blur", tip.hide);
        });
        if (spec.target) {
            el("line", { class: "target-line", x1: pad.l, x2: W - pad.r, y1: y(spec.target.value), y2: y(spec.target.value) }, svg);
            el("text", { class: "target-label", x: W - pad.r + 6, y: y(spec.target.value) + 4 }, svg).textContent = spec.target.label || "Target";
        }
        container.prepend(svg);
        if (spec.target) legend(container, [{ name: spec.name || "Actual", color: color(spec.color), kind: "box" }, { name: spec.target.label || "Target", color: COLORS.ink, kind: "dash" }]);
        tableTwin(container, ["Period", (spec.name || "Value") + (spec.unit ? ` (${spec.unit})` : "")],
            labels.map((l, i) => [shortDate(l), fmt(values[i], spec.decimals)]).reverse());
    }

    // ---- Calendar heatmap (training frequency) -----------------------------

    function calendar(container, spec) {
        const values = spec.values || [];
        if (!values.length) return empty(container, spec.empty);
        const start = parseDate(spec.start);
        const offset = (start.getDay() + 6) % 7; // weeks start Monday
        const weeks = Math.ceil((values.length + offset) / 7);
        const cell = 13, gap = 3, left = 28, top = 18;
        const W = left + weeks * (cell + gap), H = top + 7 * (cell + gap);
        const max = spec.max || Math.max(1, ...values);
        const svg = el("svg", { viewBox: `0 0 ${W} ${H}`, role: "img", "aria-label": spec.title || "Activity calendar", style: `max-width:${W * 1.6}px` });
        ["Mon", "", "Wed", "", "Fri", "", ""].forEach((d, i) => {
            if (d) el("text", { class: "tick", x: 0, y: top + i * (cell + gap) + 10 }, svg).textContent = d;
        });
        const tip = tooltip(container);
        let lastMonth = -1;
        values.forEach((v, i) => {
            const pos = i + offset, w = Math.floor(pos / 7), d = pos % 7;
            const date = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i);
            if (date.getDate() <= 7 && d === 0 && date.getMonth() !== lastMonth) {
                lastMonth = date.getMonth();
                el("text", { class: "tick", x: left + w * (cell + gap), y: 11 }, svg).textContent = MONTHS[lastMonth];
            }
            const level = v <= 0 ? 0 : Math.min(SEQ.length - 1, Math.ceil((v / max) * (SEQ.length - 1)));
            const rect = el("rect", { class: "cell", x: left + w * (cell + gap), y: top + d * (cell + gap), width: cell, height: cell, rx: 2, fill: SEQ[level], tabindex: 0 }, svg);
            const label = `${MONTHS[date.getMonth()]} ${date.getDate()}`;
            const text = v > 0 ? `${fmt(v)} ${spec.unit || ""}`.trim() : "Rest day";
            rect.setAttribute("aria-label", `${label}: ${text}`);
            const show = () => tip.show((left + w * (cell + gap)) * (container.clientWidth / W), top + d * (cell + gap), label, [{ color: SEQ[level], value: text, name: "" }]);
            rect.addEventListener("pointerenter", show);
            rect.addEventListener("focus", show);
            rect.addEventListener("pointerleave", tip.hide);
            rect.addEventListener("blur", tip.hide);
        });
        container.prepend(svg);
        const key = document.createElement("div");
        key.className = "heatmap-legend";
        key.append(document.createTextNode("Less"));
        SEQ.forEach((c) => { const i = document.createElement("i"); i.style.background = c; key.appendChild(i); });
        key.append(document.createTextNode("More"));
        container.appendChild(key);
    }

    // ---- Sparkline (inline, e.g. on the black scoreboard) ------------------

    function spark(container, spec) {
        const values = (spec.values || []).filter((v) => v !== null && v !== undefined);
        if (values.length < 2) return;
        const W = 160, H = 32;
        const lo = Math.min(...values), hi = Math.max(...values), span = hi - lo || 1;
        const x = (i) => 2 + (i / (values.length - 1)) * (W - 8);
        const y = (v) => 3 + (1 - (v - lo) / span) * (H - 8);
        const svg = el("svg", { viewBox: `0 0 ${W} ${H}`, "aria-hidden": "true", preserveAspectRatio: "none", style: "height:32px" });
        el("path", { d: values.map((v, i) => (i ? "L" : "M") + x(i).toFixed(1) + " " + y(v).toFixed(1)).join(""), fill: "none", stroke: color(spec.color), "stroke-width": 2, "stroke-linecap": "round", "stroke-linejoin": "round", "vector-effect": "non-scaling-stroke" }, svg);
        el("circle", { cx: x(values.length - 1), cy: y(values[values.length - 1]), r: 3, fill: color(spec.color) }, svg);
        container.replaceChildren(svg);
    }

    const RENDERERS = { line, bar, calendar, spark };

    function render(container) {
        const source = document.getElementById(container.dataset.chart);
        if (!source) return;
        let spec;
        try { spec = JSON.parse(source.textContent); } catch (e) { return empty(container, "Chart unavailable."); }
        const draw = RENDERERS[spec.type];
        if (draw) draw(container, spec);
    }

    function renderAll() {
        document.querySelectorAll("[data-chart]").forEach(render);
    }

    document.addEventListener("DOMContentLoaded", renderAll);
    window.LedgerCharts = { render, renderAll };
})();
