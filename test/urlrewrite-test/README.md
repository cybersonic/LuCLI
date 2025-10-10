# Framework-Style URL Routing Test Suite

This directory contains a comprehensive test suite for validating the framework-style URL routing functionality in LuCLI's server implementation.

## Overview

The URL routing functionality enables modern CFML framework-style routing where URLs are routed through a central router file (default: `index.cfm`). The router can parse `PATH_INFO` to determine which controller/view to load.

**Example:** `/hello` → `/index.cfm/hello` (with `PATH_INFO` set to `/hello`)

This approach is compatible with popular CFML frameworks like ColdBox, FW/1, CFWheels, and ContentBox.

## Key Features

- ✓ **Framework-Style Routing**: All requests route through a configurable router file (default: `index.cfm`)
- ✓ **PATH_INFO Support**: Full path information available for application routing logic
- ✓ **Static Resource Exclusions**: Images, CSS, JS, fonts automatically excluded
- ✓ **Lucee Admin Protection**: `/lucee/*` paths are protected
- ✓ **Direct .cfm Access**: Traditional `.cfm` URLs still work
- ✓ **RESTful URLs**: Support for patterns like `/api/users/123`
- ✓ **Query String Preservation**: Query parameters are maintained
- ✓ **Configurable**: Enable/disable and customize router file via `lucee.json`

## Components Being Tested

1. **UrlRewriteFilter JAR**: The urlrewritefilter-4.0.4.jar from tuckey.org
2. **Configuration Files**:
   - `urlrewrite.xml`: Framework-style routing rules with placeholder support
   - `web.xml`: UrlRewriteFilter servlet filter configuration
3. **Server Configuration**: `lucee.json` with URL rewrite settings
4. **Deployment Process**: Verification that files are correctly deployed
5. **Routing Functionality**: Testing that framework-style routing works correctly

## Configuration

Configure URL rewriting in your `lucee.json`:

```json
{
  "name": "my-app",
  "version": "6.2.2.91",
  "port": 8080,
  "urlRewrite": {
    "enabled": true,
    "routerFile": "index.cfm"
  }
}
```

### Configuration Options

- **`enabled`** (boolean, default: `true`): Enable or disable URL rewriting
- **`routerFile`** (string, default: `"index.cfm"`): The file that handles all routes

## Test Files

### CFML Test Pages

- **`index.cfm`**: Router file that parses PATH_INFO and handles all routes
  - Demonstrates route parsing and action dispatching
  - Includes examples for `/hello`, `/about`, `/api/*` routes
  - Shows how to access PATH_INFO and route parameters
  
- **`test.cfm`**: Direct access test file (bypasses router)
- **`about.cfm`**: Direct access test file (bypasses router)
- **`api/data.cfm`**: Direct API endpoint (bypasses router)

### Test Scripts

- **`test-urlrewrite.sh`**: Automated test script (recommended)
- **`verify-deployment.sh`**: Standalone verification script for checking deployed files

## Quick Start - Automated Testing

The easiest way to test is using the automated test script:

```bash
# From the LuCLI project root directory
./test/urlrewrite-test/test-urlrewrite.sh
```

This script will:
1. Build LuCLI
2. Start a test server on port 8888
3. Verify deployment files are in place
4. Test all URL patterns and routing
5. Verify PATH_INFO is set correctly
6. Check that static resources are excluded
7. Display a comprehensive summary

### Expected Output

```
========================================
   LuCLI Framework Routing Test Suite
========================================

>>> Step 1: Building LuCLI
✓ LuCLI built successfully

>>> Step 2: Checking Server Status

>>> Step 3: Starting Test Server
✓ Server start command executed

>>> Waiting for server to be ready...
✓ Server is ready and responding

>>> Verifying Deployment Files
✓ UrlRewriteFilter JAR found in WEB-INF/lib/
✓ urlrewrite.xml found in WEB-INF/
✓ Framework-style routing rules detected
✓ UrlRewriteFilter configuration found in web.xml

>>> Step 4: Testing Framework-Style Routing
Testing: Root URL (/) - Router home action ... ✓ OK (HTTP 200, content verified)
Testing: Hello route (/hello) - Framework routing ... ✓ OK (HTTP 200, content verified)
Testing: About route (/about) - Framework routing ... ✓ OK (HTTP 200, content verified)
Testing: API route (/api/users) - JSON response ... ✓ OK (HTTP 200)
Testing: API route with ID (/api/users/123) - RESTful ... ✓ OK (HTTP 200)
Testing: Multi-segment route (/products/laptop/dell) ... ✓ OK (HTTP 200)
Testing: Direct .cfm access (/test.cfm) - Bypasses router ... ✓ OK (HTTP 200, content verified)

>>> Test Summary
Tests Passed: 7
Tests Failed: 0

========================================
  Framework Routing is working!
========================================

✓ URLs are routed through index.cfm
✓ PATH_INFO is set correctly
✓ Router can parse and handle routes
✓ Static resources are excluded
✓ Direct .cfm access still works
```

## Manual Testing

If you prefer to test manually:

### 1. Build LuCLI

```bash
./build.sh
```

### 2. Start the Test Server

```bash
./lucli server start \
    --name urlrewrite-test \
    --port 8888 \
    --webroot test/urlrewrite-test \
    --open false
```

### 3. Test URLs in Browser

