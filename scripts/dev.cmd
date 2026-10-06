@echo off
rem Launches dev.ps1 without requiring a change to the PowerShell execution policy.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0dev.ps1" %*
