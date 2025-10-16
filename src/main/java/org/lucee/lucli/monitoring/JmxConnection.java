package org.lucee.lucli.monitoring;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 * Utility class for connecting to and retrieving metrics from JMX-enabled Lucee servers
 */
public class JmxConnection implements AutoCloseable {
    
    private MBeanServerConnection mbeanServer;
    private JMXConnector connector;
    private final String host;
    private final int port;
    
    public JmxConnection(String host, int port) {
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
            throw new IOException("Failed to connect to JMX server at " + host + ":" + port + 
                                ". Make sure the server is running with JMX enabled.", e);
        }
    }
    
    /**
     * Test if the connection is alive
     */
    public boolean isConnected() {
        try {
            return mbeanServer != null && mbeanServer.getMBeanCount() > 0;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Get memory usage information
     */
    public MemoryMetrics getMemoryMetrics() throws Exception {
        ObjectName memoryName = new ObjectName("java.lang:type=Memory");
        
        CompositeData heapMemory = (CompositeData) mbeanServer.getAttribute(memoryName, "HeapMemoryUsage");
        CompositeData nonHeapMemory = (CompositeData) mbeanServer.getAttribute(memoryName, "NonHeapMemoryUsage");
        
        return new MemoryMetrics(
            (Long) heapMemory.get("used"),
            (Long) heapMemory.get("committed"),
            (Long) heapMemory.get("max"),
            (Long) nonHeapMemory.get("used"),
            (Long) nonHeapMemory.get("committed"),
            (Long) nonHeapMemory.get("max")
        );
    }
    
    /**
     * Get threading information
     */
    public ThreadingMetrics getThreadingMetrics() throws Exception {
        ObjectName threadingName = new ObjectName("java.lang:type=Threading");
        
        Integer threadCount = (Integer) mbeanServer.getAttribute(threadingName, "ThreadCount");
        Integer peakThreadCount = (Integer) mbeanServer.getAttribute(threadingName, "PeakThreadCount");
        Integer daemonThreadCount = (Integer) mbeanServer.getAttribute(threadingName, "DaemonThreadCount");
        
        return new ThreadingMetrics(threadCount, peakThreadCount, daemonThreadCount);
    }
    
    /**
     * Get garbage collection information
     */
    public List<GcMetrics> getGcMetrics() throws Exception {
        Set<ObjectName> gcNames = mbeanServer.queryNames(
            new ObjectName("java.lang:type=GarbageCollector,name=*"), null);
        
        List<GcMetrics> gcMetrics = new ArrayList<>();
        
        for (ObjectName gcName : gcNames) {
            String name = gcName.getKeyProperty("name");
            Long collectionCount = (Long) mbeanServer.getAttribute(gcName, "CollectionCount");
            Long collectionTime = (Long) mbeanServer.getAttribute(gcName, "CollectionTime");
            
            gcMetrics.add(new GcMetrics(name, collectionCount, collectionTime));
        }
        
        return gcMetrics;
    }
    
    /**
     * Get runtime information
     */
    public RuntimeMetrics getRuntimeMetrics() throws Exception {
        ObjectName runtimeName = new ObjectName("java.lang:type=Runtime");
        
        Long uptime = (Long) mbeanServer.getAttribute(runtimeName, "Uptime");
        String vmName = (String) mbeanServer.getAttribute(runtimeName, "VmName");
        String vmVersion = (String) mbeanServer.getAttribute(runtimeName, "VmVersion");
        String vmVendor = (String) mbeanServer.getAttribute(runtimeName, "VmVendor");
        
        return new RuntimeMetrics(uptime, vmName, vmVersion, vmVendor);
    }
    
    /**
     * Get operating system information
     */
    public OsMetrics getOsMetrics() throws Exception {
        ObjectName osName = new ObjectName("java.lang:type=OperatingSystem");
        
        Double processCpuLoad = null;
        Double systemCpuLoad = null;
        Long totalPhysicalMemorySize = null;
        Long freePhysicalMemorySize = null;
        
        try {
            // These attributes might not be available on all JVM implementations
            processCpuLoad = (Double) mbeanServer.getAttribute(osName, "ProcessCpuLoad");
            systemCpuLoad = (Double) mbeanServer.getAttribute(osName, "SystemCpuLoad");
            totalPhysicalMemorySize = (Long) mbeanServer.getAttribute(osName, "TotalPhysicalMemorySize");
            freePhysicalMemorySize = (Long) mbeanServer.getAttribute(osName, "FreePhysicalMemorySize");
        } catch (Exception e) {
            // Some OS metrics might not be available, continue without them
        }
        
        Integer availableProcessors = (Integer) mbeanServer.getAttribute(osName, "AvailableProcessors");
        Double systemLoadAverage = (Double) mbeanServer.getAttribute(osName, "SystemLoadAverage");
        
        return new OsMetrics(processCpuLoad, systemCpuLoad, availableProcessors, 
                           systemLoadAverage, totalPhysicalMemorySize, freePhysicalMemorySize);
    }
    
    /**
     * Try to get Lucee-specific metrics if available
     */
    public LuceeMetrics getLuceeMetrics() throws Exception {
        try {
            // Try to find Lucee-specific MBeans
            Set<ObjectName> luceeNames = mbeanServer.queryNames(
                new ObjectName("lucee:*"), null);
            
            // This will depend on what Lucee exposes via JMX
            // For now, return a placeholder
            return new LuceeMetrics(luceeNames.size(), new HashMap<>());
        } catch (Exception e) {
            // Lucee metrics not available
            return new LuceeMetrics(0, new HashMap<>());
        }
    }
    
    @Override
    public void close() throws IOException {
        if (connector != null) {
            connector.close();
        }
    }
    
    // Inner classes for metrics data
    public static class MemoryMetrics {
        public final long heapUsed, heapCommitted, heapMax;
        public final long nonHeapUsed, nonHeapCommitted, nonHeapMax;
        
        public MemoryMetrics(long heapUsed, long heapCommitted, long heapMax,
                           long nonHeapUsed, long nonHeapCommitted, long nonHeapMax) {
            this.heapUsed = heapUsed;
            this.heapCommitted = heapCommitted;
            this.heapMax = heapMax;
            this.nonHeapUsed = nonHeapUsed;
            this.nonHeapCommitted = nonHeapCommitted;
            this.nonHeapMax = nonHeapMax;
        }
        
        public double getHeapUsagePercent() {
            return heapMax > 0 ? (double) heapUsed / heapMax * 100 : 0;
        }
        
        public double getNonHeapUsagePercent() {
            return nonHeapMax > 0 ? (double) nonHeapUsed / nonHeapMax * 100 : 0;
        }
    }
    
    public static class ThreadingMetrics {
        public final int threadCount, peakThreadCount, daemonThreadCount;
        
        public ThreadingMetrics(int threadCount, int peakThreadCount, int daemonThreadCount) {
            this.threadCount = threadCount;
            this.peakThreadCount = peakThreadCount;
            this.daemonThreadCount = daemonThreadCount;
        }
    }
    
    public static class GcMetrics {
        public final String name;
        public final long collectionCount, collectionTime;
        
        public GcMetrics(String name, long collectionCount, long collectionTime) {
            this.name = name;
            this.collectionCount = collectionCount;
            this.collectionTime = collectionTime;
        }
    }
    
    public static class RuntimeMetrics {
        public final long uptime;
        public final String vmName, vmVersion, vmVendor;
        
        public RuntimeMetrics(long uptime, String vmName, String vmVersion, String vmVendor) {
            this.uptime = uptime;
            this.vmName = vmName;
            this.vmVersion = vmVersion;
            this.vmVendor = vmVendor;
        }
        
        public String getFormattedUptime() {
            long seconds = uptime / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;
            
            if (days > 0) {
                return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
            } else if (hours > 0) {
                return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
            } else if (minutes > 0) {
                return String.format("%dm %ds", minutes, seconds % 60);
            } else {
                return String.format("%ds", seconds);
            }
        }
    }
    
    public static class OsMetrics {
        public final Double processCpuLoad, systemCpuLoad;
        public final int availableProcessors;
        public final double systemLoadAverage;
        public final Long totalPhysicalMemorySize, freePhysicalMemorySize;
        
        public OsMetrics(Double processCpuLoad, Double systemCpuLoad, int availableProcessors,
                        double systemLoadAverage, Long totalPhysicalMemorySize, Long freePhysicalMemorySize) {
            this.processCpuLoad = processCpuLoad;
            this.systemCpuLoad = systemCpuLoad;
            this.availableProcessors = availableProcessors;
            this.systemLoadAverage = systemLoadAverage;
            this.totalPhysicalMemorySize = totalPhysicalMemorySize;
            this.freePhysicalMemorySize = freePhysicalMemorySize;
        }
    }
    
    public static class LuceeMetrics {
        public final int mbeanCount;
        public final Map<String, Object> customMetrics;
        
        public LuceeMetrics(int mbeanCount, Map<String, Object> customMetrics) {
            this.mbeanCount = mbeanCount;
            this.customMetrics = customMetrics;
        }
    }
}
