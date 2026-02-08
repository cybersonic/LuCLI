package org.lucee.lucli.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lucee.lucli.deps.LockedDependency;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LuceeLockFile class.
 * Tests lock file read/write, hash computation, and server locks.
 */
class LuceeLockFileTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Each test gets a clean temp directory
    }

    // ============================================
    // Constructor Tests
    // ============================================

    @Test
    void testDefaultConstructor() {
        LuceeLockFile lockFile = new LuceeLockFile();
        
        assertEquals(1, lockFile.getLockfileVersion());
        assertNotNull(lockFile.getGeneratedAt());
        assertNotNull(lockFile.getDependencies());
        assertNotNull(lockFile.getDevDependencies());
        assertNotNull(lockFile.getServerLocks());
        assertTrue(lockFile.getDependencies().isEmpty());
        assertTrue(lockFile.getDevDependencies().isEmpty());
        assertTrue(lockFile.getServerLocks().isEmpty());
    }

    // ============================================
    // Read Tests
    // ============================================

    @Test
    void testReadNonExistentFile() {
        LuceeLockFile lockFile = LuceeLockFile.read(tempDir);
        
        // Should return empty lock file
        assertNotNull(lockFile);
        assertTrue(lockFile.getDependencies().isEmpty());
    }

    @Test
    void testReadValidLockFile() throws IOException {
        // Create a valid lock file
        String json = """
            {
                "lockfileVersion": 1,
                "generatedAt": "2024-01-01T00:00:00",
                "lucliVersion": "1.0.0",
                "dependencies": {},
                "devDependencies": {},
                "serverLocks": {}
            }
            """;
        Files.writeString(tempDir.resolve("lucee-lock.json"), json);
        
        LuceeLockFile lockFile = LuceeLockFile.read(tempDir);
        
        assertNotNull(lockFile);
        assertEquals(1, lockFile.getLockfileVersion());
        assertEquals("2024-01-01T00:00:00", lockFile.getGeneratedAt());
    }

    @Test
    void testReadCorruptedFile() throws IOException {
        // Create invalid JSON
        Files.writeString(tempDir.resolve("lucee-lock.json"), "{ invalid json }");
        
        // Should return empty lock file
        LuceeLockFile lockFile = LuceeLockFile.read(tempDir);
        assertNotNull(lockFile);
    }

    @Test
    void testReadFromFile() throws IOException {
        String json = """
            {
                "lockfileVersion": 1,
                "generatedAt": "2024-01-01T00:00:00",
                "dependencies": {},
                "devDependencies": {},
                "serverLocks": {}
            }
            """;
        Files.writeString(tempDir.resolve("lucee-lock.json"), json);
        
        LuceeLockFile lockFile = LuceeLockFile.read(tempDir.toFile());
        assertNotNull(lockFile);
    }

    @Test
    void testReadFromCurrentDirectory() {
        // This tests the no-arg read() method
        LuceeLockFile lockFile = LuceeLockFile.read();
        assertNotNull(lockFile);
    }

    // ============================================
    // Write Tests
    // ============================================

    @Test
    void testWriteLockFile() throws IOException {
        LuceeLockFile lockFile = new LuceeLockFile();
        lockFile.write(tempDir.toFile());
        
        Path lockFilePath = tempDir.resolve("lucee-lock.json");
        assertTrue(Files.exists(lockFilePath));
        
        String content = Files.readString(lockFilePath);
        assertTrue(content.contains("lockfileVersion"));
        assertTrue(content.contains("generatedAt"));
    }

    @Test
    void testWriteUpdatesTimestamp() throws IOException {
        LuceeLockFile lockFile = new LuceeLockFile();
        String originalTimestamp = lockFile.getGeneratedAt();
        
        // Small delay to ensure different timestamp
        try { Thread.sleep(10); } catch (InterruptedException e) {}
        
        lockFile.write(tempDir.toFile());
        
        // Timestamp should be updated
        assertNotEquals(originalTimestamp, lockFile.getGeneratedAt());
    }

    // ============================================
    // Dependency Tests
    // ============================================

    @Test
    void testSetAndGetDependencies() {
        LuceeLockFile lockFile = new LuceeLockFile();
        
        Map<String, LockedDependency> deps = new LinkedHashMap<>();
        LockedDependency dep = new LockedDependency();
        dep.setVersion("1.0.0");
        dep.setResolved("abc123");
        deps.put("my-framework", dep);
        
        lockFile.setDependencies(deps);
        
        assertEquals(1, lockFile.getDependencies().size());
        assertEquals("1.0.0", lockFile.getDependencies().get("my-framework").getVersion());
    }

    @Test
    void testSetAndGetDevDependencies() {
        LuceeLockFile lockFile = new LuceeLockFile();
        
        Map<String, LockedDependency> devDeps = new LinkedHashMap<>();
        LockedDependency dep = new LockedDependency();
        dep.setVersion("2.0.0");
        devDeps.put("test-framework", dep);
        
        lockFile.setDevDependencies(devDeps);
        
        assertEquals(1, lockFile.getDevDependencies().size());
        assertEquals("2.0.0", lockFile.getDevDependencies().get("test-framework").getVersion());
    }

    // ============================================
    // Server Lock Tests
    // ============================================

    @Test
    void testGetServerLocksInitialized() {
        LuceeLockFile lockFile = new LuceeLockFile();
        assertNotNull(lockFile.getServerLocks());
    }

    @Test
    void testPutServerLock() {
        LuceeLockFile lockFile = new LuceeLockFile();
        
        LuceeLockFile.ServerLock serverLock = new LuceeLockFile.ServerLock();
        serverLock.locked = true;
        serverLock.environment = "prod";
        serverLock.configFile = "lucee.json";
        serverLock.configHash = "abc123def456";
        serverLock.lockedAt = "2024-01-01T00:00:00";
        
        lockFile.putServerLock("prod", serverLock);
        
        assertEquals(1, lockFile.getServerLocks().size());
        assertNotNull(lockFile.getServerLock("prod"));
        assertTrue(lockFile.getServerLock("prod").locked);
    }

    @Test
    void testGetServerLockNonExistent() {
        LuceeLockFile lockFile = new LuceeLockFile();
        assertNull(lockFile.getServerLock("nonexistent"));
    }

    @Test
    void testGetServerLockNullKey() {
        LuceeLockFile lockFile = new LuceeLockFile();
        assertNull(lockFile.getServerLock(null));
    }

    @Test
    void testGetLockedEnvironments() {
        LuceeLockFile lockFile = new LuceeLockFile();
        
        LuceeLockFile.ServerLock prodLock = new LuceeLockFile.ServerLock();
        prodLock.locked = true;
        prodLock.environment = "prod";
        
        LuceeLockFile.ServerLock devLock = new LuceeLockFile.ServerLock();
        devLock.locked = false;
        devLock.environment = "dev";
        
        LuceeLockFile.ServerLock stagingLock = new LuceeLockFile.ServerLock();
        stagingLock.locked = true;
        stagingLock.environment = "staging";
        
        lockFile.putServerLock("prod", prodLock);
        lockFile.putServerLock("dev", devLock);
        lockFile.putServerLock("staging", stagingLock);
        
        Set<String> lockedEnvs = lockFile.getLockedEnvironments();
        
        assertEquals(2, lockedEnvs.size());
        assertTrue(lockedEnvs.contains("prod"));
        assertTrue(lockedEnvs.contains("staging"));
        assertFalse(lockedEnvs.contains("dev"));
    }

    @Test
    void testGetLockedEnvironmentsEmpty() {
        LuceeLockFile lockFile = new LuceeLockFile();
        Set<String> lockedEnvs = lockFile.getLockedEnvironments();
        assertTrue(lockedEnvs.isEmpty());
    }

    // ============================================
    // Hash Computation Tests
    // ============================================

    @Test
    void testComputeConfigHashValid() throws IOException {
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, "{\"name\": \"test\"}");
        
        String hash = LuceeLockFile.computeConfigHash(configFile);
        
        assertNotNull(hash);
        assertFalse(hash.isEmpty());
        assertEquals(64, hash.length()); // SHA-256 produces 64 hex characters
    }

    @Test
    void testComputeConfigHashConsistent() throws IOException {
        Path configFile = tempDir.resolve("lucee.json");
        Files.writeString(configFile, "{\"name\": \"test\"}");
        
        String hash1 = LuceeLockFile.computeConfigHash(configFile);
        String hash2 = LuceeLockFile.computeConfigHash(configFile);
        
        assertEquals(hash1, hash2);
    }

    @Test
    void testComputeConfigHashDifferentContent() throws IOException {
        Path configFile1 = tempDir.resolve("lucee1.json");
        Path configFile2 = tempDir.resolve("lucee2.json");
        
        Files.writeString(configFile1, "{\"name\": \"test1\"}");
        Files.writeString(configFile2, "{\"name\": \"test2\"}");
        
        String hash1 = LuceeLockFile.computeConfigHash(configFile1);
        String hash2 = LuceeLockFile.computeConfigHash(configFile2);
        
        assertNotEquals(hash1, hash2);
    }

    @Test
    void testComputeConfigHashNonExistentFile() {
        Path nonExistent = tempDir.resolve("nonexistent.json");
        String hash = LuceeLockFile.computeConfigHash(nonExistent);
        assertNull(hash);
    }

    @Test
    void testComputeConfigHashNullPath() {
        String hash = LuceeLockFile.computeConfigHash(null);
        assertNull(hash);
    }

    // ============================================
    // Getters and Setters Tests
    // ============================================

    @Test
    void testLockfileVersionGetterSetter() {
        LuceeLockFile lockFile = new LuceeLockFile();
        lockFile.setLockfileVersion(2);
        assertEquals(2, lockFile.getLockfileVersion());
    }

    @Test
    void testGeneratedAtGetterSetter() {
        LuceeLockFile lockFile = new LuceeLockFile();
        lockFile.setGeneratedAt("2024-06-15T12:00:00");
        assertEquals("2024-06-15T12:00:00", lockFile.getGeneratedAt());
    }

    @Test
    void testLucliVersionGetterSetter() {
        LuceeLockFile lockFile = new LuceeLockFile();
        lockFile.setLucliVersion("2.0.0");
        assertEquals("2.0.0", lockFile.getLucliVersion());
    }

    @Test
    void testSetServerLocks() {
        LuceeLockFile lockFile = new LuceeLockFile();
        Map<String, LuceeLockFile.ServerLock> locks = new LinkedHashMap<>();
        
        LuceeLockFile.ServerLock lock = new LuceeLockFile.ServerLock();
        lock.locked = true;
        locks.put("test", lock);
        
        lockFile.setServerLocks(locks);
        
        assertEquals(1, lockFile.getServerLocks().size());
        assertTrue(lockFile.getServerLock("test").locked);
    }

    // ============================================
    // Round-Trip Tests
    // ============================================

    @Test
    void testRoundTripWithDependencies() throws IOException {
        LuceeLockFile original = new LuceeLockFile();
        
        Map<String, LockedDependency> deps = new LinkedHashMap<>();
        LockedDependency dep = new LockedDependency();
        dep.setVersion("1.0.0");
        dep.setResolved("abc123");
        dep.setIntegrity("sha256-xxx");
        deps.put("my-dep", dep);
        original.setDependencies(deps);
        
        original.write(tempDir.toFile());
        
        LuceeLockFile loaded = LuceeLockFile.read(tempDir);
        
        assertEquals(1, loaded.getDependencies().size());
        assertEquals("1.0.0", loaded.getDependencies().get("my-dep").getVersion());
    }

    @Test
    void testRoundTripWithServerLocks() throws IOException {
        LuceeLockFile original = new LuceeLockFile();
        
        LuceeLockFile.ServerLock lock = new LuceeLockFile.ServerLock();
        lock.locked = true;
        lock.environment = "prod";
        lock.configFile = "lucee.json";
        lock.configHash = "abc123";
        lock.lockedAt = "2024-01-01T00:00:00";
        
        original.putServerLock("prod", lock);
        original.write(tempDir.toFile());
        
        LuceeLockFile loaded = LuceeLockFile.read(tempDir);
        
        LuceeLockFile.ServerLock loadedLock = loaded.getServerLock("prod");
        assertNotNull(loadedLock);
        assertTrue(loadedLock.locked);
        assertEquals("prod", loadedLock.environment);
        assertEquals("abc123", loadedLock.configHash);
    }

    // ============================================
    // ServerLock Class Tests
    // ============================================

    @Test
    void testServerLockFields() {
        LuceeLockFile.ServerLock lock = new LuceeLockFile.ServerLock();
        
        lock.locked = true;
        lock.environment = "staging";
        lock.configFile = "custom-lucee.json";
        lock.configHash = "hash123";
        lock.lockedAt = "2024-06-01T10:00:00";
        
        assertTrue(lock.locked);
        assertEquals("staging", lock.environment);
        assertEquals("custom-lucee.json", lock.configFile);
        assertEquals("hash123", lock.configHash);
        assertEquals("2024-06-01T10:00:00", lock.lockedAt);
    }
}
