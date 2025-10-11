<cfscript>
    // Simple API endpoint test for URL rewriting
    
    // Set content type to JSON
    cfheader(name="Content-Type", value="application/json");
    
    // Create response data
    response = {
        "success": true,
        "message": "API endpoint accessed successfully",
        "timestamp": now(),
        "request_info": {
            "url": cgi.request_url,
            "script_name": cgi.script_name,
            "path_info": cgi.path_info,
            "query_string": cgi.query_string
        },
        "data": {
            "id": 123,
            "name": "Test Item",
            "description": "This is a test API response"
        },
        "notes": [
            "This endpoint can be accessed as /api/data (extension-less)",
            "Or as /api/data.cfm (with extension)",
            "Both should work if URL rewriting is configured correctly"
        ]
    };
    
    // Output JSON
    writeOutput(serializeJSON(response));
</cfscript>