@REM smile launcher script
@REM
@REM Environment:
@REM JAVA_HOME - location of a JDK home dir (optional if java on path)
@REM CFG_OPTS  - JVM options (optional)
@REM Configuration:
@REM SMILE_config.txt found in the SMILE_HOME.
@setlocal enabledelayedexpansion
@setlocal enableextensions

@echo off


if "%SMILE_HOME%"=="" (
  set "APP_HOME=%~dp0\\.."

  rem Also set the old env name for backwards compatibility
  set "SMILE_HOME=%~dp0\\.."
) else (
  set "APP_HOME=%SMILE_HOME%"
)

set "APP_LIB_DIR=%APP_HOME%\lib\"

rem Detect if we were double clicked, although theoretically A user could
rem manually run cmd /c
for %%x in (!cmdcmdline!) do if %%~x==/c set DOUBLECLICKED=1

rem FIRST we load the config file of extra options.
set "CFG_FILE=%APP_HOME%\SMILE_config.txt"
set CFG_OPTS=
call :parse_config "%CFG_FILE%" CFG_OPTS

rem We use the value of the JAVA_OPTS environment variable if defined, rather than the config.
set _JAVA_OPTS=%JAVA_OPTS%
if "!_JAVA_OPTS!"=="" set _JAVA_OPTS=!CFG_OPTS!

rem We keep in _JAVA_PARAMS all -J-prefixed and -D-prefixed arguments
rem "-J" is stripped, "-D" is left as is, and everything is appended to JAVA_OPTS
set _JAVA_PARAMS=
set _APP_ARGS=
set _JARS=

for /f "Delims=" %%i in ('dir /s /b /a-d %APP_LIB_DIR%\*.jar^|findstr /Riv "typesafe scala"') do call :add_jar %%i
for %%i in (%APP_HOME%\smile-kotlin-*.jar) do call :add_jar %%i
set "APP_CLASSPATH=%_JARS%"

call :add_java -Dsmile.home=%APP_HOME%
call :add_java -Djava.library.path=%APP_HOME%\bin
call :add_java -Djava.awt.headless=false
set PATH=!PATH!;%~dp0

if "%KOTLIN_HOME%" neq "" (
  if exist "%KOTLIN_HOME%\bin\kotlinc.bat" set "_JAVACMD=%KOTLIN_HOME%\bin\kotlinc.bat"
)

if "%_JAVACMD%"=="" set _JAVACMD=kotlinc.bat

rem Detect if this java is ok to use.
for /F %%j in ('"%_JAVACMD%" -version  2^>^&1') do (
  if %%~j==info: set JAVAINSTALLED=1
)

rem BAT has no logical or, so we do it OLD SCHOOL! Oppan Redmond Style
set JAVAOK=true
if not defined JAVAINSTALLED set JAVAOK=false

if "%JAVAOK%"=="false" (
  echo.
  echo Kotlin is not installed or can't be found.
  if not "%KOTLIN_HOME%"=="" (
    echo KOTLIN_HOME = "%KOTLIN_HOME%"
  )
  echo.
  echo Please go to
  echo   https://kotlinlang.org/docs/command-line.html
  echo and download a valid Kotlin command-line compiler and
  echo install before running smile.
  echo.
  echo If you think this message is in error, please check
  echo your environment variables to see if "kotlin" and "kotlinc" are
  echo available via KOTLIN_HOME or PATH.
  echo.
  if defined DOUBLECLICKED pause
  exit /B 1
)

set JAVA_OPTS=!_JAVA_OPTS! !_JAVA_PARAMS!

rem Call the application and pass all arguments unchanged.
"%_JAVACMD%" -cp !APP_CLASSPATH! !_APP_ARGS!

@endlocal

exit /B %ERRORLEVEL%


rem Loads a configuration file full of default command line options for this script.
rem First argument is the path to the config file.
rem Second argument is the name of the environment variable to write to.
:parse_config
  set _PARSE_FILE=%~1
  set _PARSE_OUT=
  if exist "%_PARSE_FILE%" (
    FOR /F "tokens=* eol=# usebackq delims=" %%i IN ("%_PARSE_FILE%") DO (
      set _PARSE_OUT=!_PARSE_OUT! %%i
    )
  )
  set %2=!_PARSE_OUT!
exit /B 0


:add_jar
  set _JARS=%1;!_JARS!
exit /B 0


:add_java
  set _JAVA_PARAMS=!_JAVA_PARAMS! %*
exit /B 0


:add_app
  set _APP_ARGS=!_APP_ARGS! %*
exit /B 0


rem Processes incoming arguments and places them in appropriate global variables
:process_args
  :param_loop
  call set _PARAM1=%%1
  set "_TEST_PARAM=%~1"

  if ["!_PARAM1!"]==[""] goto param_afterloop


  rem ignore arguments that do not start with '-'
  if "%_TEST_PARAM:~0,1%"=="-" goto param_java_check
  set _APP_ARGS=!_APP_ARGS! !_PARAM1!
  shift
  goto param_loop

  :param_java_check
  if "!_TEST_PARAM:~0,2!"=="-J" (
    rem strip -J prefix
    set _JAVA_PARAMS=!_JAVA_PARAMS! !_TEST_PARAM:~2!
    shift
    goto param_loop
  )

  if "!_TEST_PARAM:~0,2!"=="-D" (
    rem test if this was double-quoted property "-Dprop=42"
    for /F "delims== tokens=1,*" %%G in ("!_TEST_PARAM!") DO (
      if not ["%%H"] == [""] (
        set _JAVA_PARAMS=!_JAVA_PARAMS! !_PARAM1!
      ) else if [%2] neq [] (
        rem it was a normal property: -Dprop=42 or -Drop="42"
        call set _PARAM1=%%1=%%2
        set _JAVA_PARAMS=!_JAVA_PARAMS! !_PARAM1!
        shift
      )
    )
  ) else (
    set _APP_ARGS=!_APP_ARGS! !_PARAM1!
  )
  shift
  goto param_loop
  :param_afterloop

exit /B 0
