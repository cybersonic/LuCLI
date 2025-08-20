package org.lucee.lucli;

import org.jline.reader.Candidate;

import javax.script.ScriptException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides tab completion for CFML functions using Lucee's built-in function introspection
 */
public class CFMLCompleter {
    private static final Map<String, Set<String>> functionCache = new ConcurrentHashMap<>();
    private static final Map<String, FunctionInfo> functionDataCache = new ConcurrentHashMap<>();
    private static boolean cacheInitialized = false;
    private static LuceeScriptEngine luceeEngine;
    
    /**
     * Get completions for CFML functions that start with the given prefix
     */
    public static List<Candidate> getCompletions(String prefix) {
        List<Candidate> candidates = new ArrayList<>();
        
        try {
            // Initialize cache if needed
            if (!cacheInitialized) {
                initializeFunctionCache();
            }
            
            // Get all functions that start with the prefix
            Set<String> allFunctions = functionCache.getOrDefault("all", new HashSet<>());
            
            for (String functionName : allFunctions) {
                if (functionName.toLowerCase().startsWith(prefix.toLowerCase())) {
                    FunctionInfo info = functionDataCache.get(functionName.toLowerCase());
                    String description = info != null ? info.getDescription() : "CFML function";
                    
                    // Create candidate with function signature
                    String displayValue = info != null ? info.getSignature() : functionName + "()";
                    candidates.add(new Candidate(
                        functionName, // value to insert
                        displayValue, // what to display  
                        null, // group
                        description, // description
                        null, // suffix
                        null, // key
                        true // complete (finish completion)
                    ));
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error getting CFML completions: " + e.getMessage());
        }
        
        return candidates;
    }
    
    /**
     * Initialize the function cache by calling Lucee's GetFunctionList()
     */
    private static void initializeFunctionCache() {
        try {
            // Get or create Lucee engine instance
            if (luceeEngine == null) {
                luceeEngine = LuceeScriptEngine.getInstance(false, false);
            }
            
            // Get all function names using GetFunctionList()
            // Handle both array and struct returns from GetFunctionList()
            String script = "try { " +
                          "  functionList = GetFunctionList(); " +
                          "  functionNameArr = functionList.KeyArray(); " +
                          "  if (isStruct(functionList)) { " +
                          "    writeOutput(structKeyList(functionList, '|')); " +
                          "  } else if (isArray(functionList)) { " +
                          "    writeOutput(arrayToList(functionList, '|')); " +
                          "  } else { " +
                          "    writeOutput(''); " +
                          "  } " +
                          "} catch (any e) { " +
                          "  writeOutput('GetFunctionList_ERROR:' & e.message); " +
                          "}";
            Object result = luceeEngine.eval(script);
            Object functionList = luceeEngine.getEngine().get("functionList");
            Object functionNameArr = luceeEngine.getEngine().get("functionNameArr");
            
            if (result != null) {
                String functionListStr = result.toString();
                
                // Only process if it doesn't look like an error
                if (!functionListStr.startsWith("GetFunctionList_ERROR:")) {
                    String[] functions = functionListStr.split("\\|");
                    
                    Set<String> allFunctions = new HashSet<>();
                    for (String functionName : functions) {
                        String cleanName = functionName.trim();
                        if (!cleanName.isEmpty()) {
                            allFunctions.add(cleanName);
                            // Cache function data for a subset for performance (first 50 functions)
                            if (allFunctions.size() <= 50) {
                                cacheFunctionData(cleanName);
                            }
                        }
                    }
                    
                    functionCache.put("all", allFunctions);
                    cacheInitialized = true;
                    
                    if (LuCLI.verbose) {
                        System.out.println("✅ Cached " + allFunctions.size() + " CFML functions for tab completion");
                    }
                } else {
                    System.err.println("⚠️ CFML Function list error: " + functionListStr);
                    functionCache.put("all", new HashSet<>());
                    cacheInitialized = true;
                }
            }
            
        } catch (Exception e) {
            System.err.println("⚠️ Warning: Could not initialize CFML function cache: " + e.getMessage());
            // Create empty cache to prevent repeated attempts
            functionCache.put("all", new HashSet<>());
            cacheInitialized = true;
        }
    }
    
    /**
     * Cache detailed function data using getFunctionData()
     */
    private static void cacheFunctionData(String functionName) {
        try {
            // Get function metadata using getFunctionData()
            String script = "try { " +
                          "  funcData = getFunctionData('" + functionName + "'); " +
                          "  result = {" +
                          "    'name': funcData.name," +
                          "    'description': funcData.description," +
                          "    'returnType': funcData.returnType," +
                          "    'argumentCount': arrayLen(funcData.arguments)," +
                          "    'arguments': funcData.arguments" +
                          "  };" +
                          "  writeOutput(serializeJSON(result));" +
                          "} catch(any e) {" +
                          "  writeOutput('{}');" +
                          "}";
            
            Object result = luceeEngine.eval(script);
            if (result != null && !result.toString().trim().isEmpty()) {
                String jsonData = result.toString().trim();
                if (!jsonData.equals("{}")) {
                    // Parse the JSON and create FunctionInfo
                    FunctionInfo info = parseFunctionInfo(jsonData, functionName);
                    if (info != null) {
                        functionDataCache.put(functionName.toLowerCase(), info);
                    }
                }
            }
            
        } catch (Exception e) {
            // Ignore individual function data errors - not critical for basic completion
        }
    }
    
    /**
     * Parse JSON function data and create FunctionInfo object
     */
    private static FunctionInfo parseFunctionInfo(String jsonData, String functionName) {
        try {
            // Simple JSON parsing for function info
            // This is a basic implementation - you might want to use a proper JSON library
            FunctionInfo info = new FunctionInfo();
            info.name = functionName;
            
            // Extract description (simple regex-based extraction)
            if (jsonData.contains("\"description\":")) {
                int descStart = jsonData.indexOf("\"description\":\"") + 15;
                int descEnd = jsonData.indexOf("\"", descStart);
                if (descEnd > descStart) {
                    info.description = jsonData.substring(descStart, descEnd);
                }
            }
            
            // Extract return type
            if (jsonData.contains("\"returnType\":")) {
                int typeStart = jsonData.indexOf("\"returnType\":\"") + 14;
                int typeEnd = jsonData.indexOf("\"", typeStart);
                if (typeEnd > typeStart) {
                    info.returnType = jsonData.substring(typeStart, typeEnd);
                }
            }
            
            // Extract argument count
            if (jsonData.contains("\"argumentCount\":")) {
                int countStart = jsonData.indexOf("\"argumentCount\":") + 16;
                int countEnd = jsonData.indexOf(",", countStart);
                if (countEnd == -1) countEnd = jsonData.indexOf("}", countStart);
                if (countEnd > countStart) {
                    try {
                        info.argumentCount = Integer.parseInt(jsonData.substring(countStart, countEnd).trim());
                    } catch (NumberFormatException e) {
                        info.argumentCount = 0;
                    }
                }
            }
            
            return info;
            
        } catch (Exception e) {
            // Return basic info if parsing fails
            FunctionInfo info = new FunctionInfo();
            info.name = functionName;
            info.description = "CFML function";
            return info;
        }
    }
    
    /**
     * Get detailed function information for a specific function
     */
    public static FunctionInfo getFunctionInfo(String functionName) {
        if (!cacheInitialized) {
            initializeFunctionCache();
        }
        return functionDataCache.get(functionName.toLowerCase());
    }
    
    /**
     * Check if the cache contains a function
     */
    public static boolean hasFunction(String functionName) {
        if (!cacheInitialized) {
            initializeFunctionCache();
        }
        Set<String> allFunctions = functionCache.getOrDefault("all", new HashSet<>());
        return allFunctions.contains(functionName);
    }
    
    /**
     * Refresh the function cache (useful for getting latest functions)
     */
    public static void refreshCache() {
        functionCache.clear();
        functionDataCache.clear();
        cacheInitialized = false;
        initializeFunctionCache();
    }
    
    /**
     * Data class to hold function information
     */
    public static class FunctionInfo {
        public String name;
        public String description = "";
        public String returnType = "any";
        public int argumentCount = 0;
        public List<String> arguments = new ArrayList<>();
        
        public String getDescription() {
            if (description.isEmpty()) {
                return "CFML function returning " + returnType;
            }
            return description;
        }
        
        public String getSignature() {
            StringBuilder sig = new StringBuilder(name);
            sig.append("(");
            
            if (argumentCount > 0) {
                for (int i = 0; i < argumentCount; i++) {
                    if (i > 0) sig.append(", ");
                    sig.append("arg").append(i + 1);
                }
            }
            
            sig.append(")");
            if (!returnType.isEmpty() && !returnType.equals("any")) {
                sig.append(" : ").append(returnType);
            }
            
            return sig.toString();
        }
    }
}
