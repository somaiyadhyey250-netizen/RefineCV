/* ==========================================================
   RefineCV /compare/{comparisonId} Report Logic
   Side-by-side comparative scorecard, horizontal score bars,
   grounded resume evidence, deterministic verdict display,
   mutual "What to Borrow" takeaways, and improvement actions
   ========================================================== */

(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };

  var DEMO_COMPARISON_DATA = {
    comparisonId: 'demo',
    fileNameA: 'Dhyey_Resume.pdf',
    fileNameB: 'Dhyey_Resume_Final.pdf',
    jobDescription: null,
    totalScoreA: 79,
    totalScoreB: 86,
    scoreDifference: 7,
    winnerVerdict: 'Resume B has a narrow overall advantage',
    winner: 'B',
    isIdentical: false,
    isUnreadable: false,
    unreadableReason: null,
    winnerDifferentiators: [
      'Stronger evidence of relevant production experience',
      'More specific metrics and quantifiable engineering achievements',
      'Cleaner ATS-compatible layout and targeted skill hierarchy'
    ],
    overallTakeaway: 'Resume B demonstrates stronger quantified accomplishments and clearer architectural leadership, giving it an advantage over Resume A while Resume A retains strong foundational skills.',
    borrowFromBForA: [
      'Adopt Resume B’s quantified impact format with concrete percentages and velocity metrics',
      'Structure technical skills into distinct primary categories for improved recruiter scanning'
    ],
    borrowFromAForB: [
      'Incorporate Resume A’s concise summary introduction highlighting core career focus',
      'Retain clear section dividers to maintain visual breathing room across pages'
    ],
    categories: [
      {
        categoryName: 'Content & Relevance',
        maxPoints: 20,
        scoreA: 16,
        scoreB: 18,
        winner: 'B',
        evidenceA: 'Lists backend engineering roles with standard API development and database tasks.',
        evidenceB: 'Highlights high-throughput microservices, caching layers, and CI/CD automation pipelines directly relevant to modern backend expectations.',
        explanationA: 'Covers key backend responsibilities with clear relevance.',
        explanationB: 'Exhibits greater topical depth and modern production stack relevance.'
      },
      {
        categoryName: 'Skills & Keywords',
        maxPoints: 20,
        scoreA: 17,
        scoreB: 18,
        winner: 'B',
        evidenceA: 'Mentions Java, Spring Boot, MySQL, REST, Git.',
        evidenceB: 'Mentions Java 21, Spring Cloud, PostgreSQL, Redis, Kafka, Docker, Kubernetes, Prometheus.',
        explanationA: 'Solid core skill set with established enterprise tools.',
        explanationB: 'Broader ecosystem coverage with distributed systems and monitoring tools.'
      },
      {
        categoryName: 'Experience & Evidence',
        maxPoints: 20,
        scoreA: 15,
        scoreB: 17,
        winner: 'B',
        evidenceA: 'Documented 2 years as Junior Software Developer and 1 year as Software Engineer.',
        evidenceB: 'Documented progressive responsibility across 3 roles with direct ownership of payment settlement services.',
        explanationA: 'Chronological work history is verified with legitimate company listings.',
        explanationB: 'Clearer evidence of ownership, architectural autonomy, and progressive scope.'
      },
      {
        categoryName: 'Impact & Achievements',
        maxPoints: 15,
        scoreA: 10,
        scoreB: 13,
        winner: 'B',
        evidenceA: 'Phrasing like "responsible for optimizing database queries".',
        evidenceB: 'Phrasing like "reduced p99 API query latency by 34% through Redis caching and PostgreSQL query indexing".',
        explanationA: 'Tasks are described functionally but lack quantified business outcomes.',
        explanationB: 'Specific metrics provide verifiable evidence of engineering impact.'
      },
      {
        categoryName: 'Clarity & Structure',
        maxPoints: 15,
        scoreA: 12,
        scoreB: 12,
        winner: 'TIE',
        evidenceA: 'Clean two-page layout with standard serif headings and bullet points.',
        evidenceB: 'Clean single-page reverse-chronological format with consistent margins.',
        explanationA: 'Well-organized and easy to skim.',
        explanationB: 'Equal visual balance and readable typography.'
      },
      {
        categoryName: 'ATS Compatibility',
        maxPoints: 10,
        scoreA: 9,
        scoreB: 8,
        winner: 'A',
        evidenceA: 'Single column linear text flow, standard section titles.',
        evidenceB: 'Standard section titles with minor multi-column header block.',
        explanationA: 'Flawless linear text parsing without multi-column risk.',
        explanationB: 'Strong parsing with slight header formatting complexity.'
      }
    ]
  };

  function fillList(id, items, emptyText) {
    var ul = $(id);
    if (!ul) return;
    ul.textContent = '';
    if (!items || !items.length) {
      if (emptyText) {
        var li = document.createElement('li');
        li.textContent = emptyText;
        ul.appendChild(li);
      }
      return;
    }
    items.forEach(function (text) {
      var li = document.createElement('li');
      li.textContent = text;
      ul.appendChild(li);
    });
  }

  var SIX_CATEGORIES = [
    { index: 1, name: 'Content & Relevance', maxPoints: 20 },
    { index: 2, name: 'Skills & Keywords', maxPoints: 20 },
    { index: 3, name: 'Experience & Evidence', maxPoints: 20 },
    { index: 4, name: 'Impact & Achievements', maxPoints: 15 },
    { index: 5, name: 'Clarity & Structure', maxPoints: 15 },
    { index: 6, name: 'ATS Compatibility', maxPoints: 10 }
  ];

  function renderCategoryCard(cat, fileNameA, fileNameB, idx) {
    var card = document.createElement('div');
    card.className = 'report-card compare-category-card';

    var match = SIX_CATEGORIES.find(function (d) {
      return d.name.toLowerCase() === (cat.categoryName || '').trim().toLowerCase();
    }) || (idx != null && idx >= 0 && idx < SIX_CATEGORIES.length ? SIX_CATEGORIES[idx] : null);

    var catNum = match ? match.index : (idx != null ? idx + 1 : 1);
    var catName = match ? match.name : (cat.categoryName || 'Category');
    var maxPts = match ? match.maxPoints : (cat.maxPoints || 20);

    var scoreA = cat.scoreA != null ? cat.scoreA : 0;
    var scoreB = cat.scoreB != null ? cat.scoreB : 0;

    var pctA = Math.min(100, Math.max(0, Math.round((scoreA / maxPts) * 100)));
    var pctB = Math.min(100, Math.max(0, Math.round((scoreB / maxPts) * 100)));

    var isLeadA = scoreA > scoreB;
    var isLeadB = scoreB > scoreA;

    var badgeText = isLeadA ? 'Resume A leads' : (isLeadB ? 'Resume B leads' : 'Tied');
    var badgeClass = isLeadA ? 'badge-lead-a' : (isLeadB ? 'badge-lead-b' : 'badge-tied');

    card.innerHTML = `
      <div class="category-card-header">
        <div class="category-title-group">
          <h3 class="category-name">${catNum}. ${window.escapeHTML(catName)} <span class="category-pts-muted">&mdash; ${maxPts} pts</span></h3>
          <span class="category-max-sub">${maxPts} points maximum</span>
        </div>
        <span class="category-lead-badge ${badgeClass}">${badgeText}</span>
      </div>

      <div class="category-side-by-side">
        <!-- Resume A Column -->
        <div class="category-candidate-col ${isLeadA ? 'is-emphasized' : ''}">
          <div class="candidate-header">
            <div class="candidate-identity-row">
              <span class="candidate-tag">Resume A &mdash; <strong>${scoreA}</strong> / ${maxPts}</span>
            </div>
            <span class="candidate-file" title="${window.escapeHTML(fileNameA)}">${window.escapeHTML(fileNameA)}</span>
          </div>

          <div class="candidate-score-row">
            <div class="cat-bar-bg" aria-label="Resume A score bar: ${pctA}%">
              <div class="cat-bar-fill" style="width: ${pctA}%;"></div>
            </div>
          </div>

          <div class="candidate-evidence-box">
            <span class="evidence-label">Resume Evidence</span>
            <p class="evidence-text">${window.escapeHTML(cat.evidenceA || 'No specific evidence identified.')}</p>
          </div>

          ${cat.explanationA ? `<p class="candidate-note">${window.escapeHTML(cat.explanationA)}</p>` : ''}
        </div>

        <!-- Resume B Column -->
        <div class="category-candidate-col ${isLeadB ? 'is-emphasized' : ''}">
          <div class="candidate-header">
            <div class="candidate-identity-row">
              <span class="candidate-tag">Resume B &mdash; <strong>${scoreB}</strong> / ${maxPts}</span>
            </div>
            <span class="candidate-file" title="${window.escapeHTML(fileNameB)}">${window.escapeHTML(fileNameB)}</span>
          </div>

          <div class="candidate-score-row">
            <div class="cat-bar-bg" aria-label="Resume B score bar: ${pctB}%">
              <div class="cat-bar-fill" style="width: ${pctB}%;"></div>
            </div>
          </div>

          <div class="candidate-evidence-box">
            <span class="evidence-label">Resume Evidence</span>
            <p class="evidence-text">${window.escapeHTML(cat.evidenceB || 'No specific evidence identified.')}</p>
          </div>

          ${cat.explanationB ? `<p class="candidate-note">${window.escapeHTML(cat.explanationB)}</p>` : ''}
        </div>
      </div>
    `;

    return card;
  }

  function fillBorrowList(id, items) {
    var ul = $(id);
    if (!ul) return;
    ul.textContent = '';
    var validItems = (Array.isArray(items) ? items : []).filter(function (it) {
      return it && typeof it === 'string' && it.trim().length > 0 && it.trim() !== 'No specific recommendations identified.';
    });

    if (validItems.length === 0) {
      var li1 = document.createElement('li');
      li1.className = 'borrow-empty-item';
      li1.textContent = 'No clear borrowing opportunity found.';
      var li2 = document.createElement('li');
      li2.className = 'borrow-empty-sub';
      li2.textContent = 'Both resumes already have distinct strengths.';
      ul.appendChild(li1);
      ul.appendChild(li2);
      return;
    }

    validItems.forEach(function (text) {
      var li = document.createElement('li');
      li.textContent = text;
      ul.appendChild(li);
    });
  }

  function renderComparisonReport(data, comparisonId, jobDesc) {
    if (!data) return;

    var fileA = data.fileNameA || 'Resume A.pdf';
    var fileB = data.fileNameB || 'Resume B.pdf';

    // Top badges
    var reportFileA = $('reportFileA');
    if (reportFileA) reportFileA.textContent = fileA;
    var reportFileB = $('reportFileB');
    if (reportFileB) reportFileB.textContent = fileB;

    var scoreNameA = $('scoreNameA');
    if (scoreNameA) scoreNameA.textContent = fileA;
    var scoreNameB = $('scoreNameB');
    if (scoreNameB) scoreNameB.textContent = fileB;

    // Edge Cases: Identical or Unreadable
    if (data.isIdentical) {
      var identBanner = $('identicalBanner');
      if (identBanner) identBanner.hidden = false;
      var identMsg = $('identicalMessage');
      if (identMsg && data.overallTakeaway) identMsg.textContent = data.overallTakeaway;
    }

    if (data.isUnreadable) {
      var unreadBanner = $('unreadableBanner');
      if (unreadBanner) unreadBanner.hidden = false;
      var unreadMsg = $('unreadableMessage');
      if (unreadMsg && data.unreadableReason) {
        unreadMsg.textContent = 'Comparison unavailable — ' + data.unreadableReason + ', so a fair comparison cannot be completed.';
      }
    }

    // Subtitle
    var verdictSub = $('verdictSubtitle');
    if (verdictSub) {
      if (jobDesc || data.jobDescription) {
        var jd = jobDesc || data.jobDescription;
        verdictSub.textContent = 'Compared against: ' + (jd.length > 80 ? jd.substring(0, 80) + '...' : jd);
      } else {
        verdictSub.textContent = 'Overall comparison of two resumes.';
      }
    }

    var scoreA = data.totalScoreA != null ? data.totalScoreA : 0;
    var scoreB = data.totalScoreB != null ? data.totalScoreB : 0;
    var diff = Math.abs(scoreA - scoreB);

    // Deterministic Comparison Verdict per exact RefineCV product rules
    var verdictText;
    if (diff >= 8) {
      verdictText = (scoreA > scoreB ? 'Resume A' : 'Resume B') + ' is stronger overall';
    } else if (diff >= 4) {
      verdictText = (scoreA > scoreB ? 'Resume A' : 'Resume B') + ' has a narrow overall advantage';
    } else {
      verdictText = 'Too close to call';
    }

    var headline = $('verdictHeadline');
    if (headline) headline.textContent = verdictText;

    var totalScoreA = $('totalScoreA');
    if (totalScoreA) totalScoreA.textContent = scoreA;
    var totalScoreB = $('totalScoreB');
    if (totalScoreB) totalScoreB.textContent = scoreB;

    var totalBarA = $('totalBarA');
    if (totalBarA) totalBarA.style.width = Math.min(100, Math.max(0, scoreA)) + '%';
    var totalBarB = $('totalBarB');
    if (totalBarB) totalBarB.style.width = Math.min(100, Math.max(0, scoreB)) + '%';

    var diffBadge = $('scoreDiffBadge');
    if (diffBadge) diffBadge.textContent = diff + ' pts diff';

    // Tile visual emphasis
    var tileA = $('tileA');
    var tileB = $('tileB');
    if (tileA && tileB) {
      tileA.classList.remove('is-winner');
      tileB.classList.remove('is-winner');
      if (scoreA > scoreB && diff >= 4) {
        tileA.classList.add('is-winner');
      } else if (scoreB > scoreA && diff >= 4) {
        tileB.classList.add('is-winner');
      }
    }

    // "Why Winner Leads" vs "Neutral Close Match"
    var diffBox = $('differentiatorsBox');
    var diffTitle = $('differentiatorsTitle');
    var neutralBox = $('neutralVerdictBox');

    if (diff >= 4) {
      if (diffBox) diffBox.hidden = false;
      if (neutralBox) neutralBox.hidden = true;
      var winnerLabel = (scoreA > scoreB) ? 'Resume A' : 'Resume B';
      if (diffTitle) diffTitle.textContent = 'Why ' + winnerLabel + ' leads';

      var diffList = data.keyDifferentiators || data.winnerDifferentiators;
      if (!diffList || !diffList.length) {
        diffList = [];
        if (data.categories && data.categories.length) {
          data.categories.forEach(function (cat) {
            var sA = cat.scoreA != null ? cat.scoreA : 0;
            var sB = cat.scoreB != null ? cat.scoreB : 0;
            if ((scoreA > scoreB && sA > sB) || (scoreB > scoreA && sB > sA)) {
              var exp = (scoreA > scoreB) ? (cat.explanationA || cat.evidenceA) : (cat.explanationB || cat.evidenceB);
              if (exp && diffList.length < 3) diffList.push(exp);
            }
          });
        }
      }
      fillList('differentiatorsList', (diffList && diffList.length) ? diffList.slice(0, 3) : ['Higher overall evidence density across core categories.']);
    } else {
      if (diffBox) diffBox.hidden = true;
      if (neutralBox) neutralBox.hidden = false;
      var neutralText = $('neutralVerdictText');
      if (neutralText) {
        neutralText.textContent = 'These resumes are closely matched across all evaluated categories (within ' + diff + ' point' + (diff === 1 ? '' : 's') + '). Neither candidate possesses an overwhelming advantage.';
      }
    }

    // Category Scorecard
    var listContainer = $('categoryCardsList');
    if (listContainer) {
      listContainer.innerHTML = '';
      if (data.categories && data.categories.length > 0) {
        data.categories.forEach(function (cat, idx) {
          listContainer.appendChild(renderCategoryCard(cat, fileA, fileB, idx));
        });
      }
    }

    // Overall Takeaway
    var takeawayText = $('overallTakeawayText');
    if (takeawayText) {
      takeawayText.textContent = data.overallTakeaway || 'Both resumes provide strong evidence of candidate qualifications, with distinct strengths across the evaluated dimensions.';
    }

    // What to Borrow
    var borrowTitleA = $('borrowTitleA');
    if (borrowTitleA) borrowTitleA.textContent = fileA + ' can borrow from ' + fileB;
    fillBorrowList('borrowListA', data.resumeABorrowsFromB || data.borrowFromBForA);

    var borrowTitleB = $('borrowTitleB');
    if (borrowTitleB) borrowTitleB.textContent = fileB + ' can borrow from ' + fileA;
    fillBorrowList('borrowListB', data.resumeBBorrowsFromA || data.borrowFromAForB);

    // Persist to history session
    if (comparisonId && comparisonId !== 'demo' && window.RefineCVSession && window.RefineCVSession.saveComparison) {
      window.RefineCVSession.saveComparison(comparisonId, data, jobDesc || data.jobDescription, fileA, fileB);
    }
  }

  function setupNavigation() {
    var params = new URLSearchParams(window.location.search);
    var from = params.get('from');
    var backBtn = $('compareBackBtn');
    if (backBtn && from === 'history') {
      backBtn.setAttribute('href', '/history');
      backBtn.textContent = '← Back to History';
    }
  }

  document.addEventListener('DOMContentLoaded', async function () {
    var metaId = document.body.getAttribute('data-comparison-id') || 'demo';
    setupNavigation();

    // 1. Thymeleaf injected payload
    if (window.__INITIAL_COMPARISON__) {
      renderComparisonReport(window.__INITIAL_COMPARISON__, metaId, window.__JOB_DESC__);
      return;
    }

    // 2. Client-side RefineCVSession storage
    if (window.RefineCVSession && window.RefineCVSession.getComparison) {
      var sessionItem = window.RefineCVSession.getComparison(metaId);
      if (sessionItem && sessionItem.result) {
        renderComparisonReport(sessionItem.result, metaId, sessionItem.jobDescription);
        return;
      }
    }

    // 3. Demo fallback
    if (metaId === 'demo') {
      renderComparisonReport(DEMO_COMPARISON_DATA, 'demo', null);
      return;
    }

    // 4. Server REST fetch fallback
    try {
      var res = await fetch('/compare/' + encodeURIComponent(metaId) + '/status');
      if (res.ok) {
        var remoteData = await res.json();
        if (remoteData) {
          renderComparisonReport(remoteData, metaId, remoteData.jobDescription);
          return;
        }
      }
    } catch (e) {
      console.warn('Comparison status fetch failed', e);
    }

    // 5. Final fallback
    renderComparisonReport(DEMO_COMPARISON_DATA, metaId, null);
  });

  var printBtn = $('printBtn');
  if (printBtn) {
    printBtn.addEventListener('click', function () {
      window.print();
    });
  }

})();
