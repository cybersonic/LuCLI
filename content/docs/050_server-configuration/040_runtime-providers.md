---
title: Runtime Providers
layout: docs
---

LuCLI supports multiple runtime providers for running your CFML applications. This guide helps you choose the right runtime for your needs and shows you how to configure each option.

## Available Runtimes

| Runtime | Best For | Lucee Version | Setup Complexity |
|---------|----------|---------------|------------------|
| **Lucee Express** (default) | Development, quick start | Any | None |
| **External Tomcat** | Production, enterprise | 7.x for Tomcat 10+, 6.x for Tomcat 9 | Medium |

## Lucee Express (Default)

Lucee Express bundles Tomcat and Lucee together in a single download. This is the default runtime and requires no configuration.

### When to Use Lucee Express

- **Development environments** - Quick to start, no setup required
- **Learning CFML** - Get started immediately
- **Simple deployments** - Self-contained, easy to manage
- **Prototyping** - Spin up servers quickly

### Configuration

No `runtime` section needed - Lucee Express is the default:

```json
{
  "name": "my-app",
  "version": "6.2.2.91",
  "port": 8080
}
```

Or explicitly specify it:

```json
{
  "name": "my-app",
  "version": "6.2.2.91",
  "port": 8080,
  "runtime": {
    "type": "lucee-express",
    "variant": "standard"
  }
}
```

### Variants

Lucee Express comes in three variants:

| Variant | Description | Use Case |
|---------|-------------|----------|
| `standard` | Full Lucee distribution (default) | Most applications |
| `light` | Reduced size, fewer extensions | Smaller footprint |
| `zero` | Minimal install, extensions on demand | Microservices, containers |

```json
{
  "runtime": {
    "type": "lucee-express",
    "variant": "light"
  }
}
```

## External Tomcat

Use an existing Tomcat installation with Lucee deployed separately. This gives you full control over Tomcat configuration and allows sharing a single Tomcat installation across multiple projects.

### When to Use External Tomcat

- **Production environments** - Battle-tested Tomcat with your hardening
- **Enterprise deployments** - Align with IT policies and existing infrastructure
- **Tomcat expertise** - Leverage existing Tomcat knowledge
- **Shared Tomcat instances** - Run multiple apps on one Tomcat
- **Custom Tomcat configuration** - Use your own server.xml, security settings, etc.

### Version Compatibility

**Important**: Tomcat and Lucee versions must be compatible:

| Tomcat Version | Servlet API | Compatible Lucee |
|----------------|-------------|------------------|
| 9.x and below | javax.servlet | Lucee 5.x, 6.x |
| 10.x, 11.x | jakarta.servlet | Lucee 7.x |

LuCLI automatically detects the Tomcat version and validates compatibility. You'll see an error if versions don't match:

```
❌ Lucee 6.2.2.91 is not compatible with Tomcat 11

Tomcat 10+ uses jakarta.servlet (Jakarta EE), but Lucee 6.x uses javax.servlet (Java EE).

Solutions:
  1. Use Lucee 7.x with Tomcat 11 (recommended)
  2. Use Tomcat 9.x with Lucee 6.2.2.91
```

### Configuration

```json
{
  "name": "my-app",
  "version": "7.0.1.100-RC",
  "port": 8080,
  "runtime": {
    "type": "tomcat",
    "catalinaHome": "/opt/tomcat"
  }
}
```

### Using Environment Variables

You can reference the `CATALINA_HOME` environment variable:

```json
{
  "runtime": {
    "type": "tomcat",
    "catalinaHome": "${CATALINA_HOME}"
  }
}
```

If `catalinaHome` is not specified, LuCLI falls back to the `CATALINA_HOME` environment variable.

### How External Tomcat Works

LuCLI uses the CATALINA_BASE pattern to keep your Tomcat installation pristine:

```
CATALINA_HOME (your Tomcat install - untouched)
├── bin/
├── lib/
└── conf/

CATALINA_BASE (~/.lucli/servers/<name>/)
├── conf/
│   ├── server.xml    ← Patched with ports/context
│   └── web.xml       ← Lucee servlets added, JSP removed
├── lib/
│   └── lucee-*.jar   ← Lucee JAR deployed here
├── bin/
│   └── setenv.sh     ← JVM options from lucee.json
├── logs/
├── lucee-server/
└── lucee-web/
```

