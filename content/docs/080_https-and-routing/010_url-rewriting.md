---
title: URL Rewriting
layout: docs
---


LuCLI provides powerful URL rewriting capabilities that enable modern CFML applications to use clean, SEO-friendly URLs and framework-style routing patterns. This documentation covers everything you need to know about implementing URL rewriting in your projects.

## Overview

LuCLI's URL rewriting functionality provides two main routing approaches:

### 🚀 Framework-Style Routing
Routes all requests through a central router file (typically `index.cfm`) with `PATH_INFO` parsing:
- **URL**: `/hello` → **Routed to**: `/index.cfm/hello` 
- **PATH_INFO**: `/hello`
- **Use case**: Modern CFML frameworks (ColdBox, FW/1, CFWheels, ContentBox)

### 🔗 Traditional Extension-less URLs  
Direct mapping without extensions:
- **URL**: `/about` → **Routed to**: `/about.cfm`
- **Use case**: Simple websites, legacy applications

## Quick Start

### 1. Enable URL Rewriting in Your Project

Create or update your `lucee.json` file in your project root:

```json
{
  "name": "my-awesome-app",
  "lucee": {
    "version": "6.2.2.91"
  },
  "port": 8080,
  "urlRewrite": {
    "enabled": true,
    "routerFile": "index.cfm"
  }
}
```

### 2. Create Your Router File

Create `index.cfm` in your project root:

```cfml
<cfscript>
// Parse the PATH_INFO to determine the route
pathInfo = cgi.path_info ?: "";

// Remove leading slash if present
if (left(pathInfo, 1) == "/") {
    pathInfo = right(pathInfo, len(pathInfo) - 1);
}

// Default to "home" if path is empty
route = len(pathInfo) > 0 ? pathInfo : "home";

// Parse route segments for complex routing
segments = listToArray(route, "/");
action = arrayLen(segments) > 0 ? segments[1] : "home";
</cfscript>

<!DOCTYPE html>
<html>
<head>
    <title>My App - <cfoutput>#action#</cfoutput></title>
</head>
<body>
    <h1>Welcome to My App</h1>
    
    <cfswitch expression="#action#">
        <cfcase value="home">
            <h2>Home Page</h2>
            <p>Welcome to our homepage!</p>
        </cfcase>
        
        <cfcase value="about">
            <h2>About Us</h2>
            <p>Learn more about our company.</p>
        </cfcase>
        
        <cfcase value="contact">
            <h2>Contact Us</h2>
            <p>Get in touch with our team.</p>
        </cfcase>
        
        <cfdefaultcase>
            <h2>Page Not Found</h2>
            <p>The requested page "<cfoutput>#action#</cfoutput>" was not found.</p>
        </cfdefaultcase>
    </cfswitch>
</body>
</html>
```

### 3. Start Your Server

```bash
lucli server start
```

### 4. Test Your Routes

- **http://localhost:8080/** → Home page
- **http://localhost:8080/about** → About page  
- **http://localhost:8080/contact** → Contact page

## Configuration

### Basic Configuration Options

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `urlRewrite.enabled` | `boolean` | `false` | Enable/disable URL rewriting |
| `urlRewrite.routerFile` | `string` | `"index.cfm"` | Central router file for framework-style routing |

### Example Configurations

#### Framework-Style Routing (Recommended)
```json
{
  "name": "modern-app",
  "port": 8080,
  "urlRewrite": {
    "enabled": true,
    "routerFile": "index.cfm"
  }
}
```

#### Custom Router File
```json
{
  "name": "custom-app",
  "port": 8080,
  "urlRewrite": {
    "enabled": true,
    "routerFile": "app.cfm"
  }
}
```

## Framework-Style Routing

Framework-style routing is the modern approach where all requests are routed through a central controller file. This enables powerful routing patterns used by popular CFML frameworks.

### How It Works

1. **Request**: User visits `/users/123/edit`
2. **Rewrite**: Server internally routes to `/index.cfm/users/123/edit`
3. **PATH_INFO**: CGI variable contains `/users/123/edit`
4. **Processing**: Your router parses PATH_INFO and handles the request

### Router Implementation Patterns

#### Basic Router with Switch Statement

