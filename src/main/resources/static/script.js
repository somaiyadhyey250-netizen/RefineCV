/* =========================================================
   REFINECV — V4.2 PRODUCTION FRONTEND
   AI-Powered Resume Intelligence & Job Match Analyzer
   ========================================================= */

/* =========================================================
   DOM ELEMENTS
   ========================================================= */

const resumeInput = document.getElementById("resumeFile");
const dropZone = document.getElementById("dropZone");
const selectedFile = document.getElementById("selectedFile");
const analyzeButton = document.getElementById("analyzeButton");
const dropTitle = document.getElementById("dropTitle");
const dropSubtitle = document.getElementById("dropSubtitle");

const analysisLoading = document.getElementById("analysisLoading");
const resultsSection = document.getElementById("resultsSection");
const resultsContent = document.getElementById("resultsContent");
const analyzeAgain = document.getElementById("analyzeAgain");
const messageBox = document.getElementById("messageBox");

const improveResumeButton = document.getElementById("improveResumeButton");
const improvementContainer = document.getElementById("improvementContainer");
const improvementLoading = document.getElementById("improvementLoading");
const improvementContent = document.getElementById("improvementContent");

/* V4.2 Mode Selection Elements */
const modeCardGeneral = document.getElementById("modeCardGeneral");
const modeCardJob = document.getElementById("modeCardJob");
const jobDescriptionWrapper = document.getElementById("jobDescriptionWrapper");
const jobDescriptionInput = document.getElementById("jobDescriptionInput");
const jdCharCounter = document.getElementById("jdCharCounter");
const continueButton = document.getElementById("continueButton");
const uploadSection = document.getElementById("uploadSection");
const resetButton = document.getElementById("resetButton");

/* Navigation & Controls */
const themeToggleBtn = document.getElementById("themeToggleBtn");
const heroAnalyzeBtn = document.getElementById("heroAnalyzeBtn");
const heroExampleBtn = document.getElementById("heroExampleBtn");
const navExampleLink = document.getElementById("navExampleLink");
const downloadReportBtn = document.getElementById("downloadReportBtn");
const newAnalysisTopBtn = document.getElementById("newAnalysisTopBtn");
const loadingBarFill = document.getElementById("loadingBarFill");
const resultsHeroAccent = document.getElementById("resultsHeroAccent");
const resultsModeBadge = document.getElementById("resultsModeBadge");
const resultsBottomAction = document.getElementById("resultsBottomAction");
const resultsBottomPrompt = document.getElementById("resultsBottomPrompt");

/* Stepper Indicators */
const stepIndicator1 = document.getElementById("stepIndicator1");
const stepIndicator2 = document.getElementById("stepIndicator2");
const stepIndicator3 = document.getElementById("stepIndicator3");
const stepIndicator4 = document.getElementById("stepIndicator4");

/* =========================================================
   APPLICATION STATE
   ========================================================= */

let selectedResume = null;
let terminalEventReceived = false;
let currentAnalysisId = null;
let currentAnalysisResult = null;
let isImproving = false;
let isAnalyzing = false;
let statusPollingTimer = null;
const ACTIVE_ANALYSIS_STORAGE_KEY = "refinecv_active_analysis_id";
const LAST_COMPLETED_STORAGE_KEY = "refinecv_last_completed_analysis_id";

/* Mode State */
let selectedMode = null;
let currentJobDescription = null;

const MAX_FILE_SIZE = 5 * 1024 * 1024;
const MAX_JD_LENGTH = 8000;
const MIN_JD_LETTERS = 2;
const JD_WORD_REGEX = /[a-zA-Z0-9+#.-]+/g;

function getScrollBehavior() {
    return window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches
        ? "auto"
        : "smooth";
}

/* =========================================================
   THEME MANAGEMENT (DARK / LIGHT)
   ========================================================= */

function initTheme() {
    const savedTheme = localStorage.getItem("refinecv_theme");
    const prefersLight = window.matchMedia && window.matchMedia("(prefers-color-scheme: light)").matches;
    const theme = savedTheme || (prefersLight ? "light" : "dark");
    setTheme(theme);
}

function setTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem("refinecv_theme", theme);
    if (themeToggleBtn) {
        themeToggleBtn.setAttribute("aria-label", `Switch to ${theme === "dark" ? "light" : "dark"} theme`);
    }
}

if (themeToggleBtn) {
    themeToggleBtn.addEventListener("click", function () {
        const current = document.documentElement.getAttribute("data-theme") || "dark";
        const next = (current === "dark") ? "light" : "dark";
        setTheme(next);
    });
}

/* =========================================================
   STEPPER STATE SYNCHRONIZATION
   ========================================================= */

function updateStepper(activeStepNumber) {
    const indicators = [stepIndicator1, stepIndicator2, stepIndicator3, stepIndicator4];
    indicators.forEach((ind, index) => {
        if (!ind) return;
        const stepNum = index + 1;
        ind.classList.toggle("active", stepNum <= activeStepNumber);
    });
}

/* =========================================================
   FILE VALIDATION
   ========================================================= */

function validateFile(file) {
    if (!file) {
        return {
            valid: false,
            message: "Please select a resume."
        };
    }

    const isPdf =
        file.type === "application/pdf" ||
        file.name.toLowerCase().endsWith(".pdf");

    if (!isPdf) {
        return {
            valid: false,
            message: "Please upload a PDF resume."
        };
    }

    if (file.size > MAX_FILE_SIZE) {
        return {
            valid: false,
            message: "Resume must be smaller than 5 MB."
        };
    }

    if (file.size === 0) {
        return {
            valid: false,
            message: "The selected PDF appears to be empty."
        };
    }

    return {
        valid: true
    };
}

/* =========================================================
   JOB DESCRIPTION VALIDATION (V4.2 REVISED)
   ========================================================= */

function isValidJobDescription(text) {
    if (!text || typeof text !== "string") {
        return false;
    }
    const trimmed = text.trim();
    if (trimmed.length === 0 || trimmed.length > MAX_JD_LENGTH) {
        return false;
    }

    let letterCount = 0;
    const distinctChars = new Set();
    for (let i = 0; i < trimmed.length; i++) {
        const code = trimmed.charCodeAt(i);
        const char = trimmed[i].toLowerCase();
        if ((code >= 65 && code <= 90) || (code >= 97 && code <= 122)) {
            letterCount++;
        }
        if (!/\s/.test(char)) {
            distinctChars.add(char);
        }
    }

    /* Reject obvious symbol spam and pure number spam */
    if (letterCount < MIN_JD_LETTERS) {
        return false;
    }

    /* Reject symbol-dominated spam */
    if (trimmed.length >= 10 && (letterCount / trimmed.length) < 0.25) {
        return false;
    }

    /* Extreme character-level repetitive junk */
    if (trimmed.length >= 8 && distinctChars.size <= 2) {
        return false;
    }

    const tokens = trimmed.toLowerCase().match(JD_WORD_REGEX) || [];
    const words = [];
    const uniqueWords = new Set();

    for (const token of tokens) {
        if (/[a-zA-Z]/.test(token)) {
            words.push(token);
            uniqueWords.add(token);
        }
    }

    if (words.length === 0) {
        return false;
    }

    /* Extreme word-level repetitive junk */
    if (words.length >= 3 && uniqueWords.size === 1) {
        return false;
    }
    if (words.length >= 6 && uniqueWords.size <= 2) {
        return false;
    }
    if (words.length >= 9 && uniqueWords.size <= 3) {
        return false;
    }

    return true;
}

