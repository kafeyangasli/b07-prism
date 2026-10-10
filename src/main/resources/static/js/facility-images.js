(() => {
  'use strict';
  const previews = new WeakMap();
  document.addEventListener('htmx:beforeCleanupElement', event => {
    event.detail.elt.querySelectorAll?.('[data-image-input]').forEach(input => {
      (previews.get(input) || []).forEach(URL.revokeObjectURL);
      previews.delete(input);
    });
  });
  document.addEventListener('change', event => {
    const input = event.target.closest('[data-image-input]');
    if (!input) return;
    const form = input.closest('form');
    const region = form.querySelector('[data-image-previews]');
    (previews.get(input) || []).forEach(URL.revokeObjectURL);
    region.replaceChildren();
    const urls = [];
    // On creation, select the first preview; additional uploads preserve the current thumbnail.
    const creating = form.action.endsWith('/admin/facilities');
    Array.from(input.files).forEach((file, index) => {
      const label = document.createElement('label');
      const image = document.createElement('img');
      image.className = 'facility-image-small';
      image.alt = `Pratinjau gambar ${index + 1}`;
      image.src = URL.createObjectURL(file); urls.push(image.src);
      const radio = document.createElement('input');
      radio.type = 'radio'; radio.name = 'thumbnailIndex'; radio.value = index;
      radio.checked = creating && index === 0;
      label.append(image, radio, document.createTextNode(` Thumbnail ${index + 1}`));
      region.append(label);
    });
    if (!creating && input.files.length) {
      const label = document.createElement('label');
      const radio = document.createElement('input');
      radio.type = 'radio'; radio.name = 'thumbnailIndex'; radio.value = ''; radio.checked = true;
      label.append(radio, document.createTextNode(' Pertahankan thumbnail saat ini'));
      region.prepend(label);
    }
    previews.set(input, urls);
  });
  document.addEventListener('click', event => {
    const choice = event.target.closest('[data-gallery-choice]');
    if (!choice) return;
    event.preventDefault();
    const main = choice.closest('[data-facility-gallery]').querySelector('[data-gallery-main]');
    main.src = choice.href; main.alt = choice.querySelector('img').alt;
  });
  // Capturing also handles images inserted by HTMX.
  document.addEventListener('error', event => {
    const image = event.target;
    if (image.matches?.('[data-facility-image]') && !image.src.endsWith('/images/facility-placeholder.svg'))
      image.src = '/images/facility-placeholder.svg';
  }, true);
})();
