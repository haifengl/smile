/*
 * SMILE site chrome: Prism, TOC, theme, mobile nav, tabs, sticky header,
 * link prefetch, and copy buttons for code blocks.
 */
(function () {
  'use strict';

  var THEME_KEY = 'smile-theme';

  function getTheme() {
    var stored = null;
    try {
      stored = localStorage.getItem(THEME_KEY);
    } catch (e) { /* ignore */ }
    if (stored === 'dark' || stored === 'light') return stored;
    return document.documentElement.getAttribute('data-theme') === 'dark'
      ? 'dark'
      : 'light';
  }

  function syncPrismTheme(theme) {
    var light = document.getElementById('prism-light');
    var dark = document.getElementById('prism-dark');
    if (!light || !dark) return;
    var isDark = theme === 'dark';
    light.disabled = isDark;
    dark.disabled = !isDark;
  }

  function syncThemeIcons(theme) {
    var toggle = document.getElementById('theme-toggle');
    if (!toggle) return;
    var sun = toggle.querySelector('.icon-sun');
    var moon = toggle.querySelector('.icon-moon');
    var isDark = theme === 'dark';
    if (sun) sun.hidden = isDark;
    if (moon) moon.hidden = !isDark;
    toggle.setAttribute(
      'aria-label',
      isDark ? 'Switch to light mode' : 'Switch to dark mode'
    );
  }

  function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    try {
      localStorage.setItem(THEME_KEY, theme);
    } catch (e) { /* ignore */ }
    syncPrismTheme(theme);
    syncThemeIcons(theme);
  }

  function initThemeToggle() {
    var theme = getTheme();
    applyTheme(theme);

    var toggle = document.getElementById('theme-toggle');
    if (!toggle) return;
    toggle.addEventListener('click', function () {
      applyTheme(getTheme() === 'dark' ? 'light' : 'dark');
    });
  }

  function initMobileMenu() {
    var btn = document.getElementById('menu-toggle');
    var drawer = document.getElementById('mobile-drawer');
    if (!btn || !drawer) return;

    btn.addEventListener('click', function () {
      var open = !drawer.classList.contains('is-open');
      drawer.classList.toggle('is-open', open);
      btn.setAttribute('aria-expanded', open ? 'true' : 'false');
      btn.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
    });
  }

  function initTabs() {
    document.querySelectorAll('.nav-tabs').forEach(function (tabList) {
      var links = tabList.querySelectorAll('.nav-link[data-bs-target]');
      links.forEach(function (link) {
        link.addEventListener('click', function (event) {
          event.preventDefault();
          var targetSel = link.getAttribute('data-bs-target');
          if (!targetSel) return;
          var target = document.querySelector(targetSel);
          if (!target) return;

          links.forEach(function (other) {
            other.classList.remove('active');
            other.setAttribute('aria-selected', 'false');
          });
          link.classList.add('active');
          link.setAttribute('aria-selected', 'true');

          var paneRoot = target.closest('.tab-content') || target.parentElement;
          if (paneRoot) {
            paneRoot.querySelectorAll('.tab-pane').forEach(function (pane) {
              pane.classList.remove('active', 'show');
            });
          }
          target.classList.add('active', 'show');
        });
      });
    });
  }

  function initStickyHeader() {
    var header = document.getElementById('site-header');
    if (!header) return;

    var ticking = false;
    function update() {
      ticking = false;
      header.classList.toggle('is-compact', window.scrollY > 40);
    }

    window.addEventListener(
      'scroll',
      function () {
        if (!ticking) {
          ticking = true;
          window.requestAnimationFrame(update);
        }
      },
      { passive: true }
    );
    update();
  }

  function initPrefetch() {
    var prefetched = Object.create(null);

    document.addEventListener('mouseover', function (event) {
      var anchor = event.target.closest('a[href]');
      if (!anchor) return;

      var href = anchor.getAttribute('href');
      if (!href || href.indexOf('://') !== -1 || href.charAt(0) === '#') return;
      if (!/\.html(?:#|$)/.test(href) && href.indexOf('.html') === -1) return;
      if (prefetched[href]) return;

      prefetched[href] = true;
      var link = document.createElement('link');
      link.rel = 'prefetch';
      link.href = href.split('#')[0];
      document.head.appendChild(link);
    });
  }

  function copyText(text) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      return navigator.clipboard.writeText(text);
    }
    return new Promise(function (resolve, reject) {
      var ta = document.createElement('textarea');
      ta.value = text;
      ta.setAttribute('readonly', '');
      ta.style.position = 'fixed';
      ta.style.left = '-9999px';
      document.body.appendChild(ta);
      ta.select();
      try {
        document.execCommand('copy');
        resolve();
      } catch (err) {
        reject(err);
      } finally {
        document.body.removeChild(ta);
      }
    });
  }

  function initCopyButtons() {
    document.querySelectorAll('pre[class*="language-"]').forEach(function (pre) {
      if (pre.closest('.code-playground')) return;
      if (pre.parentElement && pre.parentElement.classList.contains('copy-wrap')) return;

      var wrap = document.createElement('div');
      wrap.className = 'copy-wrap';
      pre.parentNode.insertBefore(wrap, pre);
      wrap.appendChild(pre);

      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'btn btn-ghost btn-sm copy-btn';
      btn.textContent = 'Copy';
      wrap.appendChild(btn);

      btn.addEventListener('click', function () {
        var code = pre.querySelector('code');
        var text = code ? code.textContent : pre.textContent;
        copyText(text || '').then(function () {
          btn.textContent = 'Copied';
          setTimeout(function () {
            btn.textContent = 'Copy';
          }, 1500);
        });
      });
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (window.Prism) {
      Prism.highlightAll();
    }

    if (window.SmileTOC && document.querySelector('#toc')) {
      SmileTOC.build('#toc', {
        exclude: 'h1, h5, h6',
        autoId: true,
        numerate: false,
      });
    }

    initThemeToggle();
    initMobileMenu();
    initTabs();
    initStickyHeader();
    initPrefetch();
    initCopyButtons();
  });
})();