/* Real-time character counter */
if (jobDescriptionInput && jdCharCounter) {
    jobDescriptionInput.addEventListener("input", function () {
        const len = this.value.length;
        jdCharCounter.textContent = `${len} / ${MAX_JD_LENGTH}`;
        if (len > MAX_JD_LENGTH) {
            jdCharCounter.style.color = "var(--error)";
        } else {
            jdCharCounter.style.color = "var(--text-subtle)";
        }
    });
}

/* =========================================================
   FILE SELECTION & HANDLING
   ========================================================= */

function handleFile(file) {
    const validation = validateFile(file);

    if (!validation.valid) {
        selectedResume = null;
        if (resumeInput) resumeInput.value = "";
        if (analyzeButton) analyzeButton.disabled = true;
        if (resetButton) resetButton.disabled = true;

        if (selectedFile) {
            selectedFile.innerHTML = "";
            selectedFile.classList.remove("visible");
        }

        if (dropTitle) dropTitle.textContent = "Choose File";
        if (dropSubtitle) dropSubtitle.textContent = "or drag and drop your file here";

        showMessage(validation.message);
        updateStepper(1);
        return;
    }

    selectedResume = file;
    if (analyzeButton) analyzeButton.disabled = false;
    if (resetButton) resetButton.disabled = false;

    if (selectedFile) {
        selectedFile.innerHTML = `
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                <polyline points="14 2 14 8 20 8"></polyline>
            </svg>
            <span class="file-name">${escapeHTML(file.name)}</span>
            <span class="file-size">(${formatFileSize(file.size)})</span>
            <button type="button" class="btn-remove-file" id="removeFileBtn" title="Remove file" aria-label="Remove selected resume">&times;</button>
        `;
        selectedFile.classList.add("visible");

        const removeBtn = document.getElementById("removeFileBtn");
        if (removeBtn) {
            removeBtn.addEventListener("click", function (e) {
                e.stopPropagation();
                clearSelectedFile();
            });
        }
    }

    if (dropTitle) dropTitle.textContent = "Resume Selected";
    if (dropSubtitle) dropSubtitle.textContent = "Click to choose a different PDF";

    updateStepper(selectedMode ? 2 : 1);
    hideMessage();
}

function clearSelectedFile() {
    selectedResume = null;
    if (resumeInput) resumeInput.value = "";
    if (selectedFile) {
        selectedFile.innerHTML = "";
        selectedFile.classList.remove("visible");
    }
    if (dropTitle) dropTitle.textContent = "Choose File";
    if (dropSubtitle) dropSubtitle.textContent = "or drag and drop your file here";
    if (analyzeButton) analyzeButton.disabled = true;
    updateStepper(1);
}

function formatFileSize(bytes) {
    if (bytes < 1024) {
        return `${bytes} B`;
    }
    if (bytes < 1024 * 1024) {
        return `${(bytes / 1024).toFixed(1)} KB`;
    }
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

function openFilePicker() {
    if (resumeInput) {
        resumeInput.click();
    }
}

if (dropZone) {
    dropZone.addEventListener("click", function (event) {
        if (event.target === resumeInput || event.target.closest("#removeFileBtn")) {
            return;
        }
        openFilePicker();
    });

    dropZone.addEventListener("keydown", function (event) {
        if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            openFilePicker();
        }
    });

    dropZone.addEventListener("dragenter", function (event) {
        event.preventDefault();
        event.stopPropagation();
        dropZone.classList.add("drag-over");
    });

    dropZone.addEventListener("dragover", function (event) {
        event.preventDefault();
        event.stopPropagation();
        dropZone.classList.add("drag-over");
    });

    dropZone.addEventListener("dragleave", function (event) {
        event.preventDefault();
        event.stopPropagation();
        if (!dropZone.contains(event.relatedTarget)) {
            dropZone.classList.remove("drag-over");
        }
    });

    dropZone.addEventListener("drop", function (event) {
        event.preventDefault();
        event.stopPropagation();
        dropZone.classList.remove("drag-over");

        const files = event.dataTransfer.files;
        if (!files || files.length === 0) {
            return;
        }

        const file = files[0];
        try {
            const dataTransfer = new DataTransfer();
            dataTransfer.items.add(file);
            if (resumeInput) resumeInput.files = dataTransfer.files;
        } catch (error) {
            console.warn("Could not synchronize dropped file with input.", error);
        }
        handleFile(file);
    });
}

if (resumeInput) {
    resumeInput.addEventListener("change", function () {
        const file = this.files && this.files[0];
        handleFile(file);
    });
}

/* =========================================================
   ANALYSIS MODE SELECTION
   ========================================================= */

function selectMode(mode) {
    if (isAnalyzing && mode !== null) {
        return;
    }

    selectedMode = mode;

    if (modeCardGeneral) {
        modeCardGeneral.classList.toggle("selected", mode === "GENERAL");
        modeCardGeneral.setAttribute("aria-pressed", String(mode === "GENERAL"));
    }
    if (modeCardJob) {
        modeCardJob.classList.toggle("selected", mode === "SPECIFIC_JOB");
        modeCardJob.setAttribute("aria-pressed", String(mode === "SPECIFIC_JOB"));
    }

    if (jobDescriptionWrapper) {
        if (mode === "SPECIFIC_JOB") {
            jobDescriptionWrapper.classList.add("visible");
            if (jobDescriptionInput) {
                jobDescriptionInput.setAttribute("aria-required", "true");
                jobDescriptionInput.focus();
            }
        } else {
            jobDescriptionWrapper.classList.remove("visible");
            if (jobDescriptionInput) {
                jobDescriptionInput.setAttribute("aria-required", "false");
            }
        }
    }

    if (continueButton) {
        continueButton.disabled = (mode === null);
    }

    updateStepper(mode !== null ? 2 : 1);
}

if (modeCardGeneral) {
    modeCardGeneral.addEventListener("click", function () {
        selectMode("GENERAL");
    });
}

if (modeCardJob) {
    modeCardJob.addEventListener("click", function () {
        selectMode("SPECIFIC_JOB");
    });
}

