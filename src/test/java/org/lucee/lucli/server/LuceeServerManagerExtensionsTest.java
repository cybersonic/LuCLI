package org.lucee.lucli.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LuceeServerManagerExtensionsTest {

    @TempDir
    Path tempDir;

    @Test
    void buildLuceeExtensions_usesProviderExtensionsFromLuceeJsonWhenLockfileDisabled() throws Exception {
        Files.writeString(tempDir.resolve("lucee.json"), """
            {
              "name": "ext-env-preview",
              "dependencySettings": {
                "useLockFile": false
              },
              "dependencies": {
                "h2": {
                  "type": "extension",
                  "id": "465E1E35-2425-4F4E-8B3FAB638BD7280A"
                }
              }
            }
            """);


        String luceeExtensions = LuceeServerManager.buildLuceeExtensions(tempDir);
        assertEquals("465E1E35-2425-4F4E-8B3FAB638BD7280A", luceeExtensions,
                "LUCEE_EXTENSIONS should include provider extension ID from lucee.json when lockfile is disabled");
    }

    @Test
    void buildLuceeExtensions_returnsEmptyWhenNoProviderExtensionsDeclared() throws Exception {
        Files.writeString(tempDir.resolve("lucee.json"), """
            {
              "name": "no-extensions",
              "dependencySettings": {
                "useLockFile": false
              },
              "dependencies": {
                "framework-one": {
                  "type": "cfml",
                  "url": "https://github.com/example/framework-one.git"
                }
              }
            }
            """);

        String luceeExtensions = LuceeServerManager.buildLuceeExtensions(tempDir);

        assertEquals("", luceeExtensions);
    }

    @Test
    void buildLuceeExtensions_ignoresDisabledProviderExtensionsFromLuceeJson() throws Exception {
        Files.writeString(tempDir.resolve("lucee.json"), """
            {
              "name": "disabled-provider-ext",
              "dependencySettings": {
                "useLockFile": false
              },
              "dependencies": {
                "h2": {
                  "type": "extension",
                  "id": "465E1E35-2425-4F4E-8B3FAB638BD7280A",
                  "enabled": false
                }
              }
            }
            """);

        String luceeExtensions = LuceeServerManager.buildLuceeExtensions(tempDir);
        assertEquals("", luceeExtensions);
    }

    @Test
    void buildLuceeExtensions_envOverrideDisablesExtension_noLockFile() throws Exception {
        Files.writeString(tempDir.resolve("lucee.json"), """
            {
              "name": "env-override-disable",
              "dependencySettings": {
                "useLockFile": false
              },
              "dependencies": {
                "h2": {
                  "type": "extension",
                  "id": "465E1E35-2425-4F4E-8B3FAB638BD7280A",
                  "enabled": true
                }
              },
              "environments": {
                "prod": {
                  "dependencies": {
                    "h2": {
                      "enabled": false
                    }
                  }
                }
              }
            }
            """);

        String luceeExtensions = LuceeServerManager.buildLuceeExtensions(tempDir, "prod");
        assertEquals("", luceeExtensions,
                "env override setting enabled=false should exclude extension from LUCEE_EXTENSIONS");
    }

    @Test
    void buildLuceeExtensions_envOverrideDisablesExtension_withLockFile() throws Exception {
        Files.writeString(tempDir.resolve("lucee.json"), """
            {
              "name": "env-override-disable-lock",
              "dependencySettings": {
                "useLockFile": true
              },
              "dependencies": {
                "h2": {
                  "type": "extension",
                  "id": "465E1E35-2425-4F4E-8B3FAB638BD7280A",
                  "enabled": true
                }
              },
              "environments": {
                "prod": {
                  "dependencies": {
                    "h2": {
                      "enabled": false
                    }
                  }
                }
              }
            }
            """);

        Files.writeString(tempDir.resolve("lucee-lock.json"), """
            {
              "dependencies": {
                "h2": {
                  "type": "extension",
                  "version": "unknown",
                  "source": "extension-provider",
                  "id": "465E1E35-2425-4F4E-8B3FAB638BD7280A"
                }
              },
              "devDependencies": {}
            }
            """);

        String luceeExtensions = LuceeServerManager.buildLuceeExtensions(tempDir, "prod");
        assertEquals("", luceeExtensions,
                "env override setting enabled=false should exclude extension from lock file path too");
    }

    @Test
    void buildLuceeExtensions_ignoresDisabledExtensionsFromLockFileWhenConfiguredDisabled() throws Exception {
        Files.writeString(tempDir.resolve("lucee.json"), """
            {
              "name": "disabled-provider-ext-lock",
              "dependencySettings": {
                "useLockFile": true
              },
              "dependencies": {
                "h2": {
                  "type": "extension",
                  "id": "465E1E35-2425-4F4E-8B3FAB638BD7280A",
                  "enabled": false
                }
              }
            }
            """);

        Files.writeString(tempDir.resolve("lucee-lock.json"), """
            {
              "dependencies": {
                "h2": {
                  "type": "extension",
                  "version": "unknown",
                  "source": "extension-provider",
                  "id": "465E1E35-2425-4F4E-8B3FAB638BD7280A"
                }
              },
              "devDependencies": {}
            }
            """);

        String luceeExtensions = LuceeServerManager.buildLuceeExtensions(tempDir);
        assertEquals("", luceeExtensions);
    }
}
