@REM SMILE setup script

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
        EXIT /b 1
    ) ELSE (
        ECHO Virtual environment created successfully.
    )
)

ECHO Installing Python packages...
CALL "%VENV_DIR%\\Scripts\\activate.bat"
python -m pip install --upgrade pip setuptools wheel
pip install -r %APP_HOME%\\conf\\requirements.txt
pip install uv
uv tool install ty@latest --force

REM Install Scala CLI into the launcher's bin directory. The Studio launcher
REM puts this directory on PATH, so ScalaKernel finds it without further setup.
REM The version is pinned for reproducibility; bump it deliberately.
SET "SCALA_CLI_VERSION=1.17.1"
SET "SCALA_CLI=%APP_HOME%\bin\scala-cli.exe"
IF EXIST "%SCALA_CLI%" (
    ECHO Scala CLI is already installed in %APP_HOME%\bin.
    GOTO :scala_cli_done
)

ECHO Installing Scala CLI %SCALA_CLI_VERSION%...
REM The winget package is a machine-wide MSI that does not install to a PATH
REM directory, so we fetch the launcher directly instead. There is no
REM win32-arm64 asset; the x86_64 build runs under emulation on Windows on ARM.
SET "SCALA_CLI_URL=https://github.com/VirtusLab/scala-cli/releases/download/v%SCALA_CLI_VERSION%/scala-cli-x86_64-pc-win32.zip"
SET "SCALA_CLI_ZIP=%TEMP%\scala-cli-%SCALA_CLI_VERSION%.zip"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ProgressPreference='SilentlyContinue';" ^
    "try { Invoke-WebRequest -Uri '%SCALA_CLI_URL%' -OutFile '%SCALA_CLI_ZIP%' -UseBasicParsing } catch { exit 1 };" ^
    "try { Expand-Archive -Path '%SCALA_CLI_ZIP%' -DestinationPath '%APP_HOME%\bin' -Force } catch { exit 1 }"

IF ERRORLEVEL 1 (
    REM Do not fail the whole setup: Scala is optional, the other kernels still
    REM work. Clean up a partial download so a later run retries.
    DEL /Q "%SCALA_CLI_ZIP%" >NUL 2>&1
    DEL /Q "%SCALA_CLI%" >NUL 2>&1
    ECHO Failed to download Scala CLI. Scala notebooks will be unavailable.
    ECHO Install it manually and add it to PATH: https://scala-cli.virtuslab.org/install
) ELSE (
    DEL /Q "%SCALA_CLI_ZIP%" >NUL 2>&1
    ECHO Scala CLI installed to %SCALA_CLI%.
)
:scala_cli_done

