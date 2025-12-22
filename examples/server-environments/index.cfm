<cfscript>
/**
 * Environment-Based Server Configuration Example
 * 
 * This page shows environment-specific configuration
 */

serverroot  = SERVER.system.environment.CATALINA_HOME;

// Read environment file if it exists
environmentFile = "#serverroot#/.environment";
activeEnvironment = fileExists(environmentFile) ? trim(fileRead(environmentFile)) : "none (base config)";

serverInfo = {
    name: getFileFromPath(serverroot),
    environment: activeEnvironment,
    luceeVersion: server.lucee.version,
    port: cgi.server_port,
    javaVersion: server.java.version,
    maxMemory: createObject("java", "java.lang.Runtime").getRuntime().maxMemory() / 1024 / 1024 & " MB",
    osName: server.os.name
};
</cfscript>
<!DOCTYPE html>
<html>
<head>
    <title>LuCLI Server - Environments Example</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            max-width: 900px;
            margin: 50px auto;
            padding: 0 20px;
            line-height: 1.6;
        }
        .info-box {
            background: #d1ecf1;
            border: 1px solid #bee5eb;
            color: #0c5460;
            padding: 15px;
            border-radius: 4px;
            margin: 20px 0;
        }
        .info-box dt {
            font-weight: bold;
            margin-top: 10px;
        }
        .info-box dd {
            margin-left: 0;
            padding-left: 20px;
            color: #555;
        }
        .env-badge {
            display: inline-block;
            padding: 4px 12px;
            border-radius: 4px;
            font-weight: bold;
            font-size: 0.9em;
        }
        .env-prod {
            background: #d4edda;
            color: #155724;
            border: 1px solid #c3e6cb;
        }
        .env-dev {
            background: #fff3cd;
            color: #856404;
            border: 1px solid #ffeaa7;
        }
        .env-staging {
            background: #cce5ff;
            color: #004085;
            border: 1px solid #b8daff;
        }
        .env-base {
            background: #e2e3e5;
            color: #383d41;
            border: 1px solid #d6d8db;
        }
        code {
            background: #f4f4f4;
            padding: 2px 6px;
            border-radius: 3px;
            font-family: "Courier New", monospace;
        }
        pre {
            background: #f4f4f4;
            padding: 15px;
            border-radius: 4px;
            overflow-x: auto;
        }
        h1 {
            color: #333;
        }
        h2 {
            color: #555;
            margin-top: 30px;
        }
        .success {
            background: #d4edda;
            border: 1px solid #c3e6cb;
            color: #155724;
            padding: 15px;
            border-radius: 4px;
            margin: 20px 0;
        }
        a {
            color: #007bff;
            text-decoration: none;
        }
        a:hover {
            text-decoration: underline;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            margin: 20px 0;
        }
        th, td {
            padding: 10px;
            text-align: left;
            border-bottom: 1px solid #ddd;
        }
        th {
            background: #f8f9fa;
            font-weight: bold;
        }
    </style>
</head>
<body>
    <h1>🌍 LuCLI Environment Configuration</h1>
    <p><strong>Example:</strong> Environment-Based Server Configuration</p>

    <cfoutput>
    <div class="success">
        ✅ <strong>Success!</strong> Server running with 
        <cfif serverInfo.environment eq "prod">
            <span class="env-badge env-prod">PRODUCTION</span>
        <cfelseif serverInfo.environment eq "dev">
            <span class="env-badge env-dev">DEVELOPMENT</span>
        <cfelseif serverInfo.environment eq "staging">
            <span class="env-badge env-staging">STAGING</span>
        <cfelse>
            <span class="env-badge env-base">BASE CONFIG</span>
        </cfif>
        environment
    </div>

    <h2>Active Configuration</h2>
    <div class="info-box">
        <dl>
            <dt>Environment</dt>
            <dd><code>#serverInfo.environment#</code></dd>

            <dt>Server Name</dt>
            <dd><code>#serverInfo.name#</code></dd>

            <dt>Port</dt>
            <dd><code>#serverInfo.port#</code></dd>

            <dt>Max Memory</dt>
            <dd><code>#serverInfo.maxMemory#</code></dd>

            <dt>Lucee Version</dt>
            <dd><code>#serverInfo.luceeVersion#</code></dd>

            <dt>Java Version</dt>
            <dd><code>#serverInfo.javaVersion#</code></dd>

            <dt>Operating System</dt>
            <dd><code>#serverInfo.osName#</code></dd>
        </dl>
    </div>
    </cfoutput>

    <h2>Environment Comparison</h2>
    <table>
        <thead>
            <tr>
                <th>Setting</th>
                <th>Base</th>
                <th>Dev</th>
                <th>Staging</th>
                <th>Prod</th>
            </tr>
        </thead>
        <tbody>
            <tr>
                <td><strong>Port</strong></td>
                <td>8890</td>
                <td>8891</td>
                <td>8892</td>
                <td>8080</td>
            </tr>
            <tr>
                <td><strong>Max Memory</strong></td>
                <td>512m</td>
                <td>512m (inherited)</td>
                <td>1024m</td>
                <td>2048m</td>
            </tr>
            <tr>
                <td><strong>Monitoring</strong></td>
                <td>Enabled (9001)</td>
                <td>Enabled (9002)</td>
                <td>Enabled (9001)</td>
                <td>Disabled</td>
            </tr>
            <tr>
                <td><strong>Admin Password</strong></td>
                <td>None</td>
                <td>None</td>
                <td>Secured</td>
                <td>Secured</td>
            </tr>
            <tr>
                <td><strong>Open Browser</strong></td>
                <td>Yes</td>
                <td>Yes</td>
                <td>Yes</td>
                <td>No</td>
            </tr>
        </tbody>
    </table>

    <h2>Environment Commands</h2>
    <pre>cd examples/server-environments

# Start with different environments
lucli server start --env dev
lucli server start --env staging
lucli server start --env prod

# Preview configuration without starting
lucli server start --env prod --dry-run

# Check which environment is running
lucli server list
lucli server status

# Stop server
lucli server stop</pre>

    <h2>Deep Merge Behavior</h2>
    <p>When you specify an environment, LuCLI merges configurations in this order:</p>
    <ol>
        <li>LuCLI defaults (built-in)</li>
        <li>Base configuration (from lucee.json)</li>
        <li>Environment overrides (from environments.{env})</li>
    </ol>
    <p><strong>Example:</strong> The <code>prod</code> environment overrides <code>jvm.maxMemory</code> to 2048m, but <code>jvm.minMemory</code> remains 128m from the base config.</p>

    <h2>Use Cases</h2>
    <ul>
        <li><strong>Development:</strong> High logging, monitoring enabled, auto-open browser</li>
        <li><strong>Staging:</strong> Moderate resources, secured admin, for integration tests</li>
        <li><strong>Production:</strong> Maximum memory, monitoring disabled, secured</li>
    </ul>

    <h2>Next Steps</h2>
    <ul>
        <li>Try switching environments with <code>lucli server stop</code> and <code>lucli server start --env {env}</code></li>
        <li>Try the <strong>server-external-config</strong> example for CFConfig integration</li>
        <li>Try the <strong>dependency-environments</strong> example for environment-specific dependencies</li>
    </ul>

    <hr style="margin: 40px 0;">
    <p><small>Part of <a href="https://github.com/Ortus-Solutions/lucee-cli">LuCLI Examples</a></small></p>
</body>
</html>
