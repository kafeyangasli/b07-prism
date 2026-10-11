// Run before styles load so a saved dark theme is applied before first paint.
(function () {
  "use strict";
  const preference = window.matchMedia("(prefers-color-scheme: dark)");
  const cookieName = "prism_preferences";

  // Preferences are comma-separated key:value fields, e.g. theme:dark,language:en.
  function readPreferences() {
    try {
      const cookie = document.cookie.split(";").map(function (item) { return item.trim(); })
        .find(function (item) { return item.startsWith(cookieName + "="); });
      return cookie ? decodeURIComponent(cookie.slice(cookieName.length + 1)) : "";
    } catch (_) { return ""; /* Cookies may be unavailable or malformed. */ }
  }

  function readPreference() {
    const preferences = readPreferences();
    if (valid(preferences)) return preferences; // Support the previous cookie format.
    const field = preferences.split(",").find(function (item) { return item.startsWith("theme:"); });
    const theme = field ? field.slice("theme:".length) : null;
    return valid(theme) ? theme : null;
  }

  function savePreference(theme) {
    try {
      const fields = readPreferences().split(",").filter(function (item) {
        return item && !valid(item) && !item.startsWith("theme:");
      });
      fields.push("theme:" + theme);
      const value = encodeURIComponent(fields.join(",")).replace(/%3A/g, ":").replace(/%2C/g, ",");
      document.cookie = cookieName + "=" + value
        + "; Path=/; Max-Age=31536000; SameSite=Lax"
        + (window.location.protocol === "https:" ? "; Secure" : "");
      return readPreference() === theme;
    } catch (_) { return false; /* Keep this page usable if cookies are unavailable. */ }
  }

  let selected = readPreference();
  if (valid(readPreferences())) savePreference(selected);
  try {
    const previous = localStorage.getItem("prism-theme");
    if (!selected && valid(previous)) {
      selected = previous;
      savePreference(selected);
    }
    if (readPreference()) localStorage.removeItem("prism-theme");
  } catch (_) { /* Legacy storage may be unavailable. */ }

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
    savePreference(selected);
    apply(selected);
  });
  preference.addEventListener("change", function () {
    if (!selected) apply(preference.matches ? "dark" : "light");
  });
  window.addEventListener("focus", function () {
    selected = readPreference();
    apply(selected || (preference.matches ? "dark" : "light"));
  });
})();
