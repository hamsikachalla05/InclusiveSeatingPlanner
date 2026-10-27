@echo off
rem Builds a runnable JAR: double-click it, or run "java -jar InclusiveSeatingPlanner.jar".
setlocal
cd /d "%~dp0"
call "%~dp0build.bat" || exit /b 1
jar --create --file InclusiveSeatingPlanner.jar --main-class com.seatingplanner.App -C out .
if errorlevel 1 exit /b 1
echo Created InclusiveSeatingPlanner.jar
