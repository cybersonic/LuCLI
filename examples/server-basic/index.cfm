<cfscript>
/**
 * Basic Server Configuration Example
 * 
 * This page shows basic server information
 */

serverInfo = {
    name: "server-basic-example",
    luceeVersion: server.lucee.version,
    currentDir: getCurrentTemplatePath(),
    webroot: expandPath("/"),
    javaVersion: server.java.version,
    osName: server.os.name
};
</cfscript>
<!DOCTYPE html>
<html>
<head>
    <title>LuCLI Server - Basic Example</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            max-width: 800px;
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
    </style>
</head>
<body>
    <h1>🚀 LuCLI Server Management</h1>
    <p><strong>Example:</strong> Basic Server Configuration</p>

    <div class="success">
        ✅ <strong>Success!</strong> Your LuCLI server is running.
    </div>

    <h2>Server Information</h2>
    <div class="info-box">
        <dl>
            <dt>Server Name</dt>
            <dd><code><cfoutput>#serverInfo.name#</cfoutput></code></dd>

            <dt>Lucee Version</dt>
            <dd><code><cfoutput>#serverInfo.luceeVersion#</cfoutput></code></dd>

            <dt>Java Version</dt>
            <dd><code><cfoutput>#serverInfo.javaVersion#</cfoutput></code></dd>

            <dt>Operating System</dt>
            <dd><code><cfoutput>#serverInfo.osName#</cfoutput></code></dd>

            <dt>Web Root</dt>
            <dd><code><cfoutput>#serverInfo.webroot#</cfoutput></code></dd>

            <dt>Current Template</dt>
            <dd><code><cfoutput>#serverInfo.currentDir#</cfoutput></code></dd>
        </dl>
    </div>

    <h2>Configuration Used</h2>
    <p>This server is running with minimal configuration:</p>
    <pre>{
  "name": "server-basic-example",
  "port": 8888
}</pre>
    <p>All other settings use LuCLI defaults.</p>

    <h2>What You Can Do</h2>
    <ul>
        <li><a href="/lucee/admin/server.cfm">Open Lucee Server Admin</a></li>
        <li>View server status: <code>lucli server status</code></li>
        <li>Stop the server: <code>lucli server stop</code></li>
        <li>View full config: <code>lucli server start --dry-run</code></li>
    </ul>

    <h2>Server Commands</h2>
    <pre>cd examples/server-basic

# Start server
lucli server start

# Check status
lucli server status

# View all servers
lucli server list

# Stop server  
lucli server stop


</pre>

    <h2>Next Steps</h2>
    <ul>
        <li>Try the <strong>server-https</strong> example for HTTPS configuration</li>
        <li>Try the <strong>server-environments</strong> example for multi-environment setup</li>
        <li>Try the <strong>dependency-basic</strong> example to install CFML dependencies</li>
    </ul>

    <hr style="margin: 40px 0;">
    <p><small>Part of <a href="https://github.com/cybersonic/lucli">LuCLI Examples</a></small></p>
</body>
</html>
