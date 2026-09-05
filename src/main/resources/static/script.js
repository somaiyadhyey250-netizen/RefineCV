const resumeInput =
    document.getElementById("resumeFile");

const analyzeButton =
    document.getElementById("analyzeButton");

const selectedFile =
    document.getElementById("selectedFile");

const dropZone =
    document.querySelector(".drop-zone");


/* =========================================
   FILE SELECTION
========================================= */

resumeInput.addEventListener(
    "change",
    function () {

        const file =
            resumeInput.files[0];

        if (!file) {

            selectedFile.textContent = "";

            return;
        }

        handleFile(file);

    }
);


/* =========================================
   FILE VALIDATION
========================================= */

function handleFile(file) {

    if (
        file.type !==
        "application/pdf"
    ) {

        selectedFile.textContent =
            "❌ Please select a PDF file.";

        resumeInput.value = "";

        return;
    }


    const maxSize =
        5 * 1024 * 1024;


    if (file.size > maxSize) {

        selectedFile.textContent =
            "❌ File is too large. Maximum size is 5 MB.";

        resumeInput.value = "";

        return;
    }


    const fileSize =
        (
            file.size /
            (1024 * 1024)
        ).toFixed(2);


    selectedFile.textContent =
        `✓ ${file.name} (${fileSize} MB)`;

}


/* =========================================
   DRAG & DROP
========================================= */

dropZone.addEventListener(
    "dragover",
    function (event) {

        event.preventDefault();

        dropZone.classList.add(
            "dragging"
        );

    }
);


dropZone.addEventListener(
    "dragleave",
    function () {

        dropZone.classList.remove(
            "dragging"
        );

    }
);


dropZone.addEventListener(
    "drop",
    function (event) {

        event.preventDefault();

        dropZone.classList.remove(
            "dragging"
        );


        const file =
            event.dataTransfer.files[0];


        if (!file) return;


        const dataTransfer =
            new DataTransfer();


        dataTransfer.items.add(file);


        resumeInput.files =
            dataTransfer.files;


        handleFile(file);

    }
);


/* =========================================
   ANALYZE BUTTON
========================================= */

analyzeButton.addEventListener(
    "click",
    async function () {

        const file =
            resumeInput.files[0];


        if (!file) {

            showMessage(
                "Please select your resume first.",
                "error"
            );

            return;
        }


        analyzeButton.disabled =
            true;


        analyzeButton
            .querySelector("span")
            .textContent =
            "Analyzing...";


        showLoading();


        const formData =
            new FormData();


        formData.append(
            "resume",
            file
        );


        try {

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
                    "Server returned an error."
                );

            }


            const result =
                await response.json();


            console.log(
                "AI Analysis:",
                result
            );


            displayResults(
                result
            );


        } catch (error) {

            console.error(
                "Analysis error:",
                error
            );


            removeLoading();


            showMessage(
                "Something went wrong while analyzing your resume.",
                "error"
            );


        } finally {

            analyzeButton.disabled =
                false;


            analyzeButton
                .querySelector("span")
                .textContent =
                "Analyze Resume";

        }

    }
);


/* =========================================
   LOADING SCREEN
========================================= */

