/* =========================================================
   REFINECV — V2 FRONTEND
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


/* =========================================================
   STATE
   ========================================================= */

let selectedResume = null;

const MAX_FILE_SIZE = 5 * 1024 * 1024;


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
        `✓  ${file.name}  ·  ${formatFileSize(file.size)}`;

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


        startAnalysis(selectedResume);
    }
);


/* =========================================================
   START ANALYSIS
   ========================================================= */

async function startAnalysis(file) {

    hideMessage();

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
            behavior: "smooth",
            block: "start"
        });

    }, 50);


    try {

        const formData = new FormData();

        formData.append(
            "resume",
            file
        );


        const response =
            await fetch(
                "/analyze",
                {
                    method: "POST",
                    body: formData
                }
            );


        if (!response.ok) {

            throw new Error(
                `Server returned ${response.status}`
            );
        }


        if (!response.body) {

            throw new Error(
                "Streaming response is not supported by this browser."
            );
        }


        await readSSEStream(response);


    } catch (error) {

        console.error(
            "RefineCV analysis error:",
            error
        );


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


    if (buffer.trim()) {

        processSSEEvent(buffer);
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


        case "result":

            handleResult(data);

            break;


        case "error":

            handleServerError(data);

            break;


        default:

            console.log(
                "RefineCV SSE event:",
                eventName,
                data
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

            console.log(
                "Unknown RefineCV status:",
                status
            );
    }
}


/* =========================================================
   RESULT
   ========================================================= */

function handleResult(rawData) {

    try {

        const result =
            JSON.parse(rawData);


        completeAllSteps();


        setTimeout(function () {

            hideLoading();

            renderResults(result);

            resultsSection.hidden = false;


            resultsSection.scrollIntoView({
                behavior: "smooth",
                block: "start"
            });


            analyzeButton.disabled = false;

        }, 450);


    } catch (error) {

        console.error(
            "Could not parse AI result:",
            error,
            rawData
        );


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

    console.error(
        "Server error:",
        message
    );


    hideLoading();

    analyzeButton.disabled = false;


    showMessage(
        "Resume analysis failed. Please check your resume and try again."
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
                behavior: "smooth",
                block: "center"
            });

        } else {

            window.scrollTo({
                top: 0,
                behavior: "smooth"
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
   INITIAL STATE
   ========================================================= */

analyzeButton.disabled = true;


console.log(
    "RefineCV frontend loaded successfully."
);