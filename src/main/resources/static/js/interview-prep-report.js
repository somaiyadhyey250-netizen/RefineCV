/* ==========================================================
   RefineCV /interview-prep/{interviewPrepId} Report Logic
   Renders grounded interview questions, risk levels, evidence,
   practice answer evaluation, and additional question generation.
   ========================================================== */

(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };

  var prepId = document.body.getAttribute('data-prep-id') ||
               window.location.pathname.split('/').filter(Boolean).pop();

  var currentPrep = null;
  var isGeneratingMore = false;

  // Question Type display formatting
  function formatQuestionType(type) {
    if (!type) return 'DEPTH';
    var t = String(type).toUpperCase().trim();
    if (t === 'DEPTH') return 'Depth Assessment';
    if (t === 'PROOF') return 'Proof & Contribution';
    if (t === 'WHY') return 'Rationale & Decisions';
    return t;
  }

  // Risk Level badge formatting with accessible labels
  function renderRiskBadge(risk) {
    var r = String(risk || 'BE_READY').toUpperCase().trim();
    if (r === 'SAFE') {
      return '<span class="prep-risk-pill risk-safe" title="Claim is specific and reasonably supported"><span class="risk-dot" aria-hidden="true"></span>Safe Claim</span>';
    } else if (r === 'RISKY') {
      return '<span class="prep-risk-pill risk-risky" title="Strong or broad claim with limited evidence"><span class="risk-dot" aria-hidden="true"></span>Needs Strong Defense</span>';
    } else {
      return '<span class="prep-risk-pill risk-ready" title="Plausible claim that invites deeper follow-up"><span class="risk-dot" aria-hidden="true"></span>Be Ready</span>';
    }
  }

  function renderClaimRiskBadge(risk) {
    var r = String(risk || 'BE_READY').toUpperCase().trim();
    if (r === 'SAFE') {
      return '<span class="prep-risk-pill risk-safe"><span class="risk-dot" aria-hidden="true"></span>Safe</span>';
    } else if (r === 'RISKY') {
      return '<span class="prep-risk-pill risk-risky"><span class="risk-dot" aria-hidden="true"></span>High Priority</span>';
    } else {
      return '<span class="prep-risk-pill risk-ready"><span class="risk-dot" aria-hidden="true"></span>Be Ready</span>';
    }
  }

  // Render a single question card
  function createQuestionCard(q, index) {
    var card = document.createElement('div');
    card.className = 'report-card prep-question-card';
    card.setAttribute('data-question-id', q.id || ('q-' + (index + 1)));

    var questionTypeHtml = '<span class="prep-type-pill">' + window.escapeHTML(formatQuestionType(q.questionType)) + '</span>';
    var riskBadgeHtml = renderRiskBadge(q.riskLevel);
    var questionNum = index + 1;

    var basedOnHtml = '';
    if (q.basedOn && q.basedOn.trim()) {
      basedOnHtml =
        '<div class="prep-evidence-box">' +
          '<span class="prep-evidence-eyebrow">Based on resume claim:</span>' +
          '<blockquote class="prep-evidence-quote">&ldquo;' + window.escapeHTML(q.basedOn.trim()) + '&rdquo;</blockquote>' +
        '</div>';
    }

    var intentHtml = '';
    if (q.interviewerIntent && q.interviewerIntent.trim()) {
      intentHtml =
        '<div class="prep-detail-row">' +
          '<strong class="prep-detail-label">Why this may come up:</strong>' +
          '<p class="prep-detail-text">' + window.escapeHTML(q.interviewerIntent.trim()) + '</p>' +
        '</div>';
    }

    var hintHtml = '';
    if (q.preparationHint && q.preparationHint.trim()) {
      hintHtml =
        '<div class="prep-detail-row">' +
          '<strong class="prep-detail-label">Prepare to explain:</strong>' +
          '<p class="prep-detail-text">' + window.escapeHTML(q.preparationHint.trim()) + '</p>' +
        '</div>';
    }

    var qId = window.escapeHTML(q.id || ('q-' + questionNum));

    card.innerHTML =
      '<div class="prep-card-header">' +
        '<div class="prep-card-badges">' +
          '<span class="prep-q-num">Q' + questionNum + '</span>' +
          questionTypeHtml +
          riskBadgeHtml +
        '</div>' +
        '<button type="button" class="btn btn-quiet btn-sm btn-toggle-practice" data-target="practice-' + qId + '" aria-expanded="false">' +
          '<svg viewBox="0 0 20 20" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true" style="margin-right: 4px;"><path d="M11 4H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-7"/><path d="M14.5 2.5a2.121 2.121 0 0 1 3 3L10 13l-4 1 1-4 7.5-7.5z"/></svg>' +
          'Practice Answer' +
        '</button>' +
      '</div>' +
      '<h3 class="prep-question-text">' + window.escapeHTML(q.question) + '</h3>' +
      basedOnHtml +
      '<div class="prep-details-group">' +
        intentHtml +
        hintHtml +
      '</div>' +
      '<div class="prep-practice-panel" id="practice-' + qId + '" hidden>' +
        '<div class="prep-practice-inner">' +
          '<label for="ans-' + qId + '" class="prep-practice-label">Write your answer as if speaking directly to an interviewer:</label>' +
          '<textarea id="ans-' + qId + '" class="prep-practice-textarea" rows="4" placeholder="Explain your design, responsibilities, or decision-making clearly with concrete details..."></textarea>' +
          '<div class="prep-practice-actions">' +
            '<button type="button" class="btn btn-primary btn-sm btn-eval-answer" data-qid="' + qId + '">Evaluate Answer</button>' +
            '<button type="button" class="btn btn-quiet btn-sm btn-cancel-practice" data-target="practice-' + qId + '">Cancel</button>' +
            '<span class="practice-eval-status" id="status-' + qId + '" aria-live="polite" hidden></span>' +
          '</div>' +
          '<div class="prep-eval-result" id="evalResult-' + qId + '" hidden></div>' +
        '</div>' +
      '</div>';

    // Hook up practice toggles
    var toggleBtn = card.querySelector('.btn-toggle-practice');
    var practicePanel = card.querySelector('#practice-' + qId);
    var cancelBtn = card.querySelector('.btn-cancel-practice');
    var evalBtn = card.querySelector('.btn-eval-answer');
    var textarea = card.querySelector('#ans-' + qId);
    var statusEl = card.querySelector('#status-' + qId);
    var resultEl = card.querySelector('#evalResult-' + qId);

    function togglePractice() {
      var isHidden = practicePanel.hidden;
      practicePanel.hidden = !isHidden;
      toggleBtn.setAttribute('aria-expanded', String(isHidden));
      if (isHidden && textarea) {
        textarea.focus();
      }
    }

    if (toggleBtn) toggleBtn.addEventListener('click', togglePractice);
    if (cancelBtn) cancelBtn.addEventListener('click', togglePractice);

    if (evalBtn && textarea) {
      evalBtn.addEventListener('click', async function () {
        var answer = textarea.value.trim();
        if (!answer) {
          textarea.focus();
          return;
        }

        evalBtn.disabled = true;
        var originalBtnText = evalBtn.textContent;
        evalBtn.textContent = 'Evaluating...';
        if (statusEl) {
          statusEl.textContent = 'Analyzing answer quality...';
          statusEl.hidden = false;
        }

        try {
          var resp = await fetch('/api/interview-prep/' + encodeURIComponent(prepId) + '/evaluate-answer', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              questionId: q.id || ('q-' + questionNum),
              question: q.question,
              basedOn: q.basedOn || '',
              userAnswer: answer
            })
          });

          if (!resp.ok) {
            var errData = await resp.json().catch(function () { return {}; });
            throw new Error(errData.error || ('Evaluation request failed (' + resp.status + ')'));
          }

          var evalData = await resp.json();
          renderAnswerEvaluation(resultEl, evalData);
          resultEl.hidden = false;
          if (statusEl) statusEl.hidden = true;

        } catch (err) {
          if (statusEl) {
            statusEl.textContent = 'Evaluation failed: ' + (err.message || 'Please try again.');
            statusEl.hidden = false;
          }
        } finally {
          evalBtn.disabled = false;
          evalBtn.textContent = originalBtnText;
        }
      });
    }

    return card;
  }

  // Render answer evaluation results
  function renderAnswerEvaluation(container, evalData) {
    if (!container || !evalData) return;
    container.innerHTML = '';

    var wrapper = document.createElement('div');
    wrapper.className = 'eval-card';

    var header = document.createElement('div');
    header.className = 'eval-header';
    header.innerHTML =
      '<span class="card-eyebrow" style="color: var(--pen); margin-bottom: 2px;">Evaluation Feedback</span>' +
      '<h4 class="h-sm" style="margin: 0; font-size: 1rem;">Answer Quality</h4>';
    wrapper.appendChild(header);

    if (evalData.answerQuality) {
      var summaryP = document.createElement('p');
      summaryP.className = 'eval-summary';
      summaryP.textContent = evalData.answerQuality;
      wrapper.appendChild(summaryP);
    }

    var grid = document.createElement('div');
    grid.className = 'eval-feedback-grid';

    // Strengths
    var strengthsCol = document.createElement('div');
    strengthsCol.className = 'eval-col eval-col-strengths';
    strengthsCol.innerHTML = '<strong class="eval-col-title"><span class="eval-marker-good" aria-hidden="true">&check;</span> Strengths</strong>';
    var sUl = document.createElement('ul');
    sUl.className = 'eval-list';
    var strengths = Array.isArray(evalData.strengths) && evalData.strengths.length ? evalData.strengths : ['Addresses the question'];
    strengths.forEach(function (s) {
      var li = document.createElement('li');
      li.textContent = s;
      sUl.appendChild(li);
    });
    strengthsCol.appendChild(sUl);
    grid.appendChild(strengthsCol);

    // Improvements
    var improveCol = document.createElement('div');
    improveCol.className = 'eval-col eval-col-improvements';
    improveCol.innerHTML = '<strong class="eval-col-title"><span class="eval-marker-improve" aria-hidden="true">&bull;</span> Areas to Strengthen</strong>';
    var iUl = document.createElement('ul');
    iUl.className = 'eval-list';
    var improvements = Array.isArray(evalData.improvements) && evalData.improvements.length ? evalData.improvements : ['Provide more concrete details on your exact role'];
    improvements.forEach(function (imp) {
      var li = document.createElement('li');
      li.textContent = imp;
      iUl.appendChild(li);
    });
    improveCol.appendChild(iUl);
    grid.appendChild(improveCol);

    wrapper.appendChild(grid);
    container.appendChild(wrapper);
  }

  // Render Claims to Prepare section
  function renderClaims(claims) {
    var container = $('claimsContainer');
    if (!container) return;
    container.innerHTML = '';

    if (!claims || !claims.length) {
      if ($('claimsSection')) $('claimsSection').hidden = true;
      return;
    }

    if ($('claimsSection')) $('claimsSection').hidden = false;

    claims.forEach(function (c) {
      var card = document.createElement('div');
      card.className = 'report-card prep-claim-card';

      var riskBadge = renderClaimRiskBadge(c.riskLevel);
      card.innerHTML =
        '<div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; flex-wrap: wrap;">' +
          '<div style="flex: 1; min-width: 260px;">' +
            '<div style="display: flex; align-items: center; gap: 8px; margin-bottom: 6px;">' +
              '<span class="prep-claim-tag">Stated Resume Claim</span>' +
              riskBadge +
            '</div>' +
            '<blockquote class="prep-claim-quote">&ldquo;' + window.escapeHTML(c.claim) + '&rdquo;</blockquote>' +
          '</div>' +
        '</div>' +
        '<div class="prep-claim-note-box">' +
          '<strong class="prep-claim-note-label">Preparation recommendation:</strong>' +
          '<p class="prep-claim-note-text">' + window.escapeHTML(c.preparationNote) + '</p>' +
        '</div>';

      container.appendChild(card);
    });
  }

  // Update question counts and cap badge
  function updateQuestionCounts(count) {
    if ($('questionCountBadge')) {
      $('questionCountBadge').textContent = count + ' Questions Prepared';
    }
    if ($('questionCapBadge')) {
      $('questionCapBadge').textContent = count + ' of 15 questions';
    }
    if ($('questionsSummaryCount')) {
      $('questionsSummaryCount').textContent = count + ' questions grounded in your resume';
    }

    var generateBtn = $('generateMoreBtn');
    if (generateBtn) {
      if (count >= 15) {
        generateBtn.disabled = true;
        generateBtn.textContent = 'Maximum Questions Reached (15/15)';
      } else {
        generateBtn.disabled = false;
        generateBtn.textContent = 'Generate More Questions (+4)';
      }
    }
  }

  // Render the entire Interview Prep document
  function renderInterviewPrep(prep) {
    if (!prep) return;
    currentPrep = prep;

    // Unreadable resume edge case
    if (prep.isUnreadable) {
      if ($('unreadableBanner')) $('unreadableBanner').hidden = false;
      if ($('unreadableMessage') && prep.overallPreparationNote) {
        $('unreadableMessage').textContent = prep.overallPreparationNote;
      }
      if ($('overviewCard')) $('overviewCard').hidden = true;
      if ($('questionsContainer')) $('questionsContainer').hidden = true;
      if ($('generateMoreCard')) $('generateMoreCard').hidden = true;
      if ($('claimsSection')) $('claimsSection').hidden = true;
      return;
    }

    if ($('unreadableBanner')) $('unreadableBanner').hidden = true;
    if ($('overviewCard')) $('overviewCard').hidden = false;

    // Header info
    var filename = prep.filename || 'Resume.pdf';
    if ($('reportFileName')) $('reportFileName').textContent = filename;
    if ($('reportDate') && prep.createdAt) {
      try {
        var dt = new Date(prep.createdAt);
        $('reportDate').textContent = dt.toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' });
      } catch (e) {
        $('reportDate').textContent = 'Prepared with RefineCV';
      }
    }

    // Overall preparation note
    if ($('overallPreparationNote') && prep.overallPreparationNote) {
      $('overallPreparationNote').textContent = prep.overallPreparationNote;
    }

    // Questions list
    var qContainer = $('questionsContainer');
    if (qContainer) {
      qContainer.innerHTML = '';
      var questions = Array.isArray(prep.questions) ? prep.questions : [];
      questions.forEach(function (q, idx) {
        qContainer.appendChild(createQuestionCard(q, idx));
      });
    }

    updateQuestionCounts(prep.questionCount || (prep.questions ? prep.questions.length : 0));

    // Claims section
    renderClaims(prep.claimsToPrepare || []);

    // Save session in local storage for history determinism
    if (window.RefineCVSession && typeof window.RefineCVSession.saveInterviewPrep === 'function') {
      window.RefineCVSession.saveInterviewPrep(prep.id, prep, filename);
    }
  }

  // Generate More Questions handler
  var generateMoreBtn = $('generateMoreBtn');
  if (generateMoreBtn) {
    generateMoreBtn.addEventListener('click', async function () {
      if (isGeneratingMore || !currentPrep || (currentPrep.questionCount >= 15)) return;

      isGeneratingMore = true;
      generateMoreBtn.disabled = true;
      var originalText = generateMoreBtn.textContent;
      generateMoreBtn.textContent = 'Generating 4 More Questions...';

      try {
        var resp = await fetch('/api/interview-prep/' + encodeURIComponent(prepId) + '/more-questions', {
          method: 'POST'
        });

        if (!resp.ok) {
          var errData = await resp.json().catch(function () { return {}; });
          throw new Error(errData.error || ('Server returned error (' + resp.status + ')'));
        }

        var result = await resp.json();
        if (result && result.prep) {
          renderInterviewPrep(result.prep);
        }
      } catch (err) {
        alert(err.message || 'Failed to generate more questions. Please try again.');
        generateMoreBtn.disabled = false;
        generateMoreBtn.textContent = originalText;
      } finally {
        isGeneratingMore = false;
      }
    });
  }

  // Print buttons
  if ($('printBtn')) {
    $('printBtn').addEventListener('click', function () { window.print(); });
  }
  if ($('bottomPrintBtn')) {
    $('bottomPrintBtn').addEventListener('click', function () { window.print(); });
  }

  // Initialize: load prep data
  async function init() {
    if (!prepId) return;

    // 1. Check local session storage first
    if (window.RefineCVSession && typeof window.RefineCVSession.getInterviewPrep === 'function') {
      var cached = window.RefineCVSession.getInterviewPrep(prepId);
      if (cached && cached.result) {
        renderInterviewPrep(cached.result);
        return;
      }
    }

    // 2. Fetch from server endpoint
    try {
      var res = await fetch('/interview-prep/' + encodeURIComponent(prepId) + '/status');
      if (res.ok) {
        var data = await res.json();
        renderInterviewPrep(data);
        return;
      }
    } catch (e) {
      console.warn('Failed to fetch interview prep from server status endpoint:', e);
    }

    // If still not found and no banner shown
    if ($('unreadableBanner')) {
      $('unreadableBanner').hidden = false;
      if ($('unreadableMessage')) {
        $('unreadableMessage').textContent = 'This interview preparation session has expired or could not be found. Please start a new session.';
      }
      if ($('overviewCard')) $('overviewCard').hidden = true;
      if ($('questionsContainer')) $('questionsContainer').hidden = true;
      if ($('generateMoreCard')) $('generateMoreCard').hidden = true;
      if ($('claimsSection')) $('claimsSection').hidden = true;
    }
  }

  init();

})();
