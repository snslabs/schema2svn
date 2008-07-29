package com.itci.schema2svn;

public interface ScriptProcessor {
    public static final ScriptProcessor TABLE = new TableScriptProcessor();
    public static final ScriptProcessor GRANTS = new GrantsScriptProcessor();
    /**
     * Processes script
     * @param script
     * @return
     */
    String processScript(String script);
}
