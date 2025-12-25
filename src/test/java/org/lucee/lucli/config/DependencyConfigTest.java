package org.lucee.lucli.config;

import java.nio.file.Paths;
import java.util.List;

/**
 * Simple test to verify dependency configuration parsing
 * Run with: mvn test -Dtest=DependencyConfigTest
 */
public class DependencyConfigTest {
    
    public static void main(String[] args) {
        try {
            System.out.println("=== Testing Dependency Configuration Parsing ===\n");
            
            // Load config
            LuceeJsonConfig config = LuceeJsonConfig.load(Paths.get("test-deps"));
            System.out.println("✓ Loaded lucee.json");
            System.out.println("  Project name: " + config.getName());
            
            // Parse dependencies
            System.out.println("\n--- Dependencies ---");
            List<DependencyConfig> deps = config.parseDependencies();
            for (DependencyConfig dep : deps) {
                System.out.println("\n" + dep.getName() + ":");
                System.out.println("  Type: " + dep.getType());
                System.out.println("  Version: " + dep.getVersion());
                System.out.println("  Source: " + dep.getSource());
                if (dep.getUrl() != null) {
                    System.out.println("  URL: " + dep.getUrl());
                }
                if (dep.getRef() != null) {
                    System.out.println("  Ref: " + dep.getRef());
                }
                if (dep.getSubPath() != null) {
                    System.out.println("  SubPath: " + dep.getSubPath());
                }
                System.out.println("  InstallPath: " + dep.getInstallPath());
                System.out.println("  Mapping: " + dep.getMapping());
            }
            
            // Parse devDependencies
            System.out.println("\n--- Dev Dependencies ---");
            List<DependencyConfig> devDeps = config.parseDevDependencies();
            for (DependencyConfig dep : devDeps) {
                System.out.println("\n" + dep.getName() + ":");
                System.out.println("  Type: " + dep.getType());
                System.out.println("  Version: " + dep.getVersion());
                System.out.println("  Source: " + dep.getSource());
                System.out.println("  InstallPath: " + dep.getInstallPath());
                System.out.println("  Mapping: " + dep.getMapping());
            }
            
            // Test environment config
            System.out.println("\n--- Environment Config (prod) ---");
            config.applyEnvironment("prod");
            System.out.println("  installDevDependencies: " + config.getPackages().getInstallDevDependencies());
            
            System.out.println("\n✓ All tests passed!");
            
        } catch (Exception e) {
            System.err.println("✗ Test failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