**Benefits**:
- Your Tomcat installation is never modified
- Each project has isolated configuration
- Easy to upgrade Tomcat independently
- Multiple Lucee versions can coexist

### What LuCLI Configures

When using external Tomcat, LuCLI automatically:

1. **Copies and patches server.xml** - Sets ports and adds your webroot as a context
2. **Patches web.xml** - Adds Lucee servlets (CFMLServlet, RESTServlet)
3. **Removes JSP servlets** - Not needed for CFML, reduces attack surface
4. **Deploys Lucee JAR** - To CATALINA_BASE/lib
5. **Generates setenv scripts** - JVM options from your lucee.json
6. **Protects lucee.json** - Adds security constraint to block HTTP access

### Environment-Specific Runtimes

You can use different runtimes per environment:

```json
{
  "name": "my-app",
  "version": "7.0.1.100-RC",
  "port": 8080,
  "environments": {
    "dev": {
      "runtime": {
        "type": "lucee-express"
      }
    },
    "prod": {
      "runtime": {
        "type": "tomcat",
        "catalinaHome": "/opt/tomcat"
      }
    }
  }
}
```

```bash
# Development - uses Lucee Express
lucli server start --env dev

# Production - uses external Tomcat
lucli server start --env prod
```

## URL Rewriting

URL rewriting configuration depends on your Tomcat version.

### Tomcat 9 and Below

LuCLI supports UrlRewriteFilter with Tomcat 9:

```json
{
  "urlRewrite": {
    "enabled": true,
    "routerFile": "index.cfm",
    "configFile": "urlrewrite.xml"
  }
}
```

Create `urlrewrite.xml` in your project root - LuCLI copies it to the server automatically.

### Tomcat 10+

UrlRewriteFilter is not yet compatible with Tomcat 10+ (jakarta.servlet). Alternatives:

1. **Tomcat's RewriteValve** - Built into Tomcat, uses Apache mod_rewrite syntax
2. **OCPsoft Rewrite** - Jakarta-compatible (`org.ocpsoft.rewrite:rewrite-servlet:10.0.1.Final`)

LuCLI will display a warning if URL rewriting is enabled with Tomcat 10+.

## Choosing the Right Runtime

### Decision Flowchart

```
Start
  │
  ├─► Development/Learning? ──► Lucee Express
  │
  ├─► Need specific Tomcat version/config? ──► External Tomcat
  │
  ├─► Production with existing Tomcat? ──► External Tomcat
  │
  ├─► Simple deployment, self-contained? ──► Lucee Express
  │
  └─► Enterprise IT policies require Tomcat? ──► External Tomcat
```

### Quick Comparison

| Feature | Lucee Express | External Tomcat |
|---------|---------------|-----------------|
| Setup time | Instant | Requires Tomcat install |
| Tomcat version | Bundled | Your choice |
| Tomcat customization | Limited | Full control |
| Lucee/Tomcat coupling | Tied together | Independent |
| Multiple apps | Separate instances | Can share |
| Production hardening | Basic | Your configuration |
| Upgrade path | Replace download | Upgrade independently |

## Migration Between Runtimes

### From Lucee Express to External Tomcat

1. Install Tomcat 10+ (for Lucee 7.x) or Tomcat 9 (for Lucee 6.x)
2. Update `lucee.json`:

```json
{
  "version": "7.0.1.100-RC",
  "runtime": {
    "type": "tomcat",
    "catalinaHome": "/opt/tomcat"
  }
}
```

3. Stop and recreate the server:

```bash
lucli server stop
lucli server start --force
```

Your application code doesn't change - only the runtime configuration.

## Troubleshooting

### "Lucee X.x is not compatible with Tomcat Y"

Check the version compatibility table above. Update either your Lucee version or use a different Tomcat installation.

### Server starts but CFML doesn't work

1. Check the server logs: `lucli server log --type server`
2. Verify Lucee JAR was deployed: `ls ~/.lucli/servers/<name>/lib/`
3. Check web.xml has Lucee servlets: `grep CFMLServlet ~/.lucli/servers/<name>/conf/web.xml`

### Port conflicts

The external Tomcat's ports are controlled by LuCLI, not your Tomcat's default server.xml. Check your `lucee.json` port settings.
