package org.lucee.lucli.profile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Build-time branding configuration loaded from a filtered resource file.
 *
 * <p>When enabled, this allows a build to define a branded profile (name, home
 * directory, prompt, banner) without changing Java source code. Values are read
 * from {@code /branding/branding.properties}.</p>
 */
final class BuildBrandingConfig {

    private static final String RESOURCE_PATH = "/branding/branding.properties";
    private static volatile BuildBrandingConfig cached;

    private final boolean enabled;
    private final String binaryName;
    private final String profileName;
    private final String displayName;
    private final String promptPrefix;
    private final String homeDirName;
    private final String backupsDirName;
    private final String bannerText;

    private BuildBrandingConfig(
        boolean enabled,
        String binaryName,
        String profileName,
        String displayName,
        String promptPrefix,
        String homeDirName,
        String backupsDirName,
        String bannerText
    ) {
        this.enabled = enabled;
        this.binaryName = binaryName;
        this.profileName = profileName;
        this.displayName = displayName;
        this.promptPrefix = promptPrefix;
        this.homeDirName = homeDirName;
        this.backupsDirName = backupsDirName;
        this.bannerText = bannerText;
    }

    static CliProfile resolveProfile(String normalizedBinaryName) {
        if (normalizedBinaryName == null || normalizedBinaryName.isBlank()) {
            return null;
        }
        BuildBrandingConfig config = get();
        if (!config.enabled || config.binaryName == null || config.binaryName.isBlank()) {
            return null;
        }
        if (!config.binaryName.equalsIgnoreCase(normalizedBinaryName)) {
            return null;
        }
        return config.toProfile();
    }

    static BuildBrandingConfig fromProperties(Properties props) {
        boolean enabled = Boolean.parseBoolean(trimToEmpty(props.getProperty("branding.enabled")));
        String binaryName = CliProfile.normalizeBinaryName(trimToNull(props.getProperty("branding.binaryName")));
        String profileName = trimToNull(props.getProperty("branding.profileName"));
        String displayName = trimToNull(props.getProperty("branding.displayName"));
        String promptPrefix = trimToNull(props.getProperty("branding.promptPrefix"));
        String homeDirName = trimToNull(props.getProperty("branding.homeDirName"));
        String backupsDirName = trimToNull(props.getProperty("branding.backupsDirName"));
        String bannerText = decodeEscapedNewlines(trimToNull(props.getProperty("branding.bannerText")));

        return new BuildBrandingConfig(
            enabled,
            binaryName,
            profileName,
            displayName,
            promptPrefix,
            homeDirName,
            backupsDirName,
            bannerText
        );
    }

    static void clearCacheForTests() {
        cached = null;
    }

    private static BuildBrandingConfig get() {
        BuildBrandingConfig current = cached;
        if (current != null) {
            return current;
        }
        synchronized (BuildBrandingConfig.class) {
            if (cached == null) {
                cached = loadFromResource();
            }
            return cached;
        }
    }

    private static BuildBrandingConfig loadFromResource() {
        try (InputStream in = BuildBrandingConfig.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                return new BuildBrandingConfig(false, null, null, null, null, null, null, null);
            }
            Properties props = new Properties();
            props.load(in);
            return fromProperties(props);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load branding config from " + RESOURCE_PATH, e);
        }
    }

    private CliProfile toProfile() {
        String resolvedName = firstNonBlank(profileName, binaryName, "lucli");
        String resolvedDisplayName = firstNonBlank(displayName, capitalize(resolvedName));
        String resolvedPromptPrefix = firstNonBlank(promptPrefix, resolvedName);
        String resolvedHome = firstNonBlank(homeDirName, "." + resolvedName);
        String resolvedBanner = firstNonBlank(bannerText, new DefaultProfile().bannerText());
        String resolvedBackups = trimToNull(backupsDirName);

        return new CliProfile() {
            @Override
            public String name() {
                return resolvedName;
            }

            @Override
            public String homeDirName() {
                return resolvedHome;
            }

            @Override
            public String bannerText() {
                return resolvedBanner;
            }

            @Override
            public String promptPrefix() {
                return resolvedPromptPrefix;
            }

            @Override
            public String displayName() {
                return resolvedDisplayName;
            }

            @Override
            public String backupsDirName() {
                if (resolvedBackups == null || resolvedBackups.isBlank()) {
                    return CliProfile.super.backupsDirName();
                }
                return resolvedBackups;
            }
        };
    }

    CliProfile toProfileForTests() {
        return toProfile();
    }

    private static String decodeEscapedNewlines(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\\n", "\n");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String trimToEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "LuCLI";
        }
        if (value.length() == 1) {
            return value.toUpperCase();
        }
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }
}
