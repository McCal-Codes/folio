@echo off
rem Double-click this to open the supporter code admin page (scripts/code-admin.py). Windows twin of Folio Codes.command.
rem A shortcut to it anywhere still runs the script from this checkout.
cd /d "%~dp0.."
echo Folio supporter codes
echo Ctrl-C, or close this window, to stop the page.
echo.
py scripts\code-admin.py %*
set status=%errorlevel%
if %status%==0 exit /b 0
echo.
echo The page stopped (exit %status%). Press any key to close this window.
pause >nul
exit /b %status%
