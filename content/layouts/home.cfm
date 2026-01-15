<!DOCTYPE html>
<html lang="en" data-theme="dark">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>LuCLI — Lucee Command Line Interface</title>
    <cfinclude template="partials/head.html">
    
</head>
<body>
    <header class="header">
        <div class="container">
            <cfinclude template="partials/nav.html">
        </div>
    </header>
    <main id="top">
        <section class="hero">
            <div class="container">
                <div class="hero-badge">
                    <span>⚡</span> Lucee CLI for CFML developers
                </div>

                <h1 class="hero-title">LuCLI</h1>
                <div class="hero-pronunciation">/ˈluː-siː ˈɛl ˈaɪ/ • pronounced "Lucee-EL-EYE"</div>
                <div class="hero-tagline">Run Lucee, manage servers, and execute CFML — all from your terminal.</div>
                <p class="hero-subtitle">
                    LuCLI is a small, focused command line tool that gives you an interactive Lucee-powered terminal,
                    one-shot commands, and simple server management without the bloat.
                </p>

                <div class="hero-cta">
                    <a href="download/" class="cta-button cta-primary">
                        <span>⬇️</span> Download for your OS
                    </a>
                    <a href="../docs/" class="cta-button cta-secondary">
                        <span>📖</span> View full documentation
                    </a>
                    <a href="https://github.com/cybersonic/LuCLI" class="cta-button cta-secondary" target="_blank">
                        <span>🔗</span> View on GitHub
                    </a>
                </div>

                <div class="hero-demo">
                    <div class="demo-header">
                        <div class="demo-dot red"></div>
                        <div class="demo-dot yellow"></div>
                        <div class="demo-dot green"></div>
                        <div class="demo-title">LuCLI terminal</div>
                    </div>
                    <div class="demo-code">
                        <div class="demo-line">
                            <span class="demo-prompt">$</span>
                            <span class="demo-command">lucli</span>
                        </div>
                        <div class="demo-line">
                            <span class="demo-output">🚀 LuCLI Terminal 0.1.234-SNAPSHOT</span>
                        </div>
                        <div class="demo-line">
                            <span class="demo-prompt">lucli&gt;</span>
                            <span class="demo-command">server start</span>
                        </div>
                        <div class="demo-line">
                            <span class="demo-output">✅ Local Lucee server running on http://localhost:8080</span>
                        </div>
                        <div class="demo-line">
                            <span class="demo-prompt">app $</span>
                            <span class="demo-command">run MyScript.cfm</span>
                        </div>
                        <div class="demo-line">
                            <span class="demo-output">✨ CFML script executed in the current directory</span>
                        </div>
                        <!--- <div class="demo-footer">
                            <div>(illustrative example)</div>
                        </div> --->
                    </div>
                </div>
            </div>
        </section>

        <section id="what" class="section">
            <div class="container">
                <div class="section-header">
                    <h2 class="section-title">What is LuCLI?</h2>
                    <p class="section-description">
                        LuCLI is a command line interface that embeds the Lucee CFML engine and exposes it as a
                        minimal, script-friendly tool. Use it as an interactive terminal or as a one-shot CLI
                        to integrate Lucee into your workflows. It provides a modilar structure allowing you to exectute .cfc and .cfm files as well as manage Lucee servers. And More!
                    </p>
                </div>
            </div>
        </section>

        <section id="capabilities" class="section" style="background: var(--bg-secondary);">
            <div class="container">
                <div class="section-header">
                    <h2 class="section-title">What can you do with it?</h2>
                    <p class="section-description">A focused set of features for everyday CFML development.</p>
                </div>

                <div class="features-grid">
                    <div class="feature-card">
                        <span class="feature-icon">🖥️</span>
                        <h3 class="feature-title">Interactive Lucee terminal</h3>
                        <p class="feature-description">
                            Start a Lucee-backed terminal session, run CFML scripts and components, explore your
                            project, and keep state between commands.
                        </p>
                    </div>

                    <div class="feature-card">
                        <span class="feature-icon">🌐</span>
                        <h3 class="feature-title">Embedded dev server</h3>
                        <p class="feature-description">
                            Spin up a Lucee server for the current directory with a single command, manage
                            versions, and stop or restart as needed.
                        </p>
                    </div>

                    <div class="feature-card">
                        <span class="feature-icon">📦</span>
                        <h3 class="feature-title">Modules & automation</h3>
                        <p class="feature-description">
                            Add LuCLI modules to script repeatable tasks—database jobs, deployment helpers,
                            code generators, and more.
                        </p>
                    </div>

                    <div class="feature-card">
                        <span class="feature-icon">📝</span>
                        <h3 class="feature-title">Documentation & tooling</h3>
                        <p class="feature-description">
                            Generate documentation from CFML components, inspect configuration and environment
                            details, and keep project tooling in one place.
                        </p>
                    </div>
                </div>
            </div>
        </section>

        <section id="getting-started" class="section">
            <div class="container">
                <div class="section-header">
                    <h2 class="section-title">Getting started</h2>
                    <p class="section-description">
                        Download the jar, run it with Java, and you are ready to work with Lucee from the
                        command line.
                    </p>
                </div>

                <div class="features-grid" style="margin-top: 1rem;">
                    <div class="feature-card">
                        <h3 class="feature-title">1. Download LuCLI</h3>
                        <p class="feature-description">Grab the latest jar from GitHub releases:</p>
                        <div class="code-block">
                            curl -L -o lucli.jar \
https://github.com/cybersonic/LuCLI/releases/latest/download/lucli.jar
                        </div>
                    </div>

                    <div class="feature-card">
                        <h3 class="feature-title">2. Run the terminal</h3>
                        <p class="feature-description">Start the interactive LuCLI session:</p>
                        <div class="code-block">
                            java -jar lucli.jar terminal
                        </div>
                        <ul class="list">
                            <li class="list-item">
                                <span class="list-bullet">•</span>
                                <span>Use <code>server start</code> to launch a local Lucee server.</span>
                            </li>
                            <li class="list-item">
                                <span class="list-bullet">•</span>
                                <span>Run CFML with <code>run MyScript.cfm</code> from your project folder.</span>
                            </li>
                            <li class="list-item">
                                <span class="list-bullet">•</span>
                                <span>Type <code>help</code> inside the terminal for a list of commands.</span>
                            </li>
                        </ul>
                    </div>
                </div>

                <div class="section-header" style="margin-top: 2rem;">
                    <p class="section-description">
                        For a deeper dive into configuration, modules, and API details, head over to the
                        full documentation.
                    </p>
                    <div style="margin-top: 1rem;">
                        <a href="../docs/index.html" class="cta-button cta-secondary">
                            Open LuCLI documentation →
                        </a>
                    </div>
                </div>
            </div>
        </section>
    </main>

    <cfinclude template="partials/footer.html">

    

  
</body>
</html>
