/* ==========================================================
   RefineCV /analysis/{analysisId} Report Logic
   Document-first presentation, score ruler animation,
   interactive edit checklist, ATS meter, and print styling
   ========================================================== */

(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };

  // Sample baseline data for demo mode
  var DEMO_DATA = {
    score: 65,
    summary: "Mahek Somaiya is a finance postgraduate with 1.5 years of experience in student visa processing and a 1 month banking internship. She demonstrates strong communication, client coordination and presentation skills, but the resume lacks quantifiable achievements, advanced technical tools, and industry-specific certifications. The document is moderately ATS-friendly but needs formatting and keyword optimization.",
    atsCompatibility: "Moderate",
    strongestSkills: ["Communication", "Client Coordination", "Data Entry", "CRM (Kondesk & Zoho)", "Presentation"],
    missingOrWeakSkills: ["Advanced Excel", "SQL", "Python", "Financial Modeling", "Regulatory Compliance"],
    strengths: [
      "Strong communication and public speaking background",
      "Effective client relationship management across student visa workflows",
      "Proficient in Microsoft Office and digital collaboration tools",
      "Experience with CRM platforms and student case records",
      "Proven project coordination and documentation capabilities"
    ],
    weaknesses: [
      "Limited professional tenure (1.5 years total work experience)",
      "Lack of quantifiable achievements or metric-backed outcomes",
      "Absence of advanced technical or financial analytics tools",
      "No industry-specific financial certifications (e.g., CFA, FRM)",
      "Resume formatting and keyword density need alignment with ATS filters"
    ],
    suggestions: [
      "Add quantifiable results (e.g., % increase in student applications processed, volume of cases closed).",
      "Use active phrasing and strong action verbs (e.g., streamlined, coordinated, assessed, resolved).",
      "Incorporate relevant certifications or accredited coursework to validate subject mastery.",
      "Standardize section headings (Education, Experience, Technical Skills) for ATS consistency.",
      "Remove non-essential personal hobbies to reserve space for high-impact project bullet points."
    ],
    recommendedChanges: [
      "Quantify case processing volume in Student Services Officer role",
      "Specify exact CRM and database systems instead of generic 'CRM tools'",
      "Add advanced Excel competencies (VLOOKUP, Pivot Tables, financial formulas)",
      "Rephrase passive duty descriptions with active verb openers",
      "Ensure consistent date formatting across all academic and employment milestones"
    ]
  };

  function scoreBand(n) {
    if (n >= 85) return 'Strong candidate profile';
    if (n >= 70) return 'Solid, with a few targeted fixes';
    if (n >= 50) return 'Room to improve';
    return 'Needs substantial refinement';
  }

  function atsLevel(text) {
    var t = String(text || '').toLowerCase();
    if (/\b(high|good|excellent|strong|great)\b/.test(t)) return 'high';
    if (/\b(low|poor|weak|bad)\b/.test(t)) return 'low';
    if (/\b(moderate|medium|average|fair|ok|okay|partial)\b/.test(t)) return 'moderate';
    return 'moderate';
  }

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

  function animateScore(target) {
    var el = $('scoreNum');
    if (!el) return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      el.textContent = target;
      return;
    }
    var start = performance.now();
    var dur = 1000;
    (function tick(now) {
      var p = Math.min((now - start) / dur, 1);
      el.textContent = Math.round(target * (1 - Math.pow(1 - p, 3)));
      if (p < 1) requestAnimationFrame(tick);
    })(start);
  }

  function updateEditsCount() {
    var boxes = document.querySelectorAll('#listChanges input');
    var done = document.querySelectorAll('#listChanges input:checked').length;
    var countEl = $('editsCount');
    if (countEl) {
      countEl.textContent = boxes.length ? (done + ' of ' + boxes.length + ' done') : '0 of 0 done';
    }
  }

  function renderChecklist(items) {
    var ul = $('listChanges');
    if (!ul) return;
    ul.textContent = '';
    if (!items || !items.length) {
      items = ['No specific action items were identified for this resume.'];
    }
    items.forEach(function (text, i) {
      var li = document.createElement('li');
      var label = document.createElement('label');
      var input = document.createElement('input');
      input.type = 'checkbox';
      input.id = 'chg-' + i;
      var box = document.createElement('span');
      box.className = 'box';
      box.setAttribute('aria-hidden', 'true');
      var span = document.createElement('span');
      span.className = 'check-text';
      span.textContent = text;

      input.addEventListener('change', function () {
        updateEditsCount();
        li.classList.remove('flash');
        void li.offsetWidth;
        li.classList.add('flash');
      });

      li.addEventListener('click', function (e) {
        if (e.target.tagName !== 'INPUT') {
          li.classList.toggle('active-item');
        }
      });

      label.appendChild(input);
      label.appendChild(box);
      label.appendChild(span);
      li.appendChild(label);
      ul.appendChild(li);
    });
    updateEditsCount();
  }

  function renderReport(data, fileName, analysisId) {
    var score = Number(data.score);
    if (!isFinite(score)) score = 0;
    score = Math.max(0, Math.min(100, Math.round(score)));

    if ($('reportFileName')) $('reportFileName').textContent = fileName || 'Resume.pdf';
    if ($('railFile')) $('railFile').textContent = fileName || 'Resume review';
    if ($('summary')) $('summary').textContent = data.summary || 'No summary was provided.';
    if ($('scoreLabel')) $('scoreLabel').textContent = scoreBand(score);

    fillList('listStrongSkills', window.toList(data.strongestSkills), 'None identified');
    fillList('listMissingSkills', window.toList(data.missingOrWeakSkills), 'None identified');
    fillList('listStrengths', window.toList(data.strengths), 'None identified');
    fillList('listWeaknesses', window.toList(data.weaknesses), 'None identified');
    fillList('listSuggestions', window.toList(data.suggestions), 'No suggestions returned.');
    renderChecklist(window.toList(data.recommendedChanges));

    // Persist in session store
    window.RefineCVSession.save(analysisId, data, 'GENERAL', null, fileName || 'Resume.pdf');

    // ATS meter
    var atsText = data.atsCompatibility || 'Moderate';
    var lvl = atsLevel(atsText);
    Array.prototype.forEach.call(document.querySelectorAll('#atsMeter span'), function (s) {
      s.classList.toggle('on', s.getAttribute('data-lvl') === lvl);
    });
    if ($('atsMeter')) $('atsMeter').setAttribute('aria-label', 'ATS compatibility: ' + (lvl || atsText));
    if ($('atsRaw')) {
      $('atsRaw').textContent = 'Applicant tracking assessment: ' + atsText;
    }

    // Ruler fill animation
    if ($('scoreNum')) $('scoreNum').textContent = '0';
    if ($('rulerFill')) $('rulerFill').style.width = '0%';
    if ($('rulerPin')) $('rulerPin').style.left = '0%';

    requestAnimationFrame(function () {
      requestAnimationFrame(function () {
        if ($('rulerFill')) $('rulerFill').style.width = score + '%';
        if ($('rulerPin')) $('rulerPin').style.left = score + '%';
        animateScore(score);
      });
    });

    // Update navigation links to point to this analysisId
    var improveBtns = document.querySelectorAll('[data-action="improve"]');
    improveBtns.forEach(function (btn) {
      btn.addEventListener('click', function () {
        window.location.href = '/improve/' + encodeURIComponent(analysisId);
      });
    });

    initScrollSpy();
  }

  function initScrollSpy() {
    var links = Array.prototype.slice.call(document.querySelectorAll('.rail nav a'));
    if (!('IntersectionObserver' in window) || !links.length) return;
    var spy = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (en.isIntersecting) {
          links.forEach(function (a) {
            a.classList.toggle('is-current', a.getAttribute('href') === '#' + en.target.id);
          });
        }
      });
    }, { rootMargin: '-20% 0px -65% 0px' });

    links.forEach(function (a) {
      var href = a.getAttribute('href');
      if (href && href.startsWith('#')) {
        var el = document.querySelector(href);
        if (el) spy.observe(el);
      }
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

    var backHref = fromHistory ? '/history' : '/analyze';
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

  // Load report data
  document.addEventListener('DOMContentLoaded', async function () {
    var metaId = document.body.getAttribute('data-analysis-id') || 'demo';
    setupReportNavigation(metaId);

    // 1. Check window.__INITIAL_ANALYSIS__
    if (window.__INITIAL_ANALYSIS__) {
      renderReport(window.__INITIAL_ANALYSIS__, window.__FILE_NAME__ || 'Resume.pdf', metaId);
      return;
    }

    // 2. Check sessionStorage
    var session = window.RefineCVSession.get(metaId);
    if (session && session.result) {
      renderReport(session.result, session.fileName || 'Resume.pdf', metaId);
      return;
    }

    // 3. If demo mode
    if (metaId === 'demo') {
      renderReport(DEMO_DATA, 'Dhyey_Somaiya_CV (1).pdf', 'demo');
      return;
    }

    // 4. Fetch from backend /analysis/{id}/status
    try {
      var res = await fetch('/analysis/' + encodeURIComponent(metaId) + '/status');
      if (res.ok) {
        var statusData = await res.json();
        if (statusData && statusData.result) {
          renderReport(statusData.result, 'Uploaded_Resume.pdf', metaId);
          return;
        }
      }
    } catch (e) {
      console.warn('Status fetch failed, falling back to demo review', e);
    }

    // 5. Fallback to demo if session expired
    renderReport(DEMO_DATA, 'Sample_Resume.pdf', metaId);
  });

  // Action listeners
  var printBtn = $('printBtn');
  if (printBtn) {
    printBtn.addEventListener('click', function () {
      window.print();
    });
  }

})();
