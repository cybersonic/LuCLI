---
title: "QA: Runtime Providers"
layout: docs
---

Manual QA checklist for verifying the three runtime providers work correctly.
Run these from the `demo_servers/` directory after building LuCLI (`./build.sh`).

## Prerequisites

```bash
# Build LuCLI
./build.sh install

# Verify binary
lucli --version

# Clean up any leftover servers from previous runs
lucli server list
lucli server stop --all 2>/dev/null
```

---

## 1. Lucee Express (default runtime)

### 1a. Basic start/stop

```bash
mkdir -p demo_servers/qa-express && cd demo_servers/qa-express
cat > lucee.json << 'EOF'
{
  "name": "qa-express",
  "version": "6.2.2.91",
  "port": 8180,
  "webroot": "./",
  "openBrowser": false
}
EOF
echo '<cfoutput>Express OK - #now()#</cfoutput>' > index.cfm
```

```bash
# Start server
lucli server start

# VERIFY: Server starts without errors
# VERIFY: "Starting server 'qa-express' on:" message shows port 8180
```

```bash
# Test HTTP response
curl -s http://localhost:8180/index.cfm
# VERIFY: Response contains "Express OK"
```

```bash
# Check server status
lucli server status
# VERIFY: Shows "qa-express" as RUNNING

# Check CATALINA_HOME != CATALINA_BASE
ls ~/.lucli/servers/qa-express/conf/server.xml
# VERIFY: server.xml exists in CATALINA_BASE
```

```bash
# Stop server
lucli server stop
# VERIFY: Server stops cleanly
```

### 1b. Express with explicit runtime config

```bash
cd demo_servers/qa-express
cat > lucee.json << 'EOF'
{
  "name": "qa-express-explicit",
  "version": "6.2.2.91",
  "port": 8181,
  "webroot": "./",
  "openBrowser": false,
  "runtime": {
    "type": "lucee-express",
    "variant": "standard"
  }
}
EOF

lucli server start
curl -s http://localhost:8181/index.cfm
# VERIFY: Response contains "Express OK"
lucli server stop
```

### 1c. Express foreground mode

```bash
cd demo_servers/qa-express
lucli server run
# VERIFY: Server output appears in terminal
# VERIFY: "Running server 'qa-express' in foreground mode:" message
# VERIFY: Ctrl+C stops the server cleanly
```

### 1d. Express with force replace

```bash
cd demo_servers/qa-express
lucli server start
lucli server stop
lucli server start --force
# VERIFY: Server recreates CATALINA_BASE and starts fresh
lucli server stop
```

---

## 2. External Tomcat

### 2a. Setup

You need a Tomcat installation. If you don't have one:

```bash
# Option A: Homebrew
brew install tomcat

# Find your CATALINA_HOME
# Homebrew Tomcat 10: /opt/homebrew/opt/tomcat/libexec
# Homebrew Tomcat 9:  /opt/homebrew/opt/tomcat@9/libexec
ls /opt/homebrew/opt/tomcat/libexec/bin/catalina.sh

# Option B: Download manually
# https://tomcat.apache.org/download-10.cgi
```

Set your Tomcat path for the tests below:

```bash
export MY_TOMCAT_HOME="/opt/homebrew/opt/tomcat/libexec"
# Adjust this path to your actual Tomcat installation
```

### 2b. Basic external Tomcat start/stop

```bash
mkdir -p demo_servers/qa-tomcat && cd demo_servers/qa-tomcat
cat > lucee.json << 'EOF'
{
  "name": "qa-tomcat",
  "version": "7.0.0.242-RC",
  "port": 8280,
  "webroot": "./",
  "openBrowser": false,
  "runtime": {
    "type": "tomcat",
    "catalinaHome": "${CATALINA_HOME_PLACEHOLDER}"
  }
}
EOF
# Replace placeholder with actual path
sed -i '' "s|\${CATALINA_HOME_PLACEHOLDER}|$MY_TOMCAT_HOME|g" lucee.json
echo '<cfoutput>Tomcat OK - #now()# - CATALINA_HOME=#server.system.environment.CATALINA_HOME#</cfoutput>' > index.cfm
```

