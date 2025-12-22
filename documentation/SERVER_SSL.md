# HTTPS/SSL Support in LuCLI

This document describes how HTTPS/SSL is implemented in LuCLI for Lucee server instances.

## Overview

LuCLI provides built-in HTTPS support for Lucee servers with:
- Automatic self-signed certificate generation for development
- Per-server keystore management
- Dual HTTP/HTTPS support
- Optional HTTP-to-HTTPS redirection
- Modern TLS configuration (TLSv1.2, TLSv1.3)

## Configuration

HTTPS is configured in `lucee.json` using the `https` object:

```json
{
  "name": "my-server",
  "port": 8080,
  "https": {
    "enabled": true,
    "port": 8443,
    "redirect": true
  }
}
```

### Configuration Options

#### `https.enabled` (boolean)
- **Default**: `false`
- **Description**: Enables HTTPS connector
- When `false` or omitted, no SSL configuration is applied

#### `https.port` (integer, optional)
- **Default**: `8443`
- **Description**: HTTPS port number
- The port must not conflict with HTTP port, JMX port, or shutdown port

#### `https.redirect` (boolean, optional)
- **Default**: `true` (when HTTPS is enabled)
- **Description**: When `true`, configures Tomcat to redirect HTTP requests to HTTPS
- Set to `false` to allow both HTTP and HTTPS without redirection

#### `host` (string, optional)
- **Default**: `"localhost"`
- **Description**: Hostname used for certificate generation and default URLs
- Affects the CN (Common Name) and SAN (Subject Alternative Names) in generated certificates

## Implementation Details

### 1. Schema (`LuceeServerConfig.java`)

The `HttpsConfig` class models HTTPS configuration:

```java
public static class HttpsConfig {
    public boolean enabled = false;
    public Integer port;         // Defaults to 8443 if null
    public Boolean redirect;     // Defaults to true when HTTPS enabled
}
```

Added to `ServerConfig`:

```java
public HttpsConfig https;
```

### 2. Certificate Management

#### Automatic Generation

When HTTPS is enabled and the server starts, LuCLI automatically:

1. **Creates per-server certificate directory**: `~/.lucli/servers/{server-name}/certs/`
2. **Generates random password**: Stored in `certs/keystore.pass` with `600` permissions
3. **Creates PKCS12 keystore**: `certs/keystore.p12` using `keytool`
4. **Includes SANs**: Certificate includes `dns:localhost`, `dns:{host}`, and `ip:127.0.0.1`

#### Certificate Details

- **Type**: Self-signed certificate
- **Algorithm**: RSA 2048-bit
- **Validity**: 825 days (~2.25 years)
- **Format**: PKCS12
- **Alias**: `lucli`
- **CN**: Configured hostname (defaults to `localhost`)
- **SANs**: localhost, configured host, 127.0.0.1

#### Security

- Password file (`keystore.pass`) has `600` permissions (owner read/write only)
- Keystore file (`keystore.p12`) has `600` permissions
- Password is randomly generated (32-byte, URL-safe base64)
- Certificates are generated per-server (not shared between instances)

### 3. Tomcat Configuration (`TomcatServerXmlPatcher.java`)

#### HTTPS Connector

When HTTPS is enabled, LuCLI adds or updates an HTTPS connector in `server.xml`:

```xml
<Connector 
    port="8443"
    protocol="org.apache.coyote.http11.Http11NioProtocol"
    scheme="https"
    secure="true"
    SSLEnabled="true">
    <SSLHostConfig 
        hostName="_default_"
        protocols="TLSv1.2,TLSv1.3">
        <Certificate
            certificateKeystoreFile="/path/to/keystore.p12"
            certificateKeystorePassword="..."
            certificateKeystoreType="PKCS12"
            certificateKeyAlias="lucli"
            type="RSA" />
    </SSLHostConfig>
</Connector>
```

**Key Implementation Details:**
- Uses modern `SSLHostConfig` nested element (Tomcat 8.5+)
- Enables TLS 1.2 and 1.3 protocols
- Connector is inserted after existing connectors but before `<Engine>`
- Reuses existing HTTPS connector if found (checks for `scheme="https"` or `SSLEnabled="true"`)

#### HTTP-to-HTTPS Redirection

When `https.redirect` is `true`, LuCLI configures a redirect from HTTP to HTTPS by:

1. Finding the root `<Context>` element (path="" or "/")
2. Adding a `RewriteValve` with redirect rules
3. Preserving query strings and request paths

The redirect configuration looks like:

```xml
<Context path="" docBase="...">
    <Valve 
        className="org.apache.catalina.valves.rewrite.RewriteValve" />
    <!-- Redirect rules written to conf/Catalina/localhost/rewrite.config -->
</Context>
```

With `rewrite.config` containing:

```
RewriteCond %{HTTPS} !=on
RewriteRule ^(.*)$ https://%{HTTP_HOST}%{REQUEST_URI} [R=301,L]
```

### 4. Port Conflict Detection

LuCLI validates that the HTTPS port doesn't conflict with:
- HTTP port (`config.port`)
- Shutdown port (HTTP port + 1000, or explicit `config.shutdownPort`)
- JMX port (`config.monitoring.jmx.port`)

