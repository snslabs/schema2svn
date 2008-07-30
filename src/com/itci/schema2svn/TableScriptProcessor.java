package com.itci.schema2svn;

import java.io.FileReader;
import java.util.regex.Pattern;

public class TableScriptProcessor implements ScriptProcessor{
    Pattern CONSTRAINT = Pattern.compile(CONSTRAINT_REGEXP);
    Pattern STORAGE = Pattern.compile(STORAGE_REGEXP);
    Pattern DOUBLE_LF = Pattern.compile(DOUBLE_CR_REGEXP);
    private static final String CONSTRAINT_REGEXP = "CONSTRAINT[\\S\\s]+?.+((ENABLE)|(DISABLE)),?";
    private static final String STORAGE_REGEXP = "PCTFREE[\\s\\S]+?TABLESPACE.+";
    private static final String DOUBLE_CR_REGEXP = "(\\s*\\n){2,}";

    public String processScript(String script) {
        // CUT ALL CONTRAINTS
//        script = CONSTRAINT.matcher(script).replaceAll("");
        // CUT STORAGE CLAUSE
//        script = STORAGE.matcher(script).replaceAll("");
        // REMOVE EXTRA NEW LINES
        script = DOUBLE_LF.matcher(script).replaceAll("\n\n");
        return script;
    }
}