```cfml
<cfscript>
// Parse PATH_INFO
pathInfo = cgi.path_info ?: "";
route = len(pathInfo) > 1 ? right(pathInfo, len(pathInfo) - 1) : "home";
segments = listToArray(route, "/");
</cfscript>

<cfswitch expression="#arrayLen(segments) > 0 ? segments[1] : 'home'#">
    <cfcase value="api">
        <cfinclude template="controllers/api.cfm">
    </cfcase>
    <cfcase value="users">
        <cfinclude template="controllers/users.cfm">
    </cfcase>
    <cfcase value="home">
        <cfinclude template="views/home.cfm">
    </cfcase>
</cfswitch>
```

#### Advanced Router with MVC Pattern

```cfml
<cfscript>
// Parse route
pathInfo = cgi.path_info ?: "";
route = len(pathInfo) > 1 ? right(pathInfo, len(pathInfo) - 1) : "";
segments = listToArray(route, "/");

// Default routing
controller = arrayLen(segments) > 0 ? segments[1] : "home";
action = arrayLen(segments) > 1 ? segments[2] : "index";
id = arrayLen(segments) > 2 ? segments[3] : "";

// Set request scope for use in controllers
request.route = {
    controller: controller,
    action: action,
    id: id,
    segments: segments
};

// Load and execute controller
try {
    controllerPath = "controllers/" & controller & ".cfc";
    if (fileExists(expandPath(controllerPath))) {
        controllerObj = createObject("component", "controllers." & controller);
        if (structKeyExists(controllerObj, action)) {
            invoke(controllerObj, action);
        } else {
            // Handle method not found
            include "views/404.cfm";
        }
    } else {
        // Handle controller not found
        include "views/404.cfm";
    }
} catch (any e) {
    // Handle errors
    include "views/error.cfm";
}
</cfscript>
```

#### RESTful API Router

```cfml
<cfscript>
// Parse route for API
pathInfo = cgi.path_info ?: "";
route = len(pathInfo) > 1 ? right(pathInfo, len(pathInfo) - 1) : "";
segments = listToArray(route, "/");
method = cgi.request_method;

if (arrayLen(segments) > 0 && segments[1] == "api") {
    // Set JSON response
    cfheader(name="Content-Type", value="application/json");
    
    resource = arrayLen(segments) > 1 ? segments[2] : "";
    id = arrayLen(segments) > 2 ? segments[3] : "";
    
    switch (method) {
        case "GET":
            if (len(id)) {
                // GET /api/users/123
                result = getUserById(id);
            } else {
                // GET /api/users
                result = getUsers();
            }
            break;
        case "POST":
            // POST /api/users
            result = createUser();
            break;
        case "PUT":
            // PUT /api/users/123
            result = updateUser(id);
            break;
        case "DELETE":
            // DELETE /api/users/123
            result = deleteUser(id);
            break;
    }
    
    writeOutput(serializeJSON(result));
    abort;
}
</cfscript>
```

## Traditional Extension-less URLs

While framework-style routing is recommended for new projects, LuCLI also supports traditional extension-less URL mapping for simpler use cases.

### How It Works

With traditional extension-less URLs:
- `/about` → `/about.cfm`
- `/products` → `/products.cfm`
- `/contact` → `/contact.cfm`

### Configuration

```json
{
  "name": "simple-site",
  "port": 8080,
  "urlRewrite": {
    "enabled": true
  }
}
```

**Note**: When `routerFile` is not specified or is empty, LuCLI falls back to traditional extension-less URL mapping.

## Framework Integration

LuCLI's URL rewriting is designed to work seamlessly with popular CFML frameworks:

### ColdBox Framework

ColdBox uses the front controller pattern with `index.cfm`:

```json
{
  "name": "coldbox-app",
  "port": 8080,
  "urlRewrite": {
    "enabled": true,
    "routerFile": "index.cfm"
  }
}
```

Your ColdBox `index.cfm` handles all routing:
```cfml
<cfscript>
// ColdBox Bootstrap
application.bootstrap = new coldbox.system.Bootstrap();
application.bootstrap.loadColdBox();
</cfscript>
```

### FW/1 (Framework One)

FW/1 also uses `index.cfm` as the front controller:

```json
{
  "name": "fw1-app", 
  "port": 8080,
  "urlRewrite": {
    "enabled": true,
    "routerFile": "index.cfm"
  }
}
```

