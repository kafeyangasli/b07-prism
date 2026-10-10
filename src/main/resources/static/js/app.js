(function () {
  "use strict";

  let drawerTrigger = null;
  const pendingPosts = new Map();
  const postRequests = new WeakMap();

  function unlockPost(owner) {
    const pending = pendingPosts.get(owner);
    if (!pending) return;
    pending.controls.forEach(function (state) {
      state.control.disabled = state.disabled;
      if (state.html != null) state.control.innerHTML = state.html;
      if (state.value != null) state.control.value = state.value;
      if (state.busy == null) state.control.removeAttribute("aria-busy");
      else state.control.setAttribute("aria-busy", state.busy);
      state.spinner?.remove();
    });
    pendingPosts.delete(owner);
  }

  function lockPost(owner, submitter, xhr, deferred) {
    if (pendingPosts.has(owner)) return false;
    const controls = owner instanceof HTMLFormElement
      ? Array.from(owner.elements).filter(function (control) {
        return (control instanceof HTMLButtonElement && control.type === "submit")
          || (control instanceof HTMLInputElement && ["submit", "image"].includes(control.type));
      }) : [owner];
    if (submitter && !controls.includes(submitter)) controls.push(submitter);
    const pending = { controls: controls.map(function (control) {
      return { control, disabled: control.disabled, busy: control.getAttribute("aria-busy") };
    }) };
    pendingPosts.set(owner, pending);
    if (xhr) postRequests.set(xhr, owner);
    function showLoading() {
      if (pendingPosts.get(owner) !== pending) return;
      pending.controls.forEach(function (state) { state.control.disabled = true; });
      const state = pending.controls.find(function (state) { return state.control === submitter; });
      if (!state) return;
      submitter.setAttribute("aria-busy", "true");
      const spinner = document.createElement("span");
      spinner.className = "ui-loading-spinner";
      spinner.setAttribute("aria-hidden", "true");
      if (submitter instanceof HTMLInputElement) {
        state.value = submitter.value;
        submitter.value = "Memproses…";
        submitter.after(spinner);
        state.spinner = spinner;
      } else {
        state.html = submitter.innerHTML;
        submitter.prepend(spinner);
      }
    }
    // Native submission must serialize the clicked button's name/value and
    // formaction before disabling it. The form is already locked against repeats.
    if (deferred) setTimeout(showLoading, 0);
    else showLoading();
    return true;
  }

  document.addEventListener("submit", function (event) {
    if (pendingPosts.has(event.target)) {
      event.preventDefault();
      event.stopImmediatePropagation();
    }
  }, true);

  document.addEventListener("submit", function (event) {
    if (event.defaultPrevented || !(event.target instanceof HTMLFormElement)) return;
    const form = event.target;
    const method = event.submitter?.getAttribute("formmethod") || form.method;
    if (method.toLowerCase() !== "post") return;
    const submitter = event.submitter || Array.from(form.elements).find(function (control) {
      return control instanceof HTMLButtonElement && control.type === "submit" && !control.disabled;
    });
    lockPost(form, submitter, null, true);
  });

  document.addEventListener("htmx:beforeRequest", function (event) {
    if (event.detail.requestConfig?.verb?.toLowerCase() !== "post") return;
    const element = event.detail.elt;
    const owner = element.form || element.closest("form") || element;
    const submitter = event.detail.requestConfig.triggeringEvent?.submitter
      || (element.matches("button, input[type='submit']") ? element : null)
      || Array.from(owner.elements || []).find(function (control) {
        return control instanceof HTMLButtonElement && control.type === "submit" && !control.disabled;
      });
    if (!lockPost(owner, submitter, event.detail.xhr, false)) event.preventDefault();
  });

  document.addEventListener("htmx:afterRequest", function (event) {
    const owner = postRequests.get(event.detail.xhr);
    if (owner) {
      unlockPost(owner);
      postRequests.delete(event.detail.xhr);
    }
  });
  window.addEventListener("pageshow", function () {
    Array.from(pendingPosts.keys()).forEach(unlockPost);
  });

  function focusDialog(dialog) {
    const target = dialog.querySelector("[data-form-errors]")
      || dialog.querySelector('[aria-invalid="true"]')
      || dialog.querySelector("[autofocus]")
      || dialog.querySelector('input:not([type="hidden"]), select, textarea, button');
    target?.focus();
  }

  function setDrawer(open) {
    const shell = document.querySelector("[data-dashboard-shell]");
    const button = document.querySelector("[data-open-drawer]");
    const sidebar = document.getElementById("dashboard-sidebar");
    if (!shell || !sidebar) return;
    shell.classList.toggle("ui-drawer-open", open);
    button?.setAttribute("aria-expanded", String(open));
    sidebar.setAttribute("aria-hidden", String(!open && window.innerWidth < 1024));
    syncDrawerForViewport();
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
    const mobile = window.innerWidth < 1024;
    const open = mobile && shell?.classList.contains("ui-drawer-open");
    if (sidebar) {
      sidebar.inert = mobile && !open;
      sidebar.setAttribute("aria-hidden", String(sidebar.inert));
    }
    const main = document.querySelector(".ui-dashboard-main");
    if (main) main.inert = Boolean(open);
    if (!mobile) {
      shell?.classList.remove("ui-drawer-open");
      document.querySelector("[data-open-drawer]")?.setAttribute("aria-expanded", "false");
    }
  }

  syncDrawerForViewport();
  window.addEventListener("resize", syncDrawerForViewport);

  function initializeFilters(root) {
    root.querySelectorAll?.("[data-responsive-filter]:not([data-filter-ready])").forEach(function (filter) {
      filter.open = window.innerWidth >= 1024;
      filter.dataset.filterReady = "true";
    });
  }
  initializeFilters(document);

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
        focusDialog(dialog);
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
      if (!dialog.isConnected || typeof dialog.showModal !== "function" || dialog.matches(":modal")) return;
      dialog.removeAttribute("open");
      dialog.showModal();
      focusDialog(dialog);
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
    promoteOpenDialogs(document);
    if (event.detail.target?.id === "reservation-availability") {
      document.getElementById("reservation-availability")?.querySelector('input[name="startAt"]:checked, input[name="endAt"]')?.focus();
    }
  });

  document.addEventListener("htmx:afterSettle", function (event) {
    initializeFilters(document);
    promoteOpenDialogs(document);
    syncActiveNavigation();
    if (event.detail.target?.id?.endsWith("modal-region")) {
      const dialog = document.querySelector("dialog[open]");
      if (dialog) dialog.querySelector("[data-form-errors]")?.focus();
      else document.getElementById("management-feedback")?.focus();
    }
  });

  document.addEventListener("htmx:historyRestore", syncActiveNavigation);

  document.addEventListener("htmx:afterRequest", function (event) {
    if (event.detail.successful && event.detail.elt?.matches?.("[data-dashboard-link]")) {
      setDrawer(false);
      document.getElementById("dashboard-content")?.focus();
      const title = document.querySelector("#dashboard-content h1")?.textContent?.trim();
      if (title) document.title = title + " · PRISM";
    }
  });

  document.addEventListener("htmx:beforeRequest", function (event) {
    const link = event.detail.elt;
    if (link?.matches?.("[data-dashboard-link]") && link.dataset.navKey) {
      applyActiveNavigation(link.dataset.navKey);
    }
  });
})();