```bash
lucli server start
# VERIFY: "Using runtime.type="tomcat"" message
# VERIFY: "Detected Tomcat version: XX.x" message
# VERIFY: "Lucee X.x is compatible with Tomcat XX.x" message
# VERIFY: "Deploying Lucee JAR to server instance" message
# VERIFY: Port details show 8280
```

```bash
curl -s http://localhost:8280/index.cfm
# VERIFY: Response contains "Tomcat OK"
# VERIFY: CATALINA_HOME matches your Tomcat installation path
```

```bash
# Verify CATALINA_BASE structure
ls ~/.lucli/servers/qa-tomcat/
# VERIFY: Has conf/, lib/, logs/, lucee-server/, lucee-web/, bin/, temp/, work/

ls ~/.lucli/servers/qa-tomcat/lib/
# VERIFY: Contains lucee-7.0.0.242-RC.jar (or whatever version)

cat ~/.lucli/servers/qa-tomcat/conf/server.xml | grep 'port="8280"'
# VERIFY: server.xml contains the configured port

grep CFMLServlet ~/.lucli/servers/qa-tomcat/conf/web.xml
# VERIFY: Lucee servlets are present in web.xml
```

```bash
lucli server stop
# VERIFY: Server stops cleanly
```

### 2c. Tomcat version compatibility check

```bash
cd demo_servers/qa-tomcat

# Test INCOMPATIBLE combination (Lucee 6 + Tomcat 10+)
cat > lucee.json << EOF
{
  "name": "qa-tomcat-compat",
  "version": "6.2.2.91",
  "port": 8281,
  "webroot": "./",
  "openBrowser": false,
  "runtime": {
    "type": "tomcat",
    "catalinaHome": "$MY_TOMCAT_HOME"
  }
}
EOF

lucli server start
# VERIFY (if Tomcat 10+): Error "Lucee 6.2.2.91 is not compatible with Tomcat XX"
# VERIFY: Error message suggests solutions (upgrade Lucee or use Tomcat 9)
# VERIFY (if Tomcat 9): Server starts normally (6.x is compatible with 9.x)
```

### 2d. Missing catalinaHome

```bash
cd demo_servers/qa-tomcat
cat > lucee.json << 'EOF'
{
  "name": "qa-tomcat-missing",
  "port": 8282,
  "webroot": "./",
  "openBrowser": false,
  "runtime": {
    "type": "tomcat",
    "catalinaHome": "/nonexistent/path/to/tomcat"
  }
}
EOF

lucli server start
# VERIFY: Error "CATALINA_HOME does not exist: /nonexistent/path/to/tomcat"
```

### 2e. Invalid Tomcat installation

```bash
mkdir -p /tmp/fake-tomcat
cd demo_servers/qa-tomcat
cat > lucee.json << 'EOF'
{
  "name": "qa-tomcat-invalid",
  "port": 8283,
  "webroot": "./",
  "openBrowser": false,
  "runtime": {
    "type": "tomcat",
    "catalinaHome": "/tmp/fake-tomcat"
  }
}
EOF

lucli server start
# VERIFY: Error about missing bin/ or lib/ directory
rm -rf /tmp/fake-tomcat
```

### 2f. Tomcat with CATALINA_HOME env var fallback

```bash
cd demo_servers/qa-tomcat
cat > lucee.json << 'EOF'
{
  "name": "qa-tomcat-env",
  "version": "7.0.0.242-RC",
  "port": 8284,
  "webroot": "./",
  "openBrowser": false,
  "runtime": {
    "type": "tomcat"
  }
}
EOF

CATALINA_HOME="$MY_TOMCAT_HOME" lucli server start
# VERIFY: Server starts using CATALINA_HOME from environment
lucli server stop
```

### 2g. Tomcat with HTTPS

```bash
cd demo_servers/qa-tomcat
cat > lucee.json << EOF
{
  "name": "qa-tomcat-https",
  "version": "7.0.0.242-RC",
  "port": 8285,
  "webroot": "./",
  "openBrowser": false,
  "https": {
    "enabled": true,
    "port": 8443,
    "redirect": false
  },
  "runtime": {
    "type": "tomcat",
    "catalinaHome": "$MY_TOMCAT_HOME"
  }
}
EOF

lucli server start
# VERIFY: Port details show both HTTP 8285 and HTTPS 8443
curl -sk https://localhost:8443/index.cfm
# VERIFY: HTTPS response works (self-signed cert)
lucli server stop
```

