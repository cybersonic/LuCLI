<!DOCTYPE html>
<html lang="en" data-theme="dark">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>LuCLI — Lucee Command Line Interface</title>
    <cfinclude template="partials/head.cfm">
    
</head>
<body>

    <cfinclude template="partials/nav.cfm">



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
                    <a href="/docs/" class="cta-button cta-secondary">
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
                        LuCLI is a small command line interface built around the Lucee CFML engine. It gives you
                        an interactive Lucee‑backed terminal, one‑shot CFML commands, and simple per‑project
                        servers driven by a <code>lucee.json</code> file.
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
                        <h3 class="feature-title">Run CFML from your terminal</h3>
                        <p class="feature-description">
                            Start a Lucee‑backed terminal session, run CFML expressions, scripts and components,
                            and keep state between commands for quick experimentation.
                        </p>
                    </div>

                    <div class="feature-card">
                        <span class="feature-icon">🌐</span>
                        <h3 class="feature-title">Per‑project Lucee servers</h3>
                        <p class="feature-description">
                            Spin up Lucee/Tomcat servers with a single command, configured by a
                            <code>lucee.json</code> file for ports, URL rewriting, HTTPS, environments and more.
                        </p>
                    </div>

                    <div class="feature-card">
                        <span class="feature-icon">📦</span>
                        <h3 class="feature-title">Modules & automation</h3>
                        <p class="feature-description">
                            Package reusable CFML tools as LuCLI modules or <code>.lucli</code> scripts to
                            automate tests, deployments, and maintenance tasks across projects.
                        </p>
                    </div>

                    <div class="feature-card">
                        <span class="feature-icon">📊</span>
                        <h3 class="feature-title">Monitoring & advanced tooling</h3>
                        <p class="feature-description">
                            Use the built‑in JMX dashboard, Java agents and rich CLI output (emoji‑aware prompts,
                            placeholders, shell completion) to observe and debug your Lucee servers.
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
                        Download the jar or self‑contained binary, run it with Java, and you are ready to run CFML
                        and manage Lucee servers from your terminal.
                    </p>
                </div>

                <div class="features-grid" style="margin-top: 1rem;">
                    <div class="feature-card">
                        <h3 class="feature-title">1. Install LuCLI</h3>
                        <p class="feature-description">Grab the latest JAR from GitHub releases:</p>
                        <div class="code-block">
                            curl -L -o lucli.jar \ <br>
                            <a href="https://github.com/cybersonic/LuCLI/releases/latest/download/lucli.jar">https://github.com/cybersonic/LuCLI/releases/latest/download/lucli.jar</a>
                        </div>
                        <p class="feature-description" style="margin-top: 0.75rem;">
                            Then run it with Java (or place the <a href="https://github.com/cybersonic/LuCLI/releases/latest/">self‑contained binary</a> on your <code>PATH</code> to
                            use the shorter <code>lucli</code> command.
                        </p>
                    </div>

                    <div class="feature-card">
                        <h3 class="feature-title">2. Start a server</h3>
                        <p class="feature-description">From your project directory, start Lucee for that folder:</p>
                        <div class="code-block">
                            java -jar lucli.jar server start

# once installed on PATH:
lucli server start
                        </div>
                        <ul class="list">
                            <li class="list-item">
                                <span class="list-bullet">•</span>
                                <span>Open <code>http://localhost:8080</code> (or the port you configured).</span>
                            </li>
                            <li class="list-item">
                                <span class="list-bullet">•</span>
                                <span>Add a <code>lucee.json</code> file later to control ports, URL rewriting,
                                    HTTPS, environments, and more.</span>
                            </li>
                            <li class="list-item">
                                <span class="list-bullet">•</span>
                                <span>Use <code>lucli server status</code> and <code>lucli server log --follow</code>
                                    to inspect and debug.</span>
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
                        <a href="/docs/" class="cta-button cta-secondary">
                            Open LuCLI documentation →
                        </a>
                    </div>
                </div>
            </div>
        </section>
    </main>

    
    <cfinclude template="partials/footer.cfm">

</body>
</html>
