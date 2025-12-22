package org.lucee.lucli.deps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Calculate SHA-512 checksums for installed dependencies
 */
public class ChecksumCalculator {
    
    /**
     * Calculate SHA-512 checksum for a directory
     * Files are hashed in alphabetical order for consistency
     * 
     * @param dir Directory to checksum
     * @return Hex-encoded SHA-512 hash
     * @throws Exception if calculation fails
     */
    public static String calculate(Path dir) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        
        // Collect all files (excluding .git)
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> !p.toString().contains("/.git/"))
                  .forEach(files::add);
        }
        
        // Sort files alphabetically for consistent hashing
        Collections.sort(files);
        
        // Hash each file
        for (Path file : files) {
            byte[] fileBytes = Files.readAllBytes(file);
            digest.update(fileBytes);
        }
        
        // Return hex-encoded hash
        return bytesToHex(digest.digest());
    }
    
    /**
     * Convert byte array to hex string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
