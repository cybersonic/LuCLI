---
title: Server Template Development
layout: docs
---

# Server Template Development

This document explains how LuCLI generates Tomcat configuration from templates and how to safely extend those templates (especially `web.xml`) using the comment-marker conditional system.

## Overview

LuCLI does **not** edit Tomcat configuration files by hand. Instead, it:

1. Copies a Lucee Express distribution into `~/.lucli/servers/<server-name>/`.
2. Applies templates from `src/main/resources/tomcat_template/` to generate:
   - `conf/server.xml`
   - `conf/logging.properties`
   - `<project-webroot>/WEB-INF/web.xml`
   - `<project-webroot>/WEB-INF/urlrewrite.xml` (optional)
3. Optionally deploys additional artifacts (e.g. UrlRewriteFilter JAR) into the project `WEB-INF/lib`.

All template processing is implemented in `org.lucee.lucli.server.TomcatConfigGenerator`.

## Template Locations

Core template files:

- `src/main/resources/tomcat_template/conf/server.xml`
- `src/main/resources/tomcat_template/conf/logging.properties`
- `src/main/resources/tomcat_template/webapps/ROOT/WEB-INF/web.xml`
- `src/main/resources/tomcat_template/webapps/ROOT/WEB-INF/urlrewrite.xml`

At build time they end up on the classpath under:

- `tomcat_template/conf/...`
- `tomcat_template/webapps/ROOT/WEB-INF/...`

TomcatConfigGenerator loads these resources via the classloader and writes fully-resolved files to the server instance and project webroot.

## Placeholder Substitution

Templates use simple `${...}` placeholders that are replaced via `String.replace`.

Example from `server.xml`:

- `${httpPort}` – HTTP port from `lucee.json`
- `${shutdownPort}` – derived from HTTP port (`http + 1000`)
- `${jmxPort}` – JMX port from `lucee.json`
- `${webroot}` – absolute project webroot
- `${luceeServerPath}` – `~/.lucli/servers/<name>/lucee-server`
- `${luceeWebPath}` – `~/.lucli/servers/<name>/lucee-web`
- `${luceePatches}` – `~/.lucli/patches`
- `${jvmRoute}` – server name (used as Tomcat `jvmRoute`)

Placeholders are populated in:

- `createPlaceholderMap(...)` in `TomcatConfigGenerator`
- `applyTemplate(...)` and `applyWebXmlTemplate(...)`

If you add a new placeholder to any template:

1. Add an entry for it in `createPlaceholderMap(...)`.
2. Use the `${name}` form consistently across templates.

## Comment-Marker Conditional Blocks

For more complex configuration (features that can be toggled on/off), we use **comment markers** in the templates instead of a full templating engine.

### Syntax

A conditional block in a template looks like this:

```xml path=null start=null
<!-- IF_SOMETHING -->
    ... XML content ...
<!-- END_IF_SOMETHING -->
```

At generation time, `TomcatConfigGenerator.applyConditionalBlocks(...)` either:

- **Keeps** the inner content (when the condition is true), or
- **Removes** the entire block (markers + content) when the condition is false.

This is implemented by `processBooleanBlock(...)` in `TomcatConfigGenerator` and currently supports two tags out of the box:

- `IF_URLREWRITE_ENABLED`
- `IF_ADMIN_ENABLED`

### UrlRewrite Filter Block

In `web.xml` template:

```xml path=src/main/resources/tomcat_template/webapps/ROOT/WEB-INF/web.xml start=10
<!-- IF_URLREWRITE_ENABLED -->
<!-- UrlRewriteFilter for Extension-less URLs -->
<filter>
    <filter-name>UrlRewriteFilter</filter-name>
    <filter-class>org.tuckey.web.filters.urlrewrite.UrlRewriteFilter</filter-class>
    <init-param>
        <param-name>logLevel</param-name>
        <param-value>INFO</param-value>
    </init-param>
    <init-param>
        <param-name>statusEnabled</param-name>
        <param-value>true</param-value>
    </init-param>
</filter>

<filter-mapping>
    <filter-name>UrlRewriteFilter</filter-name>
    <url-pattern>/*</url-pattern>
    <dispatcher>REQUEST</dispatcher>
    <dispatcher>FORWARD</dispatcher>
</filter-mapping>
<!-- END_IF_URLREWRITE_ENABLED -->
```

Decision logic (in `applyConditionalBlocks`):

