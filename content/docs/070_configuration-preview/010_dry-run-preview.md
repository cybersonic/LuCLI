---
title: Dry Run Preview
layout: docs
---

The `--dry-run` flag allows you to preview what LuCLI will configure and deploy **before** actually starting a server. This is invaluable for verifying your `lucee.json` settings, reviewing generated configurations, and troubleshooting server setup issues.

## Basic Usage

```bash
# Preview the resolved configuration
lucli server start --dry-run

# Preview configuration for a specific directory
lucli server start --dry-run /path/to/project
```

The basic dry-run output shows:
- The resolved `lucee.json` with all defaults applied and environment variables substituted
- Confirmation that no server was actually started

## Section Selection with `--include`

Use `--include` with `--dry-run` to choose exactly which preview sections should be shown.

For `lucli server start`, valid sections are:
- `config`
- `env`
- `lucee`
- `tomcat-web`
- `tomcat-server`
- `https-keystore-plan`
- `https-redirect-rules`
- `all`

For `lucli server run`, valid sections are:
- `config`
- `env`
- `all`

By default (`--dry-run` with no selector), LuCLI shows the realized `lucee.json` (`config`). Once you pass any explicit selector (`--include ...` or a legacy include flag), LuCLI only prints requested sections.

Legacy aliases remain available for `server start`:
- `--include-env` → `--include env`
- `--include-lucee` → `--include lucee`
- `--include-tomcat-web` → `--include tomcat-web`
- `--include-tomcat-server` → `--include tomcat-server`
- `--include-https-keystore-plan` → `--include https-keystore-plan`
- `--include-https-redirect-rules` → `--include https-redirect-rules`
- `--include-all` → `--include all`

For `server run`, `--include-env` remains an alias for `--include env`.

### Examples

```bash
# Preview only environment sections (no realized lucee.json section)
lucli server start --dry-run --include env

# Preview specific start sections in one run
lucli server start --env prod --dry-run --include config,env,lucee,tomcat-web,tomcat-server

# Preview just the Lucee configuration
lucli server start --dry-run --include lucee

# Preview everything for server start
lucli server start --dry-run --include all

# Run mode supports config/env/all
lucli server run --dry-run --include env
```

## Use Cases

### 1. Verify Lucee Configuration (`--include lucee`)

**When to use:**
- After setting up datasources or mappings in `lucee.json`
- When merging external `configurationFile` with inline `configuration`
- Before deploying to ensure CFConfig is correct

**What it shows:**
The deep-merged `.CFConfig.json` that combines:
- Base settings from `configurationFile` (if specified)
- Overrides from inline `configuration` in `lucee.json`

This is the exact JSON that will be written to `~/.lucli/servers/<name>/lucee-server/context/.CFConfig.json`.

```bash
lucli server start --dry-run --include lucee
```

**Example output:**
```
📄 .CFConfig.json (patched, from lucee.json):
────────────────────────────────────────────
{
  "dataSources" : {
    "mydb" : {
      "class" : "org.h2.Driver",
      "dsn" : "jdbc:h2:{path}{database};MODE={mode}",
      "database" : "myapp_db"
      ...
    }
  },
  "mappings" : {
    "/api" : "/var/www/api"
  }
}
────────────────────────────────────────────
```

**Common issues caught:**
- Incorrect datasource connection strings
- Missing required extensions
- Conflicting mapping definitions
- Malformed JSON in `configurationFile`

---

### 2. Review Tomcat Connectors (`--include tomcat-server`)

**When to use:**
- Setting up HTTPS
- Configuring custom ports
- Troubleshooting port conflicts
- Verifying SSL/TLS settings

**What it shows:**
The patched `server.xml` with:
- HTTP connector on your specified port
- HTTPS connector (if enabled)
- Shutdown port configuration
- Host and context settings

```bash
lucli server start --dry-run --include tomcat-server
```

**Common issues caught:**
- Port conflicts between HTTP, HTTPS, and shutdown ports
- Incorrect HTTPS keystore paths
- Missing SSL protocols or ciphers
- Wrong host configuration

---

### 3. Check Servlet Mappings (`--include tomcat-web`)

**When to use:**
- Debugging routing issues
- Verifying URL rewrite filter is installed
- Checking if Lucee admin is enabled/disabled
- Confirming `enableLucee=false` removes CFML servlets

**What it shows:**
The patched `web.xml` with:
- Lucee CFML servlet mappings (if `enableLucee=true`)
- Lucee REST servlet mappings (if `enableRest=true`)
- URL rewrite filter configuration (if `urlRewrite.enabled=true`)
- Admin servlet mappings (if `admin.enabled=true`)
- Security constraints (prevents `lucee.json` from being served)

```bash
lucli server start --dry-run --include tomcat-web
```

**Common issues caught:**
- Missing servlet mappings for `.cfm` files
- URL rewrite filter not configured
- Lucee admin incorrectly exposed/hidden
- REST endpoints not enabled when expected

---

### 4. Preview HTTPS Setup (`--include https-keystore-plan`)

**When to use:**
- Before enabling HTTPS for the first time
- Verifying certificate SAN (Subject Alternative Name) includes your custom host
- Checking keystore file paths

**What it shows:**
A plan for HTTPS keystore generation including:
- Host and port
- Keystore file path and password file location
- Certificate alias
- SANs (DNS and IP)

```bash
lucli server start --dry-run --include https-keystore-plan
```

