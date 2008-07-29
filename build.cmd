@echo off
@setlocal

IF EXIST "%~dp0setEnv.cmd" (
  call "%~dp0setEnv"
)
IF EXIST "%~dp0setEnv-my.cmd" (
  call "%~dp0setEnv-my"
)


@echo ---- The Java Version is : -----
java -version
@echo --------------------------------

call ant %*

@endlocal
