# JAR Signing

This document describes how to sign the LuCLI JAR file for distribution.

## Prerequisites

- Java JDK (includes keytool and jarsigner)
- Maven 3.x

## Setup

### 1. Generate Keystore (One-time)

Create a keystore with a self-signed certificate:

```bash
keytool -genkey -alias lucli \
  -keyalg RSA \
  -keysize 2048 \
  -validity 3650 \
  -keystore ~/.lucli-keystore.jks \
  -dname "CN=Mark Drew, O=LuCLI, L=London, C=UK"
```

You'll be prompted for:
- **Keystore password**: Store this securely
- **Key password**: Can be same as keystore password

### 2. Configure Signing

#### For Local Builds

Create or edit `~/.m2/settings.xml`:

```xml
<settings>
  <profiles>
    <profile>
      <id>jar-signing</id>
      <properties>
        <jarsigner.skip>false</jarsigner.skip>
        <jarsigner.storepass>YOUR_KEYSTORE_PASSWORD</jarsigner.storepass>
        <jarsigner.keypass>YOUR_KEY_PASSWORD</jarsigner.keypass>
      </properties>
    </profile>
  </profiles>
</settings>
```

#### For GitHub Actions

Add these secrets to your repository (Settings → Secrets and variables → Actions):

- `JARSIGNER_KEYSTORE_BASE64`: Base64-encoded keystore file
- `JARSIGNER_STOREPASS`: Keystore password
- `JARSIGNER_KEYPASS`: Key password

To encode your keystore:
```bash
base64 -i ~/.lucli-keystore.jks | pbcopy
```

## Building with Signing

### Local Build (Unsigned)

Default build skips signing:
```bash
mvn clean package
```

### Local Build (Signed)

Activate the signing profile:
```bash
mvn clean package -Pjar-signing
```

Or set the property:
```bash
mvn clean package -Djarsigner.skip=false
```

### GitHub Actions

Update `.github/workflows/release.yml` to decode and use the keystore:

```yaml
- name: Setup JAR signing
  if: steps.version.outputs.is_snapshot == 'false'
  run: |
    echo "${{ secrets.JARSIGNER_KEYSTORE_BASE64 }}" | base64 -d > $HOME/.lucli-keystore.jks

- name: Build and sign JAR
  if: steps.version.outputs.is_snapshot == 'false'
  run: mvn clean package -Djarsigner.skip=false
  env:
    JARSIGNER_STOREPASS: ${{ secrets.JARSIGNER_STOREPASS }}
    JARSIGNER_KEYPASS: ${{ secrets.JARSIGNER_KEYPASS }}
```

## Verifying Signed JAR

Check if a JAR is signed:
```bash
jarsigner -verify -verbose target/lucli.jar
```

View signature details:
```bash
jarsigner -verify -verbose -certs target/lucli.jar
```

## Certificate Information

View certificate details:
```bash
keytool -list -v -keystore ~/.lucli-keystore.jks -alias lucli
```

## Notes

- Self-signed certificates are sufficient for open-source distribution
- Users won't see security warnings for JAR files (unlike executables)
- For production/commercial software, consider purchasing a code signing certificate from a CA
- The certificate is valid for 10 years (3650 days)
- Keep your keystore and passwords secure - they can't be recovered if lost

## Troubleshooting

### "keystore password was incorrect"
- Check your password in Maven settings
- Verify the keystore path is correct

### "jarsigner: unable to open jar file"
- Ensure the JAR was built successfully
- Check the path in the jarsigner configuration

### Signature verification fails
- Rebuild and sign again
- Check that keystore hasn't been modified
- Ensure correct alias is used