/* Continue button reveals upload actions / confirms mode */
if (continueButton) {
    continueButton.addEventListener("click", function () {
        if (!selectedMode) {
            showMessage("Please select an analysis mode to continue.");
            return;
        }

        if (uploadSection) {
            uploadSection.hidden = false;
        }

        /* If specific job mode, check JD validity */
        if (selectedMode === "SPECIFIC_JOB") {
            const rawJd = jobDescriptionInput ? jobDescriptionInput.value : "";
            if (!rawJd || rawJd.trim().length === 0) {
                showMessage("Please enter a target job description or role context to continue.");
                if (jobDescriptionInput) jobDescriptionInput.focus();
                return;
            }
            if (!isValidJobDescription(rawJd)) {
                showMessage("Please enter a usable job description or target role context.");
                if (jobDescriptionInput) jobDescriptionInput.focus();
                return;
            }
            currentJobDescription = rawJd.trim();
        } else {
            currentJobDescription = null;
        }

        /* If file already selected, enable analyze */
        if (analyzeButton) {
            analyzeButton.disabled = !selectedResume;
        }

        hideMessage();
        updateStepper(selectedResume ? 2 : 1);
    });
}

/* =========================================================
   ANALYSIS DISPATCH & SSE
   ========================================================= */

if (analyzeButton) {
    analyzeButton.addEventListener("click", function () {
        if (!selectedResume) {
            showMessage("Please select a resume PDF first.");
            return;
        }

        if (!selectedMode) {
            showMessage("Please select an analysis mode.");
            return;
        }

        if (selectedMode === "SPECIFIC_JOB") {
            const rawJd = jobDescriptionInput ? jobDescriptionInput.value : "";
            if (!rawJd || rawJd.trim().length === 0) {
                showMessage("Please enter a target job description or role context.");
                if (jobDescriptionInput) jobDescriptionInput.focus();
                return;
            }
            if (!isValidJobDescription(rawJd)) {
                showMessage("Please enter a meaningful job description with enough detail to analyze the match.");
                if (jobDescriptionInput) jobDescriptionInput.focus();
                return;
            }
            currentJobDescription = rawJd.trim();
        } else {
            currentJobDescription = null;
        }

        startAnalysis();
    });
}

async function startAnalysis() {
    isAnalyzing = true;
    terminalEventReceived = false;
    currentAnalysisResult = null;
    currentAnalysisId = null;

    if (analyzeButton) analyzeButton.disabled = true;
    if (modeCardGeneral) modeCardGeneral.disabled = true;
    if (modeCardJob) modeCardJob.disabled = true;

    hideMessage();
    showLoading();
    updateStepper(3);

    const formData = new FormData();
    formData.append("resume", selectedResume);
    formData.append("mode", selectedMode);
    if (selectedMode === "SPECIFIC_JOB" && currentJobDescription) {
        formData.append("jobDescription", currentJobDescription);
    }

    try {
        const response = await fetch("/analyze", {
            method: "POST",
            body: formData
        });

        if (!response.ok) {
            isAnalyzing = false;
            stopStatusPolling();
            sessionStorage.removeItem(ACTIVE_ANALYSIS_STORAGE_KEY);
            let message = "Something went wrong while analyzing the resume. Please try again.";
            if (response.status === 413) {
                message = "Resume file is too large. Please upload a smaller PDF.";
            } else if (response.status === 429) {
                message = "You're making requests too quickly. Please wait a little and try again.";
            }

            hideLoading();
            if (analyzeButton) analyzeButton.disabled = false;
            if (modeCardGeneral) modeCardGeneral.disabled = false;
            if (modeCardJob) modeCardJob.disabled = false;
            showMessage(message);
            return;
        }

        const headerAnalysisId = response.headers.get("X-Analysis-Id");
        if (headerAnalysisId) {
            currentAnalysisId = headerAnalysisId.trim();
            sessionStorage.setItem(ACTIVE_ANALYSIS_STORAGE_KEY, currentAnalysisId);
        }

        if (!response.body) {
            throw new Error("Streaming response is not supported by this browser.");
        }

        await readSSEStream(response);
        if (!terminalEventReceived && currentAnalysisId) {
            console.info("SSE stream ended before terminal event; recovering for:", currentAnalysisId);
            await recoverAnalysisStatus(currentAnalysisId);
        }

    } catch (error) {
        if (!terminalEventReceived && currentAnalysisId) {
            console.info("SSE error during read; recovering for:", currentAnalysisId);
            await recoverAnalysisStatus(currentAnalysisId);
            return;
        }

        isAnalyzing = false;
        stopStatusPolling();
        sessionStorage.removeItem(ACTIVE_ANALYSIS_STORAGE_KEY);
        hideLoading();

        if (analyzeButton) analyzeButton.disabled = false;
        if (modeCardGeneral) modeCardGeneral.disabled = false;
        if (modeCardJob) modeCardJob.disabled = false;

        showMessage("Something went wrong while analyzing the resume. Please try again.");
    }
}

/* =========================================================
   SSE STREAM READER
   ========================================================= */

async function readSSEStream(response) {
    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8");
    let buffer = "";

    while (true) {
        const { value, done } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const events = buffer.split(/\r?\n\r?\n/);
        buffer = events.pop() || "";

        for (const eventBlock of events) {
            processSSEEvent(eventBlock);
        }
    }

    buffer += decoder.decode();
    if (buffer.trim()) {
        processSSEEvent(buffer);
    }
}

function processSSEEvent(block) {
    if (!block.trim()) return;

    const lines = block.split(/\r?\n/);
    let eventName = "message";
    let data = "";

    for (const line of lines) {
        if (line.startsWith("event:")) {
            eventName = line.substring(6).trim();
        } else if (line.startsWith("data:")) {
            const val = line.substring(5).trim();
            data = data ? `${data}\n${val}` : val;
        }
    }

    switch (eventName) {
        case "stage":
            handleStatus(data);
            break;
        case "analysis-id":
            currentAnalysisId = data.trim();
            sessionStorage.setItem(ACTIVE_ANALYSIS_STORAGE_KEY, currentAnalysisId);
            break;
        case "result":
            terminalEventReceived = true;
            isAnalyzing = false;
            stopStatusPolling();
            sessionStorage.removeItem(ACTIVE_ANALYSIS_STORAGE_KEY);
            handleResult(data);
            break;
        case "error":
            terminalEventReceived = true;
            isAnalyzing = false;
            stopStatusPolling();
            sessionStorage.removeItem(ACTIVE_ANALYSIS_STORAGE_KEY);
            handleServerError(data);
            break;
        default:
            console.log("RefineCV SSE event:", eventName);
    }
}

/* =========================================================
   STAGE PROGRESS HANDLER
   ========================================================= */