- **Enabled** when `lucee.json` contains:

  ```json
  "urlRewrite": {
    "enabled": true,
    "routerFile": "index.cfm"
  }
  ```

- **Removed** when `urlRewrite` is absent or `enabled` is `false`.

When removed, the UrlRewriteFilter class is not referenced at all from project `WEB-INF/web.xml`, so Tomcat will not attempt to load it and will not fail with `ClassNotFoundException` if the JAR is missing.

### Admin Mappings Block

In `web.xml` template:

```xml path=src/main/resources/tomcat_template/webapps/ROOT/WEB-INF/web.xml start=99
<!-- IF_ADMIN_ENABLED -->
<!-- Lucee Admin Servlet Mappings -->
<servlet-mapping>
    <servlet-name>CFMLServlet</servlet-name>
    <url-pattern>/lucee/*</url-pattern>
</servlet-mapping>

<servlet-mapping>
    <servlet-name>CFMLServlet</servlet-name>
    <url-pattern>/lucee/admin/*</url-pattern>
</servlet-mapping>
<!-- END_IF_ADMIN_ENABLED -->
```

Decision logic:

- `admin.enabled` **defaults to true** for backward compatibility.
- If `lucee.json` has:

  ```json
  "admin": {
    "enabled": false
  }
  ```

  then the entire admin block is stripped and `/lucee/*` routes are not mapped.

## How Conditions Are Evaluated

Conditions are evaluated in `TomcatConfigGenerator.applyConditionalBlocks(...)`:

```java path=src/main/java/org/lucee/lucli/server/TomcatConfigGenerator.java start=224
private String applyConditionalBlocks(String content, LuceeServerConfig.ServerConfig config) {
    boolean urlRewriteEnabled = config.urlRewrite != null && config.urlRewrite.enabled;
    content = processBooleanBlock(content, "IF_URLREWRITE_ENABLED", urlRewriteEnabled);
    
    // Admin: default to enabled if config.admin is null (backwards compatibility)
    boolean adminEnabled = (config.admin == null) || config.admin.enabled;
    content = processBooleanBlock(content, "IF_ADMIN_ENABLED", adminEnabled);
    
    return content;
}
```

`processBooleanBlock(...)` searches for `<!-- TAG --> ... <!-- END_TAG -->` and either keeps or removes the inner content depending on the boolean flag.

## Adding a New Conditional Block

To introduce a new feature toggle in a template:

1. **Define the configuration field**

   Add a field to `LuceeServerConfig.ServerConfig` (and wire it through `lucee.json` as needed).

2. **Wrap the template section**

   In the appropriate template file, wrap the XML in a new block:

   ```xml
   <!-- IF_SOME_FEATURE -->
       ... XML that should only be included when feature is enabled ...
   <!-- END_IF_SOME_FEATURE -->
   ```

   Keep marker names simple and UPPER_SNAKE_CASE.

3. **Update `applyConditionalBlocks(...)`**

   In `TomcatConfigGenerator`:

   ```java
   boolean someFeatureEnabled = config.someFeature != null && config.someFeature.enabled;
   content = processBooleanBlock(content, "IF_SOME_FEATURE", someFeatureEnabled);
   ```

4. **(Optional) Set defaults**

   If you want the feature to default to on or off when the config is absent, encode that default explicitly in the boolean expression you pass into `processBooleanBlock`.

## Project vs Server-Level Files

It is important to understand where each template ends up:

- **Server-level (under `~/.lucli/servers/<name>/`):**
  - `conf/server.xml` and `conf/logging.properties` are generated from templates and apply to the Tomcat instance as a whole.

- **Project-level (under project webroot):**
  - `WEB-INF/web.xml` is generated from the template and **belongs to the project**.
  - `WEB-INF/urlrewrite.xml` and `WEB-INF/lib/urlrewritefilter-*.jar` are only deployed when URL rewriting is enabled.

When you change templates in `tomcat_template` and rebuild LuCLI, **new servers** (or servers regenerated with `--force`) will pick up the new configuration. Existing servers and existing `WEB-INF/web.xml` files may keep their previous content until they are regenerated.

## When To Use Comment Markers vs Placeholders

- Use `${...}` placeholders for **simple value substitution** (ports, paths, names).
- Use `<!-- IF_... -->` blocks when:
  - The entire section should appear or disappear based on `lucee.json`.
  - You need to keep the template valid XML without external templating engines.

This approach keeps templates readable, avoids brittle regex replacements, and centralizes feature toggles in `TomcatConfigGenerator`.
