/*
 * Render Graphviz DOT sources (script.dot-source) into SVG via @viz-js/viz.
 * Used on graph.html; no-ops when no .dot-preview elements exist.
 */
import { instance } from 'https://cdn.jsdelivr.net/npm/@viz-js/viz@3.30.0/+esm';

function readDot(el) {
  var source = el.querySelector('.dot-source');
  var text = source ? source.textContent : el.textContent;
  return (text || '').trim();
}

function showError(el, err) {
  el.classList.add('is-error');
  el.textContent = 'Could not render graph: ' + (err && err.message ? err.message : String(err));
}

function prepareSvg(svg) {
  if (!svg.getAttribute('preserveAspectRatio')) {
    svg.setAttribute('preserveAspectRatio', 'xMidYMid meet');
  }
  // Keep Graphviz intrinsic width/height so node sizes stay consistent.
}

/**
 * Side-by-side pairs: keep Graphviz node size (no per-diagram upscaling).
 * Only shrink the whole pair uniformly if it would wrap, then pad preview
 * panels to the taller diagram's height.
 */
function equalizeDotRows() {
  document.querySelectorAll('.dot-row').forEach(function (row) {
    var previews = Array.prototype.slice.call(row.querySelectorAll('.dot-preview'));
    if (previews.length < 2) return;

    previews.forEach(function (preview) {
      preview.style.minHeight = '';
      var svg = preview.querySelector('svg');
      if (!svg) return;
      svg.style.height = '';
      svg.style.width = '';
      svg.style.maxWidth = '';
    });

    var metrics = previews.map(function (preview) {
      var svg = preview.querySelector('svg');
      var box = svg ? svg.getBoundingClientRect() : { width: 0, height: 0 };
      return {
        preview: preview,
        svg: svg,
        width: box.width,
        height: box.height,
      };
    });

    var gap = parseFloat(window.getComputedStyle(row).columnGap || window.getComputedStyle(row).gap) || 16;
    var chrome = gap * (metrics.length - 1);
    previews.forEach(function (preview) {
      var ps = window.getComputedStyle(preview);
      chrome += (parseFloat(ps.paddingLeft) || 0) + (parseFloat(ps.paddingRight) || 0);
      chrome += (parseFloat(ps.borderLeftWidth) || 0) + (parseFloat(ps.borderRightWidth) || 0);
    });

    var available = row.clientWidth;
    var content = metrics.reduce(function (sum, m) { return sum + m.width; }, 0) + chrome;
    var fit = available > 0 && content > available ? available / content : 1;

    // Uniform shrink only — both diagrams keep the same node size.
    if (fit < 1) {
      metrics.forEach(function (m) {
        if (!m.svg || m.height <= 0) return;
        m.width *= fit;
        m.height *= fit;
        m.svg.style.width = m.width + 'px';
        m.svg.style.height = m.height + 'px';
        m.svg.style.maxWidth = 'none';
      });
    }

    // Equal panel height via padding, not SVG scaling.
    var target = 0;
    previews.forEach(function (preview) {
      var h = preview.getBoundingClientRect().height;
      if (h > target) target = h;
    });
    previews.forEach(function (preview) {
      preview.style.minHeight = target + 'px';
    });
  });
}

instance()
  .then(function (viz) {
    document.querySelectorAll('.dot-preview').forEach(function (el) {
      var dot = readDot(el);
      if (!dot) return;
      try {
        var engine = el.getAttribute('data-engine') || 'dot';
        var svg = viz.renderSVGElement(dot, { engine: engine });
        prepareSvg(svg);
        svg.setAttribute('role', 'img');
        var label = el.getAttribute('aria-label');
        if (label) svg.setAttribute('aria-label', label);
        el.replaceChildren(svg);
        el.classList.add('is-ready');
      } catch (err) {
        showError(el, err);
      }
    });
    equalizeDotRows();
    requestAnimationFrame(equalizeDotRows);
    window.addEventListener('resize', equalizeDotRows);
  })
  .catch(function (err) {
    document.querySelectorAll('.dot-preview').forEach(function (el) {
      showError(el, err);
    });
  });
