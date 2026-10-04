/* ==========================================================
   RefineCV Common Utilities
   Theme persistence, toast feedback, clipboard, formatting
   ========================================================== */

(function () {
  'use strict';

  // Theme Syncing
  function syncTheme() {
    var toggle = document.getElementById('themeToggle');
    if (!toggle) return;
    var isDark = document.documentElement.getAttribute('data-theme') === 'dark';
    toggle.setAttribute('aria-pressed', String(isDark));
    toggle.setAttribute('aria-label', isDark ? 'Switch to light theme' : 'Switch to dark theme');
  }

  var toggleBtn = document.getElementById('themeToggle');
  if (toggleBtn) {
    toggleBtn.addEventListener('click', function () {
      var current = document.documentElement.getAttribute('data-theme');
      var next = (current === 'dark') ? 'light' : 'dark';
      document.documentElement.setAttribute('data-theme', next);
      try {
        localStorage.setItem('refinecv-theme', next);
      } catch (e) {}
      syncTheme();
    });
    syncTheme();
  }

  // Mobile Navigation Drawer
  function initMobileNav() {
    var menuToggle = document.getElementById('menuToggle');
    var mobileNav = document.getElementById('mobileNav');
    if (!menuToggle || !mobileNav) return;

    function openMenu() {
      menuToggle.setAttribute('aria-expanded', 'true');
      menuToggle.setAttribute('aria-label', 'Close navigation menu');
      menuToggle.classList.add('is-open');
      mobileNav.removeAttribute('hidden');
      requestAnimationFrame(function () {
        mobileNav.classList.add('is-open');
      });
      document.body.classList.add('mobile-nav-active');
    }

    function closeMenu() {
      menuToggle.setAttribute('aria-expanded', 'false');
      menuToggle.setAttribute('aria-label', 'Open navigation menu');
      menuToggle.classList.remove('is-open');
      mobileNav.classList.remove('is-open');
      document.body.classList.remove('mobile-nav-active');
      setTimeout(function () {
        if (!menuToggle.classList.contains('is-open')) {
          mobileNav.setAttribute('hidden', '');
        }
      }, 220);
    }

    menuToggle.addEventListener('click', function (e) {
      e.stopPropagation();
      var isExpanded = menuToggle.getAttribute('aria-expanded') === 'true';
      if (isExpanded) {
        closeMenu();
      } else {
        openMenu();
      }
    });

    var navLinks = mobileNav.querySelectorAll('.mobile-nav-link');
    for (var i = 0; i < navLinks.length; i++) {
      navLinks[i].addEventListener('click', function () {
        closeMenu();
      });
    }

    document.addEventListener('click', function (e) {
      if (menuToggle.getAttribute('aria-expanded') === 'true') {
        if (!mobileNav.contains(e.target) && !menuToggle.contains(e.target)) {
          closeMenu();
        }
      }
    });

    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && menuToggle.getAttribute('aria-expanded') === 'true') {
        closeMenu();
        menuToggle.focus();
      }
    });

    window.addEventListener('resize', function () {
      if (window.innerWidth > 768 && menuToggle.getAttribute('aria-expanded') === 'true') {
        closeMenu();
      }
    }, { passive: true });
  }

  // Desktop Hero 3D Tilt with blue ambient glow
  function initHeroTilt() {
    var isDesktopPointer = window.matchMedia('(hover: hover) and (pointer: fine)').matches;
    var prefersReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (!isDesktopPointer || prefersReduced) return;

    var container = document.querySelector('.hero-sheet');
    var sheet = document.querySelector('.sheet');
    if (!container || !sheet) return;

    var rafId = null;
    var maxTilt = 2.8;

    function handlePointerMove(e) {
      if (rafId) cancelAnimationFrame(rafId);
      rafId = requestAnimationFrame(function () {
        var rect = sheet.getBoundingClientRect();
        var centerX = rect.left + rect.width / 2;
        var centerY = rect.top + rect.height / 2;
        var normX = Math.max(-1, Math.min(1, (e.clientX - centerX) / (rect.width / 2)));
        var normY = Math.max(-1, Math.min(1, (e.clientY - centerY) / (rect.height / 2)));

        var rotX = (-normY * maxTilt).toFixed(2);
        var rotY = (normX * maxTilt).toFixed(2);

        sheet.style.setProperty('--tilt-rx', rotX + 'deg');
        sheet.style.setProperty('--tilt-ry', rotY + 'deg');
        sheet.classList.add('is-tilting');
      });
    }

    function handlePointerLeave() {
      if (rafId) cancelAnimationFrame(rafId);
      sheet.style.setProperty('--tilt-rx', '0deg');
      sheet.style.setProperty('--tilt-ry', '0deg');
      sheet.classList.remove('is-tilting');
    }

    container.addEventListener('pointermove', handlePointerMove, { passive: true });
    container.addEventListener('pointerleave', handlePointerLeave, { passive: true });
  }

  // Scroll Reveal Animation via IntersectionObserver
  function initScrollReveal() {
    var targets = document.querySelectorAll('.reveal-on-scroll');
    if (!targets.length) return;

    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches || !('IntersectionObserver' in window)) {
      for (var i = 0; i < targets.length; i++) {
        targets[i].classList.add('is-revealed');
      }
      return;
    }

    var observer = new IntersectionObserver(function (entries, obs) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-revealed');
          obs.unobserve(entry.target);
        }
      });
    }, {
      rootMargin: '0px 0px -36px 0px',
      threshold: 0.1
    });

    for (var j = 0; j < targets.length; j++) {
      observer.observe(targets[j]);
    }
  }

  function initPageEnhancements() {
    initMobileNav();
    initHeroTilt();
    initScrollReveal();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initPageEnhancements);
  } else {
    initPageEnhancements();
  }

  // Toast notifications
  window.showToast = function (message) {
    var toast = document.getElementById('globalToast');
    if (!toast) {
      toast = document.createElement('div');
      toast.id = 'globalToast';
      toast.className = 'toast';
      document.body.appendChild(toast);
    }
    toast.textContent = message;
    toast.classList.add('show');
    clearTimeout(window._toastTimer);
    window._toastTimer = setTimeout(function () {
      toast.classList.remove('show');
    }, 2800);
  };

  // Clipboard utility
  window.copyText = function (text, successMsg) {
    if (!navigator.clipboard) {
      var ta = document.createElement('textarea');
      ta.value = text;
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
      window.showToast(successMsg || 'Copied to clipboard');
      return;
    }
    navigator.clipboard.writeText(text).then(function () {
      window.showToast(successMsg || 'Copied to clipboard');
    }).catch(function () {
      window.showToast('Failed to copy');
    });
  };

  // HTML escaping for safe DOM textContent
  window.escapeHTML = function (str) {
    if (str == null) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  };

  // Format bytes
  window.formatSize = function (bytes) {
    if (!bytes || bytes < 1024) return (bytes || 0) + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1024 / 1024).toFixed(2) + ' MB';
  };

  // Parse arrays or strings safely
  window.toList = function (v) {
    if (Array.isArray(v)) {
      return v.map(function (x) {
        return typeof x === 'string' ? x : (x && (x.text || x.title || x.name)) || JSON.stringify(x);
      }).map(function (s) { return String(s).trim(); }).filter(Boolean);
    }
    if (typeof v === 'string') {
      return v.split(/\r?\n|\u2022/).map(function (s) {
        return s.replace(/^[-*\d.)\s]+/, '').trim();
      }).filter(Boolean);
    }
    return [];
  };

  // Scroll-to-top button handler
  document.addEventListener('DOMContentLoaded', function () {
    var scrollBtn = document.getElementById('scrollTopBtn');
    if (scrollBtn) {
      window.addEventListener('scroll', function () {
        if (window.scrollY > 280) {
          scrollBtn.classList.add('is-visible');
        } else {
          scrollBtn.classList.remove('is-visible');
        }
      }, { passive: true });

      scrollBtn.addEventListener('click', function () {
        window.scrollTo({ top: 0, behavior: 'smooth' });
      });
    }
  });

  // Session & History Storage helpers
  window.RefineCVSession = {
    save: function (id, data, mode, jobDesc, fileName) {
      if (!id || id === 'demo' || (typeof id === 'string' && id.startsWith('cmp-'))) return;
      if (fileName === 'Sample_Resume.pdf' || fileName === 'Uploaded_Resume.pdf') return;
      try {
        var effScore = 0;
        if (data) {
          effScore = data.score != null ? data.score : (data.jobMatchScore != null ? data.jobMatchScore : 0);
        }
        var payload = {
          analysisId: id,
          result: data,
          mode: mode || 'GENERAL',
          jobDescription: jobDesc || null,
          fileName: fileName || 'Resume.pdf',
          score: effScore,
          timestamp: new Date().toISOString()
        };
        sessionStorage.setItem('refinecv_' + id, JSON.stringify(payload));
        localStorage.setItem('refinecv_' + id, JSON.stringify(payload));
        sessionStorage.setItem('refinecv_last_id', id);

        // Append to history list
        var history = [];
        try {
          history = JSON.parse(localStorage.getItem('refinecv_history') || sessionStorage.getItem('refinecv_history') || '[]');
        } catch (e) {}
        history = history.filter(function (h) {
          return h && h.analysisId !== id && h.analysisId !== 'demo' && !String(h.analysisId).startsWith('cmp-')
            && h.fileName !== 'Sample_Resume.pdf' && h.fileName !== 'Uploaded_Resume.pdf';
        });
        history.unshift({
          analysisId: id,
          mode: mode || 'GENERAL',
          fileName: fileName || 'Resume.pdf',
          score: effScore,
          date: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        });
        var trimmed = JSON.stringify(history.slice(0, 20));
        sessionStorage.setItem('refinecv_history', trimmed);
        localStorage.setItem('refinecv_history', trimmed);
      } catch (e) {}
    },
    get: function (id) {
      try {
        var str = sessionStorage.getItem('refinecv_' + id) || localStorage.getItem('refinecv_' + id);
        return str ? JSON.parse(str) : null;
      } catch (e) {
        return null;
      }
    },
    saveImprovement: function (id, data) {
      try {
        var str = JSON.stringify(data);
        sessionStorage.setItem('refinecv_imp_' + id, str);
        localStorage.setItem('refinecv_imp_' + id, str);
      } catch (e) {}
    },
    getImprovement: function (id) {
      try {
        var str = sessionStorage.getItem('refinecv_imp_' + id) || localStorage.getItem('refinecv_imp_' + id);
        return str ? JSON.parse(str) : null;
      } catch (e) {
        return null;
      }
    },
    saveComparison: function (id, data, jobDesc, fileNameA, fileNameB) {
      if (!id || id === 'demo') return;
      var fileA = fileNameA || (data && data.fileNameA) || 'Resume A.pdf';
      var fileB = fileNameB || (data && data.fileNameB) || 'Resume B.pdf';
      if (fileA === 'Sample_Resume.pdf' || fileB === 'Sample_Resume.pdf') return;
      try {
        var payload = {
          comparisonId: id,
          result: data,
          type: 'COMPARE',
          fileNameA: fileA,
          fileNameB: fileB,
          scoreA: data && data.totalScoreA != null ? data.totalScoreA : 0,
          scoreB: data && data.totalScoreB != null ? data.totalScoreB : 0,
          verdict: data && data.winnerVerdict ? data.winnerVerdict : '',
          jobDescription: jobDesc || (data && data.jobDescription) || null,
          timestamp: new Date().toISOString()
        };
        sessionStorage.setItem('refinecv_cmp_' + id, JSON.stringify(payload));
        localStorage.setItem('refinecv_cmp_' + id, JSON.stringify(payload));
        sessionStorage.setItem('refinecv_last_cmp_id', id);

        // Append to history list
        var history = [];
        try {
          history = JSON.parse(localStorage.getItem('refinecv_history') || sessionStorage.getItem('refinecv_history') || '[]');
        } catch (e) {}
        history = history.filter(function (h) {
          return h && h.analysisId !== id && h.analysisId !== 'demo'
            && h.fileName !== 'Sample_Resume.pdf' && h.fileName !== 'Uploaded_Resume.pdf';
        });
        history.unshift({
          analysisId: id,
          type: 'COMPARE',
          mode: payload.jobDescription ? 'SPECIFIC_JOB' : 'GENERAL',
          fileName: payload.fileNameA,
          fileNameB: payload.fileNameB,
          score: payload.scoreA,
          scoreB: payload.scoreB,
          verdict: payload.verdict,
          jobContext: payload.jobDescription,
          date: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        });
        var trimmed = JSON.stringify(history.slice(0, 20));
        sessionStorage.setItem('refinecv_history', trimmed);
        localStorage.setItem('refinecv_history', trimmed);
      } catch (e) {}
    },
    getComparison: function (id) {
      try {
        var str = sessionStorage.getItem('refinecv_cmp_' + id) || localStorage.getItem('refinecv_cmp_' + id);
        return str ? JSON.parse(str) : null;
      } catch (e) {
        return null;
      }
    },
    saveInterviewPrep: function (id, data, fileName) {
      if (!id || id === 'demo') return;
      var file = fileName || (data && data.filename) || 'Resume.pdf';
      if (file === 'Sample_Resume.pdf' || file === 'Uploaded_Resume.pdf') return;
      try {
        var qCount = (data && data.questions && data.questions.length) || (data && data.questionCount) || 0;
        var payload = {
          prepId: id,
          result: data,
          type: 'INTERVIEW_PREP',
          fileName: file,
          questionCount: qCount,
          overallPreparationNote: data && data.overallPreparationNote ? data.overallPreparationNote : '',
          timestamp: new Date().toISOString()
        };
        sessionStorage.setItem('refinecv_prep_' + id, JSON.stringify(payload));
        localStorage.setItem('refinecv_prep_' + id, JSON.stringify(payload));
        sessionStorage.setItem('refinecv_last_prep_id', id);

        // Append to history list
        var history = [];
        try {
          history = JSON.parse(localStorage.getItem('refinecv_history') || sessionStorage.getItem('refinecv_history') || '[]');
        } catch (e) {}
        history = history.filter(function (h) {
          return h && h.analysisId !== id && h.analysisId !== 'demo'
            && h.fileName !== 'Sample_Resume.pdf' && h.fileName !== 'Uploaded_Resume.pdf';
        });
        history.unshift({
          analysisId: id,
          type: 'INTERVIEW_PREP',
          mode: 'INTERVIEW_PREP',
          fileName: file,
          score: qCount,
          verdict: payload.overallPreparationNote,
          date: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        });
        var trimmed = JSON.stringify(history.slice(0, 30));
        sessionStorage.setItem('refinecv_history', trimmed);
        localStorage.setItem('refinecv_history', trimmed);
      } catch (e) {}
    },
    getInterviewPrep: function (id) {
      try {
        var str = sessionStorage.getItem('refinecv_prep_' + id) || localStorage.getItem('refinecv_prep_' + id);
        return str ? JSON.parse(str) : null;
      } catch (e) {
        return null;
      }
    },
    getLastId: function () {
      return sessionStorage.getItem('refinecv_last_id') || localStorage.getItem('refinecv_last_id');
    },
    getHistory: function () {
      try {
        var raw = JSON.parse(localStorage.getItem('refinecv_history') || sessionStorage.getItem('refinecv_history') || '[]');
        if (!Array.isArray(raw)) return [];
        return raw.filter(function (h) {
          if (!h || !h.analysisId) return false;
          if (h.analysisId === 'demo') return false;
          if (h.type !== 'COMPARE' && h.type !== 'INTERVIEW_PREP' && typeof h.analysisId === 'string' && (h.analysisId.startsWith('cmp-') || h.analysisId.startsWith('prep-'))) return false;
          if (h.fileName === 'Sample_Resume.pdf' || h.fileName === 'Uploaded_Resume.pdf') return false;
          if (h.fileNameB === 'Sample_Resume.pdf' || h.fileNameB === 'Uploaded_Resume.pdf') return false;
          return true;
        });
      } catch (e) {
        return [];
      }
    },
    clearHistory: function () {
      try {
        var history = [];
        try {
          history = JSON.parse(localStorage.getItem('refinecv_history') || sessionStorage.getItem('refinecv_history') || '[]');
        } catch (e) {}

        if (Array.isArray(history)) {
          history.forEach(function (item) {
            if (!item || !item.analysisId) return;
            var id = item.analysisId;
            try {
              localStorage.removeItem('refinecv_' + id);
              sessionStorage.removeItem('refinecv_' + id);
              localStorage.removeItem('refinecv_cmp_' + id);
              sessionStorage.removeItem('refinecv_cmp_' + id);
              localStorage.removeItem('refinecv_prep_' + id);
              sessionStorage.removeItem('refinecv_prep_' + id);
              localStorage.removeItem('refinecv_imp_' + id);
              sessionStorage.removeItem('refinecv_imp_' + id);
            } catch (e) {}
          });
        }

        localStorage.removeItem('refinecv_history');
        sessionStorage.removeItem('refinecv_history');
        localStorage.removeItem('refinecv_last_id');
        sessionStorage.removeItem('refinecv_last_id');
        localStorage.removeItem('refinecv_last_cmp_id');
        sessionStorage.removeItem('refinecv_last_cmp_id');
        localStorage.removeItem('refinecv_last_prep_id');
        sessionStorage.removeItem('refinecv_last_prep_id');
      } catch (e) {}
    }
  };

})();