Your FW/1 `index.cfm`:
```cfml
<cfscript>
// FW/1 Application
component extends="framework.one" {
    // Framework configuration
    variables.framework = {
        generateSES = true,
        SESOmitIndex = true
    };
}
</cfscript>
```

### CFWheels

CFWheels can be configured for clean URLs:

```json
{
  "name": "wheels-app",
  "port": 8080, 
  "urlRewrite": {
    "enabled": true,
    "routerFile": "index.cfm"
  }
}
```

### ContentBox CMS

ContentBox works excellently with framework-style routing:

```json
{
  "name": "contentbox-site",
  "port": 8080,
  "urlRewrite": {
    "enabled": true,
    "routerFile": "index.cfm"
  }
}
```

### Custom Frameworks

For custom frameworks, implement your router in the specified `routerFile`:

```cfml
<cfscript>
// Custom Framework Router
pathInfo = cgi.path_info ?: "";
route = len(pathInfo) > 1 ? right(pathInfo, len(pathInfo) - 1) : "home";

// Your custom routing logic here
application.myFramework.handleRoute(route);
</cfscript>
```

## Advanced Configuration

### Custom URL Patterns

LuCLI uses Tomcat's built-in **RewriteValve** for URL rewriting. The rules use Apache `mod_rewrite` syntax and are deployed to the Tomcat Host level at `conf/Catalina/<hostName>/rewrite.config`. While basic configuration is handled through `lucee.json`, you can understand the generated rules below.

#### Generated Rules (Framework-Style)

When you enable framework-style routing, LuCLI generates a `rewrite.config` file with rules similar to:

```apache
# Don't rewrite if accessing the router file directly
RewriteCond %{REQUEST_URI} !^/index.cfm

# Don't rewrite static resource directories
RewriteCond %{REQUEST_URI} !^/(images|css|js|fonts|assets|static)/

# Don't rewrite static file extensions
RewriteCond %{REQUEST_URI} !\.(css|js|jpg|jpeg|png|gif|ico|svg|woff|woff2|ttf|eot|pdf|zip|json|xml|txt|map)$

# Don't rewrite Lucee admin
RewriteCond %{REQUEST_URI} !^/lucee

# Don't rewrite REST paths
RewriteCond %{REQUEST_URI} !^/rest/

# Don't rewrite CFML files accessed directly
RewriteCond %{REQUEST_URI} !\.(cfm|cfc|cfml)$

# Route everything else through the router file
RewriteRule ^/(.*)$ /index.cfm/$1 [L]
```