#### Framework-Routed URLs (go through index.cfm)
- http://localhost:8888/ (home route)
- http://localhost:8888/hello (hello route)
- http://localhost:8888/about (about route)
- http://localhost:8888/api/users (API list)
- http://localhost:8888/api/users/123 (API with ID)
- http://localhost:8888/products/laptop/dell (multi-segment)

#### Direct .cfm Access (bypass router)
- http://localhost:8888/test.cfm
- http://localhost:8888/about.cfm
- http://localhost:8888/api/data.cfm

### 4. Test with curl

```bash
# Test framework routing
curl -v http://localhost:8888/hello

# Test API endpoint
curl http://localhost:8888/api/users/123

# Test direct access
curl http://localhost:8888/test.cfm

# Test with query string
curl http://localhost:8888/hello?name=World
```

### 5. Stop the Server

```bash
./lucli server stop --name urlrewrite-test
```

## How Framework Routing Works

### URL Flow

```
Browser Request: /hello
       ↓
UrlRewriteFilter (checks rules)
       ↓
Routes to: /index.cfm/hello
       ↓
index.cfm receives:
  - cgi.script_name = "/index.cfm"
  - cgi.path_info = "/hello"
       ↓
index.cfm parses PATH_INFO
       ↓
Dispatches to "hello" action
       ↓
Renders appropriate content
```

### Routing Rules

The `urlrewrite.xml` implements these rules:

1. **Exclude static resources**: `/images/*`, `/css/*`, `/js/*`, etc.
2. **Exclude static extensions**: `.css`, `.js`, `.png`, `.jpg`, etc.
3. **Exclude Lucee admin**: `/lucee/*`
4. **Exclude REST paths**: `/rest/*`
5. **Exclude existing .cfm/.cfc files**: Direct access bypasses router
6. **Exclude real files/directories**: File system check
7. **Route everything else**: Through `/${routerFile}/$1`

### Router Implementation

The `index.cfm` router demonstrates:

```cfml
<cfscript>
    // Get the requested path from PATH_INFO
    pathInfo = cgi.path_info;
    
    // Remove leading slash
    if (left(pathInfo, 1) == "/") {
        pathInfo = right(pathInfo, len(pathInfo) - 1);
    }
    
    // Parse route segments
    segments = listToArray(pathInfo, "/");
    action = segments[1];
    
    // Dispatch based on action
    switch(action) {
        case "hello":
            // Handle hello route
            break;
        case "api":
            // Handle API routes
            resource = segments[2];
            id = segments[3];
            break;
        default:
            // 404 or default handler
    }
</cfscript>
```

## Expected Behavior

### ✓ Success Criteria

1. **Server starts without errors**
2. **Deployment files are present**
   - urlrewritefilter JAR in WEB-INF/lib/
   - urlrewrite.xml in WEB-INF/ with framework rules
   - UrlRewriteFilter configured in web.xml
3. **Framework routing works**
   - `/hello` is routed through index.cfm
   - PATH_INFO is set to `/hello`
   - Router can parse and dispatch routes
4. **Static resources are excluded**
   - CSS/JS/images are served directly
5. **Direct .cfm access works**
   - `/test.cfm` bypasses the router
6. **RESTful patterns work**
   - `/api/users/123` routes correctly
7. **Query strings preserved**
   - `/hello?name=World` maintains parameters

## Troubleshooting

### Extension-less URLs return 404

1. Verify urlrewrite.xml has framework-style rules
2. Check for "Framework Router" in urlrewrite.xml
3. Verify `${routerFile}` placeholder is replaced with actual filename
4. Check server logs for UrlRewriteFilter errors

### Router receives empty PATH_INFO

1. Verify UrlRewriteFilter is using `type="forward"`
2. Check that rules use forward, not redirect
3. Verify filter is mapped to `/*`

### Static resources go through router

1. Check exclusion conditions in urlrewrite.xml
2. Verify static file extensions are listed
3. Add additional exclusions if needed

### Disable URL Rewriting

To disable URL rewriting, update `lucee.json`:

```json
{
  "urlRewrite": {
    "enabled": false
  }
}
```

## Framework Integration Examples

### ColdBox

```cfml
// index.cfm router integrates with ColdBox
processCommand("index.cfm")
    .withEvent(pathInfo)
    .execute();
```

### FW/1

```cfml
// index.cfm parses PATH_INFO for FW/1
application.framework.action = listFirst(pathInfo, "/");
```

### Custom Router

```cfml
// Build your own routing logic
component Router {
    function route(path) {
        var segments = listToArray(path, "/");
        var controller = segments[1];
        var action = arrayLen(segments) > 1 ? segments[2] : "index";
        
        invoke("controllers.#controller#", action);
    }
}
```

## Clean Up

```bash
# Stop the server
./lucli server stop --name urlrewrite-test

# Optional: Remove server instance
rm -rf ~/.lucli/servers/urlrewrite-test
```

## CI/CD Integration

```bash
# Run tests in CI/CD pipeline
./test/urlrewrite-test/test-urlrewrite.sh

# Script exits with 0 on success, 1 on failure
if [ $? -eq 0 ]; then
    echo "Framework routing tests passed"
else
    echo "Framework routing tests failed"
    exit 1
fi
```

## Support

If you encounter issues:

1. Run the automated test script first
2. Check server logs: `./lucli server logs --name urlrewrite-test`
3. Verify deployment files: `./test/urlrewrite-test/verify-deployment.sh`
4. Test manually to isolate the issue
5. Check that `lucee.json` has correct configuration

## License

This test suite is part of the LuCLI project and follows the same license.