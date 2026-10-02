/* ==========================================================
   RefineCV /history Session History Logic
   Renders active session analyses, timestamp, score, and links
   ========================================================== */

(function () {
  'use strict';

  document.addEventListener('DOMContentLoaded', async function () {
    var historyList = document.getElementById('historyList');
    var emptyState = document.getElementById('emptyState');
    if (!historyList) return;

    var clientHistory = window.RefineCVSession.getHistory() || [];

    // Also fetch server-side active sessions from AnalysisSessionStore
    try {
      var res = await fetch('/api/history');
      if (res.ok) {
        var serverItems = await res.json();
        if (Array.isArray(serverItems)) {
          serverItems.forEach(function (si) {
            var exists = clientHistory.some(function (ch) { return ch.analysisId === si.analysisId; });
            if (!exists) {
              clientHistory.push({
                analysisId: si.analysisId,
                mode: si.mode,
                fileName: si.fileName || 'Resume.pdf',
                score: si.score || 0,
                date: new Date(si.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
              });
            }
          });
        }
      }
    } catch (e) {
      console.warn('Could not fetch server history, using local history', e);
    }

    if (!clientHistory || !clientHistory.length) {
      if (emptyState) emptyState.hidden = false;
      historyList.hidden = true;
      return;
    }

    if (emptyState) emptyState.hidden = true;
    historyList.hidden = false;
    historyList.innerHTML = '';

    clientHistory.forEach(function (item) {
      var card = document.createElement('div');
      card.className = 'report-card';
      card.style.padding = '22px 28px';

      var isJob = item.mode === 'SPECIFIC_JOB';
      var targetUrl = (isJob ? ('/job-analysis/' + encodeURIComponent(item.analysisId)) : ('/analysis/' + encodeURIComponent(item.analysisId))) + '?from=history';

      card.innerHTML = `
        <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px;">
          <div>
            <strong style="font-size: 1.1rem; display: block; margin-bottom: 2px;">${window.escapeHTML(item.fileName || 'Resume.pdf')}</strong>
            <small style="color: var(--ink-3);">Conducted ${window.escapeHTML(item.date || 'Recent')} &bull; ID: ${window.escapeHTML(item.analysisId)}</small>
          </div>
          <div style="display: flex; align-items: center; gap: 14px; flex-wrap: wrap;">
            <span class="file-badge" style="font-weight: 700; color: var(--pen);">Score: ${item.score || 0}/100</span>
            <span class="makeover-pill">${isJob ? 'Specific Job Match' : 'General Review'}</span>
            <a href="${targetUrl}" class="btn btn-primary btn-sm">View Report &rarr;</a>
          </div>
        </div>
      `;

      historyList.appendChild(card);
    });
  });

})();
