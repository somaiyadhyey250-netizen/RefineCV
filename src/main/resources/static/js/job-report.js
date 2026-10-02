/* ==========================================================
   RefineCV /job-analysis/{analysisId} Report Logic
   Specific Job Analysis document presentation,
   role context alignment, keyword matching, and score ruler
   ========================================================== */

(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };

  var DEMO_JOB_DATA = {
    score: 55,
    jobMatchScore: 50,
    jobDescription: "banking sector",
    summary: "The candidate is a backend software engineer with strong Java and Spring Boot skills, but the job context specifies the banking sector. The resume demonstrates solid backend fundamentals but lacks explicit banking domain knowledge, transaction security standards, and financial compliance exposure.",
    atsCompatibility: "Good",
    keywordAlignment: "Limited keyword alignment due to generic job context. Bridge missing financial requirements to maximize interview conversion.",
    experienceAlignment: "Past roles align with backend web engineering, but lack direct exposure to banking transaction pipelines or regulatory compliance frameworks.",
    strongestSkills: ["Java", "Spring Boot", "REST APIs", "PostgreSQL", "Microservices"],
    missingOrWeakSkills: ["Banking Domain Knowledge", "Financial Regulations", "Payment Gateways", "PCI-DSS", "Transaction Integrity"],
    strengths: [
      "Backend development experience building scalable Java microservices",
      "Strong database query optimization and PostgreSQL indexing",
      "Proficient in RESTful API architecture and clean code principles"
    ],
    weaknesses: [
      "Lack of banking sector domain experience",
      "No exposure to financial compliance or security protocols mentioned",
      "Resume needs targeted keywords for financial technology roles"
    ],
    suggestions: [
      "Highlight any transactional consistency, ACID compliance, or ledger-like data models built in previous roles.",
      "Incorporate financial technology keywords (security, authentication, fraud mitigation) where applicable.",
      "Tailor project descriptions to emphasize reliability, low latency, and zero-downtime deployments."
    ],
    recommendedChanges: [
      "Add explicit transaction management examples in Java projects",
      "Highlight data protection and API security measures implemented",
      "Reframe backend accomplishments around financial and banking standards"
    ]
  };

  function fillList(id, items, emptyText) {
    var ul = $(id);
    if (!ul) return;
    ul.textContent = '';
    if (!items || !items.length) items = [emptyText];
    items.forEach(function (t) {
      var li = document.createElement('li');
      li.textContent = t;
      ul.appendChild(li);
    });
  }

  function renderJobReport(data, fileName, analysisId, jobDesc) {
    var jScore = Number(data.jobMatchScore != null ? data.jobMatchScore : data.score);
    if (!isFinite(jScore)) jScore = 0;
    jScore = Math.max(0, Math.min(100, Math.round(jScore)));

    var contextText = jobDesc || data.jobDescription || 'Target Position';

    if ($('reportFileName')) $('reportFileName').textContent = (fileName || 'Resume.pdf') + ' (Matched to Job)';
    if ($('railFile')) $('railFile').textContent = (fileName || 'Resume') + ' (matched to job)';
    if ($('targetContextPill')) $('targetContextPill').textContent = contextText;
    if ($('jobRoleTitle')) $('jobRoleTitle').textContent = contextText;
    if ($('summary')) $('summary').textContent = data.summary || 'No summary returned.';

    if ($('keywordAlignment')) $('keywordAlignment').textContent = data.keywordAlignment || 'Terminology evaluated against target job requirements.';
    if ($('experienceAlignment')) $('experienceAlignment').textContent = data.experienceAlignment || 'Experience evaluated against role scope.';

    if ($('scoreNum')) $('scoreNum').textContent = jScore;
    if ($('scoreLabel')) $('scoreLabel').textContent = jScore >= 75 ? 'Strong Role Fit' : (jScore >= 50 ? 'Moderate Alignment' : 'Substantial Gaps for this Role');

    fillList('listMatchingSkills', window.toList(data.strongestSkills), 'None identified');
    fillList('listMissingSkills', window.toList(data.missingOrWeakSkills), 'None identified');
    fillList('listStrengths', window.toList(data.strengths), 'None identified');
    fillList('listWeaknesses', window.toList(data.weaknesses), 'None identified');
    fillList('listSuggestions', window.toList(data.suggestions), 'No recommendations returned.');

    // Save session
    window.RefineCVSession.save(analysisId, data, 'SPECIFIC_JOB', contextText, fileName || 'Resume.pdf');

    // Ruler fill animation
    if ($('rulerFill')) $('rulerFill').style.width = '0%';
    if ($('rulerPin')) $('rulerPin').style.left = '0%';

    requestAnimationFrame(function () {
      requestAnimationFrame(function () {
        if ($('rulerFill')) $('rulerFill').style.width = jScore + '%';
        if ($('rulerPin')) $('rulerPin').style.left = jScore + '%';
      });
    });

    var improveBtns = document.querySelectorAll('[data-action="improve"]');
    improveBtns.forEach(function (btn) {
      btn.addEventListener('click', function () {
        window.location.href = '/improve/' + encodeURIComponent(analysisId);
      });
    });
  }

  function setupReportNavigation(metaId) {
    var urlParams = new URLSearchParams(window.location.search);
    var fromHistory = urlParams.get('from') === 'history' ||
                      (document.referrer && document.referrer.indexOf('/history') !== -1) ||
                      sessionStorage.getItem('refinecv_from_' + metaId) === 'history';

    if (fromHistory) {
      sessionStorage.setItem('refinecv_from_' + metaId, 'history');
    }

    var backHref = fromHistory ? '/history' : '/analyze?mode=SPECIFIC_JOB';
    var backText = fromHistory ? '← Back to History' : '← Back to Analyze';

    var backBtn = $('resultBackBtn');
    if (backBtn) {
      backBtn.setAttribute('href', backHref);
      backBtn.textContent = backText;
    }
    var bottomBack = $('bottomBackBtn');
    if (bottomBack) {
      bottomBack.setAttribute('href', backHref);
      bottomBack.textContent = backText;
    }
  }

  document.addEventListener('DOMContentLoaded', async function () {
    var metaId = document.body.getAttribute('data-analysis-id') || 'demo';
    setupReportNavigation(metaId);

    if (window.__INITIAL_ANALYSIS__) {
      renderJobReport(window.__INITIAL_ANALYSIS__, window.__FILE_NAME__ || 'Resume.pdf', metaId, window.__JOB_DESC__);
      return;
    }

    var session = window.RefineCVSession.get(metaId);
    if (session && session.result) {
      renderJobReport(session.result, session.fileName || 'Resume.pdf', metaId, session.jobDescription);
      return;
    }

    if (metaId === 'demo') {
      renderJobReport(DEMO_JOB_DATA, 'Dhyey_Somaiya_CV.pdf', 'demo', 'banking sector');
      return;
    }

    try {
      var res = await fetch('/analysis/' + encodeURIComponent(metaId) + '/status');
      if (res.ok) {
        var statusData = await res.json();
        if (statusData && statusData.result) {
          renderJobReport(statusData.result, 'Uploaded_Resume.pdf', metaId, statusData.jobDescription);
          return;
        }
      }
    } catch (e) {
      console.warn('Status fetch failed', e);
    }

    renderJobReport(DEMO_JOB_DATA, 'Sample_Resume.pdf', metaId, 'banking sector');
  });

  var printBtn = $('printBtn');
  if (printBtn) {
    printBtn.addEventListener('click', function () {
      window.print();
    });
  }

})();
