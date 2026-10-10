(() => {
  'use strict';
  const previews = new WeakMap();
  function clear(input) {
    const old = previews.get(input);
    if (old) URL.revokeObjectURL(old);
    previews.delete(input);
  }
  document.addEventListener('change', event => {
    const input = event.target.closest?.('[data-avatar-input]');
    if (!input) return;
    clear(input);
    const form = input.closest('form');
    const image = form.querySelector('[data-avatar-preview] img');
    const status = form.querySelector('[data-avatar-preview-status]');
    image.dataset.currentAvatarSrc ||= image.src;
    const file = input.files[0];
    if (!file) { image.src = image.dataset.currentAvatarSrc; status.textContent = ''; return; }
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      image.src = image.dataset.currentAvatarSrc;
      status.textContent = 'Pilih gambar JPEG, PNG, atau WebP.';
      return;
    }
    const url = URL.createObjectURL(file);
    previews.set(input, url);
    image.src = url;
    status.textContent = 'Pratinjau foto baru. Tekan Simpan Foto untuk mengunggah.';
  });
  document.addEventListener('htmx:beforeCleanupElement', event => {
    event.detail.elt.querySelectorAll?.('[data-avatar-input]').forEach(clear);
  });
  document.addEventListener('error', event => {
    const image = event.target;
    if (image.closest?.('[data-avatar-preview]') && image.src.startsWith('blob:')) {
      image.src = image.dataset.currentAvatarSrc;
      image.closest('form').querySelector('[data-avatar-preview-status]').textContent = 'Gambar tidak dapat dipratinjau. Pilih gambar yang valid.';
    }
  }, true);
})();
