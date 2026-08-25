/*
 * Command palette: open via #search-toggle or Cmd/Ctrl+K.
 * Fuzzy-filters search-index.json (titles + headings).
 */
(function () {
  'use strict';

  var INDEX_URL = 'search-index.json';
  var indexPromise = null;
  var entries = [];
  var filtered = [];
  var activeIndex = 0;

  var backdrop = null;
  var input = null;
  var resultsEl = null;
  var emptyEl = null;

  function loadIndex() {
    if (indexPromise) return indexPromise;
    indexPromise = fetch(INDEX_URL)
      .then(function (res) {
        if (!res.ok) throw new Error('Failed to load search index');
        return res.json();
      })
      .then(function (data) {
        entries = normalizeIndex(data);
        return entries;
      })
      .catch(function (err) {
        console.warn('SMILE search:', err);
        entries = [];
        return entries;
      });
    return indexPromise;
  }

  function normalizeIndex(data) {
    var list = Array.isArray(data) ? data : data && data.pages ? data.pages : [];
    var out = [];

    list.forEach(function (item) {
      if (!item) return;
      var url = item.url || item.path || '';
      var title = item.title || item.name || '';
      if (!url) return;

      if (title) {
        out.push({ title: title, url: url, subtitle: 'Page' });
      }

      var headings = item.headings || item.sections || [];
      if (typeof headings === 'string') headings = [headings];
      headings.forEach(function (h) {
        if (!h) return;
        if (typeof h === 'string') {
          out.push({
            title: h,
            url: url + '#' + slugify(h),
            subtitle: title || 'Heading',
          });
        } else if (h.text || h.title) {
          var text = h.text || h.title;
          var hash = h.id || h.anchor || slugify(text);
          out.push({
            title: text,
            url: url + (hash.indexOf('#') === 0 ? hash : '#' + hash),
            subtitle: title || 'Heading',
          });
        }
      });

      if (item.heading && !headings.length) {
        out.push({
          title: item.heading,
          url: url,
          subtitle: title || 'Heading',
        });
      }
    });

    return out;
  }

  function slugify(text) {
    return String(text)
      .trim()
      .toLowerCase()
      .replace(/[^\w\s-]/g, '')
      .replace(/\s+/g, '-');
  }

  /** Simple fuzzy score: subsequence match with bonus for contiguous / word starts. */
  function fuzzyScore(query, text) {
    if (!query) return 1;
    var q = query.toLowerCase();
    var t = text.toLowerCase();
    if (t.indexOf(q) !== -1) {
      return 100 - Math.min(40, t.indexOf(q)) + Math.min(20, q.length);
    }

    var ti = 0;
    var score = 0;
    var streak = 0;
    for (var qi = 0; qi < q.length; qi++) {
      var ch = q.charAt(qi);
      var found = -1;
      for (var j = ti; j < t.length; j++) {
        if (t.charAt(j) === ch) {
          found = j;
          break;
        }
      }
      if (found === -1) return 0;
      if (found === ti) {
        streak += 1;
        score += 2 + streak;
      } else {
        streak = 0;
        score += 1;
      }
      if (found === 0 || /[\s\-_/]/.test(t.charAt(found - 1))) score += 3;
      ti = found + 1;
    }
    return score;
  }

  function filterEntries(query) {
    var q = (query || '').trim();
    if (!q) {
      return entries.slice(0, 12);
    }
    return entries
      .map(function (entry) {
        var titleScore = fuzzyScore(q, entry.title);
        var subScore = entry.subtitle ? fuzzyScore(q, entry.subtitle) * 0.4 : 0;
        return { entry: entry, score: Math.max(titleScore, subScore) };
      })
      .filter(function (row) {
        return row.score > 0;
      })
      .sort(function (a, b) {
        return b.score - a.score;
      })
      .slice(0, 20)
      .map(function (row) {
        return row.entry;
      });
  }

  function renderResults() {
    if (!resultsEl || !emptyEl) return;
    resultsEl.innerHTML = '';

    if (!filtered.length) {
      emptyEl.hidden = false;
      return;
    }
    emptyEl.hidden = true;

    filtered.forEach(function (entry, i) {
      var li = document.createElement('li');
      var a = document.createElement('a');
      a.href = entry.url;
      a.className = 'palette-item' + (i === activeIndex ? ' is-active' : '');
      a.setAttribute('data-index', String(i));

      var title = document.createElement('div');
      title.textContent = entry.title;
      a.appendChild(title);

      if (entry.subtitle) {
        var sub = document.createElement('div');
        sub.className = 'small';
        sub.style.color = 'var(--text-muted)';
        sub.style.fontSize = '0.8rem';
        sub.textContent = entry.subtitle;
        a.appendChild(sub);
      }

      li.appendChild(a);
      resultsEl.appendChild(li);
    });
  }

  function setActive(index) {
    if (!filtered.length) {
      activeIndex = 0;
      return;
    }
    activeIndex = (index + filtered.length) % filtered.length;
    var items = resultsEl.querySelectorAll('.palette-item');
    items.forEach(function (el, i) {
      el.classList.toggle('is-active', i === activeIndex);
    });
    var active = items[activeIndex];
    if (active && active.scrollIntoView) {
      active.scrollIntoView({ block: 'nearest' });
    }
  }

  function goToActive() {
    if (!filtered.length) return;
    var entry = filtered[activeIndex];
    if (entry && entry.url) {
      window.location.href = entry.url;
    }
  }

  function openPalette() {
    if (!backdrop) return;
    loadIndex().then(function () {
      filtered = filterEntries(input ? input.value : '');
      activeIndex = 0;
      renderResults();
    });
    backdrop.hidden = false;
    backdrop.classList.add('is-open');
    if (input) {
      input.value = input.value || '';
      setTimeout(function () {
        input.focus();
        input.select();
      }, 0);
    }
  }

  function closePalette() {
    if (!backdrop) return;
    backdrop.classList.remove('is-open');
    backdrop.hidden = true;
  }

  function isOpen() {
    return backdrop && backdrop.classList.contains('is-open');
  }

  function onInput() {
    filtered = filterEntries(input.value);
    activeIndex = 0;
    renderResults();
  }

  function onKeyDown(event) {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      if (isOpen()) closePalette();
      else openPalette();
      return;
    }

    if (!isOpen()) return;

    if (event.key === 'Escape') {
      event.preventDefault();
      closePalette();
      return;
    }
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setActive(activeIndex + 1);
      return;
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActive(activeIndex - 1);
      return;
    }
    if (event.key === 'Enter') {
      event.preventDefault();
      goToActive();
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    backdrop = document.getElementById('command-palette');
    input = document.getElementById('palette-input');
    resultsEl = document.getElementById('palette-results');
    emptyEl = document.getElementById('palette-empty');
    if (!backdrop || !input || !resultsEl) return;

    var toggle = document.getElementById('search-toggle');
    if (toggle) {
      toggle.addEventListener('click', function () {
        openPalette();
      });
    }

    input.addEventListener('input', onInput);

    backdrop.addEventListener('click', function (event) {
      if (event.target === backdrop) closePalette();
    });

    resultsEl.addEventListener('click', function (event) {
      var item = event.target.closest('.palette-item');
      if (!item) return;
      var idx = Number(item.getAttribute('data-index'));
      if (!Number.isNaN(idx)) activeIndex = idx;
    });

    document.addEventListener('keydown', onKeyDown);

    // Warm the index in the background after idle.
    if ('requestIdleCallback' in window) {
      requestIdleCallback(function () {
        loadIndex();
      });
    } else {
      setTimeout(loadIndex, 1500);
    }
  });
})();
