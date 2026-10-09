/*
 * Service worker: makes the app installable and gives a friendly page when offline.
 * Personal pages are never cached on the device; only the shared look (CSS, JS, fonts, icons).
 */
const CACHE = "evan-fitness-v1";
const SHELL = ["/offline.html", "/css/app.css", "/js/app.js", "/js/theme.js", "/fonts/archivo-variable.woff2", "/icons/icon-192.png", "/favicon.svg"];

self.addEventListener("install", (event) => {
    event.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (event) => {
    event.waitUntil(caches.keys()
        .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
        .then(() => self.clients.claim()));
});

self.addEventListener("fetch", (event) => {
    const req = event.request;
    const url = new URL(req.url);
    if (req.method !== "GET" || url.origin !== self.location.origin) return;

    // Pages: always the network (fresh, private); the offline page only when there is no connection.
    if (req.mode === "navigate") {
        event.respondWith(fetch(req).catch(() => caches.match("/offline.html")));
        return;
    }
    // Shared assets: network first so deploys show up immediately, cache as the fallback.
    if (/^\/(css|js|fonts|icons)\//.test(url.pathname) || url.pathname === "/favicon.svg") {
        event.respondWith(fetch(req).then((res) => {
            if (res.ok) {
                const copy = res.clone();
                caches.open(CACHE).then((c) => c.put(req, copy));
            }
            return res;
        }).catch(() => caches.match(req)));
    }
});
