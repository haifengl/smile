/*!
 * Vanilla TOC builder (replaces jQuery samaxesJS plugin).
 */
(function (global) {
  'use strict';

  const DEFAULTS = {
    exclude: 'h1, h5, h6',
    context: '',
    autoId: false,
    numerate: true,
  };

  function generateId(text) {
    return text.replace(/[ <#\/\\?&]/g, '_');
  }

  function parseExclude(exclude) {
    return exclude.split(',').map((s) => s.trim().toLowerCase()).filter(Boolean);
  }

  function isExcluded(tag, excluded) {
    return excluded.includes(tag.toLowerCase());
  }

  function headerLevel(tag) {
    return parseInt(tag.slice(1), 10);
  }

  function buildToc(root, options) {
    const opts = { ...DEFAULTS, ...options };
    const excluded = parseExclude(opts.exclude);
    const scope = opts.context ? document.querySelector(opts.context) : document;
    if (!scope) return;

    root.innerHTML = '';
    const topUl = document.createElement('ul');
    root.appendChild(topUl);

    const headers = { h1: 0, h2: 0, h3: 0, h4: 0, h5: 0, h6: 0 };
    const indexes = { h1: 0, h2: 0, h3: 0, h4: 0, h5: 0, h6: 0 };
    let index = 0;
    for (let i = 1; i <= 6; i++) {
      const tag = `h${i}`;
      indexes[tag] = !isExcluded(tag, excluded) && scope.querySelectorAll(tag).length > 0 ? ++index : 0;
    }

    const headingNodes = scope.querySelectorAll('h1, h2, h3, h4, h5, h6');
    headingNodes.forEach((el) => {
      const tag = el.tagName.toLowerCase();
      if (isExcluded(tag, excluded)) return;

      const level = headerLevel(tag);
      if (opts.numerate) {
        checkContainer(headers[level], topUl);
        updateNumeration(headers, tag);
        if (opts.autoId && !el.id) {
          el.id = generateId(el.textContent.trim());
        }
        el.textContent = addNumeration(headers, tag, el.textContent);
      } else if (opts.autoId && !el.id) {
        el.id = generateId(el.textContent.trim());
      }

      appendToToc(topUl, indexes[tag], el.id, el.textContent.trim());
    });
  }

  function checkContainer(headerCount, toc) {
    const last = toc.lastElementChild;
    if (headerCount === 0 && last && last.tagName !== 'UL') {
      const li = toc.querySelector('li:last-child');
      if (li && !li.querySelector('ul')) {
        li.insertAdjacentHTML('beforeend', '<ul></ul>');
      }
    }
  }

  function updateNumeration(headers, header) {
    Object.keys(headers).forEach((key) => {
      if (key === header) {
        headers[key] += 1;
      } else if (headerLevel(key) > headerLevel(header)) {
        headers[key] = 0;
      }
    });
  }

  function addNumeration(headers, header, text) {
    let numeration = '';
    Object.keys(headers).forEach((key) => {
      if (headerLevel(key) <= headerLevel(header) && headers[key] > 0) {
        numeration += `${headers[key]}.`;
      }
    });
    return `${numeration} ${text}`.trim();
  }

  function appendToToc(toc, depthIndex, id, text) {
    let parent = toc;
    for (let i = 1; i < depthIndex; i++) {
      let lastLi = parent.querySelector(':scope > li:last-child');
      if (!lastLi) {
        lastLi = document.createElement('li');
        parent.appendChild(lastLi);
      }
      let childUl = lastLi.querySelector(':scope > ul');
      if (!childUl) {
        childUl = document.createElement('ul');
        lastLi.appendChild(childUl);
      }
      parent = childUl;
    }

    const li = document.createElement('li');
    if (!id) {
      li.textContent = text;
    } else {
      const a = document.createElement('a');
      a.href = `#${id}`;
      a.className = 'scroll';
      a.textContent = text;
      li.appendChild(a);
    }
    parent.appendChild(li);
  }

  global.SmileTOC = {
    build(selector, options) {
      const root = typeof selector === 'string' ? document.querySelector(selector) : selector;
      if (root) buildToc(root, options);
    },
  };
})(window);