function handleStatus(status) {
    switch (status) {
        case "upload":
            setStepActive("step-upload");
            updateProgressPercent(20);
            break;
        case "extracting":
            setStepCompleted("step-upload");
            setStepActive("step-extract");
            updateProgressPercent(40);
            break;
        case "extracted":
            setStepCompleted("step-extract");
            updateProgressPercent(50);
            break;
        case "ocr":
            setStepCompleted("step-extract");
            setStepActive("step-ocr");
            updateProgressPercent(60);
            break;
        case "ocr-complete":
            setStepCompleted("step-ocr");
            updateProgressPercent(70);
            break;
        case "ai-analysis":
            if (!document.getElementById("step-ocr")?.classList.contains("completed")) {
                setStepSkipped("step-ocr");
            }
            setStepActive("step-ai");
            updateProgressPercent(80);
            break;
        case "recommendations":
            setStepCompleted("step-ai");
            setStepActive("step-final");
            updateProgressPercent(95);
            break;
        default:
            console.log("Unknown status:", status);
    }
}

function updateProgressPercent(pct) {
    if (loadingBarFill) {
        loadingBarFill.style.width = `${pct}%`;
    }
}

function getStep(id) {
    return document.getElementById(id);
}

function setStepActive(id) {
    const step = getStep(id);
    if (!step) return;
    step.classList.add("active");
    step.classList.remove("completed", "skipped");
}

function setStepCompleted(id) {
    const step = getStep(id);
    if (!step) return;
    step.classList.remove("active", "skipped");
    step.classList.add("completed");
}

function setStepSkipped(id) {
    const step = getStep(id);
    if (!step) return;
    step.classList.remove("active", "completed");
    step.classList.add("skipped");
}

function completeAllSteps() {
    ["step-upload", "step-extract", "step-ai", "step-final"].forEach(id => setStepCompleted(id));
    const ocrStep = getStep("step-ocr");
    if (ocrStep && !ocrStep.classList.contains("completed")) {
        setStepSkipped("step-ocr");
    }
    updateProgressPercent(100);
}

function resetAnalysisSteps() {
    ["step-upload", "step-extract", "step-ocr", "step-ai", "step-final"].forEach(id => {
        const step = getStep(id);
        if (step) step.classList.remove("active", "completed", "skipped");
    });
    updateProgressPercent(10);
}

function showLoading() {
    resetAnalysisSteps();
    setStepActive("step-upload");
    if (analysisLoading) analysisLoading.hidden = false;
    if (resultsSection) resultsSection.hidden = true;
    setTimeout(() => {
        if (analysisLoading) {
            analysisLoading.scrollIntoView({ behavior: getScrollBehavior(), block: "center" });
        }
    }, 40);
}

function hideLoading() {
    if (analysisLoading) analysisLoading.hidden = true;
}

/* =========================================================
   ANALYSIS RESULT RENDERING
   ========================================================= */

function handleResult(rawData) {
    try {
        const result = (typeof rawData === "string") ? JSON.parse(rawData) : rawData;
        currentAnalysisResult = result;
        terminalEventReceived = true;
        isAnalyzing = false;

        if (currentAnalysisId) {
            sessionStorage.setItem(LAST_COMPLETED_STORAGE_KEY, currentAnalysisId);
        }

        if (improvementContainer) improvementContainer.hidden = true;
        if (improvementContent) improvementContent.innerHTML = "";
        if (improveResumeButton) {
            improveResumeButton.hidden = false;
            improveResumeButton.style.display = "";
            improveResumeButton.disabled = false;
        }

        completeAllSteps();

        setTimeout(() => {
            hideLoading();
            renderResults(result);
            if (resultsSection) resultsSection.hidden = false;
            updateStepper(4);

            if (resultsSection) {
                resultsSection.scrollIntoView({
                    behavior: getScrollBehavior(),
                    block: "start"
                });
            }

            if (analyzeButton) analyzeButton.disabled = false;
            if (modeCardGeneral) modeCardGeneral.disabled = false;
            if (modeCardJob) modeCardJob.disabled = false;
        }, 400);

    } catch (error) {
        console.error("Result parsing failed:", error);
        hideLoading();
        if (analyzeButton) analyzeButton.disabled = false;
        showMessage("The AI returned an unexpected result. Please try again.");
    }
}