function showLoading() {

    removeResults();


    const loading =
        document.createElement(
            "section"
        );


    loading.id =
        "analysisLoading";


    loading.className =
        "analysis-loading";


    loading.innerHTML = `

        <div class="loading-header">

            <div class="loading-icon">
                ✦
            </div>

            <h2>
                Analyzing your resume
            </h2>

            <p>
                ResumeAI is carefully reviewing
                your resume.
            </p>

        </div>


        <div class="analysis-steps">


            <div
                class="analysis-step active"
                id="step-upload"
            >

                <div class="step-status">

                    <span class="step-spinner"></span>

                </div>

                <div class="step-content">

                    <strong>
                        Resume uploaded
                    </strong>

                    <span>
                        Preparing your document
                    </span>

                </div>

            </div>



            <div
                class="analysis-step"
                id="step-extract"
            >

                <div class="step-status">

                    <span class="step-number-small">
                        2
                    </span>

                </div>

                <div class="step-content">

                    <strong>
                        Extracting resume content
                    </strong>

                    <span>
                        Reading information from your PDF
                    </span>

                </div>

            </div>



            <div
                class="analysis-step"
                id="step-ocr"
            >

                <div class="step-status">

                    <span class="step-number-small">
                        3
                    </span>

                </div>

                <div class="step-content">

                    <strong>
                        Reading with OCR
                    </strong>

                    <span>
                        Understanding your resume visually
                    </span>

                </div>

            </div>



            <div
                class="analysis-step"
                id="step-ai"
            >

                <div class="step-status">

                    <span class="step-number-small">
                        4
                    </span>

                </div>

                <div class="step-content">

                    <strong>
                        AI analysis
                    </strong>

                    <span>
                        Analyzing skills, experience and content
                    </span>

                </div>

            </div>



            <div
                class="analysis-step"
                id="step-final"
            >

                <div class="step-status">

                    <span class="step-number-small">
                        5
                    </span>

                </div>

                <div class="step-content">

                    <strong>
                        Generating recommendations
                    </strong>

                    <span>
                        Preparing your personalized insights
                    </span>

                </div>

            </div>


        </div>


        <div class="loading-footer">

            <span class="mini-pulse"></span>

            This may take a few moments...

        </div>

    `;


    document
        .querySelector("main")
        .appendChild(loading);


    loading.scrollIntoView({
        behavior: "smooth"
    });


    startAnalysisAnimation();

}


/* =========================================
   LOADING ANIMATION
========================================= */

function startAnalysisAnimation() {

    const steps = [

        "step-upload",
        "step-extract",
        "step-ocr",
        "step-ai",
        "step-final"

    ];


    let currentStep = 0;


    const interval =
        setInterval(
            () => {


                const current =
                    document.getElementById(
                        steps[currentStep]
                    );


                if (current) {

                    current.classList.remove(
                        "active"
                    );

                    current.classList.add(
                        "completed"
                    );


                    current
                        .querySelector(
                            ".step-status"
                        )
                        .innerHTML =
                        `<span class="step-check">✓</span>`;

                }


                currentStep++;


                if (
                    currentStep <
                    steps.length
                ) {

                    const next =
                        document.getElementById(
                            steps[currentStep]
                        );


                    if (next) {

                        next.classList.add(
                            "active"
                        );


                        next
                            .querySelector(
                                ".step-status"
                            )
                            .innerHTML =
                            `<span class="step-spinner"></span>`;

                    }

                } else {

                    clearInterval(
                        interval
                    );

                }


            },
            1200
        );

}


/* =========================================
   DISPLAY RESULTS
========================================= */

