package org.lucee.lucli.profile;

/**
 * Defines branding and directory conventions for a CLI binary identity.
 *
 * <p>LuCLI can be invoked under different binary names (e.g. {@code lucli},
 * {@code wheels}) via symlinks or the {@code -Dlucli.binary.name} system
 * property. Each binary name maps to a profile that controls the home
 * directory name, ASCII art banner, and interactive prompt prefix.</p>
 */
public interface CliProfile {

    /** Short identifier for this profile, e.g. {@code "lucli"} or {@code "wheels"}. */
    String name();

    /** Directory name under the user's home for caches/config, e.g. {@code ".lucli"}. */
    String homeDirName();

    /** ASCII art banner displayed in {@code --version} output. */
    String bannerText();

    /** Prefix shown in interactive prompts, e.g. {@code "cfml"} or {@code "wheels"}. */
    String promptPrefix();

    /** Human-readable display name for version output, e.g. {@code "LuCLI"} or {@code "Wheels"}. */
    String displayName();

    /**
     * Directory name for backups, placed alongside the home directory.
     * Defaults to {@code homeDirName() + "_backups"} (e.g. {@code ".lucli_backups"}).
     */
    default String backupsDirName() {
        return homeDirName() + "_backups";
    }

    /**
     * Resolve the appropriate profile for the given binary name.
     *
     * <p>The binary name is normalised before matching: path separators are
     * stripped (keeping only the filename) and common wrapper extensions
     * ({@code .sh}, {@code .bat}, {@code .cmd}, {@code .exe}) are removed.
     * This ensures that values like {@code /usr/local/bin/wheels} or
     * {@code wheels.sh} resolve correctly.</p>
     *
     * @param binaryName the name the CLI was invoked as (may be {@code null})
     * @return a {@link WheelsProfile} when the normalised binary name is
     *         {@code "wheels"} (case-insensitive), a {@link MarkspressoProfile}
     *         when it is {@code "markspresso"}, otherwise a {@link DefaultProfile}
     */
    static CliProfile forBinaryName(String binaryName) {
        String normalised = normalizeBinaryName(binaryName);
        CliProfile buildBrandedProfile = BuildBrandingConfig.resolveProfile(normalised);
        if (buildBrandedProfile != null) {
            return buildBrandedProfile;
        }
        if (normalised != null && normalised.equalsIgnoreCase("wheels")) {
            return new WheelsProfile();
        }
        if (normalised != null && normalised.equalsIgnoreCase("markspresso")) {
            return new MarkspressoProfile();
        }
        return new DefaultProfile();
    }

    /**
     * Strip path components and common wrapper extensions from a binary name.
     *
     * @param binaryName raw binary name (may contain path or extensions)
     * @return the bare binary name, or {@code null} if input was {@code null}
     */
    static String normalizeBinaryName(String binaryName) {
        if (binaryName == null) {
            return null;
        }
        String name = binaryName.trim();
        // Strip path — keep only the filename component
        int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSlash >= 0 && lastSlash < name.length() - 1) {
            name = name.substring(lastSlash + 1);
        }
        // Strip common wrapper extensions
        for (String ext : new String[]{".sh", ".bat", ".cmd", ".exe"}) {
            if (name.toLowerCase().endsWith(ext)) {
                name = name.substring(0, name.length() - ext.length());
                break;
            }
        }
        return name;
    }
}
