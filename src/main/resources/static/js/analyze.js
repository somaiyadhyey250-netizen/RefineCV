/* ==========================================================
   RefineCV /analyze Script
   File upload, mode selection, live character counter,
   validation, real SSE pipeline, and redirect to report
   ========================================================== */

(function () {
  'use strict';

  var MAX_BYTES = 5 * 1024 * 1024; // 5 MB
  var MAX_JD_CHARS = 8000;

  var state = {
    file: null,
    mode: 'GENERAL',
    jobDescription: '',
    analysisId: null,
    isAnalyzing: false,
    sseReceivedTerminal: false
  };

  var drop = document.getElementById('drop');
  var fileInput = document.getElementById('fileInput');
  var fileRow = document.getElementById('fileRow');
  var fileNameEl = document.getElementById('fileName');
  var fileSizeEl = document.getElementById('fileSize');
  var fileRemoveBtn = document.getElementById('fileRemove');
  var formError = document.getElementById('formError');
  var analyzeForm = document.getElementById('analyzeForm');
  var analyzeBtn = document.getElementById('analyzeBtn');
  var resetBtn = document.getElementById('resetBtn');
  var jobField = document.getElementById('jobField');
  var jobText = document.getElementById('jobText');
  var jdCharCounter = document.getElementById('jdCharCounter');

  var viewUpload = document.getElementById('view-upload');
  var viewLoading = document.getElementById('view-loading');
  var viewError = document.getElementById('view-error');
  var errorMessage = document.getElementById('errorMessage');
  var retryBtn = document.getElementById('retryBtn');
  var errorBackBtn = document.getElementById('errorBackBtn');

  var progressBar = document.getElementById('progressBar');
  var progressEl = document.getElementById('progress');
  var loadingFile = document.getElementById('loadingFile');
  var stepEls = Array.prototype.slice.call(document.querySelectorAll('#jobSteps li'));

  // Switch views
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

  // Validate job description (backend is authoritative, frontend rejects only obvious spam)
  function isUsableJobDescription(text) {
    if (!text || text.trim().length === 0) return false;
    var trimmed = text.trim();
    if (trimmed.length > MAX_JD_CHARS) return false;

    // Check letter count: meaningful input must contain letters
    var letters = trimmed.match(/[a-zA-Z]/g);
    if (!letters || letters.length < 2) return false;

    // Reject symbol/number spam where symbols dominate > 70%
    var symbols = trimmed.match(/[^a-zA-Z0-9\s]/g);
    if (symbols && symbols.length > trimmed.length * 0.65) return false;

    return true;
  }

  // Check query parameter for initial mode selection (e.g., /analyze?mode=SPECIFIC_JOB)
  try {
    var params = new URLSearchParams(window.location.search);
    var qMode = params.get('mode');
    if (qMode && (qMode.toUpperCase() === 'SPECIFIC_JOB' || qMode.toLowerCase() === 'job')) {
      var jobRadio = document.querySelector('input[name="mode"][value="SPECIFIC_JOB"]');
      if (jobRadio) jobRadio.checked = true;
    }
  } catch (e) {}

  // Sync mode radio
  function syncMode() {
    var checked = document.querySelector('input[name="mode"]:checked');
    state.mode = checked ? checked.value : 'GENERAL';
    if (jobField) {
      jobField.hidden = (state.mode !== 'SPECIFIC_JOB');
    }
  }

  var modeRadios = document.querySelectorAll('input[name="mode"]');
  Array.prototype.forEach.call(modeRadios, function (r) {
    r.addEventListener('change', syncMode);
  });
  syncMode();

  // Character counter
  if (jobText && jdCharCounter) {
    jobText.addEventListener('input', function () {
      var len = jobText.value.length;
      jdCharCounter.textContent = len + ' / ' + MAX_JD_CHARS;
    });
  }

  // Validate file signature
  function validateFile(file) {
    return new Promise(function (resolve) {
      var nameOk = /\.pdf$/i.test(file.name);
      var typeOk = !file.type || file.type === 'application/pdf';
      if (!nameOk || !typeOk) {
        return resolve('The selected file is not a PDF. Please upload a PDF resume.');
      }
      if (file.size === 0) {
        return resolve('That file is empty. Please choose a valid PDF resume.');
      }
      if (file.size > MAX_BYTES) {
        return resolve('That PDF is ' + window.formatSize(file.size) + '. The limit is 5 MB, please choose a smaller file.');
      }
      var reader = new FileReader();
      reader.onload = function () {
        var str = String(reader.result);
        resolve(str.indexOf('%PDF') === 0 ? '' : 'That file has a .pdf extension but does not contain a valid PDF signature.');
      };
      reader.onerror = function () {
        resolve('Could not read the selected file. Please try selecting it again.');
      };
      reader.readAsText(file.slice(0, 5));
    });
  }

  function setFile(file) {
    state.file = file;
    if (drop) drop.classList.toggle('has-file', !!file);
    if (fileRow) fileRow.hidden = !file;
    if (file) {
      if (fileNameEl) fileNameEl.textContent = file.name;
      if (fileSizeEl) fileSizeEl.textContent = window.formatSize(file.size);
      var title = document.getElementById('dropTitle');
      var sub = document.getElementById('dropSub');
      if (title) title.textContent = 'Resume selected';
      if (sub) sub.textContent = 'Click to choose a different PDF';
    } else {
      if (fileInput) fileInput.value = '';
      var title = document.getElementById('dropTitle');
      var sub = document.getElementById('dropSub');
      if (title) title.textContent = 'Drop your PDF here';
      if (sub) sub.textContent = 'or click to choose a file';
    }
  }

  function handleFiles(list) {
    showError('');
    if (!list || !list.length) return;
    if (list.length > 1) {
      showError('Please upload one resume at a time.');
      return;
    }
    var file = list[0];
    validateFile(file).then(function (err) {
      if (err) {
        setFile(null);
        showError(err);
      } else {
        setFile(file);
      }
    });
  }

  if (fileInput) {
    fileInput.addEventListener('change', function () {
      handleFiles(fileInput.files);
    });
  }

  if (fileRemoveBtn) {
    fileRemoveBtn.addEventListener('click', function () {
      setFile(null);
      showError('');
    });
  }

  if (drop) {
    ['dragenter', 'dragover'].forEach(function (ev) {
      drop.addEventListener(ev, function (e) {
        e.preventDefault();
        drop.classList.add('is-over');
      });
    });
    ['dragleave', 'drop'].forEach(function (ev) {
      drop.addEventListener(ev, function (e) {
        e.preventDefault();
        drop.classList.remove('is-over');
      });
    });
    drop.addEventListener('drop', function (e) {
      handleFiles(e.dataTransfer && e.dataTransfer.files);
    });
    ['dragover', 'drop'].forEach(function (ev) {
      window.addEventListener(ev, function (e) {
        if (!drop.contains(e.target)) e.preventDefault();
      });
    });
  }

  if (resetBtn) {
    resetBtn.addEventListener('click', function () {
      setFile(null);
      showError('');
      if (jobText) jobText.value = '';
      if (jdCharCounter) jdCharCounter.textContent = '0 / ' + MAX_JD_CHARS;
      var defaultRadio = document.querySelector('input[name="mode"][value="GENERAL"]');
      if (defaultRadio) defaultRadio.checked = true;
      syncMode();
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

  function handleStageUpdate(stage) {
    var m = stage ? stage.match(/^ocr-page-(\d+)(?:-of-(\d+))?$/) : null;
    if (m) {
      var pageNum = parseInt(m[1], 10);
      var totalPages = m[2] ? parseInt(m[2], 10) : null;
      setStep(0, 'done');
      setStep(1, 'done');
      setStep(2, 'active');
      var bEl = stepEls[2] ? stepEls[2].querySelector('b') : null;
      if (bEl) {
        bEl.textContent = 'Checking scanned pages';
      }
      var smallEl = stepEls[2] ? stepEls[2].querySelector('small') : null;
      if (smallEl) {
        smallEl.textContent = totalPages
          ? 'OCR page ' + pageNum + ' of ' + totalPages + '...'
          : 'OCR page ' + pageNum + '...';
      }
      var baseProgress = 48;
      var pageProgress = totalPages
        ? Math.min(68, baseProgress + Math.round((pageNum / totalPages) * 20))
        : Math.min(68, baseProgress + (pageNum * 6));
      setProgress(pageProgress);
      return;
    }
    switch (stage) {
      case 'upload':
        setStep(0, 'done');
        setStep(1, 'active');
        setProgress(25);
        break;
      case 'extracting':
      case 'extracted':
        setStep(0, 'done');
        setStep(1, 'done');
        setStep(2, 'active');
        setProgress(45);
        break;
      case 'ocr':
        setStep(0, 'done');
        setStep(1, 'done');
        setStep(2, 'active');
        setProgress(48);
        var ocrStartSmall = stepEls[2] ? stepEls[2].querySelector('small') : null;
        if (ocrStartSmall) {
          ocrStartSmall.textContent = 'Initializing optical character recognition...';
        }
        break;
      case 'ocr-complete':
        setStep(0, 'done');
        setStep(1, 'done');
        setStep(2, 'active');
        setProgress(70);
        var ocrDoneSmall = stepEls[2] ? stepEls[2].querySelector('small') : null;
        if (ocrDoneSmall) {
          ocrDoneSmall.textContent = 'Text extracted successfully';
        }
        break;
      case 'ai-analysis':
        setStep(2, 'done');
        setStep(3, 'active');
        setProgress(75);
        break;
      case 'recommendations':
        setStep(3, 'done');
        setStep(4, 'active');
        setProgress(90);
        break;
      case 'completed':
        stepEls.forEach(function (_, i) { setStep(i, 'done'); });
        setProgress(100);
        break;
    }
  }

  // Submit and start SSE analysis
  if (analyzeForm) {
    analyzeForm.addEventListener('submit', function (e) {
      e.preventDefault();
      showError('');

      if (!state.file) {
        showError('Please select a PDF resume before analyzing.');
        return;
      }

      var mode = state.mode;
      var job = (jobText ? jobText.value.trim() : '');

      if (mode === 'SPECIFIC_JOB') {
        if (!job || job.length === 0) {
          showError('Please enter a target job description or role context.');
          if (jobText) jobText.focus();
          return;
        }
        if (!isUsableJobDescription(job)) {
          showError('Please enter a meaningful job description or target role context.');
          if (jobText) jobText.focus();
          return;
        }
        state.jobDescription = job;
      } else {
        state.jobDescription = '';
      }

      startAnalysis();
    });
  }

  async function startAnalysis() {
    state.isAnalyzing = true;
    state.sseReceivedTerminal = false;

    if (loadingFile) loadingFile.textContent = state.file.name;
    stepEls.forEach(function (_, i) { setStep(i, null); });
    var ocrInitSmall = stepEls[2] ? stepEls[2].querySelector('small') : null;
    if (ocrInitSmall) ocrInitSmall.textContent = 'Applying OCR where text is an image';
    setStep(0, 'active');
    setProgress(10);
    showView('loading');

    var formData = new FormData();
    formData.append('resume', state.file);
    formData.append('mode', state.mode);
    if (state.mode === 'SPECIFIC_JOB' && state.jobDescription) {
      formData.append('jobDescription', state.jobDescription);
    }

    try {
      var response = await fetch('/analyze', {
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

      var headerId = response.headers.get('X-Analysis-Id');
      if (headerId) state.analysisId = headerId.trim();

      await readSSE(response);
      if (!state.sseReceivedTerminal) {
        throw new Error('The analysis connection closed unexpectedly. Please try again.');
      }

    } catch (err) {
      state.isAnalyzing = false;
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
          // Empty line indicates event boundary
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
    if (event === 'analysis-id') {
      state.analysisId = data.trim();
    } else if (event === 'status') {
      handleStageUpdate(data.trim());
    } else if (event === 'result') {
      state.sseReceivedTerminal = true;
      state.isAnalyzing = false;
      handleStageUpdate('completed');

      var result = null;
      try {
        result = JSON.parse(data);
      } catch (e) {
        console.error('Failed to parse result JSON', e);
      }

      var id = state.analysisId || 'analysis-' + Date.now();
      window.RefineCVSession.save(id, result, state.mode, state.jobDescription, state.file ? state.file.name : 'Resume.pdf');

      setTimeout(function () {
        if (state.mode === 'SPECIFIC_JOB') {
          window.location.href = '/job-analysis/' + encodeURIComponent(id);
        } else {
          window.location.href = '/analysis/' + encodeURIComponent(id);
        }
      }, 500);

    } else if (event === 'error') {
      state.sseReceivedTerminal = true;
      state.isAnalyzing = false;
      if (errorMessage) errorMessage.textContent = data || 'Analysis could not be completed.';
      showView('error');
    }
  }

  if (retryBtn) {
    retryBtn.addEventListener('click', function () {
      if (state.file) {
        startAnalysis();
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

  var cancelBtn = document.getElementById('cancelAnalysisBtn');
  if (cancelBtn) {
    cancelBtn.addEventListener('click', function () {
      state.isAnalyzing = false;
      if (state.analysisId) {
        try {
          fetch('/analysis/' + encodeURIComponent(state.analysisId) + '/cancel', { method: 'POST' });
        } catch (e) {}
      }
      showView('upload');
    });
  }

})();
