# 📄 Externalized CFML Script Templates

## Overview

LuCLI has moved from inline script generation in Java code to externalized CFML script templates. This system makes CFML script logic easier to maintain, modify, and understand by storing it in proper `.cfs` files with syntax highlighting support.

## 🎯 Benefits

### Maintainability
- **Direct CFML Editing**: Edit scripts in proper `.cfs` files with syntax highlighting
- **No Java Recompilation**: Changes to script logic don't require rebuilding the application
- **Version Control Friendly**: Script changes are clearly visible in diffs
- **IDE Support**: Full CFML language support in modern editors

### Flexibility  
- **Template-Based**: Uses placeholder substitution for dynamic content
- **Post-Processing**: Integration with StringOutput for emoji and variable handling
- **Fallback Support**: Graceful degradation if external scripts fail to load
- **Extensible**: Easy to add new script templates

## 📁 Script Templates Location

All externalized scripts are located in:
```
src/main/resources/script_engine/
├── cfmlOutput.cfs              # CFML expression evaluation
├── componentWrapper.cfs        # CFC component execution wrapper  
├── moduleDirectExecution.cfs   # Direct module execution
├── componentToScript.cfs       # Component-to-script conversion
├── lucliMappings.cfs          # LuCLI component mappings
└── executeComponent.cfs        # General component execution template
```

## 📋 Template Reference

### cfmlOutput.cfs
**Purpose**: Evaluates CFML expressions and outputs results with proper type handling

**Replaces**: `SimpleTerminal.createOutputScript()`

**Template Variables**:
- `${cfmlExpression}` - The CFML expression to evaluate

**Example Usage**:
```cfml
try {
    result = now();
    if (isDefined('result')) {
        if (isSimpleValue(result)) {
            writeOutput(result);
        } else if (isArray(result)) {
            writeOutput('[' & arrayToList(result, ', ') & ']');
        } else if (isStruct(result)) {
            writeOutput(serializeJSON(result));
        } else {
            writeOutput(toString(result));
        }
    }
} catch (any e) {
    writeOutput('${EMOJI_ERROR} CFML Error: ' & e.message);
    if (len(e.detail)) {
        writeOutput(' - ' & e.detail);
    }
}
```

### componentWrapper.cfs  
**Purpose**: Creates execution wrapper for CFC components

**Replaces**: `LuceeScriptEngine.createComponentWrapper()`

**Template Variables**:
- `${scriptFile}` - Path to the script file being executed
- `${componentPath}` - Dotted path or file path to the component
- `${argumentSetup}` - Code to set up arguments array
- `${componentInstantiation}` - Component creation code

**Key Features**:
- Automatic `init()` method calling
- Safe `main()` method execution
- Comprehensive error handling with emoji support

### moduleDirectExecution.cfs
**Purpose**: Direct execution of extracted modules without component instantiation

**Replaces**: `LuceeScriptEngine.createModuleDirectScript()`

**Template Variables**:
- `${argumentSetup}` - Code to populate args array
- `${moduleExecutionContent}` - The actual module execution logic

**Usage Context**: Used specifically for modules extracted to `~/.lucli/modules/`

### componentToScript.cfs
**Purpose**: Converts CFC components to script format for execution

**Replaces**: `LuceeScriptEngine.convertComponentToScript()`

**Template Variables**:
- `${processedComponentContent}` - Component content with wrapper removed and scoping fixed

**Processing Steps**:
1. Removes outer `component {}` wrapper
2. Removes `public/private` function modifiers
3. Fixes variable scoping issues
4. Calls `main()` function if available

### lucliMappings.cfs
**Purpose**: Sets up LuCLI component mappings for module and builtin access

**Replaces**: `LuceeScriptEngine.createLucliMappingScript()`

**Template Variables**:
- `${lucliHome}` - Path to the LuCLI home directory (normalized with forward slashes)

