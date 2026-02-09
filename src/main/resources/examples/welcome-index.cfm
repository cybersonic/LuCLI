<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Welcome to LuCLI</title>
    <style>
        :root {
            --primary: #4f46e5;
            --primary-dark: #4338ca;
            --bg: #f8fafc;
            --card-bg: #ffffff;
            --text: #1e293b;
            --text-muted: #64748b;
            --border: #e2e8f0;
            --code-bg: #1e293b;
            --code-text: #e2e8f0;
            --success: #22c55e;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
            background: var(--bg);
            color: var(--text);
            line-height: 1.6;
            padding: 2rem;
        }
        .container {
            max-width: 900px;
            margin: 0 auto;
        }
        header {
            text-align: center;
            padding: 3rem 0;
        }
        .logo {
            font-size: 3rem;
            margin-bottom: 1rem;
        }
        h1 {
            font-size: 2.5rem;
            font-weight: 700;
            margin-bottom: 0.5rem;
        }
        .tagline {
            font-size: 1.25rem;
            color: var(--text-muted);
        }
        .status-badge {
            display: inline-flex;
            align-items: center;
            gap: 0.5rem;
            background: #dcfce7;
            color: #166534;
            padding: 0.5rem 1rem;
            border-radius: 9999px;
            font-weight: 500;
            margin: 1.5rem 0;
        }
        .status-dot {
            width: 8px;
            height: 8px;
            background: var(--success);
            border-radius: 50%;
            animation: pulse 2s infinite;
        }
        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.5; }
        }
        .cards {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
            gap: 1.5rem;
            margin: 2rem 0;
        }
        .card {
            background: var(--card-bg);
            border-radius: 12px;
            padding: 1.5rem;
            box-shadow: 0 1px 3px rgba(0,0,0,0.1);
            border: 1px solid var(--border);
        }
        .card h3 {
            font-size: 1.1rem;
            margin-bottom: 0.75rem;
            display: flex;
            align-items: center;
            gap: 0.5rem;
        }
        .card p {
            color: var(--text-muted);
            font-size: 0.95rem;
            margin-bottom: 1rem;
        }
        .card code {
            display: block;
            background: var(--code-bg);
            color: var(--code-text);
            padding: 0.75rem 1rem;
            border-radius: 8px;
            font-family: 'SF Mono', Monaco, 'Cascadia Code', monospace;
            font-size: 0.85rem;
            overflow-x: auto;
        }
        .info-section {
            background: var(--card-bg);
            border-radius: 12px;
            padding: 1.5rem;
            margin: 2rem 0;
            border: 1px solid var(--border);
        }
        .info-section h2 {
            font-size: 1.25rem;
            margin-bottom: 1rem;
        }
        .info-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 1rem;
        }
        .info-item {
            display: flex;
            flex-direction: column;
        }
        .info-label {
            font-size: 0.8rem;
            text-transform: uppercase;
            letter-spacing: 0.05em;
            color: var(--text-muted);
            margin-bottom: 0.25rem;
        }
        .info-value {
            font-weight: 500;
        }
        .links {
            display: flex;
            gap: 1rem;
            flex-wrap: wrap;
            justify-content: center;
            margin: 2rem 0;
        }
        .links a {
            display: inline-flex;
            align-items: center;
            gap: 0.5rem;
            padding: 0.75rem 1.5rem;
            border-radius: 8px;
            text-decoration: none;
            font-weight: 500;
            transition: all 0.2s;
        }
        .links a.primary {
            background: var(--primary);
            color: white;
        }
        .links a.primary:hover {
            background: var(--primary-dark);
        }
        .links a.secondary {
            background: var(--card-bg);
            color: var(--text);
            border: 1px solid var(--border);
        }
        .links a.secondary:hover {
            border-color: var(--primary);
            color: var(--primary);
        }
        footer {
            text-align: center;
            padding: 2rem 0;
            color: var(--text-muted);
            font-size: 0.9rem;
        }
        .note {
            background: #fef3c7;
            border: 1px solid #fcd34d;
            border-radius: 8px;
            padding: 1rem;
            margin: 1.5rem 0;
            font-size: 0.9rem;
        }
        .note strong {
            color: #92400e;
        }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <div class="logo">🚀</div>
            <h1>Welcome to LuCLI</h1>
            <p class="tagline">Your Lucee CFML server is running!</p>
            <div class="status-badge">
                <span class="status-dot"></span>
                Server Active
            </div>
        </header>

        <div class="info-section">
            <h2>📊 Server Information</h2>
            <div class="info-grid">
                <div class="info-item">
                    <span class="info-label">Lucee Version</span>
                    <span class="info-value"><cfoutput>#server.lucee.version#</cfoutput></span>
                </div>
                <div class="info-item">
                    <span class="info-label">Java Version</span>
                    <span class="info-value"><cfoutput>#server.java.version#</cfoutput></span>
                </div>
                <div class="info-item">
                    <span class="info-label">Server Time</span>
                    <span class="info-value"><cfoutput>#dateTimeFormat(now(), "yyyy-mm-dd HH:nn:ss")#</cfoutput></span>
                </div>
                <div class="info-item">
                    <span class="info-label">Webroot</span>
                    <span class="info-value"><cfoutput>#expandPath("/")#</cfoutput></span>
                </div>
            </div>
        </div>

        <div class="note">
            <strong>💡 Getting Started:</strong> Replace this file with your own <code>index.cfm</code> to start building your application. 
            This welcome page will not appear once you create your own index file.
        </div>

        <h2 style="text-align: center; margin: 2rem 0 1rem;">🛠️ Quick Commands</h2>
        
        <div class="cards">
            <div class="card">
                <h3>📋 View Server Status</h3>
                <p>Check the current status of your server instance.</p>
                <code>lucli server status</code>
            </div>
            <div class="card">
                <h3>🔄 Restart Server</h3>
                <p>Restart the server to apply configuration changes.</p>
                <code>lucli server restart</code>
            </div>
            <div class="card">
                <h3>📜 View Logs</h3>
                <p>Stream server logs in real-time.</p>
                <code>lucli server log --follow</code>
            </div>
            <div class="card">
                <h3>⚙️ Edit Configuration</h3>
                <p>Modify server settings interactively.</p>
                <code>lucli server edit</code>
            </div>
            <div class="card">
                <h3>📊 Monitor Performance</h3>
                <p>View real-time JMX metrics dashboard.</p>
                <code>lucli server monitor</code>
            </div>
            <div class="card">
                <h3>🛑 Stop Server</h3>
                <p>Gracefully stop the running server.</p>
                <code>lucli server stop</code>
            </div>
        </div>

        <div class="links">
            <a href="https://github.com/lucee/lucli" class="primary" target="_blank">
                📖 LuCLI Documentation
            </a>
            <a href="https://docs.lucee.org" class="secondary" target="_blank">
                📚 Lucee Docs
            </a>
            <a href="/lucee/admin/server.cfm" class="secondary">
                🔧 Lucee Admin
            </a>
        </div>

        <footer>
            <p>Powered by <strong>LuCLI</strong> &mdash; The modern CLI for Lucee CFML</p>
            <p style="margin-top: 0.5rem;">
                <cfoutput>
                    Running Lucee #server.lucee.version# on #server.os.name# (#server.os.arch#)
                </cfoutput>
            </p>
        </footer>
    </div>
</body>
</html>
