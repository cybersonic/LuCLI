package org.lucee.lucli.debug;

import javax.management.*;
import javax.management.remote.*;
import java.io.IOException;
import java.util.*;

/**
 * Utility class for exploring Lucee debug information via JMX
 * This is the foundation for the k9s-style debug interface
 */
public class DebugExplorer implements AutoCloseable {
    
    private MBeanServerConnection mbeanServer;
    private JMXConnector connector;
    private final String host;
    private final int port;
    
    public DebugExplorer(String host, int port) {
        this.host = host;
        this.port = port;
    }
    
    /**
     * Connect to the JMX server
     */
    public void connect() throws IOException {
        String serviceUrl = String.format("service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi", host, port);
        JMXServiceURL url = new JMXServiceURL(serviceUrl);
        
        try {
            connector = JMXConnectorFactory.connect(url);
            mbeanServer = connector.getMBeanServerConnection();
        } catch (IOException e) {
            throw new IOException("Failed to connect to JMX server at " + host + ":" + port, e);
        }
    }
    
    /**
     * Discover all Lucee-specific MBeans
     */
    public List<LuceeMBeanInfo> discoverLuceeMBeans() throws Exception {
        List<LuceeMBeanInfo> luceeBeans = new ArrayList<>();
        
        // Query all MBeans
        Set<ObjectName> allBeans = mbeanServer.queryNames(null, null);
        
        for (ObjectName objectName : allBeans) {
            String name = objectName.toString();
            
            // Look for Lucee-specific patterns
            if (name.toLowerCase().contains("lucee") || 
                name.toLowerCase().contains("cfml") ||
                name.toLowerCase().contains("coldfusion")) {
                
                MBeanInfo info = mbeanServer.getMBeanInfo(objectName);
                luceeBeans.add(new LuceeMBeanInfo(objectName, info));
            }
        }
        
        return luceeBeans;
    }
    
    /**
     * Get debug-related information from a specific MBean
     */
    public Map<String, Object> getDebugInfo(ObjectName objectName) throws Exception {
        Map<String, Object> debugInfo = new HashMap<>();
        
        MBeanInfo info = mbeanServer.getMBeanInfo(objectName);
        
        // Get all attributes
        for (MBeanAttributeInfo attrInfo : info.getAttributes()) {
            try {
                Object value = mbeanServer.getAttribute(objectName, attrInfo.getName());
                debugInfo.put(attrInfo.getName(), value);
            } catch (Exception e) {
                // Some attributes might not be readable
                debugInfo.put(attrInfo.getName(), "Error: " + e.getMessage());
            }
        }
        
        return debugInfo;
    }
    
    /**
     * Search for request-related MBeans
     */
    public List<ObjectName> findRequestMBeans() throws Exception {
        List<ObjectName> requestBeans = new ArrayList<>();
        
        // Common patterns for request/session related beans
        String[] patterns = {
            "*request*",
            "*session*", 
            "*thread*",
            "*execution*",
            "*performance*",
            "*debug*"
        };
        
        for (String pattern : patterns) {
            try {
                ObjectName query = new ObjectName(pattern);
                Set<ObjectName> matches = mbeanServer.queryNames(query, null);
                requestBeans.addAll(matches);
            } catch (MalformedObjectNameException e) {
                // Skip invalid patterns
            }
        }
        
        return requestBeans;
    }
    
    /**
     * Get all MBean operations that might provide debug data
     */
    public Map<ObjectName, List<String>> getDebugOperations() throws Exception {
        Map<ObjectName, List<String>> operations = new HashMap<>();
        
        Set<ObjectName> allBeans = mbeanServer.queryNames(null, null);
        
        for (ObjectName objectName : allBeans) {
            String name = objectName.toString().toLowerCase();
            
            // Focus on potentially debug-related beans
            if (name.contains("lucee") || name.contains("cfml") || 
                name.contains("request") || name.contains("debug")) {
                
                MBeanInfo info = mbeanServer.getMBeanInfo(objectName);
                List<String> beanOperations = new ArrayList<>();
                
                for (MBeanOperationInfo opInfo : info.getOperations()) {
                    beanOperations.add(opInfo.getName() + "() - " + opInfo.getDescription());
                }
                
                if (!beanOperations.isEmpty()) {
                    operations.put(objectName, beanOperations);
                }
            }
        }
        
        return operations;
    }
    
    /**
     * Test method to explore what's available
     */
    public String generateDebugReport() throws Exception {
        StringBuilder report = new StringBuilder();
        report.append("=== Lucee Debug Information Report ===\n\n");
        
        // 1. Lucee MBeans
        List<LuceeMBeanInfo> luceeBeans = discoverLuceeMBeans();
        report.append("Lucee MBeans Found: ").append(luceeBeans.size()).append("\n");
        
        for (LuceeMBeanInfo bean : luceeBeans) {
            report.append("  - ").append(bean.objectName).append("\n");
            report.append("    Attributes: ").append(bean.info.getAttributes().length).append("\n");
            report.append("    Operations: ").append(bean.info.getOperations().length).append("\n");
        }
        report.append("\n");
        
        // 2. Request-related MBeans
        List<ObjectName> requestBeans = findRequestMBeans();
        report.append("Request-related MBeans Found: ").append(requestBeans.size()).append("\n");
        for (ObjectName bean : requestBeans) {
            report.append("  - ").append(bean).append("\n");
        }
        report.append("\n");
        
        // 3. Debug operations
        Map<ObjectName, List<String>> operations = getDebugOperations();
        report.append("Debug Operations Found: ").append(operations.size()).append(" beans\n");
        
        for (Map.Entry<ObjectName, List<String>> entry : operations.entrySet()) {
            report.append("  Bean: ").append(entry.getKey()).append("\n");
            for (String op : entry.getValue()) {
                report.append("    - ").append(op).append("\n");
            }
        }
        
        return report.toString();
    }
    
    @Override
    public void close() throws IOException {
        if (connector != null) {
            connector.close();
        }
    }
    
    /**
     * Container class for Lucee MBean information
     */
    public static class LuceeMBeanInfo {
        public final ObjectName objectName;
        public final MBeanInfo info;
        
        public LuceeMBeanInfo(ObjectName objectName, MBeanInfo info) {
            this.objectName = objectName;
            this.info = info;
        }
    }
    
    /**
     * Main method for testing debug exploration
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: DebugExplorer <host> <port>");
            System.exit(1);
        }
        
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        
        try (DebugExplorer explorer = new DebugExplorer(host, port)) {
            explorer.connect();
            
            System.out.println("Connected to Lucee server at " + host + ":" + port);
            System.out.println(explorer.generateDebugReport());
            
        } catch (Exception e) {
            System.err.println("Error exploring debug information: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
