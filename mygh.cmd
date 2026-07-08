@echo off
rem Wrapper that finds the fat-jar next to this script (skill layout) or in target\
rem (development layout), then runs it. Requires Java 17+ on PATH.
setlocal
set "DIR=%~dp0"
if exist "%DIR%mygh.jar" (
  set "JAR=%DIR%mygh.jar"
) else if exist "%DIR%target\mygh.jar" (
  set "JAR=%DIR%target\mygh.jar"
) else (
  echo mygh: mygh.jar not found next to the wrapper or in target\ - build it with: mvn -q clean package 1>&2
  exit /b 1
)
java -jar "%JAR%" %*
