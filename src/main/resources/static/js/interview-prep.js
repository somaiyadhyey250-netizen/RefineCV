/* ==========================================================
   RefineCV /interview-prep Upload Logic
   Single-resume upload, PDF validation, SSE streaming,
   progress tracking, error presentation, redirect to result
   ========================================================== */

(function () {
  'use strict';

  var MAX_FILE_BYTES = 5 * 1024 * 1024; // 5 MB

  var prepForm = document.getElementById('prepForm');
  var resumeFileInput = document.getElementById('resumeFile');
  var dropArea = document.getElementById('dropArea');
  var docCard = document.getElementById('docCard');
  var fileNameEl = document.getElementById('fileName');
  var fileSizeEl = document.getElementById('fileSize');
  var replaceBtn = document.getElementById('replaceBtn');
  var removeBtn = document.getElementById('removeBtn');
  var submitBtn = document.getElementById('submitBtn');
  var progressContainer = document.getElementById('progressContainer');
  var progressStage = document.getElementById('progressStage');
  var progressBar = document.getElementById('progressBar');
  var errorBanner = document.getElementById('errorBanner');
  var errorTitle = document.getElementById('errorTitle');
  var errorMessage = document.getElementById('errorMessage');

  var currentFile = null;
  var isSubmitting = false;

  function showError(title, msg) {
    if (!errorBanner) return;
    if (msg) {
      if (errorTitle) errorTitle.textContent = title || 'Error';
      if (errorMessage) errorMessage.textContent = msg;
      errorBanner.hidden = false;
    } else {
      errorBanner.hidden = true;
    }
  }

  function setFile(file) {
    showError(null, null);
    if (!file) {
      currentFile = null;
      if (resumeFileInput) resumeFileInput.value = '';
      if (dropArea) dropArea.hidden = false;
      if (docCard) docCard.hidden = true;
      if (submitBtn) submitBtn.disabled = true;
      return;
    }

    // Validation
    var name = file.name || '';
    if (!name.toLowerCase().endsWith('.pdf') && file.type !== 'application/pdf') {
      showError('Invalid file type', 'Please upload a valid PDF document.');
      setFile(null);
      return;
    }

    if (file.size > MAX_FILE_BYTES) {
      showError('File too large', 'Please upload a PDF smaller than 5 MB.');
      setFile(null);
      return;
    }

    currentFile = file;
    if (dropArea) dropArea.hidden = true;
    if (docCard) docCard.hidden = false;
    if (fileNameEl) fileNameEl.textContent = file.name;
    if (fileSizeEl) {
      fileSizeEl.textContent = window.formatSize
        ? window.formatSize(file.size)
        : ((file.size / 1024).toFixed(1) + ' KB');
    }
    if (submitBtn) submitBtn.disabled = false;
  }

  // File input change
  if (resumeFileInput) {
    resumeFileInput.addEventListener('change', function (e) {
      var files = e.target.files;
      if (files && files.length > 0) {
        setFile(files[0]);
      }
    });
  }

  // Drag and drop events
  if (dropArea) {
    ['dragenter', 'dragover'].forEach(function (eventName) {
      dropArea.addEventListener(eventName, function (e) {
        e.preventDefault();
        e.stopPropagation();
        dropArea.classList.add('is-dragover');
      });
    });

    ['dragleave', 'drop'].forEach(function (eventName) {
      dropArea.addEventListener(eventName, function (e) {
        e.preventDefault();
        e.stopPropagation();
        dropArea.classList.remove('is-dragover');
      });
    });

    dropArea.addEventListener('drop', function (e) {
      var dt = e.dataTransfer;
      if (dt && dt.files && dt.files.length > 0) {
        setFile(dt.files[0]);
      }
    });
  }

  // Replace & Remove buttons
  if (replaceBtn && resumeFileInput) {
    replaceBtn.addEventListener('click', function () {
      resumeFileInput.click();
    });
  }

  if (removeBtn) {
    removeBtn.addEventListener('click', function () {
      setFile(null);
    });
  }

  function setProgress(pct, message) {
    if (progressBar) progressBar.style.width = pct + '%';
    if (progressStage && message) progressStage.textContent = message;
  }

  // Form submit
  if (prepForm) {
    prepForm.addEventListener('submit', async function (e) {
      e.preventDefault();
      if (isSubmitting || !currentFile) return;

      isSubmitting = true;
      showError(null, null);

      if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.textContent = 'Generating Interview Prep...';
      }
      if (progressContainer) progressContainer.hidden = false;
      setProgress(15, 'Preparing your resume...');

      var formData = new FormData();
      formData.append('resume', currentFile);

      var prepId = null;

      try {
        var response = await fetch('/interview-prep', {
          method: 'POST',
          body: formData
        });

        if (!response.ok) {
          var errText = await response.text();
          var msg = 'The server encountered an error (' + response.status + '). Please try again.';
          try {
            var parsed = JSON.parse(errText);
            if (parsed && (parsed.error || parsed.message)) msg = parsed.error || parsed.message;
          } catch (ignored) {
            if (errText && errText.length < 150) msg = errText;
          }
          throw new Error(msg);
        }

        var headerId = response.headers.get('X-Interview-Prep-Id');
        if (headerId) prepId = headerId.trim();

        // Read SSE response stream
        var reader = response.body.getReader();
        var decoder = new TextDecoder();
        var buffer = '';
        var finalResult = null;

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
            if (!line) {
              // Dispatch event block
              if (currentEvent === 'prep-id' && currentData) {
                prepId = currentData.trim();
              } else if (currentEvent === 'status') {
                if (currentData === 'upload') {
                  setProgress(25, 'Uploading your resume...');
                } else if (currentData === 'extracting') {
                  setProgress(50, 'Extracting resume content & claims...');
                } else if (currentData === 'generating') {
                  setProgress(75, 'Generating grounded interview questions...');
                }
              } else if (currentEvent === 'result' && currentData) {
                try {
                  finalResult = JSON.parse(currentData);
                } catch (pe) {
                  console.error('Failed to parse prep result JSON:', pe);
                }
              } else if (currentEvent === 'error') {
                throw new Error(currentData || 'An error occurred during interview preparation generation.');
              }
              currentEvent = 'message';
              currentData = '';
              continue;
            }

            if (line.startsWith('event:')) {
              currentEvent = line.substring(6).trim();
            } else if (line.startsWith('data:')) {
              currentData = line.substring(5).trim();
            }
          }
        }

        if (finalResult && finalResult.id) {
          setProgress(100, 'Interview prep ready!');
          if (window.RefineCVSession && typeof window.RefineCVSession.saveInterviewPrep === 'function') {
            window.RefineCVSession.saveInterviewPrep(finalResult.id, finalResult, currentFile.name);
          }
          window.location.href = '/interview-prep/' + encodeURIComponent(finalResult.id);
        } else if (prepId) {
          // In case result event was missed, check status endpoint
          window.location.href = '/interview-prep/' + encodeURIComponent(prepId);
        } else {
          throw new Error('Interview preparation could not be completed. Please try again.');
        }

      } catch (err) {
        isSubmitting = false;
        if (progressContainer) progressContainer.hidden = true;
        if (submitBtn) {
          submitBtn.disabled = false;
          submitBtn.textContent = 'Generate Interview Prep →';
        }
        showError('Generation failed', err.message || 'An error occurred while generating interview prep.');
      }
    });
  }

})();