function renderResults(result) {
    if (!resultsContent) return;

    const score = Number.isFinite(Number(result.score)) ? Number(result.score) : 0;
    const summary = result.summary || "No executive summary was returned.";
    const isJobMode = (result && (result.analysisMode === "SPECIFIC_JOB" || result.mode === "SPECIFIC_JOB")) || selectedMode === "SPECIFIC_JOB";

    /* Update hero accent */
    if (resultsHeroAccent) {
        resultsHeroAccent.textContent = isJobMode ? "matched." : "refined.";
    }

    /* Update mode status badge */
    if (resultsModeBadge) {
        resultsModeBadge.textContent = isJobMode ? "Specific Job Analysis" : "General Analysis";
    }

    /* Circumference for r=36 is ~226 */
    const circumference = 226;
    const offset = Math.max(0, circumference - (circumference * score / 100));

    /* Score status text */
    const scoreStatus = getScoreStatus(score);

    /* ATS percentage or qualitative */
    const atsText = result.atsCompatibility || "Moderate";
    const atsScoreEst = parseAtsScore(atsText, score);

    /* Key takeaway synthesis */
    const keyInsight = synthesizeKeyInsight(result, isJobMode);

    /* Job match specific card (if in specific job mode) */
    let jobMatchBanner = "";
    if (isJobMode) {
        const jScore = Number.isFinite(Number(result.jobMatchScore)) ? Number(result.jobMatchScore) : score;
        const jOffset = Math.max(0, circumference - (circumference * jScore / 100));
        const roleTitle = extractRoleTitle(currentJobDescription);

        jobMatchBanner = `
            <div class="job-match-card" id="sectionJobMatch">
                <div class="job-match-top-row">
                    <div class="gauge-svg-container">
                        <svg class="gauge-svg" width="88" height="88" viewBox="0 0 88 88" aria-hidden="true">
                            <defs>
                                <linearGradient id="jobGradDial" x1="0%" y1="100%" x2="100%" y2="0%">
                                    <stop offset="0%" stop-color="#10B981"/>
                                    <stop offset="100%" stop-color="#06B6D4"/>
                                </linearGradient>
                            </defs>
                            <circle class="gauge-circle-bg" cx="44" cy="44" r="36"/>
                            <circle class="gauge-circle-val" cx="44" cy="44" r="36" stroke="url(#jobGradDial)" stroke-dasharray="226" stroke-dashoffset="${jOffset}"/>
                        </svg>
                        <div class="gauge-center-text">
                            <span class="gauge-number">${jScore}%</span>
                            <span class="gauge-max">Match</span>
                        </div>
                    </div>

                    <div>
                        <span class="card-eyebrow">TARGET ROLE FIT</span>
                        <h3 style="font-size:1.375rem; margin-bottom:4px;">${escapeHTML(roleTitle)}</h3>
                        <p style="font-size:0.875rem; color:var(--text-muted);">${escapeHTML(result.keywordAlignment || "Terminology evaluated against target job description context.")}</p>
                        ${currentJobDescription ? `<div class="job-meta-pill"><span>Target Context:</span> <strong>${escapeHTML(currentJobDescription.length > 60 ? currentJobDescription.substring(0, 57) + '...' : currentJobDescription)}</strong></div>` : ''}
                    </div>

                    <div>
                        <span class="card-eyebrow">EXPERIENCE ALIGNMENT</span>
                        <p style="font-size:0.875rem; color:var(--text-primary); margin-top:4px; line-height:1.5;">${escapeHTML(result.experienceAlignment || "Evaluated career history against role scope.")}</p>
                    </div>
                </div>
            </div>
        `;
    }

    /* Arrays of insights */
    const strongest = Array.isArray(result.strongestSkills) ? result.strongestSkills : [];
    const missing = Array.isArray(result.missingOrWeakSkills) ? result.missingOrWeakSkills : [];
    const strengths = Array.isArray(result.strengths) ? result.strengths : [];
    const weaknesses = Array.isArray(result.weaknesses) ? result.weaknesses : [];
    const suggestions = Array.isArray(result.suggestions) ? result.suggestions : [];
    const recommended = Array.isArray(result.recommendedChanges) ? result.recommendedChanges : [];

    resultsContent.innerHTML = `
        ${jobMatchBanner}

        <!-- Top Metrics Row -->
        <div class="metrics-row" id="sectionOverview">
            <!-- Score Card -->
            <div class="metric-card score-card">
                <div class="score-dial-wrap">
                    <div class="gauge-svg-container">
                        <svg class="gauge-svg" width="88" height="88" viewBox="0 0 88 88" aria-hidden="true">
                            <defs>
                                <linearGradient id="brandGradDial" x1="0%" y1="100%" x2="100%" y2="0%">
                                    <stop offset="0%" stop-color="#2563EB"/>
                                    <stop offset="50%" stop-color="#7C3AED"/>
                                    <stop offset="100%" stop-color="#06B6D4"/>
                                </linearGradient>
                            </defs>
                            <circle class="gauge-circle-bg" cx="44" cy="44" r="36"/>
                            <circle class="gauge-circle-val" cx="44" cy="44" r="36" stroke-dasharray="226" stroke-dashoffset="${offset}"/>
                        </svg>
                        <div class="gauge-center-text">
                            <span class="gauge-number">${score}</span>
                            <span class="gauge-max">/ 100</span>
                        </div>
                    </div>
                    <div class="score-info">
                        <span class="card-eyebrow">OVERALL RATING</span>
                        <h4>${escapeHTML(scoreStatus)}</h4>
                        <p>Evaluated against industry benchmarks and ATS criteria.</p>
                    </div>
                </div>
            </div>

            <!-- ATS Compatibility Card -->
            <div class="metric-card ats-card" id="sectionAts">
                <span class="card-eyebrow">ATS COMPATIBILITY</span>
                <div class="ats-val-row">
                    <span class="ats-number">${atsScoreEst}%</span>
                    <span style="font-weight:600; font-size:0.875rem;">${escapeHTML(atsText)}</span>
                </div>
                <div class="ats-bar-wrap">
                    <div class="ats-bar-fill" style="width: ${atsScoreEst}%;"></div>
                </div>
                <p class="ats-readiness-text">Structured layout readiness for automated applicant tracking systems.</p>
            </div>

            <!-- Key Insight Card -->
            <div class="metric-card card-insight">
                <div class="insight-icon-box">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <circle cx="12" cy="12" r="10"></circle>
                        <line x1="12" y1="16" x2="12" y2="12"></line>
                        <line x1="12" y1="8" x2="12.01" y2="8"></line>
                    </svg>
                </div>
                <span class="card-eyebrow" style="color:var(--warning);">KEY INSIGHT</span>
                <p>${escapeHTML(keyInsight)}</p>
            </div>
        </div>

        <!-- Executive Summary Full Width Card -->
        <div class="summary-card-full">
            <div class="summary-header">
                <span class="sparkle-ai" style="font-size:18px;">&#10022;</span>
                <h3 style="font-size:1.125rem;">Executive Summary</h3>
            </div>
            <p class="summary-text">${escapeHTML(summary)}</p>
        </div>

        <!-- 4-Quadrant Findings Grid -->
        <div class="quadrant-grid">
            <!-- Key Strengths -->
            <div class="quadrant-card" id="sectionSkills">
                <div class="quadrant-header">
                    <span class="quadrant-dot dot-green"></span>
                    <h3>Key Strengths</h3>
                </div>
                <div class="tag-cloud">
                    ${strongest.map(s => `<span class="cloud-pill pill-green">${escapeHTML(s)}</span>`).join("")}
                </div>
                <ul class="bullet-insights-list">
                    ${strengths.map(st => `<li>${escapeHTML(st)}</li>`).join("")}
                </ul>
            </div>

            <!-- Areas to Improve -->
            <div class="quadrant-card" id="sectionExperience">
                <div class="quadrant-header">
                    <span class="quadrant-dot dot-amber"></span>
                    <h3>Areas to Improve</h3>
                </div>
                <ul class="bullet-insights-list">
                    ${weaknesses.map(w => `<li>${escapeHTML(w)}</li>`).join("")}
                </ul>
            </div>

            <!-- Missing or Weak Skills -->
            <div class="quadrant-card">
                <div class="quadrant-header">
                    <span class="quadrant-dot dot-red"></span>
                    <h3>Missing or Weak Skills</h3>
                </div>
                <div class="tag-cloud">
                    ${missing.map(m => `<span class="cloud-pill pill-red">${escapeHTML(m)}</span>`).join("")}
                </div>
                <p style="font-size:0.8125rem; color:var(--text-subtle); margin-top:6px;">
                    Addressing these skill gaps substantially boosts candidate alignment and ATS parsing scores.
                </p>
            </div>

            <!-- Recommendations -->
            <div class="quadrant-card" id="sectionSuggestions">
                <div class="quadrant-header">
                    <span class="quadrant-dot dot-blue"></span>
                    <h3>Recommendations</h3>
                </div>
                <ul class="bullet-insights-list">
                    ${(recommended.length > 0 ? recommended : suggestions).map(r => `<li>${escapeHTML(r)}</li>`).join("")}
                </ul>
            </div>
        </div>
    `;

    attachSidebarListeners();
}

