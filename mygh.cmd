@echo off
rem Windows wrapper that runs the fat-jar built by `mvn clean package`.
set "DIR=%~dp0"
set "JAR=%DIR%target\mygh.jar"
if not exist "%JAR%" (
  echo mygh: %JAR% not found - build it first with: mvn -q clean package 1>&2
  exit /b 1
)
java -jar "%JAR%" %*
