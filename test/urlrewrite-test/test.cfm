<!DOCTYPE html>
<html>
<head>
    <title>URL Rewrite Test - Test Page</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            max-width: 800px;
            margin: 50px auto;
            padding: 20px;
            background-color: #f5f5f5;
        }
        .container {
            background: white;
            padding: 30px;
            border-radius: 8px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        h1 {
            color: #27ae60;
            border-bottom: 3px solid #2ecc71;
            padding-bottom: 10px;
        }
        .success {
            background: #d4edda;
            border: 1px solid #c3e6cb;
            color: #155724;
            padding: 15px;
            border-radius: 4px;
            margin: 20px 0;
        }
        .info {
            background: #e7f3ff;
            border: 1px solid #b3d9ff;
            padding: 15px;
            border-radius: 4px;
            margin: 20px 0;
        }
        .info strong {
            color: #0056b3;
        }
        .back-link {
            display: inline-block;
            margin-top: 20px;
            padding: 10px 20px;
            background: #3498db;
            color: white;
            text-decoration: none;
            border-radius: 4px;
        }
        .back-link:hover {
            background: #2980b9;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>✓ Test Page - URL Rewrite Working!</h1>
        
        <div class="success">
            <strong>Success!</strong> This test page was loaded successfully through URL rewriting.
        </div>
        
        <div class="info">
            <strong>How was this accessed?</strong><br>
            <strong>Request URL:</strong> <cfoutput>#cgi.request_url#</cfoutput><br>
            <strong>Script Name:</strong> <cfoutput>#cgi.script_name#</cfoutput><br>
            <strong>Path Info:</strong> <cfoutput>#cgi.path_info#</cfoutput><br>
            <strong>Query String:</strong> <cfoutput>#cgi.query_string#</cfoutput><br>
            <strong>Timestamp:</strong> <cfoutput>#now()#</cfoutput>
        </div>
        
        <h2>What Just Happened?</h2>
        <p>If you accessed this page via <code>/test</code> (without .cfm), then the URL rewrite filter successfully:</p>
        <ol>
            <li>Intercepted the request for <code>/test</code></li>
            <li>Matched it against the urlrewrite.xml rules</li>
            <li>Forwarded the request to <code>/test.cfm</code></li>
            <li>Served this page without changing the URL in your browser</li>
        </ol>
        
        <p>If you accessed via <code>/test.cfm</code>, then Lucee served the file directly without URL rewriting.</p>
        
        <a href="/" class="back-link">← Back to Home</a>
    </div>
</body>
</html>