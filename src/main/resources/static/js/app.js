(function () {
  "use strict";

  // Native details keeps navigation usable without JavaScript.
  const navigation = document.querySelector("[data-navigation]");
  const desktop = window.matchMedia("(min-width: 1024px)");
  function syncNavigation() { if (navigation) navigation.open = desktop.matches; }
  syncNavigation();
  desktop.addEventListener("change", syncNavigation);

  function csrfToken() {
    return document.querySelector('meta[name="_csrf"]')?.content;
  }

  function csrfHeader() {
    return document.querySelector('meta[name="_csrf_header"]')?.content;
  }

  function showHtmxError(message) {
    const alert = document.getElementById("htmx-error");
    if (!alert) return;

    const messageNode = alert.querySelector("[data-error-message]");
    if (messageNode) messageNode.textContent = message;
    alert.hidden = false;
    alert.focus();
  }

  document.addEventListener("click", function (event) {
    const openButton = event.target.closest("[data-open-dialog]");
    if (openButton) {
      const dialog = document.getElementById(openButton.dataset.openDialog);
      if (dialog && typeof dialog.showModal === "function") {
        dialog.showModal();
        dialog.querySelector("[autofocus], input, select, textarea, button")?.focus();
      }
      return;
    }

    const closeButton = event.target.closest("[data-close-dialog]");
    if (closeButton) {
      closeButton.closest("dialog")?.close();
      return;
    }

    const dismissButton = event.target.closest("[data-dismiss-alert]");
    if (dismissButton) {
      const alert = dismissButton.closest("[role='alert'], [role='status']");
      if (alert?.id === "htmx-error") alert.hidden = true;
      else alert?.remove();
    }
  });

  document.addEventListener("htmx:configRequest", function (event) {
    const token = csrfToken();
    const header = csrfHeader();
    if (token && header) event.detail.headers[header] = token;
  });

  document.addEventListener("htmx:beforeRequest", function () {
    const alert = document.getElementById("htmx-error");
    if (alert) alert.hidden = true;
  });

  document.addEventListener("htmx:responseError", function () {
    showHtmxError("Permintaan tidak dapat diproses. Periksa masukan Anda lalu coba lagi.");
  });

  document.addEventListener("htmx:sendError", function () {
    showHtmxError("Tidak dapat terhubung ke server. Periksa koneksi Anda lalu coba lagi.");
  });

  function promoteOpenDialogs(root) {
    root.querySelectorAll?.("dialog[open]").forEach(function (dialog) {
      if (typeof dialog.showModal !== "function") return;
      dialog.removeAttribute("open");
      dialog.showModal();
      dialog.querySelector("[autofocus], input, select, textarea, button")?.focus();
    });
  }

  promoteOpenDialogs(document);
  document.addEventListener("htmx:afterSwap", function (event) {
    promoteOpenDialogs(event.detail.target);
  });
})();