If conflicts are detected, LuCLI will either:
- Fail with an error message (default)
- Automatically reassign ports (with `--auto-reassign-ports` flag)


```

## Usage Examples

### Basic HTTPS

```json
{
  "name": "my-app",
  "port": 8080,
  "https": {
    "enabled": true
  }
}
```

**Result:**
- HTTP: `http://localhost:8080`
- HTTPS: `https://localhost:8443` (auto-generated cert)
- HTTP requests redirect to HTTPS

### Custom HTTPS Port

```json
{
  "name": "my-app",
  "port": 8080,
  "https": {
    "enabled": true,
    "port": 8843
  }
}
```

### Disable HTTP Redirect

```json
{
  "name": "my-app",
  "port": 8080,
  "https": {
    "enabled": true,
    "port": 8443,
    "redirect": false
  }
}
```

**Result:** Both HTTP and HTTPS work without redirection

### Custom Hostname for Certificate

```json
{
  "name": "my-app",
  "host": "myapp.local",
  "port": 8080,
  "https": {
    "enabled": true
  }
}
```

**Result:** Certificate includes CN=myapp.local and SANs for both localhost and myapp.local

### Environment-Specific HTTPS

```json
{
  "name": "my-app",
  "port": 8080,
  "environments": {
    "prod": {
      "https": {
        "enabled": true,
        "port": 443,
        "redirect": true
      }
    },
    "dev": {
      "https": {
        "enabled": false
      }
    }
  }
}
```

## Dry-Run Mode

When using `lucli server start --dry-run`, HTTPS configuration is shown without generating certificates:

```
HTTPS Configuration:
  Enabled: true
  Port: 8443
  Redirect: true
  Keystore: ~/.lucli/servers/my-app/certs/keystore.p12
  Password: <stored in certs/keystore.pass>
```

## Server Lifecycle

### First Start
1. User enables HTTPS in `lucee.json`
2. On `lucli server start`:
   - Creates `certs/` directory
   - Generates password file
   - Runs `keytool` to create keystore
   - Patches `server.xml` with HTTPS connector
   - Server starts with HTTPS enabled

### Subsequent Starts
- Reuses existing keystore and password
- No certificate regeneration unless files are deleted
- `server.xml` is regenerated with same certificate paths

### Certificate Renewal
To regenerate certificate:
```bash
# Stop server
lucli server stop

# Remove certificate files
rm -rf ~/.lucli/servers/my-app/certs/

# Restart (will regenerate)
lucli server start
```

## Production Considerations

### Using Custom Certificates

For production, LuCLI's auto-generated certificates should be replaced with proper certificates from a Certificate Authority:

1. Obtain certificate from CA (Let's Encrypt, DigiCert, etc.)
2. Convert to PKCS12 format:
   ```bash
   openssl pkcs12 -export \
     -in fullchain.pem \
     -inkey privkey.pem \
     -out keystore.p12 \
     -name lucli
   ```
3. Replace `~/.lucli/servers/{server-name}/certs/keystore.p12`
4. Update `certs/keystore.pass` with the password
5. Restart server

### Reverse Proxy Setup

For production deployments, consider using a reverse proxy (nginx, Apache) to handle SSL:

```nginx
# nginx example
server {
    listen 443 ssl;
    server_name myapp.com;
    
    ssl_certificate /path/to/fullchain.pem;
    ssl_certificate_key /path/to/privkey.pem;
    
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Then disable HTTPS in `lucee.json` and let nginx handle SSL termination.

## Troubleshooting

### Browser Security Warning

**Problem:** Browser shows "Your connection is not private"

**Solution:** This is expected with self-signed certificates in development. Click "Advanced" and proceed (dev only). For production, use certificates from trusted CAs.

### keytool Not Found

**Problem:** `Failed to generate self-signed HTTPS certificate. Ensure 'keytool' is available on PATH`

**Solution:** Install Java JDK (not just JRE). The `keytool` command comes with the JDK.

### Port Conflict

**Problem:** `HTTPS port 8443 is already in use`

**Solution:** 
- Change HTTPS port in `lucee.json`
- Stop the service using port 8443
- Use `--auto-reassign-ports` flag

### Permission Denied on Certificate Files

**Problem:** Can't read keystore or password file

**Solution:** Check file permissions:
```bash
ls -la ~/.lucli/servers/my-app/certs/
# Should show: -rw------- (600)
```

## Testing

Test HTTPS configuration:

```bash
# Start server with HTTPS
lucli server start

# Test HTTP (should redirect if redirect=true)
curl -I http://localhost:8080

# Test HTTPS (use -k to ignore self-signed cert)
curl -k https://localhost:8443

# Check certificate details
openssl s_client -connect localhost:8443 -showcerts

# View certificate info
keytool -list -v -keystore ~/.lucli/servers/my-app/certs/keystore.p12 -storepass $(cat ~/.lucli/servers/my-app/certs/keystore.pass)
```

## Related Files

- `src/main/java/org/lucee/lucli/server/LuceeServerConfig.java` - Configuration model
- `src/main/java/org/lucee/lucli/server/TomcatServerXmlPatcher.java` - Tomcat configuration
- `examples/server-https/` - Working example with documentation
