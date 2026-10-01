/* =========================================================
   REFINECV â€” V2 FRONTEND
   ========================================================= */


/* =========================================================
   ELEMENTS
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

/* V4.2 mode selection */
const modeCardGeneral = document.getElementById("modeCardGeneral");
const modeCardJob = document.getElementById("modeCardJob");
const jobDescriptionWrapper = document.getElementById("jobDescriptionWrapper");
const jobDescriptionInput = document.getElementById("jobDescriptionInput");


/* =========================================================
   STATE
   ========================================================= */

let selectedResume = null;
let terminalEventReceived = false;
let currentAnalysisId = null;
let currentAnalysisResult = null;
let isImproving = false;
let isAnalyzing = false;
let statusPollingTimer = null;
const ACTIVE_ANALYSIS_STORAGE_KEY = "refinecv_active_analysis_id";

/* V4.2 mode state */
let selectedMode = "GENERAL";
let currentJobDescription = null;

const MAX_FILE_SIZE = 5 * 1024 * 1024;

function getScrollBehavior() {
    return window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches
        ? "auto"
        : "smooth";
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
   SELECT FILE
   ========================================================= */

function handleFile(file) {

    const validation = validateFile(file);

    if (!validation.valid) {

        selectedResume = null;

        resumeInput.value = "";

        analyzeButton.disabled = true;

        selectedFile.textContent = "";
        selectedFile.classList.remove("visible");

        dropTitle.textContent =
            "Drop your resume here";

        dropSubtitle.textContent =
            "or click anywhere here to browse";

        showMessage(validation.message);

        return;
    }


    selectedResume = file;

    analyzeButton.disabled = false;


    selectedFile.textContent =
        `âœ“  ${file.name}  Â·  ${formatFileSize(file.size)}`;

    selectedFile.classList.add("visible");


    dropTitle.textContent =
        "Resume selected";

    dropSubtitle.textContent =
        "Click here to choose a different PDF";


    hideMessage();
}


/* =========================================================
   FORMAT FILE SIZE
   ========================================================= */

function formatFileSize(bytes) {

    if (bytes < 1024) {
        return `${bytes} B`;
    }

    if (bytes < 1024 * 1024) {
        return `${(bytes / 1024).toFixed(1)} KB`;
    }

    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}


/* =========================================================
   OPEN FILE PICKER
   ========================================================= */

function openFilePicker() {

    if (!resumeInput) {
        return;
    }

    resumeInput.click();
}


/* =========================================================
   DROP ZONE CLICK
   ========================================================= */

dropZone.addEventListener("click", function (event) {

    if (event.target === resumeInput) {
        return;
    }

    openFilePicker();
});


/* =========================================================
   KEYBOARD ACCESS
   ========================================================= */

dropZone.addEventListener("keydown", function (event) {

    if (
        event.key === "Enter" ||
        event.key === " "
    ) {

        event.preventDefault();

        openFilePicker();
    }

});


/* =========================================================
   FILE INPUT CHANGE
   ========================================================= */

resumeInput.addEventListener("change", function () {

    const file =
        this.files && this.files[0];

    handleFile(file);
});


/* =========================================================
   DRAG ENTER
   ========================================================= */

dropZone.addEventListener("dragenter", function (event) {

    event.preventDefault();
    event.stopPropagation();

    dropZone.classList.add("drag-over");
});


/* =========================================================
   DRAG OVER
   ========================================================= */

dropZone.addEventListener("dragover", function (event) {

    event.preventDefault();
    event.stopPropagation();

    dropZone.classList.add("drag-over");
});


/* =========================================================
   DRAG LEAVE
   ========================================================= */

dropZone.addEventListener("dragleave", function (event) {

    event.preventDefault();
    event.stopPropagation();

    if (!dropZone.contains(event.relatedTarget)) {
        dropZone.classList.remove("drag-over");
    }
});


/* =========================================================
   DROP
   ========================================================= */

dropZone.addEventListener("drop", function (event) {

    event.preventDefault();
    event.stopPropagation();

    dropZone.classList.remove("drag-over");


    const files =
        event.dataTransfer.files;


    if (!files || files.length === 0) {
        return;
    }


    const file = files[0];


    try {

        const dataTransfer =
            new DataTransfer();

        dataTransfer.items.add(file);

        resumeInput.files =
            dataTransfer.files;

    } catch (error) {

        console.warn(
            "Could not synchronize dropped file with input.",
            error
        );
    }


    handleFile(file);
});


/* =========================================================
   V4.2 MODE SELECTION
   ========================================================= */

function selectMode(mode) {
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
            }
        } else {
            jobDescriptionWrapper.classList.remove("visible");
            if (jobDescriptionInput) {
                jobDescriptionInput.setAttribute("aria-required", "false");
            }
        }
    }
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


