/* ==========================================================
   RefineCV /history Session History Logic
   Renders active session records across THREE distinct sections:
   1. Analyze (General & Job Analysis)
   2. Compare (Resume Comparisons)
   3. Interview Prep (Interview Prep from CV)
   Provides accessible "Clear History" confirmation and execution.
   ========================================================== */

(function () {
  'use strict';

  document.addEventListener('DOMContentLoaded', async function () {
    var historyList = document.getElementById('historyList');
    var emptyState = document.getElementById('emptyState');
    var clearHistoryBtn = document.getElementById('clearHistoryBtn');
    var clearConfirmDialog = document.getElementById('clearConfirmDialog');
    var cancelClearBtn = document.getElementById('cancelClearBtn');
    var confirmClearBtn = document.getElementById('confirmClearBtn');
    var historyFeedback = document.getElementById('historyFeedback');

    if (!historyList) return;

    var clientHistory = (window.RefineCVSession && window.RefineCVSession.getHistory()) || [];

    // Also fetch server-side active sessions from AnalysisSessionStore
    try {
      var res = await fetch('/api/history');
      if (res.ok) {
        var serverItems = await res.json();
        if (Array.isArray(serverItems)) {
          serverItems.forEach(function (si) {
            if (!si || !si.analysisId || si.analysisId === 'demo') return;
            if (si.type !== 'COMPARE' && si.type !== 'INTERVIEW_PREP' && String(si.analysisId).startsWith('cmp-')) return;
            if (si.fileName === 'Sample_Resume.pdf' || si.fileName === 'Uploaded_Resume.pdf') return;
            if (si.fileNameB === 'Sample_Resume.pdf' || si.fileNameB === 'Uploaded_Resume.pdf') return;

            var exists = clientHistory.some(function (ch) { return ch.analysisId === si.analysisId; });
            if (!exists) {
              clientHistory.push({
                analysisId: si.analysisId,
                type: si.type || 'ANALYSIS',
                mode: si.mode,
                fileName: si.fileName || 'Resume.pdf',
                fileNameB: si.fileNameB || null,
                score: si.score || 0,
                scoreB: si.scoreB != null ? si.scoreB : null,
                verdict: si.verdict || null,
                jobContext: si.jobContext || null,
                date: new Date(si.createdAt).toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' }) + ' ' + new Date(si.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
              });
            }
          });
        }
      }
    } catch (e) {
      console.warn('Could not fetch server history, using local history', e);
    }

    function createAnalyzeCard(item) {
      var card = document.createElement('div');
      card.className = 'report-card';
      card.style.padding = '22px 28px';

      var isJobMode = item.mode === 'SPECIFIC_JOB';
      var reportUrl = (isJobMode ? ('/job-analysis/' + encodeURIComponent(item.analysisId)) : ('/analysis/' + encodeURIComponent(item.analysisId))) + '?from=history';

      card.innerHTML =
        '<div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px;">' +
          '<div>' +
            '<strong style="font-size: 1.1rem; display: block; margin-bottom: 2px;">' + window.escapeHTML(item.fileName || 'Resume.pdf') + '</strong>' +
            '<small style="color: var(--ink-3);">Conducted ' + window.escapeHTML(item.date || 'Recent') + ' &bull; ID: ' + window.escapeHTML(item.analysisId) + '</small>' +
          '</div>' +
          '<div style="display: flex; align-items: center; gap: 14px; flex-wrap: wrap;">' +
            '<span class="file-badge" style="font-weight: 700; color: var(--pen);">Score: ' + (item.score || 0) + '/100</span>' +
            '<span class="makeover-pill">' + (isJobMode ? 'Specific Job Match' : 'General Review') + '</span>' +
            '<a href="' + reportUrl + '" class="btn btn-primary btn-sm">View Report &rarr;</a>' +
          '</div>' +
        '</div>';
      return card;
    }

    function createCompareCard(item) {
      var card = document.createElement('div');
      card.className = 'report-card';
      card.style.padding = '22px 28px';

      var targetUrl = '/compare/' + encodeURIComponent(item.analysisId) + '?from=history';
      var isJob = !!item.jobContext;
      var verdictText = item.verdict ? item.verdict : ('A: ' + (item.score || 0) + ' vs B: ' + (item.scoreB || 0));
      var filesTitle = window.escapeHTML(item.fileName || 'Resume A') + ' vs ' + window.escapeHTML(item.fileNameB || 'Resume B');
      var jobMeta = isJob ? '<span style="display: block; font-size: .84rem; color: var(--ink-2); margin-top: 2px;">Role Context: ' + window.escapeHTML(item.jobContext.length > 50 ? item.jobContext.substring(0, 50) + '...' : item.jobContext) + '</span>' : '';

      card.innerHTML =
        '<div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px;">' +
          '<div>' +
            '<span class="card-eyebrow" style="margin-bottom: 2px;">Resume Comparison</span>' +
            '<strong style="font-size: 1.1rem; display: block; margin-bottom: 2px;">' + filesTitle + '</strong>' +
            '<small style="color: var(--ink-3);">Conducted ' + window.escapeHTML(item.date || 'Recent') + ' &bull; ID: ' + window.escapeHTML(item.analysisId) + '</small>' +
            jobMeta +
          '</div>' +
          '<div style="display: flex; align-items: center; gap: 14px; flex-wrap: wrap;">' +
            '<span class="file-badge" style="font-weight: 700; color: var(--pen);">' + window.escapeHTML(verdictText) + '</span>' +
            '<span class="makeover-pill">' + (isJob ? 'Job-Specific Comparison' : 'General Comparison') + '</span>' +
            '<a href="' + targetUrl + '" class="btn btn-primary btn-sm">View Comparison &rarr;</a>' +
          '</div>' +
        '</div>';
      return card;
    }

    function createInterviewPrepCard(item) {
      var card = document.createElement('div');
      card.className = 'report-card';
      card.style.padding = '22px 28px';

      var prepUrl = '/interview-prep/' + encodeURIComponent(item.analysisId) + '?from=history';
      var qCount = item.score || 0;
      var qText = qCount + (qCount === 1 ? ' Question' : ' Questions');
      var noteMeta = item.verdict
        ? '<span style="display: block; font-size: .84rem; color: var(--ink-2); margin-top: 4px; max-width: 50ch; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">' + window.escapeHTML(item.verdict) + '</span>'
        : '';

      card.innerHTML =
        '<div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px;">' +
          '<div>' +
            '<span class="card-eyebrow" style="color: var(--pen); margin-bottom: 2px;">Interview Prep</span>' +
            '<strong style="font-size: 1.1rem; display: block; margin-bottom: 2px;">' + window.escapeHTML(item.fileName || 'Resume.pdf') + '</strong>' +
            '<small style="color: var(--ink-3);">Prepared ' + window.escapeHTML(item.date || 'Recent') + ' &bull; ID: ' + window.escapeHTML(item.analysisId) + '</small>' +
            noteMeta +
          '</div>' +
          '<div style="display: flex; align-items: center; gap: 14px; flex-wrap: wrap;">' +
            '<span class="file-badge" style="font-weight: 700; color: var(--pen);">' + qText + '</span>' +
            '<span class="makeover-pill" style="background: var(--pen-soft); color: var(--pen);">Interview Guide</span>' +
            '<a href="' + prepUrl + '" class="btn btn-primary btn-sm">View Prep Guide &rarr;</a>' +
          '</div>' +
        '</div>';
      return card;
    }

    function renderSection(eyebrow, title, items, createCardFn) {
      if (!items || !items.length) return null;

      var sec = document.createElement('div');
      sec.className = 'history-section';

      var header = document.createElement('div');
      header.className = 'history-section-header';
      header.innerHTML =
        '<span class="card-eyebrow">' + window.escapeHTML(eyebrow) + '</span>' +
        '<h2 class="h-sm">' + window.escapeHTML(title) + '</h2>';
      sec.appendChild(header);

      var itemsContainer = document.createElement('div');
      itemsContainer.className = 'history-section-items';
      items.forEach(function (it) {
        itemsContainer.appendChild(createCardFn(it));
      });
      sec.appendChild(itemsContainer);

      return sec;
    }

    function renderHistory() {
      if (!clientHistory || !clientHistory.length) {
        if (emptyState) emptyState.hidden = false;
        historyList.hidden = true;
        historyList.innerHTML = '';
        if (clearHistoryBtn) clearHistoryBtn.disabled = true;
        return;
      }

      var analyzeItems = [];
      var compareItems = [];
      var interviewPrepItems = [];

      clientHistory.forEach(function (item) {
        if (item.type === 'COMPARE') {
          compareItems.push(item);
        } else if (item.type === 'INTERVIEW_PREP') {
          interviewPrepItems.push(item);
        } else {
          analyzeItems.push(item);
        }
      });

      if (!analyzeItems.length && !compareItems.length && !interviewPrepItems.length) {
        if (emptyState) emptyState.hidden = false;
        historyList.hidden = true;
        historyList.innerHTML = '';
        if (clearHistoryBtn) clearHistoryBtn.disabled = true;
        return;
      }

      if (emptyState) emptyState.hidden = true;
      historyList.hidden = false;
      historyList.innerHTML = '';
      if (clearHistoryBtn) clearHistoryBtn.disabled = false;

      var wrap = document.createElement('div');
      wrap.className = 'history-sections-wrap';

      // 1. Analyze Section
      var analyzeSec = renderSection('Analyze', 'Resume Analyses', analyzeItems, createAnalyzeCard);
      if (analyzeSec) wrap.appendChild(analyzeSec);

      // 2. Compare Section
      var compareSec = renderSection('Compare', 'Resume Comparisons', compareItems, createCompareCard);
      if (compareSec) wrap.appendChild(compareSec);

      // 3. Interview Prep Section
      var prepSec = renderSection('Interview Prep', 'Interview Prep from CV', interviewPrepItems, createInterviewPrepCard);
      if (prepSec) wrap.appendChild(prepSec);

      historyList.appendChild(wrap);
    }

    renderHistory();

    var feedbackTimer = null;
    function showFeedback(msg, typeClass) {
      if (!historyFeedback) return;
      if (feedbackTimer) clearTimeout(feedbackTimer);
      historyFeedback.textContent = msg;
      historyFeedback.className = 'history-feedback ' + typeClass;
      historyFeedback.hidden = false;
      historyFeedback.style.opacity = '1';

      feedbackTimer = setTimeout(function () {
        historyFeedback.style.opacity = '0';
        setTimeout(function () {
          historyFeedback.hidden = true;
        }, 300);
      }, 4000);
    }

    // Modal dialog controls
    if (clearHistoryBtn && clearConfirmDialog) {
      clearHistoryBtn.addEventListener('click', function () {
        if (clearHistoryBtn.disabled) return;
        if (typeof clearConfirmDialog.showModal === 'function') {
          clearConfirmDialog.showModal();
        } else {
          clearConfirmDialog.setAttribute('open', '');
        }
        if (cancelClearBtn) {
          cancelClearBtn.focus();
        }
      });

      if (cancelClearBtn) {
        cancelClearBtn.addEventListener('click', function () {
          if (typeof clearConfirmDialog.close === 'function') {
            clearConfirmDialog.close();
          } else {
            clearConfirmDialog.removeAttribute('open');
          }
        });
      }

      clearConfirmDialog.addEventListener('close', function () {
        if (clearHistoryBtn && !clearHistoryBtn.disabled) {
          clearHistoryBtn.focus();
        }
      });

      // Close when clicking the backdrop
      clearConfirmDialog.addEventListener('click', function (event) {
        var rect = clearConfirmDialog.getBoundingClientRect();
        var clickedInside = (
          rect.top <= event.clientY && event.clientY <= rect.top + rect.height &&
          rect.left <= event.clientX && event.clientX <= rect.left + rect.width
        );
        if (!clickedInside) {
          if (typeof clearConfirmDialog.close === 'function') {
            clearConfirmDialog.close();
          } else {
            clearConfirmDialog.removeAttribute('open');
          }
        }
      });

      if (confirmClearBtn) {
        confirmClearBtn.addEventListener('click', async function () {
          confirmClearBtn.disabled = true;
          if (cancelClearBtn) cancelClearBtn.disabled = true;
          var originalText = confirmClearBtn.textContent;
          confirmClearBtn.textContent = 'Clearing...';

          try {
            var delRes = await fetch('/api/history', { method: 'DELETE' });
            if (!delRes.ok) {
              throw new Error('Server returned status ' + delRes.status);
            }

            if (typeof clearConfirmDialog.close === 'function') {
              clearConfirmDialog.close();
            } else {
              clearConfirmDialog.removeAttribute('open');
            }

            if (window.RefineCVSession && typeof window.RefineCVSession.clearHistory === 'function') {
              window.RefineCVSession.clearHistory();
            }

            clientHistory = [];
            renderHistory();
            showFeedback('History cleared.', 'is-success');
          } catch (err) {
            console.error('Failed to clear history:', err);
            if (typeof clearConfirmDialog.close === 'function') {
              clearConfirmDialog.close();
            } else {
              clearConfirmDialog.removeAttribute('open');
            }
            showFeedback('Failed to clear history. Please try again.', 'is-error');
          } finally {
            confirmClearBtn.disabled = false;
            if (cancelClearBtn) cancelClearBtn.disabled = false;
            confirmClearBtn.textContent = originalText;
          }
        });
      }
    }
  });

})();
