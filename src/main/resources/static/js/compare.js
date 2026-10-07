/* ==========================================================
   RefineCV /compare Client Script
   Handles symmetrical dual-file upload, document card states,
   optional job description counter, SSE comparative pipeline,
   and redirection to comparison report
   ========================================================== */

(function () {
  'use strict';

  var MAX_BYTES = 5 * 1024 * 1024; // 5 MB
  var MAX_JD_CHARS = 8000;

  var state = {
    fileA: null,
    fileB: null,
    jobDescription: '',
    comparisonId: null,
    isComparing: false,
    sseTerminal: false
  };

  // DOM elements - Slots
  var dropA = document.getElementById('dropA');
  var dropB = document.getElementById('dropB');
  var fileInputA = document.getElementById('fileInputA');
  var fileInputB = document.getElementById('fileInputB');
  var docCardA = document.getElementById('docCardA');
  var docCardB = document.getElementById('docCardB');
  var fileNameA = document.getElementById('fileNameA');
  var fileNameB = document.getElementById('fileNameB');
  var fileSizeA = document.getElementById('fileSizeA');
  var fileSizeB = document.getElementById('fileSizeB');
  var replaceBtnA = document.getElementById('replaceBtnA');
  var replaceBtnB = document.getElementById('replaceBtnB');
  var removeBtnA = document.getElementById('removeBtnA');
  var removeBtnB = document.getElementById('removeBtnB');
  var statusBadgeA = document.getElementById('statusBadgeA');
  var statusBadgeB = document.getElementById('statusBadgeB');

  // Job text & form
  var jobText = document.getElementById('jobText');
  var jdCharCounter = document.getElementById('jdCharCounter');
  var jdModeIndicator = document.getElementById('jdModeIndicator');
  var compareForm = document.getElementById('compareForm');
  var compareBtn = document.getElementById('compareBtn');
  var resetBtn = document.getElementById('resetBtn');
  var compareGuidance = document.getElementById('compareGuidance');
  var formError = document.getElementById('formError');

  // Views
  var viewUpload = document.getElementById('view-upload');
  var viewLoading = document.getElementById('view-loading');
  var viewError = document.getElementById('view-error');
  var loadingFiles = document.getElementById('loadingFiles');
  var errorMessage = document.getElementById('errorMessage');
  var progressBar = document.getElementById('progressBar');
  var progressEl = document.getElementById('progress');
  var stepEls = Array.prototype.slice.call(document.querySelectorAll('#jobSteps li'));
  var cancelBtn = document.getElementById('cancelBtn');
  var retryBtn = document.getElementById('retryBtn');
  var errorBackBtn = document.getElementById('errorBackBtn');

  function showView(name) {
    if (viewUpload) viewUpload.hidden = (name !== 'upload');
    if (viewLoading) viewLoading.hidden = (name !== 'loading');
    if (viewError) viewError.hidden = (name !== 'error');
    window.scrollTo({ top: 0, behavior: 'auto' });
  }

  function showError(msg) {
    if (!formError) return;
    formError.textContent = msg || '';
    formError.hidden = !msg;
  }

  function updateSlotState(slot, file) {
    var isA = (slot === 'A');
    var dropEl = isA ? dropA : dropB;
    var cardEl = isA ? docCardA : docCardB;
    var nameEl = isA ? fileNameA : fileNameB;
    var sizeEl = isA ? fileSizeA : fileSizeB;
    var badgeEl = isA ? statusBadgeA : statusBadgeB;

    if (file) {
      if (dropEl) dropEl.hidden = true;
      if (cardEl) cardEl.hidden = false;
      if (nameEl) nameEl.textContent = file.name;
      if (sizeEl) sizeEl.textContent = window.formatSize ? window.formatSize(file.size) : ((file.size / 1024).toFixed(1) + ' KB');
      if (badgeEl) {
        badgeEl.textContent = 'PDF Ready';
        badgeEl.classList.add('is-ready');
      }
    } else {
      if (dropEl) dropEl.hidden = false;
      if (cardEl) cardEl.hidden = true;
      if (badgeEl) {
        badgeEl.textContent = 'Awaiting PDF';
        badgeEl.classList.remove('is-ready');
      }
    }

    // Evaluate button state and guidance
    var hasA = !!state.fileA;
    var hasB = !!state.fileB;

    if (hasA && hasB) {
      if (compareBtn) compareBtn.disabled = false;
      if (compareGuidance) compareGuidance.hidden = true;
    } else {
      if (compareBtn) compareBtn.disabled = true;
      if (compareGuidance) {
        compareGuidance.hidden = false;
        if (!hasA && !hasB) {
          compareGuidance.textContent = 'Upload two resumes to continue.';
        } else {
          compareGuidance.textContent = 'Upload another resume to continue.';
        }
      }
    }
  }

  function validateFile(file) {
    if (!file) return 'No file selected.';
    var isPdf = file.type === 'application/pdf' || /\.pdf$/i.test(file.name);
    if (!isPdf) return 'Only PDF files are supported. Please upload a .pdf document.';
    if (file.size > MAX_BYTES) return 'File exceeds the 5 MB limit. Please upload a smaller document.';
    return null;
  }

  function setFile(slot, file) {
    showError('');
    if (file) {
      var err = validateFile(file);
      if (err) {
        showError(err);
        return;
      }
    }

    if (slot === 'A') {
      state.fileA = file;
      updateSlotState('A', file);
    } else {
      state.fileB = file;
      updateSlotState('B', file);
    }
  }

  // Setup Drop Zone
  function bindDropZone(slot, dropEl, inputEl) {
    if (!dropEl || !inputEl) return;

    inputEl.addEventListener('change', function () {
      var files = inputEl.files;
      if (files && files.length > 0) {
        setFile(slot, files[0]);
      }
    });

    dropEl.addEventListener('dragover', function (e) {
      e.preventDefault();
      dropEl.classList.add('drag-over');
    });

    dropEl.addEventListener('dragleave', function (e) {
      if (!dropEl.contains(e.relatedTarget)) {
        dropEl.classList.remove('drag-over');
      }
    });

    dropEl.addEventListener('drop', function (e) {
      e.preventDefault();
      dropEl.classList.remove('drag-over');
      var dt = e.dataTransfer;
      if (dt && dt.files && dt.files.length > 0) {
        setFile(slot, dt.files[0]);
      }
    });
  }

  bindDropZone('A', dropA, fileInputA);
  bindDropZone('B', dropB, fileInputB);

  // Replace & Remove buttons
  if (replaceBtnA && fileInputA) {
    replaceBtnA.addEventListener('click', function () {
      fileInputA.click();
    });
  }
  if (replaceBtnB && fileInputB) {
    replaceBtnB.addEventListener('click', function () {
      fileInputB.click();
    });
  }

  if (removeBtnA && fileInputA) {
    removeBtnA.addEventListener('click', function () {
      fileInputA.value = '';
      setFile('A', null);
    });
  }
  if (removeBtnB && fileInputB) {
    removeBtnB.addEventListener('click', function () {
      fileInputB.value = '';
      setFile('B', null);
    });
  }

  // Job Description textarea live sync
  if (jobText) {
    jobText.addEventListener('input', function () {
      var val = jobText.value;
      if (val.length > MAX_JD_CHARS) {
        jobText.value = val.substring(0, MAX_JD_CHARS);
        val = jobText.value;
      }
      if (jdCharCounter) {
        jdCharCounter.textContent = val.length + ' / ' + MAX_JD_CHARS;
      }
      if (jdModeIndicator) {
        if (val.trim().length > 0) {
          jdModeIndicator.textContent = 'Job-specific comparison mode';
          jdModeIndicator.classList.add('is-job-mode');
        } else {
          jdModeIndicator.textContent = 'General comparison mode (no JD)';
          jdModeIndicator.classList.remove('is-job-mode');
        }
      }
    });
  }

  // Reset Button
  if (resetBtn) {
    resetBtn.addEventListener('click', function () {
      if (fileInputA) fileInputA.value = '';
      if (fileInputB) fileInputB.value = '';
      setFile('A', null);
      setFile('B', null);
      if (jobText) {
        jobText.value = '';
        if (jdCharCounter) jdCharCounter.textContent = '0 / ' + MAX_JD_CHARS;
        if (jdModeIndicator) {
          jdModeIndicator.textContent = 'General comparison mode (no JD)';
          jdModeIndicator.classList.remove('is-job-mode');
        }
      }
      showError('');
    });
  }

  // Paced loading step controller
  function setStep(index, status) {
    var el = stepEls[index];
    if (!el) return;
    el.classList.remove('is-active', 'is-done');
    if (status) el.classList.add('is-' + status);
  }

  function setProgress(pct) {
    if (progressBar) progressBar.style.width = pct + '%';
    if (progressEl) progressEl.setAttribute('aria-valuenow', String(Math.round(pct)));
  }

  function updateLoadingStatus(text) {
    if (loadingFiles) {
      var baseText = (state.fileA && state.fileB) ? (state.fileA.name + ' vs ' + state.fileB.name) : 'Comparing Resumes';
      loadingFiles.innerHTML = '<strong>' + baseText + '</strong><br><span style="font-size:0.9rem; opacity:0.85;">' + text + '</span>';
    }
  }

  function handleStageUpdate(stage) {
    switch (stage) {
      case 'upload':
      case 'comparison-upload':
        setStep(0, 'done');
        setStep(1, 'active');
        setProgress(20);
        updateLoadingStatus('Preparing documents for comparison...');
        break;
      case 'comparison-extract-a':
      case 'reading':
      case 'extracting':
      case 'extracting_a':
        setStep(0, 'done');
        setStep(1, 'active');
        setProgress(35);
        updateLoadingStatus('Reading content and structure from Resume A...');
        break;
      case 'comparison-extract-b':
      case 'extracting_b':
        setStep(0, 'done');
        setStep(1, 'active');
        setProgress(50);
        updateLoadingStatus('Reading content and structure from Resume B...');
        break;
      case 'comparison-ocr':
      case 'ocr':
        setStep(0, 'done');
        setStep(1, 'done');
        setStep(2, 'active');
        setProgress(65);
        updateLoadingStatus('Running OCR on scanned document pages...');
        break;
      case 'comparison-ai':
      case 'comparing':
      case 'evaluating':
      case 'ai-comparison':
        setStep(0, 'done');
        setStep(1, 'done');
        setStep(2, 'done');
        setStep(3, 'active');
        setProgress(75);
        updateLoadingStatus('Evaluating 6 core categories with AI...');
        break;
      case 'comparison-fallback':
      case 'ai-fallback':
        setStep(0, 'done');
        setStep(1, 'done');
        setStep(2, 'done');
        setStep(3, 'active');
        setProgress(80);
        updateLoadingStatus('Primary AI provider is taking longer than expected. Switching to backup...');
        break;
      case 'comparison-scorecard':
      case 'scorecard':
      case 'writing':
        setStep(0, 'done');
        setStep(1, 'done');
        setStep(2, 'done');
        setStep(3, 'done');
        setStep(4, 'active');
        setProgress(92);
        updateLoadingStatus('Writing comparative scorecard & takeaways...');
        break;
      case 'comparison-complete':
      case 'completed':
        stepEls.forEach(function (_, i) { setStep(i, 'done'); });
        setProgress(100);
        updateLoadingStatus('Comparison complete! Redirecting...');
        break;
    }
  }

  // Submit and start Comparison SSE
  if (compareForm) {
    compareForm.addEventListener('submit', function (e) {
      e.preventDefault();
      showError('');

      if (!state.fileA || !state.fileB) {
        showError('Please upload both Resume A and Resume B before comparing.');
        return;
      }

      state.jobDescription = (jobText ? jobText.value.trim() : '');
      startComparison();
    });
  }

  async function startComparison() {
    state.isComparing = true;
    state.sseTerminal = false;

    if (loadingFiles) {
      loadingFiles.textContent = state.fileA.name + ' vs ' + state.fileB.name;
    }
    stepEls.forEach(function (_, i) { setStep(i, null); });
    setStep(0, 'active');
    setProgress(15);
    showView('loading');

    var formData = new FormData();
    formData.append('resumeA', state.fileA);
    formData.append('resumeB', state.fileB);
    if (state.jobDescription) {
      formData.append('jobDescription', state.jobDescription);
    }

    try {
      var response = await fetch('/compare', {
        method: 'POST',
        body: formData
      });

      if (!response.ok) {
        var errText = await response.text();
        var msg = 'The server encountered an error (' + response.status + '). Please try again.';
        try {
          var parsed = JSON.parse(errText);
          if (parsed && (parsed.error || parsed.message)) msg = parsed.error || parsed.message;
        } catch (e) {
          if (errText && errText.length < 150) msg = errText;
        }
        throw new Error(msg);
      }

      var headerId = response.headers.get('X-Comparison-Id');
      if (headerId) state.comparisonId = headerId.trim();

      await readSSE(response);

    } catch (err) {
      state.isComparing = false;
      if (errorMessage) errorMessage.textContent = err.message || 'An unexpected error occurred.';
      showView('error');
    }
  }

  async function readSSE(response) {
    var reader = response.body.getReader();
    var decoder = new TextDecoder();
    var buffer = '';

    while (true) {
      var chunk = await reader.read();
      if (chunk.done) break;

      buffer += decoder.decode(chunk.value, { stream: true });
      var lines = buffer.split('\n');
      buffer = lines.pop(); // keep remainder

      var currentEvent = 'message';
      var currentData = '';

      for (var i = 0; i < lines.length; i++) {
        var line = lines[i].trim();
        if (line.startsWith('event:')) {
          currentEvent = line.substring(6).trim();
        } else if (line.startsWith('data:')) {
          currentData = line.substring(5).trim();
        } else if (line === '') {
          if (currentEvent && currentData) {
            handleSSEEvent(currentEvent, currentData);
          }
          currentEvent = 'message';
          currentData = '';
        }
      }
    }

    if (buffer.length > 0 && currentEvent && currentData) {
      handleSSEEvent(currentEvent, currentData);
    }
  }

  function handleSSEEvent(event, data) {
    if (event === 'comparison-id') {
      state.comparisonId = data.trim();
    } else if (event === 'status') {
      handleStageUpdate(data.trim());
    } else if (event === 'result') {
      state.sseTerminal = true;
      state.isComparing = false;
      handleStageUpdate('completed');

      var result = null;
      try {
        result = JSON.parse(data);
      } catch (e) {
        console.error('Failed to parse result JSON', e);
      }

      var id = state.comparisonId || 'cmp-' + Date.now();
      if (window.RefineCVSession && window.RefineCVSession.saveComparison) {
        window.RefineCVSession.saveComparison(
          id,
          result,
          state.jobDescription,
          state.fileA ? state.fileA.name : 'Resume A.pdf',
          state.fileB ? state.fileB.name : 'Resume B.pdf'
        );
      }

      setTimeout(function () {
        window.location.href = '/compare/' + encodeURIComponent(id);
      }, 500);

    } else if (event === 'error') {
      state.sseTerminal = true;
      state.isComparing = false;
      if (errorMessage) errorMessage.textContent = data || 'Comparison could not be completed.';
      showView('error');
    }
  }

  if (cancelBtn) {
    cancelBtn.addEventListener('click', function () {
      state.isComparing = false;
      showView('upload');
    });
  }

  if (retryBtn) {
    retryBtn.addEventListener('click', function () {
      if (state.fileA && state.fileB) {
        startComparison();
      } else {
        showView('upload');
      }
    });
  }

  if (errorBackBtn) {
    errorBackBtn.addEventListener('click', function () {
      showView('upload');
    });
  }

})();
