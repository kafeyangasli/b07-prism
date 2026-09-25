(function () {
  "use strict";

  let drawerTrigger = null;

  function setDrawer(open) {
    const shell = document.querySelector("[data-dashboard-shell]");
    const button = document.querySelector("[data-open-drawer]");
    const sidebar = document.getElementById("dashboard-sidebar");
    if (!shell || !sidebar) return;
    shell.classList.toggle("ui-drawer-open", open);
    button?.setAttribute("aria-expanded", String(open));
    sidebar.setAttribute("aria-hidden", String(!open && window.innerWidth < 1024));
    if (open) {
      drawerTrigger = document.activeElement;
      sidebar.querySelector("a, button")?.focus();
    } else if (drawerTrigger instanceof HTMLElement) {
      drawerTrigger.focus();
    }
  }

  function syncDrawerForViewport() {
    const sidebar = document.getElementById("dashboard-sidebar");
    const shell = document.querySelector("[data-dashboard-shell]");
    if (sidebar) sidebar.setAttribute("aria-hidden", String(
      window.innerWidth < 1024 && !shell?.classList.contains("ui-drawer-open")
    ));
  }

  syncDrawerForViewport();
  window.addEventListener("resize", syncDrawerForViewport);

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
    if (event.target.closest("[data-open-drawer]")) {
      setDrawer(true);
      return;
    }

    if (event.target.closest("[data-close-drawer]")) {
      setDrawer(false);
      return;
    }

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

    if (event.target instanceof HTMLDialogElement && event.target.hasAttribute("open")) {
      const bounds = event.target.getBoundingClientRect();
      const outside = event.clientX < bounds.left || event.clientX > bounds.right
        || event.clientY < bounds.top || event.clientY > bounds.bottom;
      if (outside) event.target.close();
    }
  });

  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape" && document.querySelector(".ui-drawer-open")) setDrawer(false);
    if (event.key === "Tab" && document.querySelector(".ui-drawer-open")) {
      const sidebar = document.getElementById("dashboard-sidebar");
      const focusable = Array.from(sidebar?.querySelectorAll(
        'a[href], button:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])'
      ) || []);
      if (!focusable.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
  });

  document.addEventListener("change", function (event) {
    const endInput = event.target.closest('input[name="endAt"][data-duration-minutes]');
    if (!endInput) return;
    const minutes = Number(endInput.dataset.durationMinutes || 0);
    const hours = Math.floor(minutes / 60);
    const remainder = minutes % 60;
    const durationText = hours > 0
      ? hours + " jam" + (remainder ? " " + remainder + " menit" : "")
      : remainder + " menit";
    const form = endInput.closest("form");
    const summary = form?.querySelector("[data-duration-summary]");
    const startInput = form?.querySelector('input[name="startAt"]:checked');
    if (summary) {
      summary.querySelector("[data-duration-text]").textContent = durationText;
      const startLabel = startInput?.closest("label")?.querySelector("strong")?.textContent?.trim();
      summary.querySelector("[data-duration-range]").textContent = startLabel + "–" + endInput.dataset.endLabel;
    }
    const proposal = form?.querySelector("[data-proposal-state]");
    const required = minutes >= 360;
    proposal?.classList.toggle("ui-proposal-required", required);
    if (proposal) {
      proposal.querySelector("[data-proposal-label]").textContent = required ? "Wajib" : "Opsional";
      proposal.querySelector("[data-proposal-help]").textContent = required
        ? "Wajib untuk reservasi berdurasi 6 jam atau lebih."
        : "Opsional untuk reservasi kurang dari 6 jam.";
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
    syncActiveNavigation();
    showHtmxError("Permintaan tidak dapat diproses. Periksa masukan Anda lalu coba lagi.");
  });

  document.addEventListener("htmx:sendError", function () {
    syncActiveNavigation();
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

  function syncActiveNavigation() {
    const content = document.getElementById("dashboard-content");
    const activeNav = content?.dataset.activeNav;
    if (!activeNav) return;
    applyActiveNavigation(activeNav);
  }

  function applyActiveNavigation(activeNav) {
    document.querySelectorAll("[data-dashboard-link]").forEach(function (link) {
      const active = link.dataset.navKey === activeNav
        || (activeNav === "minimal" && link.dataset.navKey === "overview");
      link.classList.toggle("ui-sidebar-link-active", active);
      if (active) link.setAttribute("aria-current", "page");
      else link.removeAttribute("aria-current");
    });
  }

  promoteOpenDialogs(document);
  document.addEventListener("htmx:afterSwap", function (event) {
    promoteOpenDialogs(event.detail.target);
  });

  document.addEventListener("htmx:afterSettle", function () {
    promoteOpenDialogs(document);
    syncActiveNavigation();
    if (!document.querySelector("dialog[open]")) {
      document.getElementById("dashboard-content")?.focus();
    }
  });

  document.addEventListener("htmx:historyRestore", syncActiveNavigation);

  document.addEventListener("htmx:afterRequest", function (event) {
    if (event.detail.successful && event.detail.elt?.matches?.("[data-dashboard-link]")) {
      setDrawer(false);
      document.getElementById("dashboard-content")?.focus();
    }
  });

  document.addEventListener("htmx:beforeRequest", function (event) {
    const link = event.detail.elt;
    if (link?.matches?.("[data-dashboard-link]") && link.dataset.navKey) {
      applyActiveNavigation(link.dataset.navKey);
    }
  });
})();