**Functionality**:
- Sets up `/modules` mapping to `~/.lucli/modules`
- Sets up `/builtin` mapping to `~/.lucli/builtin`
- Gracefully handles mapping errors

### executeComponent.cfs
**Purpose**: General component execution template (extensible)

**Template Variables**:
- `${componentExecutionContent}` - Dynamic content based on execution type

**Usage**: Base template for specific component execution scenarios

## 🔧 Template Processing System

### Placeholder Substitution
Templates use `${variableName}` syntax for placeholder substitution:

```cfml
// Template content
writeOutput('${EMOJI_SUCCESS} Processing ${fileName}');

// After substitution and post-processing  
writeOutput('✅ Processing myfile.cfs');
// or on unsupported terminals:
writeOutput('[OK] Processing myfile.cfs');
```

### Processing Flow
1. **Load Template**: Read `.cfs` file from resources
2. **Replace Placeholders**: Substitute `${variable}` with actual values
3. **Post-Process**: Run through StringOutput for emoji and variable handling  
4. **Execute**: Send processed script to Lucee engine
5. **Fallback**: Use inline generation if template loading fails

### Java Integration
```java
// Template loading example from LuceeScriptEngine
private String createModuleDirectScript(String moduleContent, String[] scriptArgs) {
    try {
        // Read external template
        String scriptTemplate = readScriptTemplate("/script_engine/moduleDirectExecution.cfs");
        
        // Build replacements
        StringBuilder argSetup = new StringBuilder();
        for (int i = 0; i < scriptArgs.length; i++) {
            argSetup.append("arrayAppend(args, '").append(scriptArgs[i].replace("'", "''")).append("');\\n");
        }
        
        // Apply substitutions
        String result = scriptTemplate
            .replace("${argumentSetup}", argSetup.toString())
            .replace("${moduleExecutionContent}", executionContent);
            
        // Post-process for emojis and placeholders
        return StringOutput.getInstance().process(result);
        
    } catch (Exception e) {
        // Fallback to inline generation
        return createModuleDirectScriptFallback(moduleContent, scriptArgs);
    }
}
```

## 🎨 StringOutput Integration

Templates can use StringOutput placeholders for consistent emoji and variable handling:

### Emoji Placeholders in Templates
```cfml
// Success messages
writeOutput('${EMOJI_SUCCESS} Operation completed successfully');

// Error messages  
writeOutput('${EMOJI_ERROR} Component execution failed: ' & e.message);

// Informational messages
writeOutput('${EMOJI_INFO} Processing arguments: ' & arrayLen(args));

// Warnings
writeOutput('${EMOJI_WARNING} Main function not found in component');
```

### System Information Placeholders
```cfml
// Timestamp logging
writeOutput('${TIME_HH:mm:ss} Starting execution');

// Environment context
writeOutput('Working directory: ${WORKING_DIR}');
writeOutput('User: ${USER_NAME} on ${OS_NAME}');
```

## 🚀 Performance and Caching

### Template Loading
- Templates are loaded from JAR resources via `getResourceAsStream()`
- UTF-8 encoding is enforced for proper character handling
- Loading failures trigger fallback to inline generation

### Fallback System
Each externalized script has a corresponding fallback method:
- `createModuleDirectScriptFallback()`
- `createComponentWrapperFallback()`
- `createLucliMappingScriptFallback()`
- `convertComponentToScriptFallback()`

### Error Handling
```java
try {
    // Load and process external template
    return processExternalTemplate(templatePath, variables);
} catch (Exception e) {
    if (isDebugMode()) {
        StringOutput.Quick.warning("Failed to read external script template, using fallback: " + e.getMessage());
    }
    return fallbackMethod(originalParameters);
}
```

## 🛠️ Development Workflow

