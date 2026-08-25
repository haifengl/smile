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
    return (code ? code.textContent : source.textContent) || '';
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

  function initPlayground(playground) {
    var copyBtn = playground.querySelector('.playground-copy');
    var editBtn = playground.querySelector('.playground-edit');
    var source = playground.querySelector('.playground-source');
    var editorHost = playground.querySelector('.playground-editor');
    var editor = null;

    if (copyBtn) {
      copyBtn.addEventListener('click', function () {
        var text = editor
          ? editor.getValue()
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

    if (!editBtn || !source || !editorHost) return;

    editBtn.addEventListener('click', function () {
      if (editor) {
        editor.focus();
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

          source.hidden = true;
          editorHost.hidden = false;

          editor = monaco.editor.create(editorHost, {
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
          editor.focus();
        })
        .catch(function (err) {
          console.warn('SMILE playground: Monaco failed to load', err);
          source.hidden = false;
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
