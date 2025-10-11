<!DOCTYPE html>
<html>
<head>
    <title>URL Rewrite Test - About</title>
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
            color: #8e44ad;
            border-bottom: 3px solid #9b59b6;
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
            background: #fff3cd;
            border: 1px solid #ffeaa7;
            padding: 15px;
            border-radius: 4px;
            margin: 20px 0;
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
        <h1>📖 About URL Rewrite Testing</h1>
        
        <div class="success">
            <strong>About page loaded successfully!</strong>
        </div>
        
        <div class="info">
            <strong>Request Details:</strong><br>
            <strong>URL:</strong> <cfoutput>#cgi.request_url#</cfoutput><br>
            <strong>Script:</strong> <cfoutput>#cgi.script_name#</cfoutput><br>
            <strong>Time:</strong> <cfoutput>#now()#</cfoutput>
        </div>
        
        <h2>About This Test Suite</h2>
        <p>This test suite validates the URL rewrite functionality in LuCLI's server implementation.</p>
        
        <h3>Components Being Tested:</h3>
        <ul>
            <li><strong>UrlRewriteFilter JAR:</strong> tuckey.org's urlrewritefilter-4.0.4.jar</li>
            <li><strong>Configuration:</strong> urlrewrite.xml with extension-less URL rules</li>
            <li><strong>Web Container:</strong> Tomcat with Lucee CFML engine</li>
            <li><strong>Filter Integration:</strong> web.xml filter configuration</li>
        </ul>
        
        <h3>Expected Behavior:</h3>
        <ul>
            <li>URLs without extensions (e.g., /about) should work</li>
            <li>URLs with extensions (e.g., /about.cfm) should still work</li>
            <li>Browser URL should remain clean (no extension shown)</li>
            <li>No errors in server logs</li>
        </ul>
        
        <a href="/" class="back-link">← Back to Home</a>
    </div>
</body>
</html>