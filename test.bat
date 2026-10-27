@echo off
rem Compiles the application plus the self-test suite and runs the tests.
setlocal
cd /d "%~dp0"
call "%~dp0build.bat" || exit /b 1
javac -encoding UTF-8 -cp out -d out test\com\seatingplanner\*.java || exit /b 1
java -cp out com.seatingplanner.SelfTest
