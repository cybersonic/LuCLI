# Lucee Extension Registry

This file (`lucee-extensions.json`) contains mappings from friendly extension names/slugs to their Lucee extension UUIDs.

## Purpose

Allows developers to reference Lucee extensions by name instead of UUID:

```json
{
  "dependencies": {
    "h2": {
      "type": "extension"
    }
  }
}
```

Instead of:

```json
{
  "dependencies": {
    "h2": {
      "id": "465E1E35-2425-4F4E-8B3FAB638BD7280A",
      "type": "extension"
    }
  }
}
```

## File Format

```json
{
  "version": "1.0",
  "lastUpdated": "2025-12-23",
  "extensions": [
    {
      "id": "UUID-OF-EXTENSION",
      "slug": "primary-slug",
      "name": "Human Readable Name",
      "aliases": ["alternative-name", "another-alias"]
    }
  ]
}
```

## Fields

- **id**: The Lucee extension UUID (required)
- **slug**: Primary short identifier for the extension (required)
- **name**: Human-readable name (required)
- **aliases**: Array of alternative names/slugs (optional)

## Usage

The extension registry resolves in this order:
1. slug (e.g., "h2", "redis")
2. name (e.g., "H2 Embedded Database")
3. aliases (e.g., "postgresql" for "postgres")
4. If already a UUID, returns as-is

## Updating the Registry

### Manual Update

Edit `src/main/resources/lucee-extensions.json` and add new extensions:

```json
{
  "id": "NEW-UUID-HERE",
  "slug": "my-extension",
  "name": "My Extension Name",
  "aliases": ["myext", "my-ext"]
}
```

### Automatic Update (Future)

A script can be created to fetch extensions from Lucee's extension provider API:

```bash
# Future implementation
./scripts/update-extension-registry.sh
```

This would query https://download.lucee.org/ (or other providers) and generate the JSON file.

## Finding Extension IDs

1. **Lucee Admin**: Go to Extensions → Applications and inspect the extension
2. **Extension Provider**: Query the extension provider's `listApplications()` method
3. **Download URL**: Extension IDs are in the download URLs on download.lucee.org

## Contributing

When adding new extensions:
1. Use lowercase slugs (e.g., "redis" not "Redis")
2. Include common aliases developers might use
3. Verify the UUID is correct
4. Update the `lastUpdated` field
5. Test with: `lucli install` using the new slug
