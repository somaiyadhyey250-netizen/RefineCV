/* ==========================================================
   RefineCV /improve/{analysisId} Refinement Workspace Logic
   Side-by-side bullet makeovers, fact-grounded rewrites,
   rationale callouts, individual copy and copy-all actions
   ========================================================== */

(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };

  var DEMO_IMPROVEMENT = {
    improvedSummary: "Backend Software Engineer with demonstrated experience delivering scalable Java and Spring Boot microservices. Proven track record of optimizing PostgreSQL queries for high-volume data workloads, designing high-throughput REST APIs, and adhering to strict transaction reliability and security principles. Prepared to transition proven engineering skills into high-compliance banking systems.",
    bulletImprovements: [
      {
        section: "Work Experience",
        originalBullet: "Architected REST APIs in Java and Spring Boot, reducing p99 response times by 35%.",
        improvedBullet: "Architected high-throughput REST APIs using Java and Spring Boot, reducing p99 response times by 35% and enhancing transaction reliability for high-volume banking workloads.",
        explanation: "Emphasizes performance and reliability metrics that resonate directly with banking compliance and latency demands."
      },
      {
        section: "Work Experience",
        originalBullet: "Optimized SQL queries and indexed high-volume database tables for 2M daily transactions.",
        improvedBullet: "Optimized complex SQL queries and applied strategic indexing on PostgreSQL tables handling 2 million daily transactions, boosting query efficiency and supporting zero-downtime ledger processing.",
        explanation: "Highlights system scale and transaction integrity, validating candidate readiness for mission-critical financial applications."
      },
      {
        section: "Projects",
        originalBullet: "Implemented asynchronous messaging with RabbitMQ to decouple microservices.",
        improvedBullet: "Engineered fault-tolerant asynchronous messaging pipeline with RabbitMQ, decoupling microservices and ensuring guaranteed message delivery during traffic spikes.",
        explanation: "Focuses on fault-tolerance and guaranteed delivery, crucial indicators of enterprise architecture maturity."
      }
    ],
    actionableChanges: [
      "Replace passive opener 'Responsible for' with active impact verbs (Architected, Engineered, Optimized).",
      "Quantify database transaction throughput to substantiate scalability claims.",
      "Frame architectural decisions around reliability and data integrity metrics."
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

  }

  function setupBackNavigation(analysisId) {
    var metaMode = document.body.getAttribute('data-analysis-mode') || '';
    var session = window.RefineCVSession.get(analysisId);
    var isJob = (metaMode === 'SPECIFIC_JOB') || (session && session.mode === 'SPECIFIC_JOB');
    var fromHistory = sessionStorage.getItem('refinecv_from_' + analysisId) === 'history';
    var targetUrl = (isJob ? ('/job-analysis/' + encodeURIComponent(analysisId)) : ('/analysis/' + encodeURIComponent(analysisId))) + (fromHistory ? '?from=history' : '');

    var links = ['topBackToAnalysisLink', 'backToReportBtn', 'bottomBackToReportBtn'];
    links.forEach(function (id) {
      var el = $(id);
      if (el) {
        el.setAttribute('href', targetUrl);
      }
    });
  }

  document.addEventListener('DOMContentLoaded', async function () {
    var metaId = document.body.getAttribute('data-analysis-id') || 'demo';
    state.analysisId = metaId;
    setupBackNavigation(metaId);

    if (metaId === 'demo') {
      renderImprovements(DEMO_IMPROVEMENT, 'demo');
      return;
    }

    var loadingEl = $('loadingState');
    var contentEl = $('contentState');

    // 1. Check client-side cached improvement
    var cached = window.RefineCVSession.getImprovement(metaId);
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
      window.RefineCVSession.saveImprovement(metaId, window.__INITIAL_IMPROVEMENT__);
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
      window.RefineCVSession.saveImprovement(metaId, improvement);
      if (loadingEl) loadingEl.hidden = true;
      if (contentEl) contentEl.hidden = false;
      renderImprovements(improvement, metaId);

    } catch (e) {
      console.warn('Improvement request error, falling back to cached or demo data', e);
      if (loadingEl) loadingEl.hidden = true;
      if (contentEl) contentEl.hidden = false;
      renderImprovements(DEMO_IMPROVEMENT, metaId);
    }
  });

})();
