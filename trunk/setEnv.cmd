@echo off

rem  ----------------------------------------
rem   Using this file you can set your own
rem   values of JAVA_HOME variable.
rem   Note, that you should never commit
rem   the "setEnv-my.cmd" file.
rem  ----------------------------------------

set ANT_HOME=%~dp0\apache-ant-1.6.3
SET JAVA_HOME=c:\jdk5
SET PATH=%JAVA_HOME%\bin;%ANT_HOME%\bin;%PATH%

rem @echo ---- The Java Version is : -----
rem java -version
rem @echo --------------------------------