function displayResults(data) {

    removeLoading();

    removeResults();


    const score =
        Number(data.score) || 0;


    const results =
        document.createElement(
            "section"
        );


    results.id =
        "results";


    results.className =
        "results-section";


    results.innerHTML = `

        <div class="results-heading">

            <span class="section-label">
                AI ANALYSIS
            </span>

            <h2>
                Your Resume Analysis
            </h2>

            <p>
                Here is what our AI found in your resume.
            </p>

        </div>



        <!-- SCORE -->

        <div class="score-card">

            <div>

                <span class="result-label">
                    OVERALL SCORE
                </span>

                <h3>

                    ${score}

                    <span>
                        /100
                    </span>

                </h3>


                <p class="score-status">

                    ${getScoreStatus(score)}

                </p>


                <p>

                    Based on resume quality,
                    skills and ATS readiness.

                </p>

            </div>



            <div
                class="score-circle"
                style="--score: ${score}"
            >

                <div class="score-inner">

                    <span>
                        ${score}
                    </span>

                    <small>
                        /100
                    </small>

                </div>

            </div>

        </div>



        <!-- SUMMARY -->

        <div class="result-card">

            <span class="result-label">

                RESUME SUMMARY

            </span>


            <p>

                ${escapeHTML(
                    data.summary
                )}

            </p>

        </div>



        <!-- GRID -->

        <div class="results-grid">


            <div class="result-card">

                <span class="result-label">

                    STRONGEST SKILLS

                </span>


                ${createList(
                    data.strongestSkills
                )}

            </div>



            <div class="result-card">

                <span class="result-label">

                    MISSING / WEAK SKILLS

                </span>


                ${createList(
                    data.missingOrWeakSkills
                )}

            </div>



            <div class="result-card">

                <span class="result-label">

                    STRENGTHS

                </span>


                ${createList(
                    data.strengths
                )}

            </div>



            <div class="result-card">

                <span class="result-label">

                    WEAKNESSES

                </span>


                ${createList(
                    data.weaknesses
                )}

            </div>


        </div>



        <!-- ATS -->

        <div class="result-card">

            <span class="result-label">

                ATS COMPATIBILITY

            </span>


            <p>

                ${escapeHTML(
                    data.atsCompatibility
                )}

            </p>

        </div>



        <!-- SUGGESTIONS -->

        <div class="result-card">

            <span class="result-label">

                AI IMPROVEMENT SUGGESTIONS

            </span>


            ${createList(
                data.suggestions
            )}

        </div>



        <!-- RECOMMENDATIONS -->

        <div class="result-card">

            <span class="result-label">

                RECOMMENDED RESUME CHANGES

            </span>


            ${createList(
                data.recommendedChanges
            )}

        </div>



        <!-- ANALYZE AGAIN -->

        <div class="analyze-again">

            <button
                type="button"
                onclick="scrollToUpload()"
            >

                Analyze Another Resume →

            </button>

        </div>

    `;


    document
        .querySelector("main")
        .appendChild(results);


    results.scrollIntoView({
        behavior: "smooth"
    });

}


/* =========================================
   SCORE STATUS
========================================= */

function getScoreStatus(score) {

    if (score >= 80) {

        return "Excellent Resume";

    }


    if (score >= 65) {

        return "Good Resume";

    }


    if (score >= 50) {

        return "Needs Improvement";

    }


    return "Needs Major Improvement";

}


/* =========================================
   CREATE LIST
========================================= */

function createList(items) {

    if (
        !items ||
        !Array.isArray(items) ||
        items.length === 0
    ) {

        return `
            <p>
                Not mentioned.
            </p>
        `;

    }


    return `

        <ul class="result-list">

            ${items
                .map(
                    item => `

                        <li>
                            ${escapeHTML(item)}
                        </li>

                    `
                )
                .join("")}

        </ul>

    `;

}


/* =========================================
   HTML SAFETY
========================================= */

function escapeHTML(text) {

    if (!text) return "";


    const div =
        document.createElement(
            "div"
        );


    div.textContent =
        String(text);


    return div.innerHTML;

}


/* =========================================
   MESSAGE
========================================= */

function showMessage(
    message,
    type
) {

    removeMessage();


    const messageBox =
        document.createElement(
            "div"
        );


    messageBox.id =
        "messageBox";


    messageBox.className =
        `message-box ${type}`;


    messageBox.textContent =
        message;


    document
        .querySelector("main")
        .appendChild(messageBox);


    messageBox.scrollIntoView({
        behavior: "smooth"
    });


    setTimeout(
        () => {

            if (messageBox) {

                messageBox.remove();

            }

        },
        4000
    );

}


/* =========================================
   REMOVE LOADING
========================================= */

function removeLoading() {

    const loading =
        document.getElementById(
            "analysisLoading"
        );


    if (loading) {

        loading.remove();

    }

}


/* =========================================
   REMOVE RESULTS
========================================= */

function removeResults() {

    const results =
        document.getElementById(
            "results"
        );


    if (results) {

        results.remove();

    }

}


/* =========================================
   REMOVE MESSAGE
========================================= */

function removeMessage() {

    const message =
        document.getElementById(
            "messageBox"
        );


    if (message) {

        message.remove();

    }

}


/* =========================================
   ANALYZE ANOTHER
========================================= */

function scrollToUpload() {

    removeResults();


    document
        .querySelector(
            ".upload-card"
        )
        .scrollIntoView({
            behavior: "smooth"
        });

}