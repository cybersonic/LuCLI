<!DOCTYPE html>
<html lang="en" data-theme="dark">
    <cfoutput>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>#page.meta.title# -  LuCLI Documentation</title>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="/css/main.css">
    <link rel="stylesheet" href="/css/docs.css">

</head>
<body>

     <header class="header">
        <div class="container">
            <cfinclude template="partials/nav.html">
        </div>
    </header>
    <!-- <header class="header">
        
        <div class="header-content">
            <a href="../index.html" class="logo">
                <div class="logo-icon">L&gt;</div>
                LuCLI
            </a>
            <nav class="header-nav">
                <a href="../index.html" class="nav-link">Home</a>
                <a href="../docs/index.html" class="nav-link active">Documentation</a>
                <a href="https://github.com/cybersonic/LuCLI" class="nav-link" target="_blank">GitHub</a>
            </nav>
            <button class="theme-toggle" onclick="toggleTheme()">🌙</button>
        </div>
    </header> -->

    <div class="docs-layout">
        <!-- Left Sidebar Navigation -->
        <aside class="sidebar">
            #data.navigation#

            <!--- <nav class="sidebar-section">
                <h3 class="sidebar-title">Getting Started</h3>
                <nav class="header-nav">
                    <a href="/##what" class="nav-link">What it is</a>
                    <a href="/##capabilities" class="nav-link">Capabilities</a>
                    <a href="/##getting-started" class="nav-link">Get started</a>
                    <a href="/download" class="nav-link">Download</a>
                </nav>
            </nav>

            <nav class="sidebar-section">
                <h3 class="sidebar-title">Core Features</h3>
                <ul class="sidebar-nav">
                    <li class="sidebar-item"><a href="terminal.html" class="sidebar-link">Interactive Terminal</a></li>
                    <li class="sidebar-item"><a href="server-management.html" class="sidebar-link">Server Management</a></li>
                    <li class="sidebar-item"><a href="script-execution.html" class="sidebar-link">Script Execution</a></li>
                    <li class="sidebar-item"><a href="modules.html" class="sidebar-link">Modules</a></li>
                </ul>
            </nav>

            <nav class="sidebar-section">
                <h3 class="sidebar-title">Configuration</h3>
                <ul class="sidebar-nav">
                    <li class="sidebar-item"><a href="configuration.html" class="sidebar-link">Configuration Files</a></li>
                    <li class="sidebar-item"><a href="environments.html" class="sidebar-link">Environments</a></li>
                    <li class="sidebar-item"><a href="settings.html" class="sidebar-link">Settings</a></li>
                </ul>
            </nav>

            <nav class="sidebar-section">
                <h3 class="sidebar-title">Advanced</h3>
                <ul class="sidebar-nav">
                    <li class="sidebar-item"><a href="monitoring.html" class="sidebar-link">JMX Monitoring</a></li>
                    <li class="sidebar-item"><a href="customization.html" class="sidebar-link">Customization</a></li>
                    <li class="sidebar-item"><a href="troubleshooting.html" class="sidebar-link">Troubleshooting</a></li>
                </ul>
            </nav>

            <nav class="sidebar-section">
                <h3 class="sidebar-title">Reference</h3>
                <ul class="sidebar-nav">
                    <li class="sidebar-item"><a href="command-reference.html" class="sidebar-link">Command Reference</a></li>
                    <li class="sidebar-item"><a href="api.html" class="sidebar-link">API Documentation</a></li>
                </ul>
            </nav> --->
        </aside>

        <!-- Main Content Area -->
        <main class="main-content">
            <div class="content-header">
                <h1 class="content-title">#page.meta.title#</h1>
                <!--- {{#if description}}
                <p class="content-description">{{description}}</p>
                {{/if}} --->
            </div>

            <div class="content-body">
                #content#

                <!-- Docs prev/next pagination (driven by Markspresso meta: prev_url/next_url) -->
                <cfif (structKeyExists(data, "prev_url") and len(data.prev_url)) or (structKeyExists(data, "next_url") and len(data.next_url))>
                    <nav class="docs-pagination">
                        <cfif structKeyExists(data, "prev_url") and len(data.prev_url)>
                            <a href="#data.prev_url#" class="docs-pagination__link docs-pagination__prev">&larr; #htmlEditFormat(data.prev_title)#</a>
                        </cfif>
                        <cfif structKeyExists(data, "next_url") and len(data.next_url)>
                            <a href="#data.next_url#" class="docs-pagination__link docs-pagination__next">#htmlEditFormat(data.next_title)# &rarr;</a>
                        </cfif>
                    </nav>
                </cfif>
            </div>
        </main>

        <!-- Right Table of Contents -->
        <aside class="toc-sidebar">
            <h3 class="toc-title">On This Page</h3>
            <ul class="toc-list">
                <!-- Table of contents will be auto-generated by JavaScript -->
            </ul>
        </aside>
    </div>
    
    <!-- Docs search overlay (Cmd/Ctrl+K) -->
    <div class="docs-search-overlay" id="docs-search-overlay" aria-hidden="true">
        <div class="docs-search-backdrop"></div>
        <div class="docs-search-dialog" role="dialog" aria-modal="true" aria-labelledby="docs-search-label">
            <div class="docs-search-input-row">
                <span class="docs-search-icon">🔍</span>
                <input
                    id="markspresso-search-input"
                    type="search"
                    placeholder="Search the LuCLI docs…"
                    autocomplete="off"
                    aria-label="Search the documentation"
                />
                <button type="button" class="docs-search-close" aria-label="Close search">
                    Esc
                </button>
            </div>
            <div id="markspresso-search-results" class="docs-search-results"></div>
        </div>
    </div>

    <cfinclude template="partials/footer.html">
    <script src="/js/main.js"></script>

    <script>
    (function () {
        var overlay = document.getElementById('docs-search-overlay');
        var input = document.getElementById('markspresso-search-input');
        var closeBtn = overlay ? overlay.querySelector('.docs-search-close') : null;
        var activeBefore = null;

        function openSearch() {
            if (!overlay) return;
            activeBefore = document.activeElement;
            overlay.classList.add('is-open');
            overlay.setAttribute('aria-hidden', 'false');
            if (input) {
                setTimeout(function () { input.focus(); }, 0);
            }
        }

        function closeSearch() {
            if (!overlay) return;
            overlay.classList.remove('is-open');
            overlay.setAttribute('aria-hidden', 'true');
            if (activeBefore && typeof activeBefore.focus === 'function') {
                activeBefore.focus();
            }
        }

        // Expose a hook for header search control
        window.MarkspressoOpenSearch = openSearch;

        document.addEventListener('keydown', function (e) {
            var isMac = navigator.platform && navigator.platform.toUpperCase().indexOf('MAC') >= 0;
            var metaOrCtrl = isMac ? e.metaKey : e.ctrlKey;

            // Cmd/Ctrl + K opens search
            if (metaOrCtrl && (e.key === 'k' || e.key === 'K')) {
                e.preventDefault();
                openSearch();
                return;
            }

            // Esc closes when overlay is open
            if (e.key === 'Escape' && overlay && overlay.classList.contains('is-open')) {
                e.preventDefault();
                closeSearch();
            }
        });

        if (closeBtn) {
            closeBtn.addEventListener('click', function (e) {
                e.preventDefault();
                closeSearch();
            });
        }

        if (overlay) {
            overlay.addEventListener('click', function (e) {
                if (e.target === overlay || e.target.classList.contains('docs-search-backdrop')) {
                    closeSearch();
                }
            });
        }
    })();
    </script>

    #markspressoScripts#
    </cfoutput>
</body>
</html>
