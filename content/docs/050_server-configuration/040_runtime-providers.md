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
| **Docker** *(experimental)* | Containers, CI/CD | Determined by image | Low |

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
  "lucee": {
    "version": "6.2.2.91"
  },
  "port": 8080
}
```

Or explicitly specify it:

```json
{
  "name": "my-app",
  "lucee": {
    "version": "6.2.2.91"
  },
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
  "lucee": {
    "version": "7.0.1.100-RC"
  },
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
  "lucee": {
    "version": "7.0.1.100-RC"
  },
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

## Docker *(Experimental)*

Run your CFML application inside a Docker container using the official `lucee/lucee` image. LuCLI manages the container lifecycle so `server start`, `server stop`, `server status`, and `server list` all work as expected.

> **Note:** Docker runtime is experimental and may not cover all image variants or advanced Docker configurations yet.

### When to Use Docker

- **Container-based workflows** - CI/CD pipelines, Kubernetes, Docker Compose
- **Consistent environments** - Same image in dev and production
- **Isolation** - No local Java or Tomcat installation required
- **Quick experimentation** - Try different Lucee versions via image tags

### Prerequisites

- Docker must be installed and running on your machine
- The `docker` CLI must be available on your `PATH`

### Configuration

The simplest form — just set the runtime to `"docker"`:

```json
{
  "name": "my-app",
  "port": 8380,
  "runtime": "docker"
}
```

Or with more control:

```json
{
  "name": "my-app",
  "port": 8380,
  "runtime": {
    "type": "docker",
    "image": "lucee/lucee",
    "tag": "6.2.2.91",
    "containerName": "my-custom-name"
  }
}
```

### Runtime Options

| Key | Default | Description |
|-----|---------|-------------|
| `runtime.image` | `lucee/lucee` | Docker image to use. |
| `runtime.tag` | `latest` | Image tag / version. |
| `runtime.containerName` | `lucli-{name}` | Docker container name. Defaults to `lucli-` followed by the server name. |

### How It Works

When you run `lucli server start` with a Docker runtime:

1. LuCLI removes any stale container with the same name
2. Runs `docker run -d` with:
   - Port mapping: your configured `port` → container port 8888
   - Volume mount: your project directory → `/var/www` (the Lucee webroot)
3. Waits for the server to become available on the mapped port
4. Tracks the container via a `.docker-container` marker file

`server stop` sends `docker stop` followed by `docker rm`. `server status` and `server list` check the container state via `docker inspect`.

### Environment Variables

LuCLI passes several environment variables to the container automatically:

- `LUCEE_ADMIN_PASSWORD` — from `admin.password` in `lucee.json`
- `LUCEE_EXTENSIONS` — from your dependency lock file
- Any custom variables defined in `envVars` in `lucee.json`

### Choosing an Image Tag

The `lucee/lucee` image publishes tags for specific versions:

```json
{"runtime": {"type": "docker", "tag": "6.2.2.91"}}
```

Use `latest` for the most recent stable release, or pin to a specific version for reproducibility.

### Limitations

- Foreground mode (`server run`) is not yet supported for Docker — servers always start in the background
- The server instance directory is not mounted into the container (Tomcat configuration inside the container is managed by the image)
- Advanced Docker options (networks, extra volumes, resource limits) are not yet configurable via `lucee.json`

## URL Rewriting

LuCLI uses Tomcat's built-in **RewriteValve** for URL rewriting, which works across all Tomcat versions (8–11) with no javax/jakarta compatibility issues.

```json
{
  "urlRewrite": {
    "enabled": true,
    "routerFile": "index.cfm"
  }
}
```

Rewrite rules use Apache `mod_rewrite` syntax and are deployed to `conf/Catalina/<hostName>/rewrite.config` in the server's `CATALINA_BASE`. When both HTTPS redirect and URL rewriting are enabled, rules are combined into a single `rewrite.config` file.

For full details, see [URL Rewriting](/docs/080_https-and-routing/010_url-rewriting/).

> **⚠️ Migration Notice:** The previous Tuckey UrlRewriteFilter (`urlrewrite.xml`) has been replaced. If your project uses `urlrewrite.xml`, LuCLI will display a deprecation warning at startup. See the [migration guide](/docs/080_https-and-routing/010_url-rewriting/#migration-from-urlrewritexml-tuckey-urlrewritefilter) for details.

### Jetty Runtime

URL rewriting is **not supported** with the Jetty runtime. If `urlRewrite.enabled` is set to `true`, LuCLI will display a warning and skip URL rewrite configuration.

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
  ├─► Container-based / CI/CD workflow? ──► Docker
  │
  ├─► Simple deployment, self-contained? ──► Lucee Express
  │
  └─► Enterprise IT policies require Tomcat? ──► External Tomcat
```

### Quick Comparison

| Feature | Lucee Express | External Tomcat | Docker |
|---------|---------------|-----------------|--------|
| Setup time | Instant | Requires Tomcat install | Requires Docker |
| Tomcat version | Bundled | Your choice | Determined by image |
| Tomcat customization | Limited | Full control | Via Dockerfile |
| Lucee/Tomcat coupling | Tied together | Independent | Managed by image |
| Multiple apps | Separate instances | Can share | Separate containers |
| Production hardening | Basic | Your configuration | Image-based |
| Upgrade path | Replace download | Upgrade independently | Change image tag |

## Migration Between Runtimes

### From Lucee Express to External Tomcat

1. Install Tomcat 10+ (for Lucee 7.x) or Tomcat 9 (for Lucee 6.x)
2. Update `lucee.json`:

```json
{
  "lucee": {
    "version": "7.0.1.100-RC"
  },
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

### Docker: Container starts but pages don't load

1. Check the container is running: `docker ps --filter name=lucli-<name>`
2. Check container logs: `docker logs lucli-<name>`
3. Verify your project directory is mounted: `docker exec lucli-<name> ls /var/www/`

### Docker: "container name already in use"

LuCLI automatically cleans up stale containers before starting. If you see this error, the container may have been created outside of LuCLI. Remove it manually:

```bash
docker rm -f lucli-<name>
```
