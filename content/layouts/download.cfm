<!DOCTYPE html>
<html lang="en" data-theme="dark">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Download LuCLI - Lucee Command Line Interface</title>
    {{ include "partials/head.html"}}
    <link href="/css/download.css" rel="stylesheet">
</head>
<body>
<header class="header">
    <div class="container">
       {{ include "partials/nav.html"}}
    </div>
</header>

<main id="top">
    <section class="hero">
        <div class="container">
            <p class="detected-os-pill">
                <span>⬇️</span>
                <span id="detected-os-text">We'll pick the best download for your system.</span>
            </p>

            <h1 class="hero-title">Download LuCLI</h1>
            <p class="hero-subtitle">
                LuCLI bundles the Lucee CFML engine into a focused CLI, with self-contained binaries for
                macOS, Linux, and Windows, plus a universal JAR that works anywhere Java 17+ is available.
            </p>

            <div class="download-primary-card">
                <h2 class="download-primary-title" id="primary-download-title">Recommended download</h2>
                <p class="download-primary-subtitle" id="primary-download-subtitle">
                    We'll detect your OS and suggest the right binary. You can always choose a different option below.
                </p>

                <a id="primary-download-link" href="#all-downloads" class="download-button">
                    <span id="primary-download-label">Choose a download</span>
                    <span class="download-button-details" id="primary-download-details">Scroll to see all available builds.</span>
                </a>

                <p class="download-meta">
                    Prefer GitHub? 
                    <a id="latest-release-link" href="https://github.com/cybersonic/LuCLI/releases/latest" target="_blank" rel="noopener noreferrer">
                        Open the latest release page
                    </a>.
                </p>
            </div>
        </div>
    </section>

    <section id="all-downloads" class="section">
        <div class="container">
            <div class="section-header">
                <h2 class="section-title">All downloads</h2>
                <p class="section-description">
                    Pick the build that matches your environment. Self-contained binaries don't require Java;
                    the JAR works anywhere with Java 17+.
                </p>
            </div>

            <div class="downloads-grid">
                <article class="download-card" data-os="macos">
                    <div class="download-card-header">
                        <span class="download-os-label">Recommended for Mac</span>
                        <span class="download-os-name">macOS</span>
                        <p class="download-description">
                            Self-contained macOS binary. No separate Java install required.
                        </p>
                    </div>
                    <div class="download-card-footer">
                        <a
                            id="download-macos"
                            class="download-secondary-button"
                            data-os="macos"
                            data-file-template="lucli-__LATEST_VERSION__-macos"
                            href="#"
                        >
                            Download for macOS
                        </a>
                        <span class="download-size-hint">Executable binary</span>
                    </div>
                </article>

                <article class="download-card" data-os="linux">
                    <div class="download-card-header">
                        <span class="download-os-label">For servers & containers</span>
                        <span class="download-os-name">Linux</span>
                        <p class="download-description">
                            Self-contained Linux binary. Ideal for servers, Docker images, and CI runners.
                        </p>
                    </div>
                    <div class="download-card-footer">
                        <a
                            id="download-linux"
                            class="download-secondary-button"
                            data-os="linux"
                            data-file-template="lucli-__LATEST_VERSION__-linux"
                            href="#"
                        >
                            Download for Linux
                        </a>
                        <span class="download-size-hint">Executable binary</span>
                    </div>
                </article>

                <article class="download-card" data-os="windows">
                    <div class="download-card-header">
                        <span class="download-os-label">For Windows machines</span>
                        <span class="download-os-name">Windows</span>
                        <p class="download-description">
                            Self-contained Windows launcher (.bat). No separate Java install required.
                        </p>
                    </div>
                    <div class="download-card-footer">
                        <a
                            id="download-windows"
                            class="download-secondary-button"
                            data-os="windows"
                            data-file-template="lucli-__LATEST_VERSION__.bat"
                            href="#"
                        >
                            Download for Windows
                        </a>
                        <span class="download-size-hint">Batch file launcher</span>
                    </div>
                </article>

                <article class="download-card" data-os="jar">
                    <div class="download-card-header">
                        <span class="download-os-label">Universal</span>
                        <span class="download-os-name">JAR (any OS)</span>
                        <p class="download-description">
                            Universal JAR for any system with Java 17+ installed. Great for scripting or bundling
                            into your own tooling.
                        </p>
                    </div>
                    <div class="download-card-footer">
                        <a
                            id="download-jar"
                            class="download-secondary-button"
                            data-os="jar"
                            data-file-template="lucli-__LATEST_VERSION__.jar"
                            href="#"
                        >
                            Download JAR
                        </a>
                        <span class="download-size-hint">Requires Java 17+</span>
                    </div>
                </article>
            </div>
        </div>
    </section>
</main>
{{ include "partials/footer.html"}}


</body>
</html>
