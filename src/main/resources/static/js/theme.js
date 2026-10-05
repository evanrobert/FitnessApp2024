/* Applies the saved theme before the page paints (loaded without defer). Dark unless the member picked light. */
(function () {
    try {
        if (localStorage.getItem("theme") === "light") document.documentElement.setAttribute("data-theme", "light");
    } catch (e) { /* storage blocked: stay dark */ }
})();
