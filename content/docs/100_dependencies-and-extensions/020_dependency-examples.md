---
title: Dependency Examples
layout: docs
---

Use this page as a copy/paste reference for dependency declarations in `lucee.json`.

Current `lucli deps install` support includes:

- Git dependencies (`source: "git"`)
- ForgeBox dependencies (`source: "forgebox"`)
- Lucee extensions (`type: "extension"`) by ID/slug, URL, or local `.lex` path

## Minimal scaffold

```json
{
  "dependencies": {},
  "devDependencies": {},
  "dependencySettings": {
    "useLockFile": true
  }
}
```

## Git dependency examples

### Git dependency (branch/tag/commit)

```json
{
  "dependencies": {
    "my-framework": {
      "type": "cfml",
      "source": "git",
      "url": "https://github.com/example/my-framework.git",
      "ref": "main",
      "installPath": "dependencies/my-framework",
      "mapping": "/my-framework"
    }
  }
}
```

### Git dependency from a subdirectory (`subPath`)

```json
{
  "dependencies": {
    "my-monorepo-module": {
      "type": "cfml",
      "source": "git",
      "url": "https://github.com/example/mono-repo.git",
      "ref": "v2.1.0",
      "subPath": "modules/my-monorepo-module",
      "installPath": "dependencies/my-monorepo-module",
      "mapping": "/my-monorepo-module"
    }
  }
}
```

## ForgeBox dependency examples

### ForgeBox package (version pinned)

```json
{
  "dependencies": {
    "testbox": {
      "type": "cfml",
      "source": "forgebox",
      "version": "5.0.0",
      "installPath": "dependencies/testbox",
      "mapping": "/testbox"
    }
  }
}
```

### ForgeBox package (latest)

```json
{
  "dependencies": {
    "wirebox": {
      "type": "cfml",
      "source": "forgebox",
      "installPath": "dependencies/wirebox",
      "mapping": "/wirebox"
    }
  }
}
```

## Lucee extension examples

### Extension by slug/name (via `id`)

```json
{
  "dependencies": {
    "redis": {
      "type": "extension",
      "id": "redis"
    }
  }
}
```

### Extension by UUID

```json
{
  "dependencies": {
    "h2": {
      "type": "extension",
      "id": "465E1E35-2425-4F4E-8B3FAB638BD7280A"
    }
  }
}
```

### Extension from URL

```json
{
  "dependencies": {
    "my-url-extension": {
      "type": "extension",
      "url": "https://extensions.example.com/my-ext.lex",
      "installPath": "extensions/my-ext.lex"
    }
  }
}
```

### Extension from local `.lex` file

```json
{
  "dependencies": {
    "my-local-extension": {
      "type": "extension",
      "path": "./extensions/my-local-ext.lex",
      "installPath": "extensions/my-local-ext.lex"
    }
  }
}
```

## Dev dependencies example

```json
{
  "dependencies": {
    "my-framework": {
      "type": "cfml",
      "source": "git",
      "url": "https://github.com/example/my-framework.git",
      "ref": "main",
      "installPath": "dependencies/my-framework",
      "mapping": "/my-framework"
    }
  },
  "devDependencies": {
    "testbox": {
      "type": "cfml",
      "source": "forgebox",
      "version": "5.0.0",
      "installPath": "dependencies/testbox",
      "mapping": "/testbox"
    }
  }
}
```

## Dependency settings example

```json
{
  "dependencySettings": {
    "useLockFile": true,
    "materializeExtensionsOnInstall": true,
    "installDevDependencies": true
  }
}
```

## Install commands

```bash
# Install dependencies + devDependencies
lucli deps install

# Install only production dependencies
lucli deps install --production

# Apply environment-specific overrides before install
lucli deps install --env prod

# Preview what will be installed
lucli deps install --dry-run

# Preview root + nested dependency projects
lucli deps install --dry-run --include-nested-deps
```
