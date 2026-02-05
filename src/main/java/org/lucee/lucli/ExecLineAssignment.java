package org.lucee.lucli;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExecLineAssignment {
    // Nice defaults. 
    String variableName="";
    String variableValue="";
    boolean isAssignment=false;
    int assignmentType = ExecLineAssignment.ASSIGNMENT_NONE; // 0 = none, 1 = simple, 2 = command substitution

    static final int ASSIGNMENT_NONE = 0;
    static final int ASSIGNMENT_SIMPLE = 1;
    static final int ASSIGNMENT_COMMAND_SUBSTITUTION = 2;
    static final int ASSIGNMENT_ENVIRONMENT = 3;
    static final int ASSIGNMENT_SECRET = 4;



    public ExecLineAssignment(String line) {
        String trimmed = line.trim();
        Pattern assignmentPattern = Pattern.compile("^(?i)(?:set\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.+)\\s*$");
        Matcher assignmentMatcher = assignmentPattern.matcher(trimmed);
        if (!assignmentMatcher.matches()) {
            this.assignmentType = ASSIGNMENT_NONE;
            return;
        }

        this.isAssignment = true;
        this.variableName = assignmentMatcher.group(1);
        this.variableValue = assignmentMatcher.group(2).trim();

        Pattern commandPattern = Pattern.compile("^\\$\\((.*)\\)$");
        Pattern envPattern = Pattern.compile("^\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}$");
        Pattern secretPattern = Pattern.compile("^\\$\\{secret:([^}]+)\\}$");
        Pattern quotedPattern = Pattern.compile("^(\".*\"|'.*')$");

        if (commandPattern.matcher(this.variableValue).matches()) {
            this.assignmentType = ASSIGNMENT_COMMAND_SUBSTITUTION;
            this.variableValue = this.variableValue.substring(2, this.variableValue.length() - 1);
        } else if (secretPattern.matcher(this.variableValue).matches()) {
            this.assignmentType = ASSIGNMENT_SECRET;
        } else if (envPattern.matcher(this.variableValue).matches()) {
            this.assignmentType = ASSIGNMENT_ENVIRONMENT;
        } else if (quotedPattern.matcher(this.variableValue).matches()) {
            this.assignmentType = ASSIGNMENT_SIMPLE;
        } else {
            this.assignmentType = ASSIGNMENT_SIMPLE;
        }

    }

    public String getVariableName() {
        return variableName;
    }

    public String getVariableValue() {
        return variableValue;
    }
    
    public boolean isAssignment() {
        return isAssignment;
    } 
}
