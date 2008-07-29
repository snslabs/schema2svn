package com.itci.schema2svn;

public class GrantsScriptProcessor implements ScriptProcessor {
    public String processScript(String script) {
        // need to add ; after every line
        return script.replaceAll("(?<=.{20})\n",";\n");
    }
}
