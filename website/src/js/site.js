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

  function normalizeCodeBlocks() {
    var dedentFn = window.smileDedent;
    if (!dedentFn) return;

    document.querySelectorAll('pre > code').forEach(function (code) {
        var cleaned = dedentFn(code.textContent);
        if (cleaned !== code.textContent) {
          code.textContent = cleaned;
        }
      });
  }

  function initContactModal() {
    var modal = document.getElementById('contact-modal');
    if (!modal) return;

    var form = document.getElementById('order-request-form');
    var resultBox = document.getElementById('order-result');
    var modalTitle = document.getElementById('contact-modal-title');
    var modalSub = document.getElementById('contact-modal-sub');
    var modeInput = document.getElementById('order-mode');
    var modeTabs = modal.querySelectorAll('[data-contact-tab]');
    var firstNameInput = document.getElementById('order-first-name');
    var lastNameInput = document.getElementById('order-last-name');
    var emailInput = document.getElementById('order-email');
    var customerInput = document.getElementById('order-customer');
    var roleInput = document.getElementById('order-role');
    var companySizeSelect = document.getElementById('order-company-size');
    var orgTypeSelect = document.getElementById('order-org-type');
    var interestSelect = document.getElementById('order-interest');
    var demoSpecificWrap = document.getElementById('demo-specific-fields');
    var demoProductSelect = document.getElementById('demo-product');
    var pilotEnvWrap = document.getElementById('pilot-env-wrap');
    var pilotEnvSelect = document.getElementById('pilot-env');
    var pilotScaleWrap = document.getElementById('pilot-scale-wrap');
    var pilotScaleSelect = document.getElementById('pilot-scale');
    var tierWrap = document.getElementById('order-tier-wrap');
    var tierSelect = document.getElementById('order-tier');
    var nodesInput = document.getElementById('order-nodes');
    var seatsInput = document.getElementById('order-seats');
    var nodesWrap = document.getElementById('order-nodes-wrap');
    var seatsWrap = document.getElementById('order-seats-wrap');
    var billingWrap = document.getElementById('order-billing-wrap');
    var billingEmailInput = document.getElementById('order-billing-email');
    var nodesLabel = document.getElementById('order-nodes-label');
    var nodesHint = document.getElementById('order-nodes-hint');
    var seatsHint = document.getElementById('order-seats-hint');
    var notesInput = document.getElementById('order-notes');
    var summaryBox = document.getElementById('order-summary');
    var summaryTitle = document.getElementById('order-summary-title');
    var summaryAgreement = document.getElementById('order-summary-agreement');
    var summaryPrice = document.getElementById('order-summary-price');
    var errorEl = document.getElementById('order-error');
    var submitBtn = document.getElementById('order-submit-btn');
    var copyBtn = document.getElementById('order-copy-btn');
    var mailtoBtn = document.getElementById('order-mailto-btn');
    var resultBadge = document.getElementById('order-result-badge');
    var resultTitle = document.getElementById('order-result-title');
    var resultMessage = document.getElementById('order-result-message');
    var resultCode = document.getElementById('order-result-code');
    var closeBtns = modal.querySelectorAll('[data-contact-close]');
    var lastFocus = null;
    var activeMode = 'order';

    var MODE_CONFIG = {
      contact: {
        title: 'Contact Us',
        sub: 'HALO.AI LLC · Direct engineering, architecture & licensing inquiries',
        submitLabel: 'Send Inquiry',
        defaultInterest: 'AI Framework Integration & Open-Source Tooling',
        placeholder: 'Tell us about your technical requirements, licensing inquiries, or project scope...'
      },
      demo: {
        title: 'Get a Live Technical Demo',
        sub: 'See SMILE Core Engine, Serve Engine, and Agent Studio on your workload',
        submitLabel: 'Request Demo',
        defaultInterest: 'High-Performance Model Serving & Inference Infrastructure',
        placeholder: 'Share the models (.sml, .onnx, LLM), latency/throughput targets, or workflows you want to see in the demo...'
      },
      pilot: {
        title: 'Request a 30-Day Enterprise Pilot',
        sub: 'Run SMILE on your edge gateways, servers, or air-gapped network · 100% credited to annual contract',
        submitLabel: 'Request Pilot',
        defaultInterest: 'On-Premises / Hybrid Cloud Private AI Deployment',
        placeholder: 'Tell us where your nodes sit, what models you score or train today, and your target pilot timeline...'
      },
      order: {
        title: 'Request Commercial Order Form',
        sub: 'Administered by HALO.AI LLC · Standard Version 1.0 Agreements · Net-30 Invoicing & Offline Ed25519 License Keys',
        submitLabel: 'Request Order Form & Key',
        defaultInterest: 'Commercial Licensing & Enterprise Pricing Models',
        placeholder: 'Optional: target go-live date, OEM application product line name, Year 2 Maintenance preference, or PO / vendor onboarding instructions...'
      }
    };

    var TIER_DEFAULTS = {
      PRODUCTION_RUNTIME: { nodes: 4, seats: 5 },
      OEM: { nodes: 10, seats: 10 },
      ENTERPRISE: { nodes: 100, seats: 25 },
      STUDIO_TEAM: { nodes: 0, seats: 5 },
      EVALUATION_PILOT: { nodes: 4, seats: 5 }
    };

    function formatUsd(amount) {
      return '$' + amount.toLocaleString('en-US');
    }

    function setMode(mode, preferredTier) {
      activeMode = MODE_CONFIG[mode] ? mode : 'contact';
      var cfg = MODE_CONFIG[activeMode];
      if (modeInput) modeInput.value = activeMode;
      if (modalTitle) modalTitle.textContent = cfg.title;
      if (modalSub) modalSub.textContent = cfg.sub;
      if (submitBtn) submitBtn.textContent = cfg.submitLabel;
      if (notesInput) notesInput.placeholder = cfg.placeholder;
      if (interestSelect && cfg.defaultInterest) {
        interestSelect.value = cfg.defaultInterest;
      }

      modeTabs.forEach(function (tab) {
        var isMatch = tab.getAttribute('data-contact-tab') === activeMode;
        tab.classList.toggle('is-active', isMatch);
        tab.setAttribute('aria-selected', isMatch ? 'true' : 'false');
      });

      var isDemo = activeMode === 'demo';
      var isPilot = activeMode === 'pilot';
      var isOrder = activeMode === 'order';

      if (demoSpecificWrap) demoSpecificWrap.hidden = !isDemo;
      if (pilotEnvWrap) pilotEnvWrap.hidden = !isPilot;
      if (pilotScaleWrap) pilotScaleWrap.hidden = !isPilot;
      if (tierWrap) tierWrap.hidden = !isOrder;
      if (billingWrap) billingWrap.hidden = !isOrder;
      if (summaryBox) summaryBox.hidden = !isOrder;

      if (isOrder) {
        if (tierSelect && preferredTier && TIER_DEFAULTS[preferredTier]) {
          tierSelect.value = preferredTier;
          updateSummary(true);
        } else {
          updateSummary(false);
        }
      } else {
        if (nodesWrap) nodesWrap.hidden = true;
        if (seatsWrap) seatsWrap.hidden = true;
      }
    }

    function updateSummary(resetDefaults) {
      if (!tierSelect || activeMode !== 'order') return;
      var tier = tierSelect.value;
      var defaults = TIER_DEFAULTS[tier] || TIER_DEFAULTS.PRODUCTION_RUNTIME;

      if (resetDefaults) {
        if (nodesInput) nodesInput.value = String(defaults.nodes);
        if (seatsInput) seatsInput.value = String(defaults.seats);
      }

      var nodes = Math.max(0, parseInt(nodesInput ? nodesInput.value : '0', 10) || 0);
      var seats = Math.max(1, parseInt(seatsInput ? seatsInput.value : '1', 10) || 1);

      if (tier === 'PRODUCTION_RUNTIME') {
        if (nodesWrap) nodesWrap.hidden = false;
        if (seatsWrap) seatsWrap.hidden = false;
        if (nodesLabel) nodesLabel.textContent = 'Production Server Nodes';
        if (nodesHint) nodesHint.textContent = '4 nodes included in base pack (+$3,000/node/yr above 4)';
        if (seatsHint) seatsHint.textContent = '5 Studio seats bundled free (+$995/seat/yr above 5)';
        var extraNodes = Math.max(0, nodes - 4);
        var extraSeats = Math.max(0, seats - 5);
        var total = 12000 + extraNodes * 3000 + extraSeats * 995;
        if (summaryTitle) summaryTitle.textContent = 'Production Runtime (' + Math.max(4, nodes) + ' Nodes + ' + Math.max(5, seats) + ' Studio Seats)';
        if (summaryAgreement) summaryAgreement.textContent = 'Governing Agreement: Serve Engine Commercial License 1.0 (§3.5 includes Core + Studio)';
        if (summaryPrice) summaryPrice.innerHTML = formatUsd(total) + ' <span>/ year</span>';
      } else if (tier === 'OEM') {
        if (nodesWrap) nodesWrap.hidden = false;
        if (seatsWrap) seatsWrap.hidden = false;
        if (nodesLabel) nodesLabel.textContent = 'Developer Build Seats';
        if (nodesInput && resetDefaults) nodesInput.value = '10';
        var devSeats = Math.max(1, parseInt(nodesInput ? nodesInput.value : '10', 10) || 10);
        if (nodesHint) nodesHint.textContent = 'Up to 10 dev build seats included (+$995/seat above 10)';
        if (seatsHint) seatsHint.textContent = '10 Studio seats included in Year 1 Maintenance (+$995/seat above 10)';
        var extraDevs = Math.max(0, devSeats - 10);
        var extraStudio = Math.max(0, seats - 10);
        var oemTotal = 25000 + (extraDevs + extraStudio) * 995;
        if (summaryTitle) summaryTitle.textContent = 'OEM & Embedded Distribution (1 App Line · ' + Math.max(10, devSeats) + ' Dev Seats · ' + Math.max(10, seats) + ' Studio Seats)';
        if (summaryAgreement) summaryAgreement.textContent = 'Governing Agreement: Core Engine Commercial License 1.0 (Perpetual + Optional 20% Yr 2 Maintenance)';
        if (summaryPrice) summaryPrice.innerHTML = formatUsd(oemTotal) + ' <span>one-time</span>';
      } else if (tier === 'ENTERPRISE') {
        if (nodesWrap) nodesWrap.hidden = false;
        if (seatsWrap) seatsWrap.hidden = false;
        if (nodesLabel) nodesLabel.textContent = 'Estimated Edge / Server Nodes';
        if (nodesHint) nodesHint.textContent = 'Volume brackets scale from $50 down to $10 / edge node / mo';
        if (seatsHint) seatsHint.textContent = '25 Studio seats included with Enterprise & Edge Fleet';
        if (summaryTitle) summaryTitle.textContent = 'Enterprise & Edge Fleet (' + Math.max(100, nodes) + ' Edge Nodes + ' + Math.max(25, seats) + ' Studio Seats)';
        if (summaryAgreement) summaryAgreement.textContent = 'Governing Agreements: Serve, Core & Studio Commercial Licenses 1.0';
        if (summaryPrice) summaryPrice.innerHTML = 'From $50,000 <span>/ year</span>';
      } else if (tier === 'STUDIO_TEAM') {
        if (nodesWrap) nodesWrap.hidden = true;
        if (seatsWrap) seatsWrap.hidden = false;
        if (seatsHint) seatsHint.textContent = '$995 / seat / yr paid annually (5-seat minimum for standalone orders)';
        var billableSeats = Math.max(5, seats);
        var studioTotal = billableSeats * 995;
        if (summaryTitle) summaryTitle.textContent = 'SMILE Agent Studio Team (' + billableSeats + ' Authorized User Seats)';
        if (summaryAgreement) summaryAgreement.textContent = 'Governing Agreement: Agent Studio Commercial License 1.0';
        if (summaryPrice) summaryPrice.innerHTML = formatUsd(studioTotal) + ' <span>/ year</span>';
      } else if (tier === 'EVALUATION_PILOT') {
        if (nodesWrap) nodesWrap.hidden = false;
        if (seatsWrap) seatsWrap.hidden = false;
        if (nodesLabel) nodesLabel.textContent = 'Target Pilot Nodes';
        if (nodesHint) nodesHint.textContent = '30-day Studio commercial trial ($0) or $15k Turnkey Edge Pilot (100% credited)';
        if (seatsHint) seatsHint.textContent = 'Authorized User seats for evaluation team';
        if (summaryTitle) summaryTitle.textContent = '30-Day Commercial Trial / Turnkey Enterprise Pilot';
        if (summaryAgreement) summaryAgreement.textContent = 'Includes signed offline Ed25519 trial key (SMILE-STUDIO-v1)';
        if (summaryPrice) summaryPrice.innerHTML = 'Trial / Pilot <span>30 days</span>';
      }
    }

    function open(preferredMode, preferredTier) {
      lastFocus = document.activeElement;
      if (form) form.hidden = false;
      if (resultBox) resultBox.hidden = true;
      if (errorEl) errorEl.hidden = true;

      var resolvedMode = preferredMode || (preferredTier ? 'order' : 'contact');
      setMode(resolvedMode, preferredTier);

      var drawer = document.getElementById('mobile-drawer');
      var menuBtn = document.getElementById('menu-toggle');
      if (drawer) drawer.classList.remove('is-open');
      if (menuBtn) {
        menuBtn.setAttribute('aria-expanded', 'false');
        menuBtn.setAttribute('aria-label', 'Open menu');
      }

      modal.hidden = false;
      modal.classList.add('is-open');
      document.body.classList.add('contact-open');
      if (firstNameInput) firstNameInput.focus();
    }

    function close() {
      if (!modal.classList.contains('is-open')) return;
      modal.classList.remove('is-open');
      modal.hidden = true;
      document.body.classList.remove('contact-open');
      if (lastFocus && typeof lastFocus.focus === 'function') {
        lastFocus.focus();
      }
    }

    modeTabs.forEach(function (tab) {
      tab.addEventListener('click', function () {
        setMode(tab.getAttribute('data-contact-tab'));
      });
    });

    if (tierSelect) {
      tierSelect.addEventListener('change', function () { updateSummary(true); });
    }
    if (nodesInput) {
      nodesInput.addEventListener('input', function () { updateSummary(false); });
    }
    if (seatsInput) {
      seatsInput.addEventListener('input', function () { updateSummary(false); });
    }

    document.querySelectorAll('[data-contact-open]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var mode = btn.getAttribute('data-contact-mode');
        var tier = btn.getAttribute('data-contact-tier');
        open(mode, tier);
      });
    });

    closeBtns.forEach(function (btn) {
      btn.addEventListener('click', close);
    });
    modal.addEventListener('click', function (event) {
      if (event.target === modal) close();
    });
    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') close();
    });

    function buildOrderSummaryText(payload, orderId) {
      var header = payload.requestType === 'demo'
        ? 'SMILE DEMO REQUEST (' + orderId + ')'
        : payload.requestType === 'pilot'
        ? 'SMILE 30-DAY PILOT REQUEST (' + orderId + ')'
        : payload.requestType === 'order'
        ? 'SMILE COMMERCIAL ORDER REQUEST (' + orderId + ')'
        : 'SMILE CONTACT INQUIRY (' + orderId + ')';

      var lines = [
        header,
        '============================================================',
        'Reference ID      : ' + orderId,
        'Request Type      : ' + payload.requestType.toUpperCase(),
        'Submitted At      : ' + payload.submittedAt,
        'Name              : ' + payload.firstName + ' ' + payload.lastName,
        'Work Email        : ' + payload.email,
        'Job Title / Role  : ' + payload.jobTitle,
        'Company / Org     : ' + payload.customer,
        'Organization Type : ' + payload.organizationType,
        'Company Size      : ' + payload.companySize,
        'Primary Interest  : ' + payload.primaryInterest
      ];

      if (payload.requestType === 'demo' && payload.demoProduct) {
        lines.push('Demo Focus        : ' + payload.demoProduct);
      } else if (payload.requestType === 'pilot') {
        lines.push('Pilot Environment : ' + (payload.pilotEnvironment || 'On-Premises'));
        lines.push('Pilot Program     : ' + (payload.pilotScale || '30-Day Pilot'));
      } else if (payload.requestType === 'order') {
        lines.push('Commercial Tier   : ' + payload.tier);
        lines.push('Estimated Quote   : ' + payload.estimatedPrice);
        lines.push('Billing Contact   : ' + (payload.billingEmail || payload.email));
        lines.push('Nodes / Devs      : ' + payload.nodes);
        lines.push('Studio Seats      : ' + payload.seats);
      }

      lines.push('Requirements/Scope: ' + (payload.notes || 'None'));
      lines.push('============================================================');
      return lines.join('\n');
    }

    function showResult(payload, orderId, deliveredOnline) {
      var text = buildOrderSummaryText(payload, orderId);
      if (form) form.hidden = true;
      if (resultBox) resultBox.hidden = false;
      if (resultCode) resultCode.textContent = text;

      var typeLabel = payload.requestType === 'demo' ? 'Demo Request'
        : payload.requestType === 'pilot' ? 'Pilot Request'
        : payload.requestType === 'order' ? 'Order Request'
        : 'Inquiry';
      var subject = encodeURIComponent('SMILE ' + typeLabel + ' [' + orderId + '] — ' + payload.customer);
      var body = encodeURIComponent(text);
      if (mailtoBtn) {
        mailtoBtn.href = 'mailto:sales@aihalo.dev?subject=' + subject + '&body=' + body;
      }

      if (deliveredOnline) {
        if (resultBadge) resultBadge.textContent = 'Received · ' + orderId;
        if (resultTitle) resultTitle.textContent = typeLabel + ' Logged (' + orderId + ')';
        if (resultMessage) {
          resultMessage.textContent = 'We have received your ' + typeLabel.toLowerCase() + ' and sent a confirmation to ' + payload.email + '. Our team will respond within 1 business day.';
        }
      } else {
        if (resultBadge) resultBadge.textContent = 'Reference · ' + orderId;
        if (resultTitle) resultTitle.textContent = 'Send Your ' + typeLabel + ' (' + orderId + ')';
        if (resultMessage) {
          resultMessage.textContent = 'Your structured ' + typeLabel.toLowerCase() + ' is ready below. Click "Send via Email Client" (or copy the summary to sales@aihalo.dev) to complete your request.';
        }
      }
    }

    if (copyBtn) {
      copyBtn.addEventListener('click', function () {
        var text = resultCode ? resultCode.textContent : '';
        copyText(text || '').then(function () {
          copyBtn.textContent = 'Copied!';
          setTimeout(function () {
            copyBtn.textContent = 'Copy Summary';
          }, 1800);
        });
      });
    }

    if (form) {
      form.addEventListener('submit', function (event) {
        event.preventDefault();
        if (errorEl) errorEl.hidden = true;

        var honeypot = form.querySelector('[name="website_url"]');
        if (honeypot && honeypot.value) return;

        var firstName = (firstNameInput ? firstNameInput.value : '').trim();
        var lastName = (lastNameInput ? lastNameInput.value : '').trim();
        var email = (emailInput ? emailInput.value : '').trim();
        var customer = (customerInput ? customerInput.value : '').trim();
        var jobTitle = (roleInput ? roleInput.value : '').trim();
        var companySize = companySizeSelect ? companySizeSelect.value : '';
        var organizationType = orgTypeSelect ? orgTypeSelect.value : '';
        var primaryInterest = interestSelect ? interestSelect.value : '';
        var billingEmail = (billingEmailInput ? billingEmailInput.value : '').trim();
        var tier = activeMode === 'order' ? (tierSelect ? tierSelect.value : 'PRODUCTION_RUNTIME')
          : activeMode === 'pilot' ? 'EVALUATION_PILOT'
          : 'GENERAL_INQUIRY';
        var nodes = parseInt(nodesInput ? nodesInput.value : '4', 10) || 4;
        var seats = parseInt(seatsInput ? seatsInput.value : '5', 10) || 5;
        var notes = (notesInput ? notesInput.value : '').trim();

        if (!firstName || !lastName || !email || email.indexOf('@') === -1 || !customer || !jobTitle) {
          if (errorEl) {
            errorEl.textContent = 'Please fill in First Name, Last Name, Work Email, Company / Organization Name, and Job Title / Role.';
            errorEl.hidden = false;
          }
          return;
        }

        var now = new Date();
        var ymd = now.toISOString().slice(0, 10).replace(/-/g, '');
        var rand = String(Math.floor(100 + Math.random() * 900));
        var prefix = activeMode === 'demo' ? 'DEMO' : activeMode === 'pilot' ? 'PILOT' : activeMode === 'order' ? 'ORD' : 'INQ';
        var fallbackOrderId = prefix + '-' + ymd.slice(0, 4) + '-' + ymd.slice(4, 8) + '-' + rand;

        var payload = {
          orderId: fallbackOrderId,
          requestType: activeMode,
          firstName: firstName,
          lastName: lastName,
          contactName: firstName + ' ' + lastName + ', ' + jobTitle,
          jobTitle: jobTitle,
          email: email,
          billingEmail: billingEmail,
          customer: customer,
          organizationType: organizationType,
          companySize: companySize,
          primaryInterest: primaryInterest,
          demoProduct: demoProductSelect ? demoProductSelect.value : '',
          pilotEnvironment: pilotEnvSelect ? pilotEnvSelect.value : '',
          pilotScale: pilotScaleSelect ? pilotScaleSelect.value : '',
          tier: tier,
          nodes: nodes,
          seats: seats,
          estimatedPrice: activeMode === 'order' && summaryPrice ? summaryPrice.textContent.trim() : '',
          notes: notes,
          sourcePage: window.location.pathname,
          submittedAt: now.toISOString()
        };

        var endpoint = form.getAttribute('data-endpoint');
        var defaultBtnLabel = (MODE_CONFIG[activeMode] || MODE_CONFIG.contact).submitLabel;
        if (submitBtn) {
          submitBtn.disabled = true;
          submitBtn.textContent = 'Submitting…';
        }

        var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
        var timer = controller ? setTimeout(function () { controller.abort(); }, 6000) : null;

        fetch(endpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
          body: JSON.stringify(payload),
          signal: controller ? controller.signal : undefined
        })
          .then(function (res) {
            if (timer) clearTimeout(timer);
            if (!res.ok) throw new Error('HTTP ' + res.status);
            return res.json();
          })
          .then(function (data) {
            if (submitBtn) {
              submitBtn.disabled = false;
              submitBtn.textContent = defaultBtnLabel;
            }
            showResult(payload, (data && data.orderId) ? data.orderId : fallbackOrderId, true);
          })
          .catch(function () {
            if (timer) clearTimeout(timer);
            if (submitBtn) {
              submitBtn.disabled = false;
              submitBtn.textContent = defaultBtnLabel;
            }
            showResult(payload, fallbackOrderId, false);
          });
      });
    }

    // Auto-open modal if URL contains ?mode=..., ?order=1, or ?tier=...
    try {
      var params = new URLSearchParams(window.location.search);
      var urlMode = params.get('mode');
      var urlTier = params.get('tier');
      if (urlTier && TIER_DEFAULTS[urlTier]) {
        open('order', urlTier);
      } else if (urlMode && MODE_CONFIG[urlMode]) {
        open(urlMode);
      } else if (params.get('order') === '1') {
        open('order', 'PRODUCTION_RUNTIME');
      }
    } catch (e) { /* ignore */ }
  }

  function initImageLightbox() {
    var images = document.querySelectorAll('img.enlarge');
    if (!images.length) return;

    var backdrop = document.createElement('div');
    backdrop.className = 'lightbox-backdrop';
    backdrop.setAttribute('role', 'dialog');
    backdrop.setAttribute('aria-modal', 'true');
    backdrop.setAttribute('aria-label', 'Enlarged image');
    backdrop.hidden = true;

    var stage = document.createElement('div');
    stage.className = 'lightbox-stage';
    var full = document.createElement('img');
    full.alt = '';
    stage.appendChild(full);
    backdrop.appendChild(stage);
    document.body.appendChild(backdrop);

    var lastFocus = null;

    function open(src, alt) {
      lastFocus = document.activeElement;
      full.src = src;
      full.alt = alt || '';
      backdrop.hidden = false;
      backdrop.classList.add('is-open');
      document.body.classList.add('lightbox-open');
      backdrop.focus();
    }

    function close() {
      if (!backdrop.classList.contains('is-open')) return;
      backdrop.classList.remove('is-open');
      backdrop.hidden = true;
      document.body.classList.remove('lightbox-open');
      full.removeAttribute('src');
      if (lastFocus && typeof lastFocus.focus === 'function') {
        lastFocus.focus();
      }
    }

    images.forEach(function (img) {
      if (!img.hasAttribute('tabindex')) img.tabIndex = 0;
      if (!img.getAttribute('role')) img.setAttribute('role', 'button');
      img.setAttribute('aria-label', (img.alt || 'Image') + ' (click to enlarge)');

      img.addEventListener('click', function () {
        open(img.currentSrc || img.src, img.alt);
      });
      img.addEventListener('keydown', function (event) {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          open(img.currentSrc || img.src, img.alt);
        }
      });
    });

    backdrop.tabIndex = -1;
    backdrop.addEventListener('click', function (event) {
      if (event.target !== full) close();
    });
    full.addEventListener('click', function (event) {
      event.stopPropagation();
    });
    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') close();
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    normalizeCodeBlocks();

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
    initImageLightbox();
    initContactModal();
  });
})();
