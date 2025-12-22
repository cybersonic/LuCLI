<cfscript>
/**
 * HTTPS Server Configuration Example
 * 
 * This page shows connection and SSL information
 */

isSecure = cgi.server_port_secure == 1 || cgi.https == "on";
protocol = isSecure ? "HTTPS" : "HTTP";

serverInfo = {
    protocol: protocol,
    port: cgi.server_port,
    serverName: cgi.server_name,
    isSecure: isSecure,
    luceeVersion: server.lucee.version,
    javaVersion: server.java.version
};

// SSL/TLS info (only available on HTTPS)
if (isSecure) {
    serverInfo.sslProtocol = cgi.server_protocol ?: "Unknown";
}
</cfscript>
<!DOCTYPE html>
<html>
<head>
    <title>LuCLI Server - HTTPS Example</title>
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
        .secure-badge {
            display: inline-block;
            padding: 4px 12px;
            border-radius: 4px;
            font-weight: bold;
            font-size: 0.9em;
            background: #d4edda;
            color: #155724;
            border: 1px solid #c3e6cb;
        }
        .insecure-badge {
            display: inline-block;
            padding: 4px 12px;
            border-radius: 4px;
            font-weight: bold;
            font-size: 0.9em;
            background: #fff3cd;
            color: #856404;
            border: 1px solid #ffeaa7;
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
        .warning {
            background: #fff3cd;
            border: 1px solid #ffeaa7;
            color: #856404;
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
    <h1>🔒 LuCLI HTTPS Configuration</h1>
    <p><strong>Example:</strong> HTTPS/SSL Server Configuration</p>

    <cfoutput>
    <div class="#serverInfo.isSecure ? 'success' : 'warning'#">
        <cfif serverInfo.isSecure>
            🔒 <strong>Secure Connection!</strong> You are connected via 
            <span class="secure-badge">HTTPS</span>
        <cfelse>
            ⚠️ <strong>Insecure Connection</strong> You are connected via 
            <span class="insecure-badge">HTTP</span>
        </cfif>
    </div>

    <h2>Connection Information</h2>
    <div class="info-box">
        <dl>
            <dt>Protocol</dt>
            <dd><code>#serverInfo.protocol#</code></dd>

            <dt>Port</dt>
            <dd><code>#serverInfo.port#</code></dd>

            <dt>Server Name</dt>
            <dd><code>#serverInfo.serverName#</code></dd>

            <cfif serverInfo.isSecure>
            <dt>SSL/TLS Protocol</dt>
            <dd><code>#serverInfo.sslProtocol#</code></dd>
            </cfif>

            <dt>Lucee Version</dt>
            <dd><code>#serverInfo.luceeVersion#</code></dd>

            <dt>Java Version</dt>
            <dd><code>#serverInfo.javaVersion#</code></dd>
        </dl>
    </div>
    </cfoutput>

    <h2>Access URLs</h2>
    <p>This server is configured to run on both HTTP and HTTPS:</p>
    <ul>
        <li><strong>HTTP:</strong> <a href="http://localhost:8893">http://localhost:8893</a></li>
        <li><strong>HTTPS:</strong> <a href="https://localhost:8843">https://localhost:8843</a></li>
    </ul>

    <h2>Configuration</h2>
    <p>The server is configured with the following HTTPS settings:</p>
    <pre>{
  "name": "server-https-example",
  "port": 8893,
  "https": {
    "enabled": true,
    "port": 8843,
    "keystore": "./ssl/keystore.jks",
    "keystorePassword": "changeit",
    "keyPassword": "changeit"
  }
}</pre>

    <h2>Certificate Information</h2>
    <cfif serverInfo.isSecure>
        <div class="success">
            ✅ You are viewing this page over HTTPS with a self-signed certificate.
        </div>
        <p>For development, this example uses a self-signed certificate generated with the <code>generate-cert.sh</code> script.</p>
    <cfelse>
        <div class="warning">
            ⚠️ You are viewing this page over HTTP. Try accessing via HTTPS at <a href="https://localhost:8843">https://localhost:8843</a>
        </div>
    </cfif>

    <h2>Self-Signed Certificate Warning</h2>
    <p>When accessing via HTTPS, your browser will show a security warning because this example uses a self-signed certificate. This is normal for development.</p>
    <p><strong>To proceed:</strong></p>
    <ol>
        <li>Click "Advanced" in the browser warning</li>
        <li>Click "Proceed to localhost" or similar option</li>
    </ol>
    <p><strong>Production:</strong> Use certificates from a trusted Certificate Authority (e.g., Let's Encrypt, DigiCert, etc.)</p>

    <h2>Server Commands</h2>
    <pre>cd examples/server-https


# Start server
lucli server start

# Check status
lucli server status

# Stop server
lucli server stop</pre>

    <h2>Security Best Practices</h2>
    <ul>
        <li><strong>Development:</strong> Self-signed certificates are fine</li>
        <li><strong>Production:</strong> Always use certificates from trusted CAs</li>
        <li>Store passwords securely (not in version control)</li>
        <li>Consider enabling HSTS for production</li>
        <li>Keep certificates up to date and renew before expiration</li>
    </ul>

    <h2>Next Steps</h2>
    <ul>
        <li>Try switching between HTTP and HTTPS URLs</li>
        <li>Examine the certificate details in your browser</li>
        <li>Try the <strong>server-environments</strong> example for environment-specific configs</li>
        <li>Try the <strong>server-external-config</strong> example for advanced configuration</li>
    </ul>

