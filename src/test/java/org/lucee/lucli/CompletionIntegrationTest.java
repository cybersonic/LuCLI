package org.lucee.lucli;

import org.jline.reader.Candidate;
import org.jline.reader.ParsedLine;
import org.jline.reader.impl.DefaultParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Integration test for tab completion functionality
 * Tests the actual completion logic without requiring interactive input
 */
public class CompletionIntegrationTest {

    private LucliCompleter completer;
    private DefaultParser parser;
    private MockCommandProcessor mockProcessor;

    /**
     * Mock CommandProcessor for testing
     */
    private static class MockCommandProcessor extends CommandProcessor {
        public MockCommandProcessor() {
            super();
        }
        
        // Override methods needed for completion testing
        @Override
        public FileSystemState getFileSystemState() {
            return new FileSystemState();
        }
        
        @Override
        public Settings getSettings() {
            return new Settings();
        }
    }

    @BeforeEach
    void setUp() {
        mockProcessor = new MockCommandProcessor();
        completer = new LucliCompleter(mockProcessor);
        parser = new DefaultParser();
    }

    /**
     * Test completion for server config get version=7
     */
    @Test
    void testServerConfigGetVersionCompletion() {
        String input = "server config get version=7";
        
        List<Candidate> candidates = getCompletions(input);
        
        // Should find version completions starting with "7"
        List<String> versionCandidates = candidates.stream()
            .map(Candidate::value)
            .filter(value -> value.startsWith("7"))
            .collect(Collectors.toList());
        
        assertFalse(versionCandidates.isEmpty(), 
            "Should find version completions starting with '7'");
        
        // Should contain 7.x versions
        boolean has7xVersions = versionCandidates.stream()
            .anyMatch(version -> version.matches("7\\.\\d+\\.\\d+.*"));
        
        assertTrue(has7xVersions, 
            "Should contain properly formatted 7.x versions");
        
        System.out.println("Found version completions for 'version=7':");
        versionCandidates.forEach(version -> System.out.println("  " + version));
    }

    /**
     * Test completion for server config set version=7
     */
    @Test
    void testServerConfigSetVersionCompletion() {
        String input = "server config set version=7";
        
        List<Candidate> candidates = getCompletions(input);
        
        // Should find version completions starting with "7"
        List<String> versionCandidates = candidates.stream()
            .map(Candidate::value)
            .filter(value -> value.startsWith("7"))
            .collect(Collectors.toList());
        
        assertFalse(versionCandidates.isEmpty(), 
            "Should find version completions starting with '7'");
        
        System.out.println("Found version completions for 'set version=7':");
        versionCandidates.forEach(version -> System.out.println("  " + version));
    }

    /**
     * Test completion for server config get port=80
     */
    @Test
    void testServerConfigGetPortCompletion() {
        String input = "server config get port=80";
        
        List<Candidate> candidates = getCompletions(input);
        
        // Should find port completions starting with "80"
        List<String> portCandidates = candidates.stream()
            .map(Candidate::value)
            .filter(value -> value.startsWith("80"))
            .collect(Collectors.toList());
        
        assertFalse(portCandidates.isEmpty(), 
            "Should find port completions starting with '80'");
        
        System.out.println("Found port completions for 'port=80':");
        portCandidates.forEach(port -> System.out.println("  " + port));
    }

    /**
     * Test completion for boolean values
     */
    @Test
    void testServerConfigBooleanCompletion() {
        String input = "server config set monitoring.enabled=tr";
        
        List<Candidate> candidates = getCompletions(input);
        
        // Should find "true" completion
        List<String> booleanCandidates = candidates.stream()
            .map(Candidate::value)
            .filter(value -> value.equals("true"))
            .collect(Collectors.toList());
        
        assertFalse(booleanCandidates.isEmpty(), 
            "Should find 'true' completion for boolean field");
        
        System.out.println("Found boolean completions for 'monitoring.enabled=tr':");
        booleanCandidates.forEach(bool -> System.out.println("  " + bool));
    }

    /**
     * Test basic server subcommand completion
     */
    @Test
    void testServerSubcommandCompletion() {
        String input = "server conf";
        
        List<Candidate> candidates = getCompletions(input);
        
        // Should find "config" completion
        List<String> configCandidates = candidates.stream()
            .map(Candidate::value)
            .filter(value -> value.equals("config"))
            .collect(Collectors.toList());
        
        assertFalse(configCandidates.isEmpty(), 
            "Should find 'config' completion for 'server conf'");
        
        System.out.println("Found server subcommand completions for 'server conf':");
        candidates.forEach(candidate -> System.out.println("  " + candidate.value()));
    }

    /**
     * Test config get/set subcommand completion
     */
    @Test
    void testConfigSubcommandCompletion() {
        String input = "server config g";
        
        List<Candidate> candidates = getCompletions(input);
        
        // Should find both "get" completion
        List<String> getCandidates = candidates.stream()
            .map(Candidate::value)
            .filter(value -> value.equals("get"))
            .collect(Collectors.toList());
        
        assertFalse(getCandidates.isEmpty(), 
            "Should find 'get' completion for 'server config g'");
        
        System.out.println("Found config subcommand completions for 'server config g':");
        candidates.forEach(candidate -> System.out.println("  " + candidate.value()));
    }

    /**
     * Utility method to get completions for a given input
     */
    private List<Candidate> getCompletions(String input) {
        try {
            ParsedLine parsedLine = parser.parse(input, input.length());
            List<Candidate> candidates = new ArrayList<>();
            
            completer.complete(null, parsedLine, candidates);
            
            return candidates;
        } catch (Exception e) {
            fail("Failed to get completions for input '" + input + "': " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Integration test that verifies the ServerConfigHelper works
     */
    @Test
    void testVersionHelperIntegration() {
        try {
            org.lucee.lucli.commands.ServerConfigHelper helper = 
                new org.lucee.lucli.commands.ServerConfigHelper();
            
            List<String> versions = helper.getAvailableVersions();
            
            assertNotNull(versions, "Version list should not be null");
            assertFalse(versions.isEmpty(), "Version list should not be empty");
            
            // Check if 7.x versions are available
            boolean has7xVersions = versions.stream()
                .anyMatch(version -> version.startsWith("7"));
            
            assertTrue(has7xVersions, "Should have 7.x versions available");
            
            System.out.println("Available versions from ServerConfigHelper:");
            versions.forEach(version -> {
                if (version.startsWith("7")) {
                    System.out.println("  " + version + " (7.x version)");
                } else {
                    System.out.println("  " + version);
                }
            });
            
        } catch (Exception e) {
            fail("ServerConfigHelper integration test failed: " + e.getMessage());
        }
    }
}