function getScoreStatus(score) {
    if (score >= 80) return "Excellent Candidate Match";
    if (score >= 70) return "Good Potential with Key Upsides";
    if (score >= 55) return "Competitive with Notable Gaps";
    return "Requires Targeted Polish";
}

function parseAtsScore(atsText, fallbackScore) {
    if (!atsText) return fallbackScore;
    const lower = atsText.toLowerCase();
    if (lower.includes("high") || lower.includes("excellent")) return 88;
    if (lower.includes("moderate") || lower.includes("good")) return 72;
    if (lower.includes("low") || lower.includes("poor")) return 45;
    return fallbackScore;
}

function synthesizeKeyInsight(result, isJobMode) {
    if (isJobMode && result.keywordAlignment) {
        return `Target alignment is centered around: ${result.keywordAlignment}. Bridge missing requirements to maximize interview conversion.`;
    }
    const weaknesses = Array.isArray(result.weaknesses) ? result.weaknesses : [];
    if (weaknesses.length > 0) {
        return `Your profile demonstrates genuine technical substance, but addressing ${weaknesses[0].toLowerCase()} will significantly amplify recruiter conversion.`;
    }
    return "Your experience is solid, but quantifying achievements and adding relevant skills will significantly improve your impact.";
}

function extractRoleTitle(text) {
    if (!text) return "Target Position";
    const cleaned = text.split("\n")[0].trim();
    if (cleaned.length > 40) {
        return cleaned.substring(0, 37) + "...";
    }
    return cleaned;
}

function attachSidebarListeners() {
    const tabs = document.querySelectorAll(".sidebar-tab");
    tabs.forEach(tab => {
        tab.addEventListener("click", function (e) {
            tabs.forEach(t => t.classList.remove("active"));
            this.classList.add("active");

            const targetId = this.getAttribute("data-target");
            const targetEl = document.getElementById(targetId);
            if (targetEl) {
                targetEl.scrollIntoView({ behavior: getScrollBehavior(), block: "start" });
            }
        });
    });
}

/* =========================================================
   SERVER ERROR HANDLING
   ========================================================= */

function handleServerError(message) {
    isAnalyzing = false;
    stopStatusPolling();
    sessionStorage.removeItem(ACTIVE_ANALYSIS_STORAGE_KEY);
    hideLoading();

    if (analyzeButton) analyzeButton.disabled = false;
    if (modeCardGeneral) modeCardGeneral.disabled = false;
    if (modeCardJob) modeCardJob.disabled = false;

    showMessage(message || "An unexpected error occurred. Please try again.");
}

/* =========================================================
   RESET FULL WORKSPACE FLOW
   ========================================================= */

function resetFullFlow() {
    if (isAnalyzing) return;

    if (resultsSection) resultsSection.hidden = true;
    if (resultsContent) resultsContent.innerHTML = "";
    if (improvementContainer) improvementContainer.hidden = true;
    if (improvementContent) improvementContent.innerHTML = "";
    hideLoading();

    clearSelectedFile();
    selectMode(null);

    if (jobDescriptionInput) jobDescriptionInput.value = "";
    if (jdCharCounter) jdCharCounter.textContent = `0 / ${MAX_JD_LENGTH}`;
    if (jobDescriptionWrapper) jobDescriptionWrapper.classList.remove("visible");

    if (continueButton) continueButton.disabled = true;
    if (uploadSection) uploadSection.hidden = true;
    if (analyzeButton) analyzeButton.disabled = true;

    if (improveResumeButton) {
        improveResumeButton.hidden = false;
        improveResumeButton.style.display = "";
        improveResumeButton.disabled = false;
    }
    if (resultsBottomPrompt) {
        resultsBottomPrompt.textContent = "Ready to take the next step?";
    }

    currentJobDescription = null;
    currentAnalysisResult = null;
    currentAnalysisId = null;
    sessionStorage.removeItem(ACTIVE_ANALYSIS_STORAGE_KEY);
    sessionStorage.removeItem(LAST_COMPLETED_STORAGE_KEY);

    updateStepper(1);

    const workspaceEl = document.getElementById("workspace");
    if (workspaceEl) {
        workspaceEl.scrollIntoView({ behavior: getScrollBehavior(), block: "start" });
    }
}

if (analyzeAgain) {
    analyzeAgain.addEventListener("click", resetFullFlow);
}

if (resetButton) {
    resetButton.addEventListener("click", resetFullFlow);
}

if (newAnalysisTopBtn) {
    newAnalysisTopBtn.addEventListener("click", resetFullFlow);
}

if (downloadReportBtn) {
    downloadReportBtn.addEventListener("click", function () {
        window.print();
    });
}

/* =========================================================
   V4.1 AI RESUME IMPROVEMENT
   ========================================================= */

if (improveResumeButton) {
    improveResumeButton.addEventListener("click", function () {
        if (isImproving) return;
        if (!currentAnalysisId && !currentAnalysisResult) {
            showMessage("Please analyze your resume before requesting improvements.");
            return;
        }
        requestImprovements();
    });
}

async function requestImprovements() {
    isImproving = true;
    if (improveResumeButton) improveResumeButton.disabled = true;

    if (improvementContainer) improvementContainer.hidden = false;
    if (improvementLoading) improvementLoading.hidden = false;
    if (improvementContent) improvementContent.hidden = true;

    setTimeout(() => {
        if (improvementLoading) {
            improvementLoading.scrollIntoView({ behavior: getScrollBehavior(), block: "center" });
        }
    }, 50);

    try {
        const payload = {
            analysisId: currentAnalysisId,
            analysis: currentAnalysisResult,
            jobDescription: currentJobDescription || undefined
        };

        const response = await fetch("/improve", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            const errorData = await response.json().catch(() => null);
            const errorMsg = (errorData && errorData.error)
                ? errorData.error
                : (response.status === 429
                    ? "You're making requests too quickly. Please wait a little and try again."
                    : "Failed to generate resume improvements. Please try again.");
            throw new Error(errorMsg);
        }

        const improvementData = await response.json();
        renderImprovements(improvementData);

        /* V4.2 Rule: Once improvements rendered, hide bottom improve button */
        if (improveResumeButton) {
            improveResumeButton.hidden = true;
            improveResumeButton.style.display = "none";
        }
        if (resultsBottomPrompt) {
            resultsBottomPrompt.textContent = "Ready to analyze another resume?";
        }

        if (improvementLoading) improvementLoading.hidden = true;
        if (improvementContent) improvementContent.hidden = false;

        setTimeout(() => {
            if (improvementContainer) {
                improvementContainer.scrollIntoView({ behavior: getScrollBehavior(), block: "start" });
            }
        }, 100);

    } catch (err) {
        if (improvementLoading) improvementLoading.hidden = true;
        if (improvementContainer) improvementContainer.hidden = true;
        if (improveResumeButton) {
            improveResumeButton.hidden = false;
            improveResumeButton.style.display = "";
            improveResumeButton.disabled = false;
        }
        showMessage(err.message || "Failed to generate resume improvements. Please try again.");
    } finally {
        isImproving = false;
        if (improveResumeButton && !improveResumeButton.hidden) {
            improveResumeButton.disabled = false;
        }
    }
}

