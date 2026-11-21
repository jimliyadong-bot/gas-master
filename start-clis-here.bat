@echo off
REM Open the Codex, Claude, Copilot, Pi and OpenCode CLI tabs in one new Windows Terminal window.
setlocal EnableExtensions
pushd "%~dp0" || exit /b 1
set "HERE=%CD%"

wt.exe -w -1 new-tab -d "%HERE%" --title codex powershell.exe -NoLogo -NoExit -NoProfile -Command "codex --dangerously-bypass-approvals-and-sandbox" ; new-tab -d "%HERE%" --title claude powershell.exe -NoLogo -NoExit -NoProfile -Command "claude" ; new-tab -d "%HERE%" --title copilot powershell.exe -NoLogo -NoExit -NoProfile -Command "copilot --yolo" ; new-tab -d "%HERE%" --title pi powershell.exe -NoLogo -NoExit -NoProfile -Command "pi" ; new-tab -d "%HERE%" --title opencode powershell.exe -NoLogo -NoExit -NoProfile -Command "opencode"
if errorlevel 1 (
    echo Windows Terminal could not start. Check the error above.
    pause
)

popd
endlocal
