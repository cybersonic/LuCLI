<cfscript>
    // Simple router for testing URL rewrite functionality
    pathInfo = cgi.path_info ?: "";
    
    // Remove leading slash if present
    if (left(pathInfo, 1) == "/") {
        pathInfo = right(pathInfo, len(pathInfo) - 1);
    }
    
    // Default to "home" if path is empty
    route = len(pathInfo) > 0 ? pathInfo : "home";
    
    // Set content type to JSON for API routes
    if (left(route, 4) == "api/") {
        cfheader(name="Content-Type", value="application/json");
        response = {
            "success": true,
            "route": route,
            "pathInfo": pathInfo,
            "message": "URL rewrite working",
            "timestamp": now()
        };
        writeOutput(serializeJSON(response));
        return;
    }
</cfscript>
<!DOCTYPE html>
<html>
<head>
    <title>URL Rewrite Test</title>
</head>
<body>
    <h1>URL Rewrite Test Page</h1>
    <cfoutput>
        <p><strong>Status:</strong> URL rewrite is working!</p>
        <p><strong>Route:</strong> #route#</p>
        <p><strong>PATH_INFO:</strong> #pathInfo#</p>
        <p><strong>Request URI:</strong> #cgi.request_uri#</p>
        <p><strong>Timestamp:</strong> #now()#</p>
    </cfoutput>
    
    <cfswitch expression="#route#">
        <cfcase value="home">
            <h2>Home Route</h2>
            <p>Welcome to the URL rewrite test home page.</p>
        </cfcase>
        <cfcase value="hello">
            <h2>Hello Route</h2>
            <p>Hello from the framework router!</p>
        </cfcase>
        <cfcase value="about">
            <h2>About Route</h2>
            <p>This is the about page via URL rewriting.</p>
        </cfcase>
        <cfdefaultcase>
            <h2>Dynamic Route: <cfoutput>#route#</cfoutput></h2>
            <p>This is a dynamically handled route.</p>
        </cfdefaultcase>
    </cfswitch>
</body>
</html>