function renderImprovements(data) {
    if (!improvementContent) return;

    const bullets = Array.isArray(data.bulletImprovements) ? data.bulletImprovements : [];
    const explanations = Array.isArray(data.improvementExplanations) ? data.improvementExplanations : [];
    const actions = Array.isArray(data.actionableChanges) ? data.actionableChanges : [];
    const summary = data.improvedSummary || "No improved summary generated.";

    let html = `
        <div class="improvement-header">
            <div>
                <h3>Refined Resume Enhancements</h3>
                <p class="improvement-subtitle">Action-oriented, fact-grounded rewrites derived strictly from your uploaded resume.</p>
                <p class="improvement-note">Review AI-generated changes for accuracy before incorporating them into your official resume.</p>
            </div>
            <button type="button" id="copyAllImprovementsBtn" class="copy-all-btn">
                <span>&#128203; Copy All Improvements</span>
            </button>
        </div>

        <div class="improvement-summary-card">
            <div class="summary-card-header">
                <div>
                    <span class="card-eyebrow">REFINED SUMMARY</span>
                    <h4 style="font-size:1.125rem;">Improved Professional Summary</h4>
                </div>
                <button type="button" class="copy-snippet-btn" data-copy="${escapeHTML(summary)}">
                    <span>&#128203; Copy Summary</span>
                </button>
            </div>
            <p class="improved-summary-text">${escapeHTML(summary)}</p>
        </div>

        <div class="improvement-bullets-container">
            <div style="margin-bottom:16px;">
                <span class="card-eyebrow">EXPERIENCE & PROJECTS</span>
                <h4 style="font-size:1.25rem;">Bullet Point Makeovers</h4>
                <p style="font-size:0.875rem; color:var(--text-muted);">Comparing original bullets against high-impact, metric-focused active phrasing.</p>
            </div>

            <div class="bullet-cards-grid">
    `;

    bullets.forEach(bullet => {
        html += `
            <div class="bullet-card">
                <div class="bullet-card-header">
                    <span class="bullet-section-tag">${escapeHTML(bullet.section || "Experience")}</span>
                    <div style="display:flex; gap:8px;">
                        <button type="button" class="btn-use-suggestion copy-snippet-btn" data-copy="${escapeHTML(bullet.improved)}">
                            <span>Use Suggestion</span>
                        </button>
                        <button type="button" class="copy-snippet-btn" data-copy="${escapeHTML(bullet.improved)}">
                            <span>&#128203; Copy</span>
                        </button>
                    </div>
                </div>

                <div class="bullet-compare-row">
                    <div class="bullet-box original-box">
                        <span class="box-tag">ORIGINAL</span>
                        <p>${escapeHTML(bullet.original)}</p>
                    </div>

                    <div class="bullet-box improved-box">
                        <span class="box-tag">REFINED</span>
                        <p>${escapeHTML(bullet.improved)}</p>
                    </div>
                </div>

                <div class="bullet-explanation-callout">
                    <span class="sparkle-ai" style="font-size:14px;">&#10022;</span>
                    <span><strong>Why this works:</strong> ${escapeHTML(bullet.explanation)}</span>
                </div>
            </div>
        `;
    });

    html += `
            </div>
        </div>

        <div class="improvement-bottom-grid">
            <div class="improvement-info-card">
                <span class="card-eyebrow">STRATEGIC ENHANCEMENTS</span>
                <h4>Key Improvements Applied</h4>
                <ul class="improvement-check-list">
                    ${explanations.map(exp => `<li>&#10003; ${escapeHTML(exp)}</li>`).join("")}
                </ul>
            </div>

            <div class="improvement-info-card">
                <span class="card-eyebrow">ACTIONABLE NEXT STEPS</span>
                <h4>Recommended Next Steps</h4>
                <ul class="improvement-action-list">
                    ${actions.map(act => `<li>&#8594; ${escapeHTML(act)}</li>`).join("")}
                </ul>
            </div>
        </div>
    `;

    improvementContent.innerHTML = html;
    attachCopyListeners(data);
}

function attachCopyListeners(data) {
    const copyButtons = improvementContent.querySelectorAll(".copy-snippet-btn");
    copyButtons.forEach(btn => {
        btn.addEventListener("click", function () {
            const textToCopy = btn.getAttribute("data-copy");
            if (textToCopy) {
                copyTextToClipboard(textToCopy, btn);
            }
        });
    });

    const copyAllBtn = document.getElementById("copyAllImprovementsBtn");
    if (copyAllBtn) {
        copyAllBtn.addEventListener("click", function () {
            const allText = buildAllImprovementsText(data);
            copyTextToClipboard(allText, copyAllBtn);
        });
    }
}

function buildAllImprovementsText(data) {
    let output = "";
    if (data.improvedSummary) {
        output += "=== IMPROVED PROFESSIONAL SUMMARY ===\n" + data.improvedSummary + "\n\n";
    }

    if (Array.isArray(data.bulletImprovements) && data.bulletImprovements.length > 0) {
        output += "=== IMPROVED BULLET POINTS ===\n";
        data.bulletImprovements.forEach((b, idx) => {
            output += `${idx + 1}. [${b.section || "Experience"}]\n`;
            output += `Original: ${b.original}\n`;
            output += `Refined:  ${b.improved}\n`;
            output += `Rationale: ${b.explanation}\n\n`;
        });
    }

    if (Array.isArray(data.actionableChanges) && data.actionableChanges.length > 0) {
        output += "=== RECOMMENDED ACTIONS ===\n";
        data.actionableChanges.forEach(a => {
            output += `• ${a}\n`;
        });
    }

    return output.trim();
}

function copyTextToClipboard(text, btnElement) {
    if (!navigator.clipboard) {
        const textarea = document.createElement("textarea");
        textarea.value = text;
        textarea.style.position = "fixed";
        textarea.style.opacity = "0";
        document.body.appendChild(textarea);
        textarea.focus();
        textarea.select();
        try {
            document.execCommand("copy");
            showCopySuccess(btnElement);
        } catch (e) {
            showMessage("Could not copy to clipboard. Please copy manually.");
        }
        document.body.removeChild(textarea);
        return;
    }

    navigator.clipboard.writeText(text).then(() => {
        showCopySuccess(btnElement);
    }).catch(() => {
        showMessage("Could not copy to clipboard. Please copy manually.");
    });
}

function showCopySuccess(btnElement) {
    const originalText = btnElement.textContent;
    btnElement.textContent = "\u2713 Copied!";
    btnElement.classList.add("copied");
    setTimeout(() => {
        btnElement.textContent = originalText;
        btnElement.classList.remove("copied");
    }, 2000);
}

/* =========================================================
   INTERACTIVE LIVE EXAMPLE DEMO
   ========================================================= */

function loadDemoExample() {
    const demoData = {
        score: 78,
        summary: "Candidate demonstrates a solid foundation in computer science and modern backend application development. Technical depth in Java, Spring Boot, REST microservices, and relational schema optimization is evident. Enhancing quantifiable business outcomes and cloud deployment exposure will maximize interviewer impact.",
        strongestSkills: ["Java", "Spring Boot", "REST APIs", "SQL Query Optimization", "Problem Solving", "Git"],
        missingOrWeakSkills: ["Cloud Infrastructure (AWS/GCP)", "Docker & Kubernetes", "CI/CD Pipeline Automation", "Unit Test Coverage Metrics"],
        strengths: [
            "Demonstrated experience architecting maintainable backend services with Java and Spring Boot.",
            "Strong understanding of database schema design and relational integrity.",
            "Clear technical communication and disciplined version control workflow."
        ],
        weaknesses: [
            "Limited quantified metrics demonstrating measurable business value or throughput improvements.",
            "Lacks hands-on containerization and automated cloud delivery experience.",
            "Bullet phrasing relies on passive responsibilities rather than achievement-driven verbs."
        ],
        atsCompatibility: "Likely to pass ATS systems with minor formatting optimizations.",
        suggestions: [
            "Quantify key accomplishments with percentages or load figures (e.g., 'reduced API response times by 35%').",
            "Incorporate containerization keywords (Docker, Kubernetes) to meet modern backend ATS filters.",
            "Restructure bullet points around the Google XYZ formula: Accomplished [X], as measured by [Y], by doing [Z]."
        ],
        recommendedChanges: [
            "Add a dedicated Core Technologies section prominently near the top.",
            "Consolidate education entries to give priority to technical projects and impact.",
            "Replace generic job duty statements with action verbs such as Engineered, Optimized, Delivered."
        ],
        analysisMode: "GENERAL",
        jobMatchScore: 78,
        keywordAlignment: "Strong core terminology alignment with backend software engineering standards.",
        experienceAlignment: "Well-aligned academic and project experience for Junior to Mid-level engineering roles."
    };

    renderResults(demoData);
    if (resultsSection) resultsSection.hidden = false;
    updateStepper(4);

    if (resultsSection) {
        resultsSection.scrollIntoView({ behavior: getScrollBehavior(), block: "start" });
    }
}

if (heroExampleBtn) {
    heroExampleBtn.addEventListener("click", loadDemoExample);
}
if (navExampleLink) {
    navExampleLink.addEventListener("click", function (e) {
        e.preventDefault();
        loadDemoExample();
    });
}

/* =========================================================
   STATUS RECOVERY & POLLING (V4.1 SESSION RECOVERY)
   ========================================================= */

async function recoverAnalysisStatus(analysisId) {
    if (!analysisId) return;

    try {
        const response = await fetch(`/analysis/${encodeURIComponent(analysisId)}/status`);
        if (!response.ok) {
            if (response.status === 404) {
                stopStatusPolling();
                isAnalyzing = false;
                sessionStorage.removeItem(ACTIVE_ANALYSIS_STORAGE_KEY);
                hideLoading();
                if (analyzeButton) analyzeButton.disabled = false;
                showMessage("Analysis session expired or not found. Please analyze again.");
            }
            return;
        }

        const data = await response.json();
        const status = data.status;

        if (status === "COMPLETED") {
            stopStatusPolling();
            isAnalyzing = false;
            sessionStorage.removeItem(ACTIVE_ANALYSIS_STORAGE_KEY);
            terminalEventReceived = true;
            currentAnalysisId = data.analysisId;
            handleResult(data.result);
        } else if (status === "PROCESSING") {
            isAnalyzing = true;
            showLoading();
            if (data.stage) {
                handleStatus(data.stage);
            }
            startStatusPolling(analysisId);
        } else if (status === "FAILED") {
            stopStatusPolling();
            isAnalyzing = false;
            sessionStorage.removeItem(ACTIVE_ANALYSIS_STORAGE_KEY);
            terminalEventReceived = true;
            hideLoading();
            if (analyzeButton) analyzeButton.disabled = false;
            handleServerError(data.error || "We couldn't complete the AI analysis. Please try again.");
        } else if (status === "CANCELLED") {
            stopStatusPolling();
            isAnalyzing = false;
            sessionStorage.removeItem(ACTIVE_ANALYSIS_STORAGE_KEY);
            terminalEventReceived = true;
            hideLoading();
            if (analyzeButton) analyzeButton.disabled = false;
            showMessage("The analysis was cancelled. Please try again.");
        }
    } catch (err) {
        console.warn("Status recovery check failed:", err);
    }
}

function startStatusPolling(analysisId) {
    if (statusPollingTimer) return;
    statusPollingTimer = setInterval(() => {
        recoverAnalysisStatus(analysisId);
    }, 2000);
}

function stopStatusPolling() {
    if (statusPollingTimer) {
        clearInterval(statusPollingTimer);
        statusPollingTimer = null;
    }
}

/* Visibility Change listener for background tab recovery */
document.addEventListener("visibilitychange", function () {
    if (document.visibilityState === "visible") {
        if (isImproving) return;
        const activeId = currentAnalysisId || sessionStorage.getItem(ACTIVE_ANALYSIS_STORAGE_KEY);
        if (activeId && isAnalyzing) {
            recoverAnalysisStatus(activeId);
        }
    }
});

/* =========================================================
   MESSAGING / TOAST HELPER
   ========================================================= */

function showMessage(msg) {
    if (!messageBox) return;
    messageBox.textContent = msg;
    messageBox.hidden = false;

    clearTimeout(showMessage.timeout);
    showMessage.timeout = setTimeout(() => {
        hideMessage();
    }, 4500);
}

function hideMessage() {
    if (messageBox) {
        messageBox.hidden = true;
    }
}

function escapeHTML(str) {
    if (str == null) return "";
    return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

/* =========================================================
   INITIALIZATION
   ========================================================= */

window.addEventListener("DOMContentLoaded", function () {
    initTheme();

    const savedAnalysisId = sessionStorage.getItem(ACTIVE_ANALYSIS_STORAGE_KEY)
        || sessionStorage.getItem(LAST_COMPLETED_STORAGE_KEY);

    if (savedAnalysisId) {
        currentAnalysisId = savedAnalysisId;
        recoverAnalysisStatus(savedAnalysisId);
    }

    if (analyzeButton) analyzeButton.disabled = true;
    updateStepper(1);

    console.log("RefineCV V4.2 SaaS Frontend loaded successfully.");
});
