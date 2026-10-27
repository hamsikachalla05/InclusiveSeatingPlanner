@echo off
rem Builds (if needed) and starts the Inclusive Event Seating Planner.
setlocal
cd /d "%~dp0"
call "%~dp0build.bat" || exit /b 1
java -cp out com.seatingplanner.App