**Example output:**
```
🔐 HTTPS keystore plan:
─────────────────────────────────────────
Host:         myapp.localhost
HTTPS port:   8443
Keystore:     ~/.lucli/servers/myapp/certs/keystore.p12
Password file:~/.lucli/servers/myapp/certs/keystore.pass
Alias:        lucli
SANs:
  - DNS:localhost
  - DNS:myapp.localhost
  - IP:127.0.0.1
Note: Files are generated only when starting (no side effects in --dry-run).
─────────────────────────────────────────
```

---

### 5. Verify HTTP→HTTPS Redirects (`--include https-redirect-rules`)

**When to use:**
- After enabling HTTPS with `https.redirect=true`
- Troubleshooting redirect loops
- Verifying redirect target host and port

**What it shows:**
The `rewrite.config` rules that will redirect all HTTP traffic to HTTPS:

```bash
lucli server start --dry-run --include https-redirect-rules
```

**Example output:**
```
↪️  HTTPS redirect rules plan:
─────────────────────────────────────────
Rewrite config path: ~/.lucli/servers/myapp/conf/Catalina/localhost/rewrite.config
Valve: org.apache.catalina.valves.rewrite.RewriteValve

rewrite.config contents:
RewriteCond %{HTTPS} !=on
RewriteRule ^/(.*)$ https://myapp.localhost:8443/$1 [R=302,L]
─────────────────────────────────────────
```

---

### 6. Complete Configuration Audit (`--include all`)

**When to use:**
- Before first deployment
- After major configuration changes
- When troubleshooting complex issues
- For documentation/compliance audits

**What it shows:**
All preview sections combined in one output.

```bash
lucli server start --dry-run --include all
```

---

## Workflow Examples

### Workflow 1: Setting up a new project with datasources

```bash
# 1. Create your lucee.json with datasource configuration
cat > lucee.json << 'EOF'
{
  "name": "myapp",
  "port": 8080,
  "configuration": {
    "dataSources": {
      "mydb": {
        "class": "org.postgresql.Driver",
        "dsn": "jdbc:postgresql://{host}:{port}/{database}",
        "host": "localhost",
        "port": "5432",
        "database": "myapp_db",
        "username": "dbuser",
        "password": "secret123"
      }
    }
  }
}
EOF

# 2. Preview the CFConfig to verify datasource settings
lucli server start --dry-run --include lucee

# 3. Check output for correct DSN and credentials
# Look for: "dsn" : "jdbc:postgresql://localhost:5432/myapp_db"

# 4. If correct, start the server
lucli server start
```

### Workflow 2: Enabling HTTPS

```bash
# 1. Update lucee.json to enable HTTPS
lucli server config set https.enabled=true

# 2. Preview HTTPS configuration
lucli server start --dry-run --include https-keystore-plan
lucli server start --dry-run --include tomcat-server

# 3. Verify:
#    - HTTPS port is available
#    - Keystore path is correct
#    - SANs include your hostname
#    - server.xml has HTTPS connector

# 4. Start server with HTTPS
lucli server start
```

### Workflow 3: Debugging URL routing issues

```bash
# 1. Preview web.xml to check servlet mappings
lucli server start --dry-run --include tomcat-web

# 2. Look for:
#    - CFMLServlet mapped to *.cfm
#    - UrlRewriteFilter installed
#    - routerFile set correctly

# 3. Check URL rewrite rules
lucli server start --dry-run --include tomcat-server | grep -A20 "RewriteValve"

# 4. If configuration looks correct, check actual rewrite.config
cat ~/.lucli/servers/myapp/webapps/ROOT/WEB-INF/urlrewrite.xml
```

### Workflow 4: Merging external configuration files

```bash
# 1. Create base config file
cat > lucee-base.json << 'EOF'
{
  "inspectTemplate": "never",
  "typeChecking": "true",
  "errorTemplate": "/error.cfm"
}
EOF

# 2. Reference it in lucee.json with project-specific overrides
cat > lucee.json << 'EOF'
{
  "configurationFile": "lucee-base.json",
  "configuration": {
    "dataSources": {
      "mydb": { ... }
    }
  }
}
EOF

# 3. Preview the merged configuration
lucli server start --dry-run --include lucee

# 4. Verify:
#    - Base settings from lucee-base.json are present
#    - Datasource from inline configuration is merged in
#    - No conflicts or overwritten values
```

---

## Environment Variable Preview

When using environment variables in `lucee.json`, use the env preview section to inspect resolved values:

```bash
# Set environment variables
export HTTP_PORT=9090
export DB_HOST=production.example.com

# Preview realized substitution variables and runtime env
lucli server start --dry-run --include env
```
The output shows realized placeholder values and the runtime environment map LuCLI will pass to the process. Add `config` if you also want the realized `lucee.json` section (for example `--include config,env`).

---

## Tips and Best Practices

1. **Always dry-run after configuration changes:** Catch errors before starting the server
2. **Use `--include lucee` for CFConfig validation:** Especially important for datasources and extensions
3. **Check `--include tomcat-server` for port conflicts:** Before starting multiple servers
4. **Review `--include all` before production deployment:** Complete configuration audit
5. **Combine with version control:** Run dry-run in CI to validate configuration files
6. **Document expected output:** Save dry-run output as reference documentation

---

## Limitations

- **No filesystem side effects:** Dry-run does not create directories, keystores, or server instances
- **Cannot test runtime behavior:** Only shows configuration, not how server will actually run
- **Lucee Express must be available:** Dry-run downloads the Lucee version if not cached
- **No validation of external resources:** Cannot verify datasource connections or file paths
