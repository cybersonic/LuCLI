# Docker Usage Guide

This guide explains how to use LuCLI with Docker for both server and CLI modes.

## Quick Start

### Pull the Image

```bash
docker pull markdrew/lucli:latest
```

### Run a Lucee Server

Start a Lucee server with your CFML application:

```bash
docker run -d -p 8080:8080 -v $(pwd):/app --name my-lucee-app markdrew/lucli:latest
```

Your application will be available at `http://localhost:8080`

## Usage Modes

The Docker image supports two primary modes: **Server Mode** (default) and **CLI Mode**.

### Server Mode (Default)

By default, the container starts a Lucee server using `lucli server start`.

**Basic server:**
```bash
docker run -p 8080:8080 -v $(pwd):/app markdrew/lucli:latest
```

**With specific Lucee version:**
```bash
docker run -p 8080:8080 -v $(pwd):/app markdrew/lucli:latest server start --version 6.2.2.91
```

**With environment configuration:**
```bash
docker run -p 8080:8080 -v $(pwd):/app markdrew/lucli:latest server start --env prod
```

**Expose JMX monitoring:**
```bash
docker run -p 8080:8080 -p 8999:8999 -v $(pwd):/app markdrew/lucli:latest
```

**Run as daemon:**
```bash
docker run -d -p 8080:8080 -v $(pwd):/app --name my-app markdrew/lucli:latest
```

### CLI Mode

Override the default command to use LuCLI as a CLI tool.

**Show version:**
```bash
docker run markdrew/lucli:latest --version
```

**Show help:**
```bash
docker run markdrew/lucli:latest --help
```

**Execute a CFML script:**
```bash
docker run -v $(pwd):/app markdrew/lucli:latest script.cfs
```

**Interactive terminal mode:**
```bash
docker run -it -v $(pwd):/app markdrew/lucli:latest terminal
```

## Configuration

### Project Configuration (lucee.json)

Mount a directory containing a `lucee.json` file to configure your server:

```json
{
  "name": "my-app",
  "version": "6.2.2.91",
  "port": 8080,
  "webroot": "./",
  "jvm": {
    "maxMemory": "512m",
    "minMemory": "128m"
  }
}
```

```bash
docker run -p 8080:8080 -v $(pwd):/app markdrew/lucli:latest 
```

### Environment Variables

Use environment variables for configuration:

```bash
docker run -p 8080:8080 \
  -e LUCEE_VERSION=6.2.2.91 \
  -v $(pwd):/app \
  lucli
```

### Volume Mounts

The `/app` directory is the working directory and default webroot.

**Mount your application:**
```bash
-v $(pwd):/app
```

**Persist Lucee configuration:**
```bash
-v lucee-config:/root/.lucli
```

**Mount external configuration:**
```bash
-v $(pwd)/config:/config
```

## Common Use Cases

### Development Environment

Run a development server with live code reloading:

```bash
docker run -it --rm \
  -p 8080:8080 \
  -v $(pwd):/app \
  --name lucee-dev \
  lucli
```

### Production Deployment

Run with production environment and resource limits:

```bash
docker run -d \
  -p 80:80 \
  -v $(pwd):/app \
  -v lucee-data:/root/.lucli \
  --restart unless-stopped \
  --memory=1g \
  --cpus=2 \
  --name lucee-prod \
  lucli server start --env prod
```

### CI/CD Testing

Run tests in a container:

```bash
docker run --rm \
  -v $(pwd):/app \
  lucli tests/run-tests.cfs
```

### Multiple Applications

Run multiple Lucee applications on different ports:

```bash
# App 1
docker run -d -p 8080:8080 -v $(pwd)/app1:/app --name app1 markdrew/lucli:latest 

# App 2
docker run -d -p 8081:8080 -v $(pwd)/app2:/app --name app2 markdrew/lucli:latest 

# App 3
docker run -d -p 8082:8080 -v $(pwd)/app3:/app --name app3 markdrew/lucli:latest 
```

## Docker Compose

Create a `docker-compose.yml` for easier management:

