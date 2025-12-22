# HTTPS Server Configuration

Demonstrates how to configure a Lucee server with HTTPS/SSL support using LuCLI.

## What This Shows

- HTTPS/SSL configuration for Lucee servers
- Self-signed certificate generation for development
- Running HTTP and HTTPS simultaneously
- SSL keystore management

## Prerequisites

- LuCLI installed
- Java 17+ installed (includes keytool for certificate generation)

## Quick Start

```bash
cd examples/server-https

# Generate a self-signed certificate (for development)
./generate-cert.sh

# Start the server
lucli server start

# Access via HTTPS
# https://localhost:8843

# Access via HTTP (also available)
# http://localhost:8893

# Stop server
lucli server stop
```

=
## Configuration Explained

The `lucee.json` file includes HTTPS configuration:

```json
{
  "name": "server-https-example",
  "port": 8893,
  "https": {
    "enabled": true,
    "port": 8843
  }
}
```

### Configuration Options

- **port**: HTTP port (8893)
- **https.enabled**: Enable HTTPS support
- **https.port**: HTTPS port (8843)
- **https.keystore**: Path to Java keystore file
- **https.keystorePassword**: Password for the keystore
- **https.keyPassword**: Password for the private key

## Dual Protocol Support

When HTTPS is enabled, the server runs both protocols:
- **HTTP**: http://localhost:8893
- **HTTPS**: https://localhost:8843

You can disable HTTP by setting `"httpEnabled": false` in the configuration.

## Production Certificates

For production use, obtain a certificate from a trusted CA:

### Using Let's Encrypt (Certbot)

```bash
# Generate certificate with certbot
certbot certonly --standalone -d yourdomain.com

# Convert to Java keystore
openssl pkcs12 -export -in /etc/letsencrypt/live/yourdomain.com/fullchain.pem \
  -inkey /etc/letsencrypt/live/yourdomain.com/privkey.pem \
  -out cert.p12 -name lucee

keytool -importkeystore -srckeystore cert.p12 -srcstoretype PKCS12 \
  -destkeystore keystore.jks -deststoretype JKS
```

### Using Commercial CA

1. Generate Certificate Signing Request (CSR)
2. Submit CSR to CA
3. Receive signed certificate
4. Import into Java keystore

## Server Commands

```bash
# Start with HTTPS
lucli server start

# Check server status
lucli server status

# View server logs
lucli server log

# Stop server
lucli server stop
```

## Security Best Practices

### Development
- Use self-signed certificates
- Keep passwords simple (e.g., "changeit")
- Don't commit keystore files to version control

### Production
- Use certificates from trusted CAs
- Use strong keystore passwords
- Store passwords in environment variables or secure vaults
- Enable HSTS (HTTP Strict Transport Security)
- Consider disabling HTTP entirely
- Regular certificate renewal

## Troubleshooting

### Certificate Errors in Browser

**Problem:** Browser shows "Your connection is not private"

**Solution:** This is expected with self-signed certificates. Click "Advanced" and "Proceed to localhost" (development only).

### Keystore Not Found

**Problem:** `Error: Keystore file not found`

**Solution:** Generate the certificate first:
```bash
./generate-cert.sh
```

### Port Already in Use

**Problem:** HTTPS port 8843 is already in use

**Solution:** Change the HTTPS port in `lucee.json`:
```json
{
  "https": {
    "port": 8844
  }
}
```

## Key Files

- **lucee.json** - Server configuration with HTTPS settings
- **index.cfm** - Demo page showing connection info

## Expected Output

When you visit the running server, you'll see:
- Connection protocol (HTTP/HTTPS)
- Port information
- SSL/TLS information (when using HTTPS)
- Server details

## Clean Up

```bash
lucli server stop

```

Server files are stored in `~/.lucli/servers/server-https-example/`.

## Learn More

- [Tomcat SSL Configuration](https://tomcat.apache.org/tomcat-9.0-doc/ssl-howto.html)
- [Java Keytool Documentation](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/keytool.html)
- [Let's Encrypt](https://letsencrypt.org/)
- [Server SSL ](../../documentation/SERVER_SSL.md)