### Adding New Templates
1. **Create Template File**: Add new `.cfs` file to `src/main/resources/script_engine/`
2. **Define Placeholders**: Use `${variable}` syntax for dynamic content
3. **Update Java Code**: Add template loading logic in appropriate class
4. **Add Fallback**: Implement fallback method for error cases
5. **Test**: Verify both template and fallback paths work

### Modifying Existing Templates  
1. **Edit Template**: Modify `.cfs` file directly
2. **Test Changes**: No recompilation needed for template changes
3. **Update Placeholders**: Modify Java code if new placeholders are needed
4. **Verify Fallback**: Ensure fallback still works if template is invalid

### Example: Adding New Template
```cfml
// New template: serverStatusCheck.cfs
// Purpose: Check server status with detailed output

writeOutput('${EMOJI_MAGNIFYING_GLASS} Checking server status...' & chr(10));

try {
    // Server status logic here
    ${serverStatusLogic}
    
    writeOutput('${EMOJI_SUCCESS} Server is running on port ${serverPort}' & chr(10));
} catch (any e) {
    writeOutput('${EMOJI_ERROR} Server status check failed: ' & e.message & chr(10));
}

writeOutput('Status check completed at ${NOW}' & chr(10));
```

## 📊 Benefits Achieved

### Developer Experience
- **Immediate Feedback**: Template changes are visible without rebuilding
- **Syntax Highlighting**: Full CFML support in editors like VS Code
- **Better Debugging**: CFML syntax errors are easier to spot and fix
- **Code Organization**: Script logic is separated from Java application logic

### Maintenance
- **Version Control**: Script changes are clearly visible in git diffs  
- **Documentation**: Templates are self-documenting with comments
- **Collaboration**: Non-Java developers can contribute to CFML logic
- **Testing**: Scripts can be tested independently of Java code

### Flexibility
- **Customization**: Users could potentially override templates (future enhancement)
- **Localization**: Templates could be localized for different languages
- **Themes**: Different template sets could be used for different themes
- **A/B Testing**: Easy to test different script variations

## 🔮 Future Enhancements

### Template System Extensions
- **Template Inheritance**: Base templates with override capabilities
- **Conditional Logic**: `${IF condition}...${ENDIF}` template syntax
- **Loop Support**: `${FOREACH items}...${ENDFOR}` template constructs
- **Include System**: `${INCLUDE template}` for shared components

### User Customization
- **User Templates**: Allow users to override templates in `~/.lucli/templates/`
- **Template Themes**: Different template sets for different use cases
- **Plugin Templates**: Third-party template contributions
- **Hot Reloading**: Watch for template changes and reload automatically

### Advanced Features
- **Template Validation**: Schema validation for template structure
- **Performance Profiling**: Track template processing performance
- **Caching System**: Cache processed templates for better performance
- **Template Debugging**: Debug mode for template processing steps

## 📚 Migration Summary

### Before: Inline Generation
```java
private String createScript() {
    StringBuilder script = new StringBuilder();
    script.append("// Generated script\n");
    script.append("writeOutput('Success');\n");
    return script.toString();
}
```

### After: External Templates
```java
private String createScript() {
    try {
        String template = readScriptTemplate("/script_engine/myScript.cfs");
        String result = template.replace("${variable}", value);
        return StringOutput.getInstance().process(result);
    } catch (Exception e) {
        return createScriptFallback(); // Fallback for safety
    }
}
```

### Template File: myScript.cfs
```cfml
// External template with proper CFML syntax
// Comments and documentation are preserved

writeOutput('${EMOJI_SUCCESS} Script executed successfully' & chr(10));
writeOutput('Timestamp: ${NOW}' & chr(10));

// Complex logic is easier to read and maintain
if (isDefined('complexLogic')) {
    // Multi-line CFML logic
    for (var i = 1; i <= arrayLen(items); i++) {
        writeOutput('Processing item ' & i & ': ' & items[i] & chr(10));
    }
}
```

---

*For more information about the StringOutput system used in templates, see STRING_OUTPUT_SYSTEM.md.*
