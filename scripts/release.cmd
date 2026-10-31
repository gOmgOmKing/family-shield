@echo off
rem Double-click to publish a new version (see docs). Uses Git Bash.
chcp 65001 >nul
"%ProgramFiles%\Git\bin\bash.exe" --login "%~dp0release.sh"
pause
