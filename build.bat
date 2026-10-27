@echo off
rem Compiles the application into .\out (requires JDK 17 or later on PATH).
setlocal
cd /d "%~dp0"
if exist out rmdir /s /q out
javac -encoding UTF-8 -d out ^
  src\com\seatingplanner\*.java ^
  src\com\seatingplanner\model\*.java ^
  src\com\seatingplanner\service\*.java ^
  src\com\seatingplanner\ui\*.java
if errorlevel 1 (
  echo Build FAILED.
  exit /b 1
)
echo Build OK - classes in .\out
