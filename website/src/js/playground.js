/*
 * Code playground: Copy + lazy Monaco edit. Binder link is provided in HTML.
 */
(function () {
  'use strict';

  var MONACO_VERSION = '0.52.2';
  var MONACO_BASE =
    'https://cdn.jsdelivr.net/npm/monaco-editor@' + MONACO_VERSION + '/min';
  var monacoLoading = null;

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

  function sourceText(playground) {
    var visible = playground.querySelector('.hero-pane:not([hidden]) .playground-source');
    var source = visible || playground.querySelector('.playground-source');
    if (!source) return '';
    var code = source.querySelector('code');
    var text = (code ? code.textContent : source.textContent) || '';
    return window.smileDedent ? window.smileDedent(text) : text;
  }

  function mapLang(lang) {
    var key = (lang || 'java').toLowerCase();
    var map = {
      java: 'java',
      scala: 'scala',
      kotlin: 'kotlin',
      groovy: 'groovy',
      clojure: 'clojure',
      python: 'python',
      py: 'python',
      js: 'javascript',
      javascript: 'javascript',
      ts: 'typescript',
      typescript: 'typescript',
      bash: 'shell',
      sh: 'shell',
      shell: 'shell',
      xml: 'xml',
      html: 'html',
      json: 'json',
    };
    return map[key] || key;
  }

  function loadScript(src) {
    return new Promise(function (resolve, reject) {
      var existing = document.querySelector('script[src="' + src + '"]');
      if (existing) {
        if (existing.dataset.loaded === '1') {
          resolve();
          return;
        }
        existing.addEventListener('load', function () {
          resolve();
        });
        existing.addEventListener('error', reject);
        return;
      }
      var script = document.createElement('script');
      script.src = src;
      script.async = true;
      script.onload = function () {
        script.dataset.loaded = '1';
        resolve();
      };
      script.onerror = reject;
      document.head.appendChild(script);
    });
  }

  function loadMonaco() {
    if (window.monaco) return Promise.resolve(window.monaco);
    if (monacoLoading) return monacoLoading;

    monacoLoading = loadScript(MONACO_BASE + '/vs/loader.js').then(function () {
      return new Promise(function (resolve, reject) {
        try {
          window.require.config({ paths: { vs: MONACO_BASE + '/vs' } });
          window.require(['vs/editor/editor.main'], function () {
            resolve(window.monaco);
          });
        } catch (err) {
          reject(err);
        }
      });
    });

    return monacoLoading;
  }

  function hideSources(playground) {
    var panes = playground.querySelectorAll('.hero-pane');
    if (panes.length) {
      panes.forEach(function (pane) {
        pane.hidden = true;
      });
      return;
    }
    playground.querySelectorAll('.playground-source').forEach(function (source) {
      source.hidden = true;
    });
  }

  function showSources(playground) {
    var panes = playground.querySelectorAll('.hero-pane');
    if (panes.length) {
      panes.forEach(function (pane) {
        pane.hidden = true;
      });
      var activeTab = playground.querySelector('.lang-tab.is-active[data-pane]');
      if (activeTab) {
        var pane = document.getElementById(activeTab.getAttribute('data-pane'));
        if (pane) pane.hidden = false;
      }
      return;
    }
    playground.querySelectorAll('.playground-source').forEach(function (source) {
      source.hidden = false;
    });
  }

  function initLangTabs(playground, editorRef) {
    var tabs = playground.querySelectorAll('.lang-tab[data-pane]');
    if (!tabs.length) return;

    tabs.forEach(function (tab) {
      tab.addEventListener('click', function () {
        var paneId = tab.getAttribute('data-pane');
        playground.querySelectorAll('.lang-tab').forEach(function (t) {
          t.classList.remove('is-active');
        });
        playground.querySelectorAll('.hero-pane').forEach(function (pane) {
          pane.hidden = true;
        });
        tab.classList.add('is-active');
        var pane = document.getElementById(paneId);
        if (pane) pane.hidden = false;

        var lang = tab.getAttribute('data-lang') || tab.textContent.trim().toLowerCase();
        playground.setAttribute('data-lang', lang);

        if (editorRef.current && window.monaco) {
          editorRef.current.setValue(sourceText(playground));
          window.monaco.editor.setModelLanguage(
            editorRef.current.getModel(),
            mapLang(lang)
          );
        }
      });
    });
  }

  function initPlayground(playground) {
    var copyBtn = playground.querySelector('.playground-copy');
    var editBtn = playground.querySelector('.playground-edit');
    var editorHost = playground.querySelector('.playground-editor');
    var editorRef = { current: null };

    initLangTabs(playground, editorRef);

    if (copyBtn) {
      copyBtn.addEventListener('click', function () {
        var text = editorRef.current
          ? editorRef.current.getValue()
          : sourceText(playground);
        copyText(text).then(function () {
          var prev = copyBtn.textContent;
          copyBtn.textContent = 'Copied';
          setTimeout(function () {
            copyBtn.textContent = prev || 'Copy';
          }, 1500);
        });
      });
    }

    if (!editBtn || !editorHost) return;
    if (!playground.querySelector('.playground-source')) return;

    editBtn.addEventListener('click', function () {
      if (editorRef.current) {
        editorRef.current.focus();
        return;
      }

      editBtn.disabled = true;
      editBtn.textContent = 'Loading…';

      loadMonaco()
        .then(function (monaco) {
          var lang = mapLang(playground.getAttribute('data-lang'));
          var theme =
            document.documentElement.getAttribute('data-theme') === 'dark'
              ? 'vs-dark'
              : 'vs';

          hideSources(playground);
          editorHost.hidden = false;

          editorRef.current = monaco.editor.create(editorHost, {
            value: sourceText(playground),
            language: lang,
            theme: theme,
            automaticLayout: true,
            minimap: { enabled: false },
            scrollBeyondLastLine: false,
            fontSize: 13,
            fontFamily: 'IBM Plex Mono, ui-monospace, monospace',
            padding: { top: 12 },
          });

          editBtn.textContent = 'Edit';
          editBtn.disabled = false;
          editorRef.current.focus();
        })
        .catch(function (err) {
          console.warn('SMILE playground: Monaco failed to load', err);
          showSources(playground);
          editorHost.hidden = true;
          editBtn.textContent = 'Edit unavailable';
          editBtn.disabled = true;
        });
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.code-playground').forEach(initPlayground);
  });
})();