```yaml
version: '3.8'

services:
  lucee:
    image: markdrew/lucli:latest
    ports:
      - "8080:8080"
      - "8999:8999"  # JMX monitoring
    volumes:
      - .:/app
      - lucee-config:/root/.lucli
    environment:
      - LUCEE_ADMIN_PASSWORD=${ADMIN_PASSWORD}
    restart: unless-stopped
    command: ["server", "run", "--env", "prod"]

volumes:
  lucee-config:
```

Start with:
```bash
docker-compose up -d
```

### With Database

```yaml
version: '3.8'

services:
  lucee:
    image: lucli:latest
    ports:
      - "8080:8080"
    volumes:
      - .:/app
    environment:
      - DB_HOST=mysql
      - DB_NAME=myapp
      - DB_USER=root
      - DB_PASSWORD=secret
    depends_on:
      - mysql

  mysql:
    image: mysql:8.0
    environment:
      - MYSQL_ROOT_PASSWORD=secret
      - MYSQL_DATABASE=myapp
    volumes:
      - mysql-data:/var/lib/mysql

volumes:
  mysql-data:
```

## Building the Image

### From Source

Build the LuCLI Docker image from source:

```bash
# Build the JAR first
mvn clean package

# Build Docker image
docker build -t lucli:latest .

# Or with a specific tag
docker build -t lucli:1.0.0 .
```

## Monitoring and Debugging

### View logs:
```bash
docker logs my-app
```

### Follow logs:
```bash
docker logs -f my-app
```

### Execute commands in running container:
```bash
docker exec -it my-app lucli server status
```

### Interactive shell:
```bash
docker exec -it my-app /bin/sh
```

### Monitor with JMX:
```bash
# Expose JMX port
docker run -p 8080:8080 -p 8999:8999 -v $(pwd):/app lucli

# In another terminal, connect to monitor
docker exec -it <container-id> lucli server monitor
```

## Networking

### Bridge Network (Default)

Containers can communicate using container names:

```bash
docker network create lucee-network

docker run -d \
  --network lucee-network \
  --name app1 \
  -p 8080:8080 \
  -v $(pwd)/app1:/app \
  lucli

docker run -d \
  --network lucee-network \
  --name app2 \
  -p 8081:8080 \
  -v $(pwd)/app2:/app \
  lucli
```

### Host Network

Use host networking for direct access:

```bash
docker run --network host -v $(pwd):/app lucli
```

## Security Considerations


### Secure Admin Password

Use environment variables or secrets:

```bash
docker run -p 8080:8080 \
  -e LUCEE_ADMIN_PASSWORD=$(cat /run/secrets/admin_password) \
  -v $(pwd):/app \
  lucli
```

### Read-Only Root Filesystem

```bash
docker run --read-only \
  --tmpfs /tmp \
  --tmpfs /root/.lucli \
  -v $(pwd):/app \
  lucli
```

## Troubleshooting

### Container Exits Immediately

Check if the application has CFML files or a valid `lucee.json`:

```bash
docker run --rm -v $(pwd):/app lucli ls -la
```

### Port Already in Use

Change the host port mapping:

```bash
docker run -p 8081:8080 -v $(pwd):/app lucli
```

### Permission Issues

Ensure mounted volumes have correct permissions:

```bash
chmod -R 755 $(pwd)
```

### Memory Issues

Increase container memory limits:

```bash
docker run --memory=2g -v $(pwd):/app lucli
```

### JVM Settings

Override JVM settings in `lucee.json` or use environment variables:

```bash
docker run \
  -e JAVA_OPTS="-Xmx1024m -Xms256m" \
  -v $(pwd):/app \
  lucli
```

## Best Practices

1. **Use volume mounts** for development to enable live reloading
2. **Use named volumes** for persistent data in production
3. **Set resource limits** (`--memory`, `--cpus`) to prevent resource exhaustion
4. **Use health checks** in Docker Compose for production deployments
5. **Don't store secrets** in the image; use environment variables or Docker secrets
6. **Use multi-stage builds** to keep image size small
7. **Tag your images** with version numbers for reproducible deployments
8. **Use `.dockerignore`** to exclude unnecessary files from the build context

## Additional Resources

- [LuCLI Documentation](README.md)
- [Environment Variables](ENVIRONMENT_VARIABLES.md)
- [Docker Documentation](https://docs.docker.com)
