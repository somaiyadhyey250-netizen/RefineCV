/* ==========================================================
   RefineCV /improve/{analysisId} Refinement Workspace Logic
   Side-by-side bullet makeovers, fact-grounded rewrites,
   rationale callouts, individual copy and copy-all actions
   ========================================================== */

(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };

  var DEMO_IMPROVEMENT = {
    improvedSummary: "Dedicated professional with demonstrated experience in client coordination, student administration workflows, and digital records management. Proven ability to streamline document intake, maintain data accuracy across CRM platforms, and facilitate effective multi-stakeholder communication.",
    bulletImprovements: [
      {
        section: "Work Experience",
        originalBullet: "Handled student visa applications and coordinated with international clients.",
        improvedBullet: "Managed end-to-end student visa intake workflows, conducting initial document reviews and coordinating client communications across partner institutions.",
        explanation: "Replaces passive 'handled' with an active operational verb and clarifies the scope of responsibility without inventing unsupported metrics."
      },
      {
        section: "Work Experience",
        originalBullet: "Used Kondesk and Zoho CRM to keep track of student records.",
        improvedBullet: "Maintained accurate applicant profiles and communication logs within Kondesk and Zoho CRM, ensuring timely status tracking across active cases.",
        explanation: "Emphasizes record precision and proactive workflow tracking grounded strictly in the source tools."
      },
      {
        section: "Work Experience",
        originalBullet: "Assisted senior counselors with presentation and documentation.",
        improvedBullet: "Collaborated with senior advising staff to prepare candidate orientation briefings and assemble required regulatory visa filings.",
        explanation: "Clarifies specific deliverables and strengthens professional tone based on source responsibilities."
      }
    ],
    actionableChanges: [
      "Replace generic duty verbs (Handled, Used) with active operational verbs (Managed, Maintained, Collaborated).",
      "Document specific application volume numbers if verifiable records are available.",
      "Highlight specific software workflows (CRM logging, verification checklists) already performed in past roles."
    ]
  };

  var state = {
    analysisId: null,
    improvementData: null,
    isLoading: false
  };

  function renderImprovements(data, analysisId) {
    state.improvementData = data;

    if ($('summaryText')) $('summaryText').textContent = data.improvedSummary || 'No summary generated.';

    // Copy Summary button
    if ($('copySummaryBtn')) {
      $('copySummaryBtn').addEventListener('click', function () {
        window.copyText(data.improvedSummary, 'Summary copied to clipboard');
      });
    }

    // Render Makeovers
    var makeoversContainer = $('makeoversList');
    if (makeoversContainer) {
      makeoversContainer.innerHTML = '';
      var bullets = Array.isArray(data.bulletImprovements) ? data.bulletImprovements : [];

      if (!bullets.length) {
        makeoversContainer.innerHTML = '<p class="hint">No specific bullet improvements were generated for this resume.</p>';
      } else {
        bullets.forEach(function (b, index) {
          var item = document.createElement('div');
          item.className = 'makeover-item';

          var sectionName = b.section || 'Experience';
          var orig = b.original || b.originalBullet || '';
          var refined = b.improved || b.improvedBullet || '';
          var why = b.explanation || 'Reframes statement with active verb and quantified impact.';

          item.innerHTML = `
            <div class="makeover-head">
              <span class="makeover-pill">${window.escapeHTML(sectionName)}</span>
              <button type="button" class="btn btn-quiet btn-sm btn-copy-bullet" data-index="${index}">Copy</button>
            </div>
            <div class="diff-cols">
              <div class="diff-col diff-orig">
                <span class="diff-label">Original</span>
                <p class="diff-text">${window.escapeHTML(orig)}</p>
              </div>
              <div class="diff-col diff-refined">
                <span class="diff-label">Refined</span>
                <p class="diff-text"><strong>${window.escapeHTML(refined)}</strong></p>
              </div>
            </div>
            <div class="rationale-callout">
              <strong>Why this helps:</strong> ${window.escapeHTML(why)}
            </div>
          `;

          makeoversContainer.appendChild(item);
        });

        // Attach bullet copy listeners
        var copyBtns = makeoversContainer.querySelectorAll('.btn-copy-bullet');
        copyBtns.forEach(function (btn) {
          btn.addEventListener('click', function () {
            var idx = parseInt(btn.getAttribute('data-index'), 10);
            var targetBullet = bullets[idx] ? (bullets[idx].improved || bullets[idx].improvedBullet || '') : '';
            if (targetBullet) {
              window.copyText(targetBullet, 'Improved bullet copied');
            }
          });
        });
      }
    }

    // Actionable changes checklist
    var actionsUl = $('actionsList');
    if (actionsUl) {
      actionsUl.innerHTML = '';
      var actions = Array.isArray(data.actionableChanges) ? data.actionableChanges : [];
      actions.forEach(function (act) {
        var li = document.createElement('li');
        li.textContent = act;
        actionsUl.appendChild(li);
      });
    }

    // Copy All Improvements
    var copyAllBtn = $('copyAllBtn');
    if (copyAllBtn) {
      copyAllBtn.addEventListener('click', function () {
        var text = '=== REFINECV IMPROVED RESUME ===\n\n';
        text += 'PROFESSIONAL SUMMARY:\n' + (data.improvedSummary || '') + '\n\n';
        text += 'BULLET POINT MAKEOVERS:\n';
        var bullets = Array.isArray(data.bulletImprovements) ? data.bulletImprovements : [];
        bullets.forEach(function (b, i) {
          text += (i + 1) + '. ' + (b.improved || b.improvedBullet || '') + '\n';
        });
        window.copyText(text, 'All improvements copied to clipboard');
      });
    }

    // Reveal Copy All controls once improvements are available
    var copyBtn = $('copyAllBtn');
    if (copyBtn) copyBtn.hidden = false;
    var bottomCopy = $('bottomCopyAllBtn');
    if (bottomCopy) bottomCopy.hidden = false;
  }

  function renderImprovementError(message) {
    if ($('summaryText')) {
      $('summaryText').textContent = 'Unable to safely generate improvements from this session evidence.';
    }
    var makeoversContainer = $('makeoversList');
    if (makeoversContainer) {
      makeoversContainer.innerHTML = `
        <div class="report-card" style="padding: 24px; text-align: center; border: 1px solid var(--border-subtle);">
          <p style="color: var(--ink-2); font-weight: 500; margin-bottom: 8px;">${window.escapeHTML(message)}</p>
          <p class="hint">Cannot safely strengthen or rewrite claims without additional verifiable evidence.</p>
        </div>
      `;
    }
    var actionsUl = $('actionsList');
    if (actionsUl) {
      actionsUl.innerHTML = '<li>Review original resume for missing metrics or certifications before re-analyzing.</li>';
    }
    var copyBtn = $('copyAllBtn');
    if (copyBtn) copyBtn.hidden = true;
    var bottomCopy = $('bottomCopyAllBtn');
    if (bottomCopy) bottomCopy.hidden = true;
  }

  function setupBackNavigation(analysisId) {
    var metaMode = document.body.getAttribute('data-analysis-mode') || '';
    var session = window.RefineCVSession ? window.RefineCVSession.get(analysisId) : null;
    var isJob = (metaMode === 'SPECIFIC_JOB') || (session && session.mode === 'SPECIFIC_JOB');
    var fromHistory = sessionStorage.getItem('refinecv_from_' + analysisId) === 'history';
    var targetUrl = (isJob ? ('/job-analysis/' + encodeURIComponent(analysisId)) : ('/analysis/' + encodeURIComponent(analysisId))) + (fromHistory ? '?from=history' : '');

    ['topBackLink', 'loadingBackBtn', 'bottomBackToReportBtn'].forEach(function (id) {
      var el = $(id);
      if (el) {
        el.setAttribute('href', targetUrl);
        el.textContent = '← Back to Analysis';
      }
    });
  }

  document.addEventListener('DOMContentLoaded', async function () {
    var metaId = document.body.getAttribute('data-analysis-id') || 'demo';
    state.analysisId = metaId;
    setupBackNavigation(metaId);

    // Ensure completed-state actions remain hidden during loading
    var copyBtn = $('copyAllBtn');
    if (copyBtn) copyBtn.hidden = true;
    var bottomCopy = $('bottomCopyAllBtn');
    if (bottomCopy) bottomCopy.hidden = true;

    if (metaId === 'demo') {
      renderImprovements(DEMO_IMPROVEMENT, 'demo');
      return;
    }

    var loadingEl = $('loadingState');
    var contentEl = $('contentState');

    // 1. Check client-side cached improvement
    var cached = window.RefineCVSession ? window.RefineCVSession.getImprovement(metaId) : null;
    if (cached) {
      if (loadingEl) loadingEl.hidden = true;
      if (contentEl) contentEl.hidden = false;
      renderImprovements(cached, metaId);
      return;
    }

    // 2. Check server-injected initial improvement if available
    if (window.__INITIAL_IMPROVEMENT__) {
      if (loadingEl) loadingEl.hidden = true;
      if (contentEl) contentEl.hidden = false;
      if (window.RefineCVSession) window.RefineCVSession.saveImprovement(metaId, window.__INITIAL_IMPROVEMENT__);
      renderImprovements(window.__INITIAL_IMPROVEMENT__, metaId);
      return;
    }

    if (loadingEl) loadingEl.hidden = false;
    if (contentEl) contentEl.hidden = true;

    try {
      var res = await fetch('/improve', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ analysisId: metaId })
      });

      if (!res.ok) {
        throw new Error('Failed to generate improvements from session (' + res.status + ').');
      }

      var improvement = await res.json();
      if (window.RefineCVSession) window.RefineCVSession.saveImprovement(metaId, improvement);
      if (loadingEl) loadingEl.hidden = true;
      if (contentEl) contentEl.hidden = false;
      renderImprovements(improvement, metaId);

    } catch (e) {
      console.warn('Improvement request failed, displaying limitation notice', e);
      if (loadingEl) loadingEl.hidden = true;
      if (contentEl) contentEl.hidden = false;
      renderImprovementError(e.message || 'Cannot safely strengthen claims without additional evidence.');
    }
  });

})();
