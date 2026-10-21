(function () {
  "use strict";

  // Only server-rendered values update the persistent navigation shell.
  function syncDisplayName(root) {
    const content = root.querySelector?.('[data-navigation-name]');
    if (!content) return;
    document.querySelectorAll('[data-display-name]').forEach(function (element) {
      element.textContent = content.dataset.navigationName;
    });
  }

  function openDialog(dialog, trigger) {
    if (!trigger && dialog.dataset.dialogTrigger) trigger = document.getElementById(dialog.dataset.dialogTrigger);
    document.querySelectorAll('dialog[open]').forEach(function (other) {
      if (other !== dialog) other.close();
    });
    if (trigger) {
      trigger.setAttribute('aria-expanded', 'true');
      dialog.returnFocus = trigger;
    }
    if (!dialog.open) dialog.showModal();
    focusDialog(dialog);
  }

  // Upgrade existing disclosure forms without cloning fields, CSRF tokens or
  // HTMX attributes. Native dialogs enter the top layer, outside table layout.
  function initializeActionDialogs(root) {
    if (typeof HTMLDialogElement === 'undefined' || !HTMLDialogElement.prototype.showModal) return;
    root.querySelectorAll('.ui-table details:not([data-overlay-ready]), details[data-action-dialog]:not([data-overlay-ready])').forEach(function (details) {
      const summary = details.querySelector(':scope > summary');
      if (!summary) return;
      const dialog = document.createElement('dialog');
      dialog.id = 'action-' + crypto.randomUUID();
      dialog.className = 'ui-dialog';
      dialog.setAttribute('aria-labelledby', dialog.id + '-title');
      const header = document.createElement('div');
      header.className = 'ui-dialog-header';
      const title = document.createElement('h2');
      title.id = dialog.id + '-title';
      title.textContent = summary.textContent.trim();
      const close = document.createElement('button');
      close.type = 'button';
      close.className = 'ui-btn ui-btn-ghost';
      close.dataset.closeDialog = '';
      close.setAttribute('aria-label', 'Tutup dialog');
      close.textContent = '×';
      header.append(title, close);
      const content = document.createElement('div');
      content.className = 'space-y-5';
      Array.from(details.children).forEach(function (child) {
        if (child !== summary) content.append(child);
      });
      dialog.append(header, content);
      details.after(dialog);
      details.open = false;
      details.dataset.overlayReady = 'true';
      details.classList.add('ui-action-dialog');
      summary.dataset.openDialog = dialog.id;
      summary.setAttribute('aria-haspopup', 'dialog');
      summary.setAttribute('aria-controls', dialog.id);
      summary.setAttribute('aria-expanded', 'false');
    });
  }

  document.addEventListener('close', function (event) {
    if (!(event.target instanceof HTMLDialogElement)) return;
    if (event.target.matches('[data-account-deletion]')) {
      event.target.querySelectorAll('input[autocomplete="current-password"]').forEach(input => { input.value = ''; input.type = 'password'; });
      event.target.querySelectorAll('[data-toggle-password]').forEach(function (toggle) {
        toggle.textContent = 'Tampilkan';
        toggle.setAttribute('aria-pressed', 'false');
      });
      event.target.querySelectorAll('input[type="checkbox"]').forEach(input => { input.checked = false; });
      event.target.querySelector('[data-form-errors]')?.remove();
    }
    document.querySelectorAll('[data-open-dialog]').forEach(function (trigger) {
      if (trigger.dataset.openDialog === event.target.id) trigger.setAttribute('aria-expanded', 'false');
    });
    if (event.target.returnFocus?.isConnected) event.target.returnFocus.focus();
  }, true);

  // Early format/size feedback; the server also parses the uploaded PDF.
  document.addEventListener("change", async function (event) {
    const input = event.target;
    if (!(input instanceof HTMLInputElement) || !input.matches("[data-validated-proposal]")) return;
    input.setCustomValidity("");
    const file = input.files?.[0];
    if (!file) return;
    let error = "";
    if (!/\.pdf$/i.test(file.name) || file.size === 0) error = "Pilih berkas PDF tervalidasi yang tidak kosong.";
    else if (file.size > Number(input.dataset.maxBytes)) error = "Ukuran PDF melebihi batas unggahan.";
    else {
      // Prevent native submission until the asynchronous header check finishes.
      input.setCustomValidity("Sedang memeriksa PDF. Mohon tunggu.");
      try {
        const header = new TextDecoder().decode(await file.slice(0, 5).arrayBuffer());
        if (input.files?.[0] !== file) return;
        if (header !== "%PDF-") error = "Isi berkas harus berupa PDF yang valid.";
      } catch (_) { error = "Berkas PDF tidak dapat dibaca. Pilih ulang berkas."; }
    }
    if (input.files?.[0] !== file) return;
    input.setCustomValidity(error);
    if (error) input.reportValidity();
  });

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
      || dialog.querySelector('input:not([type="hidden"]), select, textarea')
      || dialog.querySelector('button');
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
    const mobileHeader = document.querySelector(".ui-mobile-header");
    if (mobileHeader) mobileHeader.inert = Boolean(open);
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

  function filterRecords(input) {
    const region = document.getElementById(input.dataset.listSearch);
    if (!region) return;
    const query = input.value.trim().toLocaleLowerCase('id');
    const tools = input.closest('[data-list-tools]');
    const status = tools?.querySelector('[data-record-filter][aria-pressed="true"]')?.dataset.recordFilter || '';
    const from = tools?.querySelector('[data-record-from]')?.value || '';
    const to = tools?.querySelector('[data-record-to]')?.value || '';
    const invalidRange = Boolean(from && to && from > to);
    const dateError = tools?.querySelector('[data-date-range-error]');
    if (dateError) dateError.hidden = !invalidRange;
    const records = Array.from(region.querySelectorAll('[data-list-record]'));
    let visible = 0;
    records.forEach(function (record) {
      // Action forms and dialogs should not contribute to search results.
      const text = record.dataset.searchText || Array.from(record.querySelectorAll('td:not(.ui-table-actions), h3, dl'))
        .map(function (node) { return node.textContent; }).join(' ');
      const date = record.dataset.recordDate || '';
      const show = !invalidRange && text.toLocaleLowerCase('id').includes(query)
        && (!status || record.dataset.recordStatus === status)
        && (!from || date >= from) && (!to || (date && date <= to));
      record.hidden = !show;
      if (show) visible++;
    });
    const count = tools?.querySelector('[data-list-count]');
    if (count) count.textContent = visible + ' dari ' + records.length + ' ditampilkan';
    let empty = region.querySelector('[data-search-empty]');
    if (!empty) {
      empty = document.createElement('p');
      empty.className = 'p-6 text-center text-sm text-muted';
      empty.dataset.searchEmpty = '';
      empty.textContent = 'Tidak ada hasil yang sesuai. Ubah pencarian atau filter.';
      region.append(empty);
    }
    empty.hidden = visible > 0 || records.length === 0;
  }

  function initializeEnhancements(root) {
    initializeAjaxLinks(root);
    initializeActionDialogs(root);
    initializeFacilityPickers(root);
    initializeMultiFilters(root);
    root.querySelectorAll?.('[data-list-search]').forEach(function (input) {
      const region = document.getElementById(input.dataset.listSearch);
      const tools = input.closest('[data-list-tools]');
      if (tools) tools.hidden = !region?.querySelector('[data-list-record]');
      filterRecords(input);
    });
    root.querySelectorAll?.('[data-character-count]:not([data-count-ready])').forEach(function (input) {
      input.dataset.countReady = 'true';
      const count = document.createElement('p');
      count.className = 'mt-2 text-right text-xs tabular-nums text-muted';
      count.dataset.characterFeedback = '';
      input.after(count);
      count.textContent = input.value.length + ' / ' + input.maxLength + ' karakter';
    });
    root.querySelectorAll?.('[data-password-toggle]:not([data-toggle-ready])').forEach(function (input) {
      input.dataset.toggleReady = 'true';
      if (!input.id) input.id = 'password-' + crypto.randomUUID();
      const wrapper = document.createElement('span');
      wrapper.className = 'relative block';
      input.before(wrapper);
      wrapper.append(input);
      input.classList.add('pr-24');
      const toggle = document.createElement('button');
      toggle.type = 'button';
      toggle.className = 'absolute inset-y-0 right-0 min-w-20 rounded-r-xl px-3 text-xs font-semibold text-primary hover:bg-primary-subtle';
      toggle.textContent = 'Tampilkan';
      toggle.dataset.togglePassword = input.id;
      toggle.setAttribute('aria-controls', input.id);
      toggle.setAttribute('aria-pressed', 'false');
      toggle.setAttribute('aria-label', 'Tampilkan kata sandi: ' + (input.labels?.[0]?.textContent.trim() || 'Kata sandi'));
      wrapper.append(toggle);
    });
  }
  initializeEnhancements(document);
  document.querySelector('[data-form-errors]')?.focus();

  document.addEventListener('input', function (event) {
    if (event.target.matches('[data-facility-search]')) {
      const picker = event.target.closest('[data-facility-picker]');
      if (picker.closest('form').querySelector('[data-selected-facility-input]').value) clearFacility(picker, false);
      picker.querySelector('[data-facility-results]').hidden = false;
      event.target.setCustomValidity(event.target.value.trim() ? 'Pilih fasilitas dari hasil pencarian.' : '');
    }
    if (event.target.matches('[data-list-search]')) filterRecords(event.target);
    if (event.target.matches('[data-character-count]')) {
      event.target.nextElementSibling.textContent = event.target.value.length + ' / ' + event.target.maxLength + ' karakter';
    }
  });
  document.addEventListener('click', function (event) {
    const dateShortcut = event.target.closest('[data-reservation-date]');
    if (dateShortcut) {
      const input = dateShortcut.closest('form')?.querySelector('input[type="date"][name="date"]');
      if (!input) return;
      input.value = dateShortcut.dataset.reservationDate;
      input.dispatchEvent(new Event('change', { bubbles: true }));
      input.focus({ preventScroll: true });
      return;
    }
    const facilityChoice = event.target.closest('[data-select-facility]');
    if (facilityChoice) {
      if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
      event.preventDefault();
      const picker = facilityChoice.closest('[data-facility-picker]');
      const input = picker.closest('form').querySelector('[data-selected-facility-input]');
      const search = picker.querySelector('[data-facility-search]');
      input.value = facilityChoice.dataset.facilityId;
      search.value = facilityChoice.dataset.facilityName;
      search.setCustomValidity('');
      picker.querySelector('[data-selected-facility-context]').replaceChildren(
        facilityChoice.querySelector('[data-facility-context-template]').content.cloneNode(true));
      picker.querySelector('[data-facility-results]').hidden = true;
      picker.querySelector('[data-clear-facility]').hidden = false;
      resetFacilitySchedule(picker.closest('form'));
      input.dispatchEvent(new Event('change', { bubbles: true }));
      search.focus();
      return;
    }
    const clearChoice = event.target.closest('[data-clear-facility]');
    if (clearChoice) { clearFacility(clearChoice.closest('[data-facility-picker]'), true); return; }
    const clearMulti = event.target.closest('[data-clear-multi]');
    if (clearMulti) {
      const group = clearMulti.closest('[data-multi-filter]');
      group.querySelectorAll('input[type="checkbox"]').forEach(input => { input.checked = false; });
      updateMultiFilter(group);
      scheduleFilter(group.closest('form[data-auto-filter]'));
      return;
    }
    const resetHistory = event.target.closest('[data-history-reset]');
    if (resetHistory) {
      const tools = resetHistory.closest('[data-list-tools]');
      tools.querySelectorAll('[data-list-search], [data-record-from], [data-record-to]').forEach(input => { input.value = ''; });
      tools.querySelectorAll('[data-record-filter]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.recordFilter === '')));
      filterRecords(tools.querySelector('[data-list-search]'));
      return;
    }
    const toggle = event.target.closest('[data-toggle-password]');
    if (toggle) {
      const input = document.getElementById(toggle.dataset.togglePassword);
      const visible = input.type === 'password';
      input.type = visible ? 'text' : 'password';
      toggle.textContent = visible ? 'Sembunyikan' : 'Tampilkan';
      toggle.setAttribute('aria-pressed', String(visible));
    }
    const filter = event.target.closest('[data-record-filter]');
    if (filter) {
      const tools = filter.closest('[data-list-tools]');
      tools.querySelectorAll('[data-record-filter]').forEach(function (button) {
        button.setAttribute('aria-pressed', String(button === filter));
      });
      filterRecords(tools.querySelector('[data-list-search]'));
    }
  });

  document.addEventListener('change', function (event) {
    scheduleFilter(event.target.closest('form[data-auto-filter]'));
    if (event.target.matches('[data-multi-filter] input[type="checkbox"]')) updateMultiFilter(event.target.closest('[data-multi-filter]'));
    if (event.target.matches('[data-record-from], [data-record-to]')) {
      filterRecords(event.target.closest('[data-list-tools]').querySelector('[data-list-search]'));
    }
  });

  const filterTimers = new WeakMap();
  function scheduleFilter(form, delay = 0) {
    if (!form) return;
    clearTimeout(filterTimers.get(form));
    filterTimers.set(form, setTimeout(function () {
      if (form.isConnected && form.reportValidity()) {
        form.dispatchEvent(new Event('prism:filter', { bubbles: true }));
      }
    }, delay));
  }

  document.addEventListener('input', function (event) {
    if (event.target.matches('input[type="number"], input[type="search"], input[type="text"]')) {
      scheduleFilter(event.target.closest('form[data-auto-filter]'), 250);
    }
  });

  function updateMultiFilter(group) {
    const count = group.querySelectorAll('input[type="checkbox"]:checked').length;
    group.querySelector('[data-selection-count]').textContent = count ? count + ' dipilih' : 'Semua';
    group.querySelector('[data-clear-multi]').disabled = count === 0;
  }

  function initializeMultiFilters(root) {
    root.querySelectorAll?.('[data-multi-filter]').forEach(updateMultiFilter);
  }

  function initializeFacilityPickers(root) {
    root.querySelectorAll?.('[data-facility-picker]').forEach(function (picker) {
      const input = picker.closest('form').querySelector('[data-selected-facility-input]');
      const search = picker.querySelector('[data-facility-search]');
      const selected = picker.querySelector('[data-selected-facility-context] [data-selected-facility-id]');
      search.setCustomValidity(input.value && selected ? '' : (search.value.trim() ? 'Pilih fasilitas dari hasil pencarian.' : ''));
    });
  }

  function resetFacilitySchedule(form) {
    form.querySelectorAll('[name="startAt"], [name="endAt"]').forEach(function (input) {
      if (input.type === 'radio') input.checked = false;
      else input.value = '';
    });
    const quantity = form.querySelector('[name="quantity"]');
    if (quantity) quantity.value = '1';
  }

  function clearFacility(picker, resetSearch) {
    const form = picker.closest('form');
    const input = form.querySelector('[data-selected-facility-input]');
    const search = picker.querySelector('[data-facility-search]');
    input.value = '';
    picker.querySelector('[data-selected-facility-context]').replaceChildren();
    picker.querySelector('[data-clear-facility]').hidden = true;
    picker.querySelector('[data-facility-results]').hidden = false;
    if (resetSearch) { search.value = ''; search.setCustomValidity(''); filterRecords(search); search.focus(); }
    resetFacilitySchedule(form);
    input.dispatchEvent(new Event('change', { bubbles: true }));
  }

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

  // Enhance page links individually so selection/target attributes cannot leak
  // into nested modal forms, uploads, downloads, or other partial requests.
  function initializeAjaxLinks(root) {
    if (!window.htmx) return;
    const target = document.getElementById('dashboard-content') ? '#dashboard-content' : '#main-content';
    const pageRoutes = [
      /^\/$/,
      /^\/facilities(?:\/\d+(?:\/availability)?)?$/,
      /^\/(?:reservations|reports)(?:\/(?:new|\d+))?$/,
      /^\/staff\/(?:dashboard|reservations(?:\/\d+)?|reports(?:\/\d+)?|blockages(?:\/\d+\/edit)?)$/,
      /^\/admin\/(?:users|facilities|facility-types|blockage-types|settings|recap)$/,
      /^\/account(?:\/password)?$/
    ];
    root.querySelectorAll?.('a[href]').forEach(function (link) {
      if (link.hasAttribute('download') || link.hasAttribute('target') || link.matches('[data-select-facility]')
          || link.hasAttribute('hx-boost') || link.closest('[hx-boost="false"], [data-no-ajax]')
          || ['get', 'post', 'put', 'patch', 'delete'].some(verb => link.hasAttribute('hx-' + verb))) return;
      const url = new URL(link.href, location.href);
      if (url.origin !== location.origin || url.hash || !pageRoutes.some(route => route.test(url.pathname))) return;
      link.dataset.ajaxLink = '';
      link.setAttribute('hx-boost', 'true');
      link.setAttribute('hx-target', target);
      link.setAttribute('hx-select', target);
      link.setAttribute('hx-swap', 'outerHTML show:none');
      link.setAttribute('hx-sync', '#main-content:replace');
      link.setAttribute('hx-indicator', '#global-progress');
      window.htmx.process(link);
    });
    root.querySelectorAll?.('[data-dashboard-link]').forEach(function (link) {
      link.setAttribute('hx-sync', '#main-content:replace');
    });
  }

  const navigationRequests = new WeakSet();
  document.addEventListener('htmx:beforeRequest', function (event) {
    if (event.detail.elt?.matches?.('[data-ajax-link], [data-dashboard-link]')
        || (event.detail.requestConfig?.verb === 'get' && ['dashboard-content', 'main-content'].includes(event.detail.target?.id))) {
      navigationRequests.add(event.detail.xhr);
      document.querySelectorAll('form[data-auto-filter]').forEach(function (form) {
        clearTimeout(filterTimers.get(form));
        window.htmx.trigger(form, 'htmx:abort');
      });
    }
  });

  document.addEventListener('htmx:beforeSwap', function (event) {
    const target = event.detail.target;
    if (!event.detail.shouldSwap || !['dashboard-content', 'main-content'].includes(target?.id)) return;
    const response = new DOMParser().parseFromString(event.detail.serverResponse, 'text/html');
    if (response.getElementById(target.id)) return;
    // A session may expire while the persistent shell is open. Follow the
    // server's login redirect as a full navigation instead of swapping nothing.
    event.detail.shouldSwap = false;
    const destination = new URL(event.detail.xhr.responseURL, location.href);
    if (destination.pathname === '/login') location.assign(destination.href);
    else showHtmxError('Halaman tidak dapat dimuat. Silakan coba lagi.');
  });

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
      event.preventDefault();
      const dialog = document.getElementById(openButton.dataset.openDialog);
      if (dialog && typeof dialog.showModal === "function") {
        openDialog(dialog, openButton);
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
    if (event.key === "Escape" && document.querySelector(".ui-drawer-open")) {
      event.preventDefault();
      setDrawer(false);
    }
    if (event.key === "Tab" && document.querySelector(".ui-drawer-open")) {
      const sidebar = document.getElementById("dashboard-sidebar");
      const focusable = Array.from(sidebar?.querySelectorAll(
        'a[href], button:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])'
      ) || []).filter(function (element) { return element.getClientRects().length > 0; });
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
    const required = minutes >= 360 || Number(form?.querySelector('[name="quantity"]')?.value || 0) > 5;
    proposal?.classList.toggle("ui-proposal-required", required);
    if (proposal) {
      proposal.dataset.proposalRequired = String(required);
      const upload = form.querySelector('input[name="proposal"]');
      if (upload) upload.required = required;
      proposal.querySelector("[data-proposal-label]").textContent = required ? "Wajib" : "Opsional";
      proposal.querySelector("[data-proposal-help]").textContent = required
        ? "Wajib untuk durasi 6 jam atau lebih, atau jumlah Alat lebih dari 5 unit."
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

  document.addEventListener("htmx:timeout", function () {
    syncActiveNavigation();
    showHtmxError("Server membutuhkan waktu lebih lama. Silakan coba lagi.");
  });

  function promoteOpenDialogs(root) {
    root.querySelectorAll?.("dialog[open]").forEach(function (dialog) {
      if (!dialog.isConnected || typeof dialog.showModal !== "function" || dialog.matches(":modal")) return;
      dialog.removeAttribute("open");
      openDialog(dialog);
    });
  }

  function syncActiveNavigation() {
    const content = document.getElementById("dashboard-content") || document.getElementById("main-content");
    const activeNav = content?.dataset.activeNav;
    if (!activeNav) return;
    applyActiveNavigation(activeNav);
    document.querySelectorAll('.ui-nav-link').forEach(function (link) {
      const active = activeNav === 'facilities' && new URL(link.href).pathname === '/facilities';
      link.classList.toggle('ui-nav-link-active', active);
      if (active) link.setAttribute('aria-current', 'page');
      else link.removeAttribute('aria-current');
    });
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

  function syncEquipmentControls(root) {
    root.querySelectorAll?.("[data-facility-type]").forEach(function (select) {
      const container = select.closest("form")?.querySelector("[data-equipment-stock]");
      if (!container) return;
      const equipment = select.selectedOptions[0]?.dataset.equipment === "true";
      container.hidden = !equipment;
      const input = container.querySelector("input");
      input.disabled = !equipment;
      input.required = equipment;
    });
    root.querySelectorAll?.("[data-proposal-state]").forEach(function (state) {
      const upload = state.closest("form")?.querySelector('input[name="proposal"]');
      if (upload) upload.required = state.dataset.proposalRequired === "true";
    });
  }
  document.addEventListener("change", function (event) {
    if (event.target.matches("[data-facility-type]")) syncEquipmentControls(event.target.closest("form"));
  });
  syncEquipmentControls(document);
  promoteOpenDialogs(document);
  document.addEventListener("htmx:afterSwap", function (event) {
    // History snapshots can contain an older name; only fresh server responses
    // may replace the current navigation name.
    if (event.detail.xhr) syncDisplayName(document);
    syncEquipmentControls(document);
    promoteOpenDialogs(document);
    if (event.detail.target?.id === "reservation-availability") {
      document.getElementById("reservation-availability")?.querySelector('input[name="startAt"]:checked, input[name="endAt"]')?.focus();
    }
  });

  document.addEventListener("htmx:afterSettle", function (event) {
    initializeEnhancements(document);
    event.detail.target?.querySelector?.('[data-form-errors]')?.focus();
    initializeFilters(document);
    syncEquipmentControls(document);
    promoteOpenDialogs(document);
    syncActiveNavigation();
    if (event.detail.xhr && navigationRequests.has(event.detail.xhr)) {
      navigationRequests.delete(event.detail.xhr);
      setDrawer(false);
      const content = document.getElementById('dashboard-content') || document.getElementById('main-content');
      content?.focus({ preventScroll: true });
      window.scrollTo({ top: 0, behavior: 'instant' });
    }
    if (event.detail.target?.id?.endsWith("modal-region")) {
      const dialog = document.querySelector("dialog[open]");
      if (dialog) dialog.querySelector("[data-form-errors]")?.focus();
      else document.getElementById("management-feedback")?.focus();
    }
  });

  document.addEventListener("htmx:historyRestore", function () {
    // Results-only filter swaps retain live fields. The URL is authoritative
    // when restoring a snapshot whose input properties were not serialized.
    document.querySelectorAll('form[data-auto-filter]').forEach(function (form) {
      const parameters = new URLSearchParams(location.search);
      Array.from(form.elements).forEach(function (input) {
        if (!input.name) return;
        if (input.type === 'checkbox') input.checked = parameters.getAll(input.name).includes(input.value);
        else if (input.matches('input, select')) {
          const fallback = form.id === 'recap-filter-form' && input.type === 'date' ? input.defaultValue : input.dataset.filterDefault || '';
          input.value = parameters.get(input.name) ?? fallback;
        }
      });
    });
    initializeEnhancements(document);
    syncActiveNavigation();
    initializeFilters(document);
    syncDrawerForViewport();
    const title = document.querySelector("#dashboard-content h1")?.textContent?.trim();
    if (title) document.title = title + " · PRISM";
  });

  document.addEventListener("htmx:beforeRequest", function (event) {
    const link = event.detail.elt;
    if (link?.matches?.("[data-dashboard-link]") && link.dataset.navKey) {
      applyActiveNavigation(link.dataset.navKey);
    }
  });
})();
