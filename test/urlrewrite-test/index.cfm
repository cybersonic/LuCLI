<!DOCTYPE html>
<html>
<head>
    <title>Framework-Style Routing Test</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            max-width: 900px;
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
            color: #2c3e50;
            border-bottom: 3px solid #3498db;
            padding-bottom: 10px;
        }
        h2 {
            color: #34495e;
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
        .info {
            background: #e7f3ff;
            border: 1px solid #b3d9ff;
            padding: 15px;
            border-radius: 4px;
            margin: 20px 0;
            font-family: monospace;
            font-size: 14px;
        }
        .info strong {
            color: #0056b3;
            display: inline-block;
            min-width: 150px;
        }
        .test-links {
            margin: 30px 0;
        }
        .test-links a {
            display: block;
            padding: 12px;
            margin: 10px 0;
            background: #3498db;
            color: white;
            text-decoration: none;
            border-radius: 4px;
            transition: background 0.3s;
        }
        .test-links a:hover {
            background: #2980b9;
        }
        .route-info {
            background: #fff3cd;
            border: 1px solid #ffeaa7;
            padding: 15px;
            border-radius: 4px;
            margin: 20px 0;
        }
        .route-info h3 {
            margin-top: 0;
            color: #856404;
        }
        code {
            background: #f8f9fa;
            padding: 2px 6px;
            border-radius: 3px;
            color: #e83e8c;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>🚀 Framework-Style Routing Test</h1>
        
        <cfscript>
            // Parse the PATH_INFO to determine the route
            pathInfo = cgi.path_info;
            
            // Remove leading slash if present
            if (left(pathInfo, 1) == "/") {
                pathInfo = right(pathInfo, len(pathInfo) - 1);
            }
            
            // Default to "home" if path is empty
            if (len(pathInfo) == 0) {
                route = "home";
            } else {
                route = pathInfo;
            }
            
            // Parse route segments
            segments = listToArray(route, "/");
            action = arrayLen(segments) > 0 ? segments[1] : "home";
        </cfscript>
        
        <div class="success">
            <strong>✓ Router Active!</strong> This page is handling all routes through PATH_INFO parsing.
        </div>
        
        <div class="route-info">
            <h3>🔀 Current Route Information</h3>
            <cfoutput>
                <strong>Requested Route:</strong> <code>/#route#</code><br>
                <strong>Action:</strong> <code>#action#</code><br>
                <cfif arrayLen(segments) gt 1>
                    <strong>Parameters:</strong> <code>#arrayToList(segments.slice(2), "/")#</code><br>
                </cfif>
            </cfoutput>
        </div>
        
        <div class="info">
            <strong>Request URI:</strong> <cfoutput>#cgi.request_url#</cfoutput><br>
            <strong>Script Name:</strong> <cfoutput>#cgi.script_name#</cfoutput><br>
            <strong>PATH_INFO:</strong> <cfoutput>#cgi.path_info#</cfoutput><br>
            <strong>Query String:</strong> <cfoutput>#cgi.query_string#</cfoutput><br>
            <strong>Timestamp:</strong> <cfoutput>#now()#</cfoutput>
        </div>
        
        <cfswitch expression="#action#">
            <cfcase value="home">
                <h2>🏠 Home</h2>
                <p>Welcome to the framework-style routing test. This demonstrates how URLs are routed through <code>index.cfm</code> with PATH_INFO.</p>
                
                <h3>How It Works</h3>
                <ul>
                    <li><strong>URL:</strong> <code>/hello</code> → <strong>Routes to:</strong> <code>/index.cfm/hello</code></li>
                    <li><strong>PATH_INFO:</strong> Set to <code>/hello</code></li>
                    <li><strong>Router:</strong> Parses PATH_INFO to determine action</li>
                    <li><strong>Response:</strong> Renders appropriate content based on action</li>
                </ul>
            </cfcase>
            
            <cfcase value="hello">
                <h2>👋 Hello Route</h2>
                <p>You've successfully accessed the <strong>/hello</strong> route!</p>
                <p>This content is served by the same <code>index.cfm</code> file, but the router parsed the PATH_INFO and rendered different content.</p>
            </cfcase>
            
            <cfcase value="about">
                <h2>ℹ️ About Route</h2>
                <p>This is the <strong>/about</strong> route, demonstrating framework-style routing.</p>
                <p>Unlike the old extension-less URL approach, this method allows:</p>
                <ul>
                    <li>Complex URL patterns like <code>/users/123/edit</code></li>
                    <li>RESTful API endpoints</li>
                    <li>Clean separation between routing and controller logic</li>
                    <li>Better compatibility with modern CFML frameworks</li>
                </ul>
            </cfcase>
            
            <cfcase value="api">
                <cfheader name="Content-Type" value="application/json">
                <cfscript>
                    // Parse API path
                    resource = arrayLen(segments) > 1 ? segments[2] : "info";
                    resourceId = arrayLen(segments) > 2 ? segments[3] : "";
                    
                    response = {
                        "success": true,
                        "route": route,
                        "resource": resource,
                        "id": resourceId,
                        "method": cgi.request_method,
                        "timestamp": now().getTime()
                    };
                    
                    writeOutput(serializeJSON(response));
                    abort;
                </cfscript>
            </cfcase>
            
            <cfdefaultcase>
                <h2>🔍 Custom Route: <cfoutput>#action#</cfoutput></h2>
                <p>This is a dynamic route handler. Any URL pattern is captured and can be processed.</p>
                <div class="info">
                    <strong>Full Path:</strong> <cfoutput>#route#</cfoutput><br>
                    <cfif arrayLen(segments) gt 1>
                        <strong>Segments:</strong><br>
                        <cfloop array="#segments#" index="i" item="segment">
                            <cfoutput>&nbsp;&nbsp;[#i#] #segment#<br></cfoutput>
                        </cfloop>
                    </cfif>
                </div>
            </cfdefaultcase>
        </cfswitch>
        
        <h2>🧪 Test Routes</h2>
        <div class="test-links">
            <a href="/">Home (Root)</a>
            <a href="/hello">Hello Route</a>
            <a href="/about">About Route</a>
            <a href="/api/users">API: Users List</a>
            <a href="/api/users/123">API: User #123</a>
            <a href="/products/laptop/dell">Multi-Segment Route</a>
            <a href="/test.cfm">Direct .cfm Access (Bypasses Router)</a>
        </div>
        
        <h2>📚 Framework Compatibility</h2>
        <p>This routing style is compatible with popular CFML frameworks:</p>
        <ul>
            <li><strong>ColdBox:</strong> Uses <code>index.cfm</code> as the front controller</li>
            <li><strong>FW/1:</strong> Routes through <code>index.cfm</code> with action parsing</li>
            <li><strong>CFWheels:</strong> Uses <code>index.cfm</code> for all route handling</li>
            <li><strong>ContentBox:</strong> Modern CMS routing through front controller</li>
        </ul>
        
        <h2>✨ Key Features</h2>
        <ul>
            <li>✓ All routes go through <code>index.cfm</code> (configurable via <code>lucee.json</code>)</li>
            <li>✓ PATH_INFO contains the full requested path</li>
            <li>✓ Static resources (CSS, JS, images) are excluded</li>
            <li>✓ Direct <code>.cfm</code> access still works</li>
            <li>✓ Lucee admin paths are protected</li>
            <li>✓ RESTful URL patterns supported</li>
            <li>✓ Query strings are preserved</li>
        </ul>
        
        <h2>⚙️ Configuration</h2>
        <p>Configure URL rewriting in your <code>lucee.json</code>:</p>
        <pre style="background: #f8f9fa; padding: 15px; border-radius: 4px; overflow-x: auto;">
{
  "name": "my-app",
  "version": "6.2.2.91",
  "port": 8080,
  "urlRewrite": {
    "enabled": true,
    "routerFile": "index.cfm"
  }
}</pre>
    </div>
</body>
</html>