/* =========================================================
   ANALYZE BUTTON
   ========================================================= */

analyzeButton.addEventListener(
    "click",
    function () {

        if (!selectedResume) {

            showMessage(
                "Please select a PDF resume first."
            );

            return;
        }

        if (selectedMode === "SPECIFIC_JOB") {
            const jd = jobDescriptionInput ? jobDescriptionInput.value.trim() : "";
            if (!jd) {
                showMessage("Please paste a job description before analyzing in Specific Job mode.");
                if (jobDescriptionInput) {
                    jobDescriptionInput.focus();
                }
                return;
            }
        }

        startAnalysis(selectedResume);
    }
);


/* =========================================================
   START ANALYSIS
   ========================================================= */

async function startAnalysis(file) {

    if (isAnalyzing) {
        console.warn("Analysis is already in progress. Ignoring duplicate request.");
        return;
    }
    isAnalyzing = true;
    stopStatusPolling();

    hideMessage();

    terminalEventReceived = false;

    analyzeButton.disabled = true;

    showLoading();

    resetAnalysisSteps();

    setStepActive("step-upload");


    /*
       IMPORTANT FIX:
       Scroll directly to the loading section.

       Previously this used window.scrollTo({ top: 0 }),
       which kept the user at the top of the website.
    */

    setTimeout(function () {

        analysisLoading.scrollIntoView({
            behavior: getScrollBehavior(),
            block: "start"
        });

    }, 50);


    try {

        const formData = new FormData();

        formData.append(
            "resume",
            file
        );

        /* V4.2: include analysis mode and optional job description */
        formData.append("mode", selectedMode);
        if (selectedMode === "SPECIFIC_JOB" && jobDescriptionInput) {
            const jd = jobDescriptionInput.value.trim();
            if (jd) {
                currentJobDescription = jd;
                formData.append("jobDescription", jd);
            } else {
                currentJobDescription = null;
            }
        } else {
            currentJobDescription = null;
        }


        const response =
            await fetch(
                "/analyze",
                {
                    method: "POST",
                    body: formData
                }
            );


        if (!response.ok) {
            isAnalyzing = false;
            stopStatusPolling();
            sessionStorage.removeItem(ACTIVE_ANALYSIS_STORAGE_KEY);
            const message = response.status === 413
                ? "Resume file is too large. Please upload a smaller PDF."
                : "Something went wrong while analyzing the resume. Please try again.";

            hideLoading();
            analyzeButton.disabled = false;
            showMessage(message);
            return;
        }

        const headerAnalysisId = response.headers.get("X-Analysis-Id");
        if (headerAnalysisId) {
            currentAnalysisId = headerAnalysisId.trim();
            sessionStorage.setItem(ACTIVE_ANALYSIS_STORAGE_KEY, currentAnalysisId);
        }


        if (!response.body) {

            throw new Error(
                "Streaming response is not supported by this browser."
            );
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

        analyzeButton.disabled = false;


        showMessage(
            "Something went wrong while analyzing the resume. Please try again."
        );
    }
}


/* =========================================================
   SSE STREAM READER
   ========================================================= */

async function readSSEStream(response) {

    const reader =
        response.body.getReader();

    const decoder =
        new TextDecoder("utf-8");

    let buffer = "";


    while (true) {

        const {
            value,
            done
        } = await reader.read();


        if (done) {
            break;
        }


        buffer += decoder.decode(
            value,
            {
                stream: true
            }
        );


        const events =
            buffer.split(/\r?\n\r?\n/);


        buffer =
            events.pop() || "";


        for (const eventBlock of events) {

            processSSEEvent(eventBlock);
        }
    }


    buffer += decoder.decode();

    if (buffer.trim()) {

        processSSEEvent(buffer);
    }

    if (!terminalEventReceived) {
        return;
    }
}


/* =========================================================
   PROCESS SSE EVENT
   ========================================================= */

function processSSEEvent(eventBlock) {

    if (!eventBlock.trim()) {
        return;
    }


    const lines =
        eventBlock.split(/\r?\n/);


    let eventName = "message";

    const dataLines = [];


    for (const line of lines) {

        if (line.startsWith("event:")) {

            eventName =
                line.substring(6).trim();

        } else if (line.startsWith("data:")) {

            dataLines.push(
                line.substring(5).trim()
            );
        }
    }


    const data =
        dataLines.join("\n");


    if (!data) {
        return;
    }


    switch (eventName) {

        case "status":

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

            console.log(
                "RefineCV SSE event:",
                eventName
            );
    }
}


/* =========================================================
   STATUS EVENTS
   ========================================================= */

function handleStatus(status) {

    switch (status) {

        case "upload":

            setStepActive("step-upload");

            break;


        case "extracting":

            setStepCompleted("step-upload");

            setStepActive("step-extract");

            break;


        case "extracted":

            setStepCompleted("step-extract");

            break;


        case "ocr":

            setStepCompleted("step-extract");

            setStepActive("step-ocr");

            break;


        case "ocr-complete":

            setStepCompleted("step-ocr");

            break;


        case "ai-analysis":

            if (
                !document
                    .getElementById("step-ocr")
                    .classList.contains("completed")
            ) {

                setStepSkipped("step-ocr");
            }


            setStepActive("step-ai");

            break;


        case "recommendations":

            setStepCompleted("step-ai");

            setStepActive("step-final");

            break;


        default:

            console.log("Unknown RefineCV status:", status);
    }
}


/* =========================================================
   RESULT
   ========================================================= */

function handleResult(rawData) {

    try {

        const result =
            (typeof rawData === "string") ? JSON.parse(rawData) : rawData;

        currentAnalysisResult = result;
        terminalEventReceived = true;
        isAnalyzing = false;
        if (currentAnalysisId) {
            sessionStorage.setItem("refinecv_last_completed_analysis_id", currentAnalysisId);
        }
        if (improvementContainer) {
            improvementContainer.hidden = true;
        }
        if (improvementContent) {
            improvementContent.innerHTML = "";
        }
        if (improveResumeButton) {
            improveResumeButton.disabled = false;
        }


        completeAllSteps();


        setTimeout(function () {

            hideLoading();

            renderResults(result);

            resultsSection.hidden = false;


            resultsSection.scrollIntoView({
                behavior: getScrollBehavior(),
                block: "start"
            });


            analyzeButton.disabled = false;

        }, 450);


    } catch (error) {

        hideLoading();

        analyzeButton.disabled = false;


        showMessage(
            "The AI returned an unexpected result. Please try again."
        );
    }
}


/* =========================================================
   SERVER ERROR
   ========================================================= */

function handleServerError(message) {

    hideLoading();

    analyzeButton.disabled = false;


    showMessage(
        message || "Resume analysis failed. Please check your resume and try again."
    );
}


/* =========================================================
   LOADING
   ========================================================= */

function showLoading() {

    resultsSection.hidden = true;

    analysisLoading.hidden = false;
}


function hideLoading() {

    analysisLoading.hidden = true;
}


function resetAnalysisSteps() {

    const steps =
        document.querySelectorAll(
            ".analysis-step"
        );


    steps.forEach(function (step) {

        step.classList.remove(
            "active",
            "completed",
            "skipped"
        );
    });
}


/* =========================================================
   STEP HELPERS
   ========================================================= */

function getStep(id) {

    return document.getElementById(id);
}


function setStepActive(id) {

    const step =
        getStep(id);

    if (!step) {
        return;
    }


    step.classList.add("active");

    step.classList.remove(
        "completed",
        "skipped"
    );
}


function setStepCompleted(id) {

    const step =
        getStep(id);

    if (!step) {
        return;
    }


    step.classList.remove(
        "active",
        "skipped"
    );

    step.classList.add("completed");
}


function setStepSkipped(id) {

    const step =
        getStep(id);

    if (!step) {
        return;
    }


    step.classList.remove(
        "active",
        "completed"
    );

    step.classList.add("skipped");
}


function completeAllSteps() {

    const stepIds = [
        "step-upload",
        "step-extract",
        "step-ai",
        "step-final"
    ];


    stepIds.forEach(function (id) {

        setStepCompleted(id);

    });


    const ocrStep =
        getStep("step-ocr");


    if (
        ocrStep &&
        !ocrStep.classList.contains("completed")
    ) {

        setStepSkipped("step-ocr");
    }
}


/* =========================================================
   RESULTS RENDERING
   ========================================================= */

function renderResults(result) {

    const score =
        Number.isFinite(Number(result.score))
            ? Number(result.score)
            : 0;


    const summary =
        result.summary ||
        "No summary was returned.";

    /* V4.2: job match panel shown only when mode is SPECIFIC_JOB */
    const isJobMode = result.analysisMode === "SPECIFIC_JOB";
    const jobMatchScore = Number.isFinite(Number(result.jobMatchScore))
        ? Number(result.jobMatchScore)
        : null;

    const jobMatchPanel = isJobMode ? `

        <div class="score-card" style="margin-top: 20px;">

            <div>
                <div class="result-label">
                    JOB MATCH
                </div>

                <div class="score-circle" style="width:90px;height:90px;">
                    <div class="score-inner">
                        <div class="score-number" style="font-size:28px;">
                            ${jobMatchScore !== null ? jobMatchScore : "—"}
                        </div>
                        <div class="score-max">
                            job fit
                        </div>
                    </div>
                </div>
            </div>

            <div style="flex:1;">
                <div class="result-label">
                    KEYWORD ALIGNMENT
                </div>
                <p class="result-summary" style="margin-top:6px;">
                    ${escapeHTML(result.keywordAlignment || "Not evaluated.")}
                </p>
                <div class="result-label" style="margin-top:14px;">
                    EXPERIENCE ALIGNMENT
                </div>
                <p class="result-summary" style="margin-top:6px;">
                    ${escapeHTML(result.experienceAlignment || "Not evaluated.")}
                </p>
            </div>

        </div>

    ` : "";


    resultsContent.innerHTML = `

        <div class="score-card">

            <div>

                <div class="result-label">
                    OVERALL RESUME SCORE
                </div>

                <div class="score-circle">

                    <div class="score-inner">

                        <div class="score-number">
                            ${score}
                        </div>

                        <div class="score-max">
                            out of 100
                        </div>

                        <div class="score-status">
                            ${escapeHTML(
                                getScoreStatus(score)
                            )}
                        </div>

                    </div>

                </div>

            </div>


            <div>

                <div class="result-label">
                    EXECUTIVE SUMMARY
                </div>

                <p class="result-summary">
                    ${escapeHTML(summary)}
                </p>

            </div>

        </div>

        ${jobMatchPanel}

        <div class="results-grid">

            ${createResultCard(
                "Strongest Skills",
                result.strongestSkills,
                "Your strongest areas based on the resume."
            )}


            ${createResultCard(
                "Missing or Weak Skills",
                result.missingOrWeakSkills,
                "Skills or areas that may need improvement."
            )}


            ${createResultCard(
                "Strengths",
                result.strengths,
                "What your resume is already doing well."
            )}


            ${createResultCard(
                "Weaknesses",
                result.weaknesses,
                "Areas that could make your resume stronger."
            )}


            ${createTextResultCard(
                "ATS Compatibility",
                result.atsCompatibility
            )}


            ${createResultCard(
                "Suggestions",
                result.suggestions,
                "Practical improvements you can make."
            )}


            ${createResultCard(
                "Recommended Changes",
                result.recommendedChanges,
                "Specific changes worth prioritizing."
            )}

        </div>

    `;
}


/* =========================================================
   RESULT CARD
   ========================================================= */

function createResultCard(
    title,
    items,
    description
) {

    const safeItems =
        Array.isArray(items)
            ? items
            : [];


    return `

        <article class="result-card">

            <div class="result-label">
                REFINECV INSIGHT
            </div>

            <h3>
                ${escapeHTML(title)}
            </h3>

            <p>
                ${escapeHTML(description)}
            </p>

            ${
                safeItems.length
                    ? `
                        <ul class="result-list">

                            ${safeItems
                                .map(
                                    item =>
                                        `<li>${escapeHTML(
                                            String(item)
                                        )}</li>`
                                )
                                .join("")
                            }

                        </ul>
                    `
                    : `
                        <p>
                            Not mentioned.
                        </p>
                    `
            }

        </article>

    `;
}


/* =========================================================
   TEXT RESULT CARD
   ========================================================= */

function createTextResultCard(
    title,
    value
) {

    return `

        <article class="result-card">

            <div class="result-label">
                REFINECV INSIGHT
            </div>

            <h3>
                ${escapeHTML(title)}
            </h3>

            <p>
                ${escapeHTML(
                    value || "Not mentioned."
                )}
            </p>

        </article>

    `;
}


/* =========================================================
   SCORE STATUS
   ========================================================= */

function getScoreStatus(score) {

    if (score >= 85) {
        return "Strong resume";
    }

    if (score >= 70) {
        return "Good foundation";
    }

    if (score >= 55) {
        return "Room to improve";
    }

    return "Needs refinement";
}


/* =========================================================
   HTML ESCAPE
   ========================================================= */

function escapeHTML(value) {

    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}


/* =========================================================
   ANALYZE ANOTHER RESUME
   ========================================================= */

analyzeAgain.addEventListener(
    "click",
    function () {

        /*
           1. Hide old results
        */

        resultsSection.hidden = true;
        currentAnalysisId = null;
        currentAnalysisResult = null;
        isImproving = false;
        currentJobDescription = null;
        /* V4.2 reset mode to General */
        selectMode("GENERAL");
        if (jobDescriptionInput) {
            jobDescriptionInput.value = "";
        }
        if (improvementContainer) {
            improvementContainer.hidden = true;
        }
        if (improvementContent) {
            improvementContent.innerHTML = "";
        }


        /*
           2. Hide loading screen just in case
        */

        analysisLoading.hidden = true;


        /*
           3. Completely remove old selected file
        */

        selectedResume = null;

        resumeInput.value = "";

        selectedFile.textContent = "";

        selectedFile.classList.remove(
            "visible"
        );


        /*
           4. Reset upload area text
        */

        dropTitle.textContent =
            "Drop your resume here";

        dropSubtitle.textContent =
            "or click anywhere here to browse";


        /*
           5. Disable Analyze until
              a new PDF is selected
        */

        analyzeButton.disabled = true;


        /*
           6. Reset all analysis steps
        */

        resetAnalysisSteps();


        /*
           7. Clear previous result HTML
        */

        resultsContent.innerHTML = "";


        /*
           8. Scroll back to the upload card.
              The upload card is inside the hero section.
        */

        const uploadCard =
            document.querySelector(".upload-card");


        if (uploadCard) {

            uploadCard.scrollIntoView({
                behavior: getScrollBehavior(),
                block: "center"
            });

        } else {

            window.scrollTo({
                top: 0,
                behavior: getScrollBehavior()
            });
        }

    }
);


/* =========================================================
   MESSAGE
   ========================================================= */

function showMessage(message) {

    messageBox.textContent =
        message;

    messageBox.hidden = false;


    clearTimeout(
        showMessage.timeout
    );


    showMessage.timeout =
        setTimeout(
            function () {

                hideMessage();

            },
            4500
        );
}


function hideMessage() {

    messageBox.hidden = true;
}


/* =========================================================
   V4.1 AI RESUME IMPROVEMENT
   ========================================================= */

if (improveResumeButton) {
    improveResumeButton.addEventListener("click", function () {
        if (isImproving) {
            return;
        }

        if (!currentAnalysisId && !currentAnalysisResult) {
            showMessage("Please analyze your resume before requesting improvements.");
            return;
        }

        requestImprovements();
    });
}

async function requestImprovements() {
    isImproving = true;
    if (improveResumeButton) {
        improveResumeButton.disabled = true;
    }

    if (improvementContainer) {
        improvementContainer.hidden = false;
    }
    if (improvementLoading) {
        improvementLoading.hidden = false;
    }
    if (improvementContent) {
        improvementContent.hidden = true;
    }

    setTimeout(function () {
        if (improvementLoading) {
            improvementLoading.scrollIntoView({
                behavior: getScrollBehavior(),
                block: "center"
            });
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

        if (improvementLoading) {
            improvementLoading.hidden = true;
        }
        if (improvementContent) {
            improvementContent.hidden = false;
        }

        setTimeout(function () {
            if (improvementContainer) {
                improvementContainer.scrollIntoView({
                    behavior: getScrollBehavior(),
                    block: "start"
                });
            }
        }, 100);

    } catch (err) {
        if (improvementLoading) {
            improvementLoading.hidden = true;
        }
        if (improvementContainer) {
            improvementContainer.hidden = true;
        }
        showMessage(err.message || "Failed to generate resume improvements. Please try again.");
    } finally {
        isImproving = false;
        if (improveResumeButton) {
            improveResumeButton.disabled = false;
        }
    }
}

function renderImprovements(data) {
    if (!improvementContent) {
        return;
    }

    const bullets = Array.isArray(data.bulletImprovements) ? data.bulletImprovements : [];
    const explanations = Array.isArray(data.improvementExplanations) ? data.improvementExplanations : [];
    const actions = Array.isArray(data.actionableChanges) ? data.actionableChanges : [];
    const summary = data.improvedSummary || "No improved summary generated.";

    let html = `
        <div class="improvement-header">
            <div>
                <h3>
                    Refined Resume Enhancements
                </h3>
                <p class="improvement-subtitle">
                    Action-oriented, fact-grounded rewrites derived strictly from your uploaded resume.
                </p>
                <p class="improvement-note">
                    Review AI-generated changes for accuracy before using them in your resume.
                </p>
            </div>
            <button type="button" id="copyAllImprovementsBtn" class="copy-all-btn">
                ðŸ“‹ Copy All Improvements
            </button>
        </div>

        <div class="improvement-summary-card">
            <div class="summary-card-header">
                <div>
                    <span class="card-eyebrow">REFINED SUMMARY</span>
                    <h4>Improved Professional Summary</h4>
                </div>
                <button type="button" class="copy-snippet-btn" data-copy="${escapeHTML(summary)}">
                    ðŸ“‹ Copy Summary
                </button>
            </div>
            <p class="improved-summary-text">${escapeHTML(summary)}</p>
        </div>

        <div class="improvement-bullets-container">
            <div class="section-title-wrap">
                <span class="card-eyebrow">EXPERIENCE & PROJECTS</span>
                <h4>Bullet Point Makeovers</h4>
                <p class="section-desc">Comparing your original resume bullets with high-impact active phrasing.</p>
            </div>

            <div class="bullet-cards-grid">
    `;

    bullets.forEach(function (bullet) {
        html += `
            <div class="bullet-card">
                <div class="bullet-card-header">
                    <span class="bullet-section-tag">${escapeHTML(bullet.section || "Experience")}</span>
                    <button type="button" class="copy-snippet-btn" data-copy="${escapeHTML(bullet.improved)}">
                        ðŸ“‹ Copy Bullet
                    </button>
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
                    <span class="explanation-icon">ðŸ’¡</span>
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
                    ${explanations.map(exp => `<li>âœ“ ${escapeHTML(exp)}</li>`).join("")}
                </ul>
            </div>

            <div class="improvement-info-card">
                <span class="card-eyebrow">ACTIONABLE NEXT STEPS</span>
                <h4>Recommended Next Steps</h4>
                <ul class="improvement-action-list">
                    ${actions.map(act => `<li>â†’ ${escapeHTML(act)}</li>`).join("")}
                </ul>
            </div>
        </div>
    `;

    improvementContent.innerHTML = html;

    attachCopyListeners(data);
}

function attachCopyListeners(data) {
    const copyButtons = improvementContent.querySelectorAll(".copy-snippet-btn");
    copyButtons.forEach(function (btn) {
        btn.addEventListener("click", function () {
            const textToCopy = btn.getAttribute("data-copy");
            if (textToCopy) {
                copyTextToClipboard(textToCopy, btn, "ðŸ“‹ Copy");
            }
        });
    });

    const copyAllBtn = document.getElementById("copyAllImprovementsBtn");
    if (copyAllBtn) {
        copyAllBtn.addEventListener("click", function () {
            const allText = buildAllImprovementsText(data);
            copyTextToClipboard(allText, copyAllBtn, "ðŸ“‹ Copy All Improvements");
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
        data.bulletImprovements.forEach(function (b, idx) {
            output += (idx + 1) + ". [" + (b.section || "Experience") + "]\n";
            output += "Original: " + b.original + "\n";
            output += "Refined:  " + b.improved + "\n";
            output += "Rationale: " + b.explanation + "\n\n";
        });
    }

    if (Array.isArray(data.actionableChanges) && data.actionableChanges.length > 0) {
        output += "=== RECOMMENDED ACTIONS ===\n";
        data.actionableChanges.forEach(function (a) {
            output += "â€¢ " + a + "\n";
        });
    }

    return output.trim();
}

function copyTextToClipboard(text, btnElement, defaultText) {
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
            showCopySuccess(btnElement, defaultText);
        } catch (e) {
            showMessage("Could not copy to clipboard. Please copy manually.");
        }
        document.body.removeChild(textarea);
        return;
    }

    navigator.clipboard.writeText(text).then(function () {
        showCopySuccess(btnElement, defaultText);
    }).catch(function () {
        showMessage("Could not copy to clipboard. Please copy manually.");
    });
}

function showCopySuccess(btnElement, defaultText) {
    const originalText = btnElement.textContent;
    btnElement.textContent = "âœ“ Copied!";
    btnElement.classList.add("copied");
    setTimeout(function () {
        btnElement.textContent = originalText;
        btnElement.classList.remove("copied");
    }, 2000);
}


/* =========================================================
   INITIAL STATE
   ========================================================= */

/* =========================================================
   ANALYSIS RECOVERY & POLLING (V4.1)
   ========================================================= */

async function recoverAnalysisStatus(analysisId) {
    if (!analysisId) return;

    try {
        const response = await fetch("/analysis/" + encodeURIComponent(analysisId) + "/status");
        if (!response.ok) {
            if (response.status === 404) {
                stopStatusPolling();
                isAnalyzing = false;
                sessionStorage.removeItem(ACTIVE_ANALYSIS_STORAGE_KEY);
                hideLoading();
                analyzeButton.disabled = false;
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
            analyzeButton.disabled = false;
            handleServerError(data.error || "We couldn't complete the AI analysis. Please try again.");
        } else if (status === "CANCELLED") {
            stopStatusPolling();
            isAnalyzing = false;
            sessionStorage.removeItem(ACTIVE_ANALYSIS_STORAGE_KEY);
            terminalEventReceived = true;
            hideLoading();
            analyzeButton.disabled = false;
            showMessage("The analysis was cancelled. Please try again.");
        }
    } catch (err) {
        console.warn("Status recovery check failed:", err);
    }
}

function startStatusPolling(analysisId) {
    if (statusPollingTimer) {
        return;
    }
    statusPollingTimer = setInterval(function () {
        recoverAnalysisStatus(analysisId);
    }, 2000);
}

function stopStatusPolling() {
    if (statusPollingTimer) {
        clearInterval(statusPollingTimer);
        statusPollingTimer = null;
    }
}

document.addEventListener("visibilitychange", function () {
    if (document.visibilityState === "visible") {
        if (isImproving) {
            return;
        }
        const activeId = currentAnalysisId || sessionStorage.getItem(ACTIVE_ANALYSIS_STORAGE_KEY);
        if (activeId && isAnalyzing) {
            recoverAnalysisStatus(activeId);
        }
    }
});

window.addEventListener("DOMContentLoaded", function () {
    const savedAnalysisId = sessionStorage.getItem(ACTIVE_ANALYSIS_STORAGE_KEY)
        || sessionStorage.getItem("refinecv_last_completed_analysis_id");
    if (savedAnalysisId) {
        currentAnalysisId = savedAnalysisId;
        recoverAnalysisStatus(savedAnalysisId);
    }
});

analyzeButton.disabled = true;


console.log(
    "RefineCV frontend loaded successfully."
);
