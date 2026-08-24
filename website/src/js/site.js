/*
 * Site-wide Bootstrap 5 helpers (navbar hover dropdowns, Prism, sticky sidebar).
 */
(function () {
  'use strict';

  document.addEventListener('DOMContentLoaded', () => {
    if (window.Prism) {
      Prism.highlightAll();
    }

    // Build sidebar TOC after toc.js has loaded (layout scripts are below page content).
    if (window.SmileTOC && document.querySelector('#toc')) {
      SmileTOC.build('#toc', { exclude: 'h1, h5, h6', autoId: true, numerate: false });
    }

    // Hover-open dropdowns on large screens (preserve legacy UX).
    document.querySelectorAll('.site-navbar .dropdown').forEach((dropdown) => {
      dropdown.addEventListener('mouseenter', () => {
        if (window.matchMedia('(min-width: 992px)').matches) {
          dropdown.classList.add('show');
          const toggle = dropdown.querySelector('[data-bs-toggle="dropdown"]');
          const menu = dropdown.querySelector('.dropdown-menu');
          if (toggle) toggle.setAttribute('aria-expanded', 'true');
          if (menu) menu.classList.add('show');
        }
      });
      dropdown.addEventListener('mouseleave', () => {
        dropdown.classList.remove('show');
        const toggle = dropdown.querySelector('[data-bs-toggle="dropdown"]');
        const menu = dropdown.querySelector('.dropdown-menu');
        if (toggle) toggle.setAttribute('aria-expanded', 'false');
        if (menu) menu.classList.remove('show');
      });
    });
  });
})();