These rules use standard Apache `mod_rewrite` syntax. For full syntax documentation, see the [Tomcat Rewrite documentation](https://tomcat.apache.org/tomcat-9.0-doc/rewrite.html).

### Static Resource Exclusions

The following resources are automatically excluded from URL rewriting:

#### File Extensions
- **Images**: `.jpg`, `.jpeg`, `.png`, `.gif`, `.ico`, `.svg`
- **Styles**: `.css`, `.map`
- **Scripts**: `.js`, `.map`
- **Fonts**: `.woff`, `.woff2`, `.ttf`, `.eot`
- **Documents**: `.pdf`, `.zip`, `.json`, `.xml`, `.txt`

#### Directory Paths
- `/images/`
- `/css/`
- `/js/`  
- `/fonts/`
- `/assets/`
- `/static/`
- `/lucee/` (Lucee admin)

#### Direct CFML Access
- `.cfm`, `.cfc`, `.cfml` files can still be accessed directly
- This maintains backward compatibility

### Environment-Specific Configuration

#### Development
```json
{
  "name": "myapp-dev",
  "port": 8080,
  "urlRewrite": {
    "enabled": true,
    "routerFile": "index.cfm"
  },
  "admin": {
    "enabled": true
  }
}
```

#### Production
```json
{
  "name": "myapp-prod",
  "port": 80,
  "urlRewrite": {
    "enabled": true,
    "routerFile": "index.cfm"
  },
  "admin": {
    "enabled": false
  },
  "jvm": {
    "maxMemory": "2g",
    "minMemory": "512m"
  }
}
```

## Troubleshooting

### Common Issues and Solutions

#### 1. 404 Errors on All Routes

**Problem**: All URLs return 404 errors.

**Solutions**:
- ✅ Verify `urlRewrite.enabled` is `true` in `lucee.json`
- ✅ Check that your router file exists (e.g., `index.cfm`)
- ✅ Restart the server: `lucli server restart`
- ✅ Check server logs: `lucli server log`

#### 2. Router File Not Being Called

**Problem**: Direct URLs work, but router isn't processing requests.

**Solutions**:
- ✅ Verify `routerFile` setting in `lucee.json`
- ✅ Check that the router file path is correct
- ✅ Ensure no syntax errors in router file
- ✅ Check server startup logs for URL rewrite filter initialization

#### 3. Static Resources Not Loading

**Problem**: CSS, JS, or images return 404 errors.

**Solutions**:
- ✅ Use standard directory names: `/css/`, `/js/`, `/images/`
- ✅ Or access static files directly by URL
- ✅ Check file paths and permissions
- ✅ Verify files exist in webroot

#### 4. Infinite Redirect Loops

**Problem**: Browser shows "too many redirects" error.

**Solutions**:
- ✅ Check router logic for accidental redirects
- ✅ Ensure router doesn't redirect to itself
- ✅ Review conditional logic in router file

#### 5. CGI.PATH_INFO Empty

**Problem**: `cgi.path_info` is empty in router.

**Solutions**:
- ✅ Check URL rewrite configuration
- ✅ Verify server restart after config changes
- ✅ Use fallback: `cgi.path_info ?: ""`
- ✅ Test with simple debug output

### Debug Tips

#### 1. Debug Router Values

Add this to the top of your router file:

```cfml
<cfscript>
if (structKeyExists(url, "debug")) {
    writeDump({
        "cgi.path_info": cgi.path_info,
        "cgi.script_name": cgi.script_name,
        "cgi.request_uri": cgi.request_uri,
        "cgi.query_string": cgi.query_string
    });
    abort;
}
</cfscript>
```

Test with: `http://localhost:8080/tests/path?debug=1`

#### 2. Check URL Rewrite Status

Look for RewriteValve in server startup logs:
```bash
lucli server log --name your-server | grep -i rewrite
```

#### 3. Verify Configuration Deployment

Check that the rewrite config was deployed to the Host level:
```bash
# Check for rewrite.config at the Tomcat Host level
cat ~/.lucli/servers/your-server/conf/Catalina/localhost/rewrite.config
```

## Examples

### Example 1: Simple Blog

```json
// lucee.json
{
  "name": "my-blog",
  "port": 8080,
  "urlRewrite": {
    "enabled": true,
    "routerFile": "index.cfm"
  }
}
```

```cfml
<!-- index.cfm -->
<cfscript>
pathInfo = cgi.path_info ?: "";
route = len(pathInfo) > 1 ? right(pathInfo, len(pathInfo) - 1) : "home";
segments = listToArray(route, "/");
</cfscript>

<!DOCTYPE html>
<html>
<head>
    <title>My Blog</title>
    <link rel="stylesheet" href="/css/main.css">
</head>
<body>
    <nav>
        <a href="/">Home</a>
        <a href="/about">About</a>
        <a href="/blog">Blog</a>
        <a href="/contact">Contact</a>
    </nav>
    
    <main>
        <cfswitch expression="#arrayLen(segments) > 0 ? segments[1] : 'home'#">
            <cfcase value="home">
                <cfinclude template="views/home.cfm">
            </cfcase>
            <cfcase value="about">
                <cfinclude template="views/about.cfm">
            </cfcase>
            <cfcase value="blog">
                <cfif arrayLen(segments) > 1>
                    <!-- Individual blog post -->
                    <cfset postSlug = segments[2]>
                    <cfinclude template="views/post.cfm">
                <cfelse>
                    <!-- Blog listing -->
                    <cfinclude template="views/blog.cfm">
                </cfif>
            </cfcase>
            <cfcase value="contact">
                <cfinclude template="views/contact.cfm">
            </cfcase>
        </cfswitch>
    </main>
</body>
</html>
```

**URLs**:
- `/` → Home page
- `/about` → About page
- `/blog` → Blog listing
- `/blog/my-first-post` → Individual blog post
- `/contact` → Contact page

### Example 2: RESTful API

```json
// lucee.json
{
  "name": "api-server",
  "port": 8080,
  "urlRewrite": {
    "enabled": true,
    "routerFile": "index.cfm"
  }
}
```

```cfml
<!-- index.cfm -->
<cfscript>
pathInfo = cgi.path_info ?: "";
route = len(pathInfo) > 1 ? right(pathInfo, len(pathInfo) - 1) : "";
segments = listToArray(route, "/");
method = cgi.request_method;

// API routing
if (arrayLen(segments) > 0 && segments[1] == "api") {
    cfheader(name="Content-Type", value="application/json");
    
    // CORS headers
    cfheader(name="Access-Control-Allow-Origin", value="*");
    cfheader(name="Access-Control-Allow-Methods", value="GET,POST,PUT,DELETE,OPTIONS");
    
    try {
        resource = arrayLen(segments) > 1 ? segments[2] : "";
        id = arrayLen(segments) > 2 ? segments[3] : "";
        
        switch (resource) {
            case "users":
                include "api/users.cfm";
                break;
            case "posts":
                include "api/posts.cfm";
                break;
            default:
                response = {"error": "Resource not found", "status": 404};
                cfheader(statuscode="404", statustext="Not Found");
        }
        
        if (!isDefined("response")) {
            response = {"error": "No response generated", "status": 500};
        }
        
    } catch (any e) {
        response = {
            "error": "Internal server error",
            "message": e.message,
            "status": 500
        };
        cfheader(statuscode="500", statustext="Internal Server Error");
    }
    
    writeOutput(serializeJSON(response));
    abort;
}

// Regular web pages
include "views/web.cfm";
</cfscript>
```

```cfml
<!-- api/users.cfm -->
<cfscript>
switch (method) {
    case "GET":
        if (len(id)) {
            // GET /api/users/123
            response = {
                "id": id,
                "name": "John Doe",
                "email": "john@example.com"
            };
        } else {
            // GET /api/users
            response = {
                "users": [
                    {"id": 1, "name": "John Doe"},
                    {"id": 2, "name": "Jane Smith"}
                ],
                "total": 2
            };
        }
        break;
        
    case "POST":
        // POST /api/users - Create user
        body = getHttpRequestData().content;
        userData = deserializeJSON(body);
        
        response = {
            "id": randRange(100, 999),
            "name": userData.name,
            "message": "User created successfully"
        };
        cfheader(statuscode="201", statustext="Created");
        break;
        
    case "PUT":
        // PUT /api/users/123 - Update user
        body = getHttpRequestData().content;
        userData = deserializeJSON(body);
        
        response = {
            "id": id,
            "name": userData.name,
            "message": "User updated successfully"
        };
        break;
        
    case "DELETE":
        // DELETE /api/users/123 - Delete user
        response = {
            "id": id,
            "message": "User deleted successfully"
        };
        break;
        
    default:
        response = {"error": "Method not allowed", "status": 405};
        cfheader(statuscode="405", statustext="Method Not Allowed");
}
</cfscript>
```

**API URLs**:
- `GET /api/users` → List all users
- `GET /api/users/123` → Get specific user
- `POST /api/users` → Create new user  
- `PUT /api/users/123` → Update user
- `DELETE /api/users/123` → Delete user

### Example 3: Multi-language Site

```json
// lucee.json
{
  "name": "multilang-site",
  "port": 8080,
  "urlRewrite": {
    "enabled": true,
    "routerFile": "index.cfm"
  }
}
```

```cfml
<!-- index.cfm -->
<cfscript>
pathInfo = cgi.path_info ?: "";
route = len(pathInfo) > 1 ? right(pathInfo, len(pathInfo) - 1) : "home";
segments = listToArray(route, "/");

// Language detection
supportedLanguages = ["en", "es", "fr", "de"];
defaultLanguage = "en";

if (arrayLen(segments) > 0 && arrayFind(supportedLanguages, segments[1])) {
    // URL starts with language code
    language = segments[1];
    segments = arraySlice(segments, 2); // Remove language from segments
} else {
    // No language in URL, use default
    language = defaultLanguage;
}

// Set language for the request
request.language = language;
setLocale("en_US"); // You can set this based on language

// Determine page
page = arrayLen(segments) > 0 ? segments[1] : "home";
</cfscript>

<!DOCTYPE html>
<html lang="<cfoutput>#language#</cfoutput>">
<head>
    <title><cfoutput>#application.messages[language].siteTitle#</cfoutput></title>
    <link rel="stylesheet" href="/css/main.css">
</head>
<body>
    <nav>
        <!-- Language switcher -->
        <div class="lang-switcher">
            <cfloop array="#supportedLanguages#" index="lang">
                <cfset currentPath = arrayLen(segments) > 0 ? "/" & arrayToList(segments, "/") : "">
                <cfset langUrl = "/" & lang & currentPath>
                <a href="<cfoutput>#langUrl#</cfoutput>" 
                   class="<cfif lang eq language>active</cfif>">
                    <cfoutput>#ucase(lang)#</cfoutput>
                </a>
            </cfloop>
        </div>
        
        <!-- Navigation -->
        <cfset navUrl = language eq defaultLanguage ? "" : "/" & language>
        <a href="<cfoutput>#navUrl#/</cfoutput>">
            <cfoutput>#application.messages[language].nav.home#</cfoutput>
        </a>
        <a href="<cfoutput>#navUrl#/about</cfoutput>">
            <cfoutput>#application.messages[language].nav.about#</cfoutput>
        </a>
        <a href="<cfoutput>#navUrl#/products</cfoutput>">
            <cfoutput>#application.messages[language].nav.products#</cfoutput>
        </a>
    </nav>
    
    <main>
        <cfswitch expression="#page#">
            <cfcase value="home">
                <cfinclude template="views/#language#/home.cfm">
            </cfcase>
            <cfcase value="about">
                <cfinclude template="views/#language#/about.cfm">
            </cfcase>
            <cfcase value="products">
                <cfinclude template="views/#language#/products.cfm">
            </cfcase>
            <cfdefaultcase>
                <cfinclude template="views/#language#/404.cfm">
            </cfdefaultcase>
        </cfswitch>
    </main>
</body>
</html>
```

**URLs**:
- `/` → Home (default language)
- `/en/about` → English about page
- `/es/acerca` → Spanish about page  
- `/fr/a-propos` → French about page

---

## Migration from urlrewrite.xml (Tuckey UrlRewriteFilter)

> **⚠️ Deprecation Notice:** The Tuckey UrlRewriteFilter (`urlrewrite.xml`) has been replaced by Tomcat's built-in RewriteValve. If your project has a `urlrewrite.xml` file, LuCLI will display a warning at server startup. You should migrate to the new `rewrite.config` format.

### Why the Change?

- **Cross-version compatibility:** RewriteValve works across all Tomcat versions (8–11) with no javax/jakarta servlet API issues.
- **No external JAR:** The Tuckey filter required downloading a separate JAR file into `WEB-INF/lib/`. RewriteValve is built into Tomcat.
- **Cleaner project structure:** Rules are no longer placed inside `WEB-INF/` in your webroot.

### Migration Steps

1. **Remove** `urlrewrite.xml` from your project's `WEB-INF/` directory.
2. **Update** `lucee.json` — the `configFile` default is now `rewrite.config`. You can remove `configFile` from your config or set it explicitly:
   ```json
   {
     "urlRewrite": {
       "enabled": true,
       "routerFile": "index.cfm"
     }
   }
   ```
3. **Restart** your server with `lucli server restart`. LuCLI will automatically generate the appropriate `rewrite.config` with `mod_rewrite` syntax rules.
4. If you had **custom rewrite rules** in `urlrewrite.xml`, translate them to Apache `mod_rewrite` syntax. See the [Tomcat Rewrite documentation](https://tomcat.apache.org/tomcat-9.0-doc/rewrite.html) for syntax reference.

### Syntax Comparison

The old Tuckey XML format:
```xml
<rule>
    <from>^/(.*)$</from>
    <to type="forward">/index.cfm/$1</to>
</rule>
```

The new `mod_rewrite` format:
```apache
RewriteRule ^/(.*)$ /index.cfm/$1 [L]
```

---

## 🚀 Getting Started

Ready to implement URL rewriting in your project? 

1. **Add configuration** to your `lucee.json`
2. **Create your router** file (e.g., `index.cfm`)
3. **Start your server** with `lucli server start`
4. **Test your routes** in the browser

For more advanced configurations and framework integrations, explore the examples above and adapt them to your specific needs.

## 📚 Additional Resources

- [LuCLI Server Management](../README.md#server-management)
- [Lucee Documentation](https://docs.lucee.org/)
- [URLRewriteFilter Documentation](http://tuckey.org/urlrewrite/)

---

*This documentation is part of the LuCLI project. For issues or contributions, visit the [GitHub repository](https://github.com/cybersonic/LuCLI).*