### 2h. Dry-run preview

```bash
cd demo_servers/qa-tomcat
cat > lucee.json << EOF
{
  "name": "qa-tomcat-dryrun",
  "version": "7.0.0.242-RC",
  "port": 8286,
  "webroot": "./",
  "openBrowser": false,
  "runtime": {
    "type": "tomcat",
    "catalinaHome": "$MY_TOMCAT_HOME"
  }
}
EOF

lucli server start --dry-run
# VERIFY: Shows configuration preview without starting the server
# VERIFY: No server instance directory created
```

---

## 3. Docker

### 3a. Prerequisites

```bash
docker --version
# VERIFY: Docker is installed and running
```

### 3b. Basic Docker start/stop

```bash
mkdir -p demo_servers/qa-docker && cd demo_servers/qa-docker
cat > lucee.json << 'EOF'
{
  "name": "qa-docker",
  "version": "6.2.2.91",
  "port": 8380,
  "webroot": "./",
  "openBrowser": false,
  "runtime": {
    "type": "docker"
  }
}
EOF
echo '<cfoutput>Docker OK - #now()#</cfoutput>' > index.cfm
```

```bash
lucli server start
# VERIFY: "Using runtime.type="docker"" message
# VERIFY: Docker container starts
```

```bash
docker ps | grep lucli-qa-docker
# VERIFY: Container "lucli-qa-docker" is running

curl -s http://localhost:8380/index.cfm
# VERIFY: Response contains "Docker OK" (may take a moment for Lucee to warm up)
```

```bash
lucli server stop
# VERIFY: Container stops
docker ps | grep lucli-qa-docker
# VERIFY: Container no longer running
```

### 3c. Docker with custom image/tag

```bash
cd demo_servers/qa-docker
cat > lucee.json << 'EOF'
{
  "name": "qa-docker-custom",
  "port": 8381,
  "webroot": "./",
  "openBrowser": false,
  "runtime": {
    "type": "docker",
    "image": "lucee/lucee",
    "tag": "latest",
    "containerName": "my-custom-lucee"
  }
}
EOF

lucli server start
docker ps | grep my-custom-lucee
# VERIFY: Container uses the custom name
lucli server stop
```

---

## 4. Cross-runtime checks

### 4a. Server list shows runtime type

```bash
# Start one of each type (adjust ports to avoid conflicts)
cd demo_servers/qa-express && lucli server start
cd demo_servers/qa-tomcat && lucli server start  # if external Tomcat is configured

lucli server list
# VERIFY: Each server shows in the list
# VERIFY: Ports are correct for each

lucli server stop --all
```

### 4b. Force replace cleans up properly

```bash
cd demo_servers/qa-express
lucli server start
lucli server stop
ls ~/.lucli/servers/qa-express/conf/server.xml
# VERIFY: Config exists from first run

lucli server start --force
# VERIFY: Old config is replaced
lucli server stop
```

---

## 5. Cleanup

```bash
# Stop all test servers
lucli server stop --all 2>/dev/null

# Remove test projects
rm -rf demo_servers/qa-express
rm -rf demo_servers/qa-tomcat
rm -rf demo_servers/qa-docker

# Remove server instances
rm -rf ~/.lucli/servers/qa-express*
rm -rf ~/.lucli/servers/qa-tomcat*
rm -rf ~/.lucli/servers/qa-docker*
```

---

## Test matrix summary

| Test | Express | Tomcat | Docker |
|------|---------|--------|--------|
| Start/stop | 1a | 2b | 3b |
| HTTP response | 1a | 2b | 3b |
| CATALINA_BASE structure | 1a | 2b | — |
| Foreground mode | 1c | — | — |
| Force replace | 1d | — | 4b |
| Version compatibility | — | 2c | — |
| Missing/invalid config | — | 2d, 2e | — |
| Env var fallback | — | 2f | — |
| HTTPS | — | 2g | — |
| Dry-run | — | 2h | — |
| Custom image/container | — | — | 3c |
| Server list | 4a | 4a | 4a |
