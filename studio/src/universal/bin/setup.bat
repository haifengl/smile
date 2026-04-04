@REM smile setup script

@echo off
ECHO Installing ripgrep...
winget install -e --id BurntSushi.ripgrep.MSVC

SET "APP_HOME=%~dp0\\.."
SET "VENV_DIR=%APP_HOME%\\venv"
REM Check if the venv directory exists by checking for a known file/folder inside it
IF NOT EXIST "%VENV_DIR%\\Scripts\\activate.bat" (
    ECHO Creating Python virtual environment...
    python -m venv %VENV_DIR%
    IF ERRORLEVEL 1 (
        ECHO Failed to create the virtual environment. Ensure Python is installed and added to PATH.
    ) ELSE (
        ECHO Virtual environment created successfully.
        CALL "%VENV_DIR%\\Scripts\\activate.bat"
        pip install -r %APP_HOME%\\conf\\requirements.txt
        pip install uv
        uv tool install ty@latest
    )
)
