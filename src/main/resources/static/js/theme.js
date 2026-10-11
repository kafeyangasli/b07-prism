// Run before styles load so a saved dark theme is applied before first paint.
(function () {
  "use strict";
  const preference = window.matchMedia("(prefers-color-scheme: dark)");
  let selected;
  try { selected = localStorage.getItem("prism-theme"); } catch (_) { /* Storage may be unavailable. */ }

  function apply(theme) {
    document.documentElement.dataset.theme = theme;
    document.documentElement.style.colorScheme = theme;
    document.querySelector('meta[name="theme-color"]')?.setAttribute("content", theme === "dark" ? "#211c2a" : "#5d5098");
    document.querySelectorAll("[data-theme-toggle]").forEach(function (button) {
      button.hidden = false;
      button.setAttribute("aria-pressed", String(theme === "dark"));
      button.setAttribute("aria-label", theme === "dark" ? "Tema gelap aktif. Gunakan tema terang" : "Tema terang aktif. Gunakan tema gelap");
      button.title = theme === "dark" ? "Gunakan tema terang" : "Gunakan tema gelap";
    });
  }

  function valid(theme) { return theme === "light" || theme === "dark"; }
  if (!valid(selected)) selected = null;
  apply(selected || (preference.matches ? "dark" : "light"));
  document.addEventListener("DOMContentLoaded", function () { apply(document.documentElement.dataset.theme); });
  document.addEventListener("click", function (event) {
    if (!event.target.closest("[data-theme-toggle]")) return;
    selected = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
    try { localStorage.setItem("prism-theme", selected); } catch (_) { /* Keep this page usable. */ }
    apply(selected);
  });
  preference.addEventListener("change", function () {
    if (!selected) apply(preference.matches ? "dark" : "light");
  });
  window.addEventListener("storage", function (event) {
    if (event.key !== "prism-theme" && event.key !== null) return;
    selected = valid(event.newValue) ? event.newValue : null;
    apply(selected || (preference.matches ? "dark" : "light"));
  });
})();
