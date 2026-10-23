plugins {
    id("buildlogic.java-application-conventions")
}

dependencies {
    implementation(project(":base"))
    implementation(project(":core"))
    implementation(project(":deep"))
    implementation(project(":nlp"))
    implementation(project(":plot"))
    implementation(project(":kotlin"))

    implementation(libs.swingx)
    implementation(libs.bundles.jackson)
    implementation(libs.bundles.jsonschema)
    implementation(libs.jsonschema.swagger)
    implementation(libs.duckdb) // smile.data.SQL, used by the agents (e.g. AutoML)
    implementation(libs.bundles.arrow)
    implementation(libs.bundles.kotlin.scripting)

    implementation("org.scala-lang:scala3-library_3:3.9.0")
    implementation("org.scala-lang:scala3-repl_3:3.9.0")
    implementation("info.picocli:picocli:4.7.7")
    implementation("ch.qos.logback:logback-classic:1.6.5")
    implementation("com.openai:openai-java:4.86.0")
    implementation("com.anthropic:anthropic-java:2.73.0")
    implementation("com.google.genai:google-genai:1.78.0")
    implementation("org.commonmark:commonmark:0.30.1")
    implementation("org.xhtmlrenderer:flying-saucer-core:10.6.0")
    implementation(libs.lsp4j)
    implementation("com.fifesoft:rsyntaxtextarea:4.0.1")
    implementation("com.fifesoft:rstaui:4.0.0")
    implementation("com.fifesoft:spellchecker:3.4.1")
    implementation("com.formdev:flatlaf:3.7.2")
    implementation("com.formdev:flatlaf-fonts-jetbrains-mono:2.304")
    implementation("org.apache.maven:maven-resolver-provider:3.10.0")
    implementation("org.apache.maven.resolver:maven-resolver-supplier-mvn4:2.0.24")
    implementation("io.modelcontextprotocol.sdk:mcp:2.1.0")
    implementation("io.github.furstenheim:copy_down:1.1")
    implementation("com.github.serpapi:serpapi-java:1.2.0")
    implementation("com.google.code.gson:gson:2.14.0") // evict older version used by serpapi
    implementation(libs.secret.store)

    implementation(libs.avro) {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
    }
    implementation(libs.snappy) // for avro
    implementation(libs.parso) // SAS7BDAT

    // local packages
    implementation(fileTree("lib") { include("*.jar") })
}

application {
    applicationName = "smile"
    mainClass = "smile.Main"
}

distributions {
    main {
        distributionBaseName = "smile"
        contents {
            // 1. Package universal resources (bin, conf, examples, notebooks, jbr, data/eng_dic.zip, etc.)
            from("src/universal")

            // 2. Package documentation and license files at root
            from("README.md")
            from("CLI.md")
            from("LICENSE") {
                rename { "LICENSE.studio" }
            }
            from(rootProject.file("serve/LICENSE")) {
                into("serve")
            }
            from(rootProject.file("COPYING"))
            from(rootProject.file("LICENSE"))

            // 3. Package serve application
            from(rootProject.file("serve/build/quarkus-app")) {
                into("serve")
            }

            // 4. Package test datasets matching sbt dataPrefixes
            val dataPrefixes = listOf(
                "mnist", "usps", "sqlite", "kylo", "weka",
                "libsvm", "regression", "sas", "stat", "sparse", "matrix"
            )
            from(rootProject.file("base/src/test/resources/data")) {
                into("data")
                dataPrefixes.forEach { prefix ->
                    include("$prefix/**")
                }
            }

            filesMatching("bin/setup") {
                permissions {
                    unix("rwxr-xr-x")
                }
            }
        }
    }
}

tasks.matching { it.name in listOf("distZip", "distTar", "installDist") }.configureEach {
    dependsOn(":serve:quarkusBuild")
}

tasks.register("stage") {
    group = "distribution"
    description = "Stages the studio application distribution into build/install/smile."
    dependsOn("installDist")
}

tasks.named<Test>("test") {
    jvmArgs = listOf(
        "-XX:+UseZGC",
        "-Xss4M",
        "-XX:MaxMetaspaceSize=1024M",
        "--enable-native-access=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED"
    )
    systemProperty("smile.home", "${project.rootDir}/base/src/test/resources")

    filter {
        // Because `scala` and `json` are sbt-only modules
        // and not included in Gradle's build, the REPL
        // child process spawned during Gradle test runs
        // cannot resolve the Scala extension methods.
        excludeTestsMatching("smile.studio.kernel.ScalaKernelTest")
    }
}

tasks.named<JavaExec>("run") {
    jvmArgs = listOf(
        "-XX:MaxMetaspaceSize=1024M",
        "-Xss4M",
        "--enable-native-access=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "-Dscala.usejavacp=true",
        "-Dsmile.home=${project.rootDir}/studio/src/universal"
    )
}

tasks.withType<Javadoc> {
    enabled = false
}

val unixScriptContent = generateUnixScript().replace("\r\n", "\n")
val windowsScriptContent = generateWindowsScript().replace("\r\n", "\n").replace("\n", "\r\n")

class CustomizeStartScripts(
    private val unixScriptContent: String,
    private val windowsScriptContent: String
) : Action<Task> {
    override fun execute(task: Task) {
        val scriptsTask = task as CreateStartScripts
        scriptsTask.unixScript.writeText(unixScriptContent)
        scriptsTask.unixScript.setExecutable(true, false)
        scriptsTask.windowsScript.writeText(windowsScriptContent)
    }
}

tasks.named<CreateStartScripts>("startScripts") {
    applicationName = "smile"
    mainClass.set("smile.Main")
    doLast(CustomizeStartScripts(unixScriptContent, windowsScriptContent))
}

fun generateUnixScript(): String {
    val d = "$"
    return """#!/usr/bin/env bash

###  ------------------------------- ###
###  Helper methods for BASH scripts ###
###  ------------------------------- ###

die() {
  echo "${d}@" 1>&2
  exit 1
}

realpath () {
(
  TARGET_FILE="${d}1"
  CHECK_CYGWIN="${d}2"

  cd "${d}(dirname "${d}TARGET_FILE")"
  TARGET_FILE=${d}(basename "${d}TARGET_FILE")

  COUNT=0
  while [ -L "${d}TARGET_FILE" -a ${d}COUNT -lt 100 ]
  do
      TARGET_FILE=${d}(readlink "${d}TARGET_FILE")
      cd "${d}(dirname "${d}TARGET_FILE")"
      TARGET_FILE=${d}(basename "${d}TARGET_FILE")
      COUNT=${d}(( ${d}COUNT + 1 ))
  done

  if [ "${d}TARGET_FILE" == "." -o "${d}TARGET_FILE" == ".." ]; then
    cd "${d}TARGET_FILE"
  fi
  TARGET_DIR="${d}(pwd -P)"
  if [ "${d}TARGET_DIR" == "/" ]; then
    TARGET_FILE="/${d}TARGET_FILE"
  else
    TARGET_FILE="${d}TARGET_DIR/${d}TARGET_FILE"
  fi

  # make sure we grab the actual windows path, instead of cygwin's path.
  if [[ "x${d}CHECK_CYGWIN" == "x" ]]; then
    echo "${d}TARGET_FILE"
  else
    echo ${d}(cygwinpath "${d}TARGET_FILE")
  fi
)
}

# Uses uname to detect if we're in the odd cygwin environment.
is_cygwin() {
  local os=${d}(uname -s)
  case "${d}os" in
    CYGWIN*) return 0 ;;
    *)  return 1 ;;
  esac
}

# This can fix cygwin style /cygdrive paths so we get the
# windows style paths.
cygwinpath() {
  local file="${d}1"
  if is_cygwin; then
    echo ${d}(cygpath -w ${d}file)
  else
    echo ${d}file
  fi
}

# Make something URI friendly
make_url() {
  url="${d}1"
  local nospaces=${d}{url// /%20}
  if is_cygwin; then
    echo "/${d}{nospaces//\\//}"
  else
    echo "${d}nospaces"
  fi
}

fixCygwinClasspath() {
  OLDIFS=${d}IFS
  IFS=":"
  read -a classpath_members <<< "${d}1"
  declare -a fixed_members
  IFS=${d}OLDIFS
  for i in "${d}{!classpath_members[@]}"
  do
    fixed_members[i]=${d}(realpath "${d}{classpath_members[i]}" "fix")
  done
  IFS=";"
  echo "${d}{fixed_members[*]}"
  IFS=${d}OLDIFS
}

# Fix the classpath we use for cygwin.
fix_classpath() {
  cp="${d}1"
  if is_cygwin; then
    echo "${d}(fixCygwinClasspath "${d}cp")"
  else
    echo "${d}cp"
  fi
}

# Detect if we should use JAVA_HOME or just try PATH.
get_java_cmd() {
  # High-priority override for Jlink images or bundled JBR
  if [[ -n "${d}bundled_jvm" ]]; then
    echo "${d}bundled_jvm/bin/java"
  elif [[ -d "${d}app_home/../jbr" ]] && [[ -x "${d}app_home/../jbr/bin/java" ]]; then
    echo "${d}app_home/../jbr/bin/java"
  elif [[ -n "${d}JAVA_HOME" ]] && [[ -x "${d}JAVA_HOME/bin/java" ]]; then
    echo "${d}JAVA_HOME/bin/java"
  else
    echo "java"
  fi
}

echoerr () {
  echo 1>&2 "${d}@"
}
vlog () {
  [[ ${d}verbose || ${d}debug ]] && echoerr "${d}@"
}
dlog () {
  [[ ${d}debug ]] && echoerr "${d}@"
}
execRunner () {
  # print the arguments one to a line, quoting any containing spaces
  [[ ${d}verbose || ${d}debug ]] && echo "# Executing command line:" && {
    for arg; do
      if printf "%s\n" "${d}arg" | grep -q ' '; then
        printf "\"%s\"\n" "${d}arg"
      else
        printf "%s\n" "${d}arg"
      fi
    done
    echo ""
  }

  exec "${d}@"
}
addJava () {
  dlog "[addJava] arg = '${d}1'"
  java_args+=( "${d}1" )
}
addApp () {
  dlog "[addApp] arg = '${d}1'"
  app_commands+=( "${d}1" )
}
addResidual () {
  dlog "[residual] arg = '${d}1'"
  residual_args+=( "${d}1" )
}
addDebugger () {
  addJava "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=${d}1"
}

require_arg () {
  local type="${d}1"
  local opt="${d}2"
  local arg="${d}3"
  if [[ -z "${d}arg" ]] || [[ "${d}{arg:0:1}" == "-" ]]; then
    die "${d}opt requires <${d}type> argument"
  fi
}
is_function_defined() {
  declare -f "${d}1" > /dev/null
}

process_args () {
  local no_more_snp_opts=0
  while [[ ${d}# -gt 0 ]]; do
    case "${d}1" in
             --) shift && no_more_snp_opts=1 && break ;;
       -h|-help) usage; exit 1 ;;
    -v|-verbose) verbose=1 && shift ;;
      -d|-debug) debug=1 && shift ;;

    -no-version-check) no_version_check=1 && shift ;;

           -mem) echo "!! WARNING !! -mem option is ignored. Please use -J-Xmx and -J-Xms" && shift 2 ;;
     -jvm-debug) require_arg port "${d}1" "${d}2" && addDebugger ${d}2 && shift 2 ;;

          -main) custom_mainclass="${d}2" && shift 2 ;;

     -java-home) require_arg path "${d}1" "${d}2" && jre=`eval echo ${d}2` && java_cmd="${d}jre/bin/java" && shift 2 ;;

 -D*|-agentlib*|-agentpath*|-javaagent*|-XX*) addJava "${d}1" && shift ;;
                                         -J*) addJava "${d}{1:2}" && shift ;;
                                           *) addResidual "${d}1" && shift ;;
    esac
  done

  if [[ no_more_snp_opts ]]; then
    while [[ ${d}# -gt 0 ]]; do
      addResidual "${d}1" && shift
    done
  fi

  is_function_defined process_my_args && {
    myargs=("${d}{residual_args[@]}")
    residual_args=()
    process_my_args "${d}{myargs[@]}"
  }
}

run() {
  process_args "${d}@"
  set -- "${d}{residual_args[@]}"
  argumentCount=${d}#

  if is_cygwin; then
    stty -icanon min 1 -echo > /dev/null 2>&1
    addJava "-Djline.terminal=jline.UnixTerminal"
    addJava "-Dsbt.cygwin=true"
  fi

  if [[ ! ${d}no_version_check ]]; then
    java_version_check
  fi

  if [ -n "${d}custom_mainclass" ]; then
    mainclass=("${d}custom_mainclass")
  else
    mainclass=("${d}{app_mainclass[@]}")
  fi

  if [ "${d}app_mainclass" = "" ] || [ ${d}custom_mainclass ]; then
    if [ "${d}custom_mainclass" = "" ]; then
      echo "You need to pass -main argument."
      exit 1
    fi
    app_mainclass=${d}custom_mainclass
  fi

  if [[ "${d}JAVA_OPTS" != "" ]]; then
    java_opts="${d}{JAVA_OPTS}"
  fi

  execRunner "${d}java_cmd" \
    ${d}{java_opts[@]} \
    "${d}{java_args[@]}" \
    -cp "${d}(fix_classpath "${d}app_classpath")" \
    "${d}{mainclass[@]}" \
    "${d}{app_commands[@]}" \
    "${d}{residual_args[@]}"

  local exit_code=${d}?
  if is_cygwin; then
    stty icanon echo > /dev/null 2>&1
  fi
  exit ${d}exit_code
}

loadConfigFile() {
  cat "${d}1" | sed ${'$'}'/^\#/d;s/\r${d}//'
}

java_version_check() {
  readonly java_version=${d}("${d}java_cmd" -version 2>&1 | awk -F '"' '/version/ {print ${d}2}')
  if [[ "${d}java_version" == "" ]]; then
    echo
    echo "No java installation was detected."
    echo "Please download a valid Java runtime and install before running smile."
    echo
    exit 1
  else
    local major=${d}(echo "${d}java_version" | cut -d'.' -f1)
    if [[ "${d}major" -eq "1" ]]; then
     local major=${d}(echo "${d}java_version" | cut -d'.' -f2)
    fi
    if [[ "${d}major" -lt "8" ]]; then
      echo
      echo "The java installation you have is not up to date."
      echo "smile requires at least version 1.8+, you have version ${d}java_version."
      echo
      exit 1
    fi
  fi
}

usage() {
 cat <<EOM
Usage: smile [options]

  -h | -help         print this message
  -v | -verbose      this runner is chattier
  -d | -debug        enable debug output for the launcher script
  -no-version-check  Don't run the java version check.
  -main <classname>  Define a custom main class
  -jvm-debug <port>  Turn on JVM debugging, open at the given port.

  # java version (default: java from PATH, currently ${d}(java -version 2>&1 | grep version))
  -java-home <path>         alternate JAVA_HOME

  # jvm options and output control
  JAVA_OPTS          environment variable, if unset uses "${d}java_opts"
  -Dkey=val          pass -Dkey=val directly to the java runtime
  -J-X               pass option -X directly to the java runtime
                     (-J is stripped)

  # special option
  --                 To stop parsing built-in commands from the rest of the command-line.

Available main classes:
	smile.Main
EOM
}

###  ------------------------------- ###
###  Main script                     ###
###  ------------------------------- ###

declare -a residual_args
declare -a java_args
declare -a app_commands
declare -r real_script_path="${d}(realpath "${d}0")"
declare -r app_home="${d}(realpath "${d}(dirname "${d}real_script_path")")"
declare -r lib_dir="${d}(realpath "${d}{app_home}/../lib")"
declare -a app_mainclass=('smile.Main')

declare -r script_conf_file="${d}{app_home}/../conf/smile.ini"
declare -r app_classpath="${d}lib_dir/*"

export SMILE_HOME=${d}(dirname "${d}{app_home}")
addJava "-XX:MaxMetaspaceSize=1024M"
addJava "-Xss4M"
addJava "--add-opens=java.base/java.nio=ALL-UNNAMED"
addJava "--enable-native-access=ALL-UNNAMED"
addJava "-Dsmile.home=${d}{SMILE_HOME}"
addJava "-Dscala.usejavacp=true"
addJava "-Dscala.repl.autoruncode=${d}{app_home}/predef.sc"
export PYTHONPATH="${d}{PYTHONPATH}:${d}{app_home}/../lib/ioa-agent-1.0.0.jar"
export PYTHONUTF8=1
export PATH="${d}{app_home}:${d}{PATH}"
[ -f "${d}SMILE_HOME/venv/bin/activate" ] && source "${d}SMILE_HOME/venv/bin/activate"
ONNX_CAPI=${d}(ls -d "${d}{SMILE_HOME}"/venv/lib/python*/site-packages/onnxruntime/capi 2>/dev/null | head -n 1)
if [ -n "${d}ONNX_CAPI" ]; then addJava "-Donnxruntime.native.path=${d}ONNX_CAPI"; fi
export LD_LIBRARY_PATH=${d}{app_home}/../venv/Lib/site-packages/torch/lib:${d}{app_home}/../venv/Lib/site-packages/onnxruntime/capi:${d}LD_LIBRARY_PATH

declare java_cmd=${d}(get_java_cmd)

# if configuration files exist, prepend their contents to ${d}@ so it can be processed by this runner
[[ -f "${d}script_conf_file" ]] && set -- ${d}(loadConfigFile "${d}script_conf_file") "${d}@"

run "${d}@"
"""
}

fun generateWindowsScript(): String {
    return """@REM smile launcher script
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

set "APP_CLASSPATH=%APP_LIB_DIR%\*"
set "APP_MAIN_CLASS=smile.Main"
set "SCRIPT_CONF_FILE=%APP_HOME%\conf\smile.ini"
call :add_java -XX:MaxMetaspaceSize=1024M
call :add_java -Xss4M
call :add_java --add-opens=java.base/java.nio=ALL-UNNAMED
call :add_java --enable-native-access=ALL-UNNAMED
call :add_java --enable-preview
call :add_java -Dsmile.home=%APP_HOME%
call :add_java -Dscala.usejavacp=true
call :add_java -Dscala.repl.autoruncode=%APP_HOME%\bin\predef.sc
set "JAVA_HOME=%APP_HOME%\jbr"
set OPENBLAS_NO_AVX512=1
set OPENBLAS_NUM_THREAD=1
set PYTHONPATH=%PYTHONPATH%;%APP_HOME%\lib\ioa-agent-1.0.0.jar
set PYTHONUTF8=1
if exist "%APP_HOME%\venv\Scripts\activate.bat" CALL "%APP_HOME%\venv\Scripts\activate.bat"
set "PATH=%~dp0;%APP_HOME%\venv\Lib\site-packages\torch\lib;!PATH!"
if exist "%APP_HOME%\venv\Lib\site-packages\onnxruntime\capi" call :add_java "-Donnxruntime.native.path=%APP_HOME%\venv\Lib\site-packages\onnxruntime\capi"

rem Bundled JRE has priority over standard environment variables
if defined BUNDLED_JVM (
  set "_JAVACMD=%BUNDLED_JVM%\bin\java.exe"
) else (
  if "%JAVACMD%" neq "" (
    set "_JAVACMD=%JAVACMD%"
  ) else (
    if "%JAVA_HOME%" neq "" (
      if exist "%JAVA_HOME%\bin\java.exe" set "_JAVACMD=%JAVA_HOME%\bin\java.exe"
    )
  )
)

if "%_JAVACMD%"=="" set _JAVACMD=java

rem if configuration files exist, prepend their contents to the script arguments so it can be processed by this runner
call :parse_config "%SCRIPT_CONF_FILE%" SCRIPT_CONF_ARGS

call :process_args %SCRIPT_CONF_ARGS% %%*

rem Detect if this java is ok to use.
for /F %%j in ('"%_JAVACMD%" -version  2^>^&1') do (
  if %%~j==java set JAVAINSTALLED=1
  if %%~j==openjdk set JAVAINSTALLED=1
)

rem BAT has no logical or, so we do it OLD SCHOOL! Oppan Redmond Style
set JAVAOK=true
if not defined JAVAINSTALLED set JAVAOK=false

if "%JAVAOK%"=="false" (
  echo.
  echo A Java JDK is not installed or can't be found.
  if not "%JAVA_HOME%"=="" (
    echo JAVA_HOME = "%JAVA_HOME%"
  )
  echo.
  echo Please go to
  echo   http://www.oracle.com/technetwork/java/javase/downloads/index.html
  echo and download a valid Java JDK and install before running smile.
  echo.
  echo If you think this message is in error, please check
  echo your environment variables to see if "java.exe" and "javac.exe" are
  echo available via JAVA_HOME or PATH.
  echo.
  if defined DOUBLECLICKED pause
  exit /B 1
)

set _JAVA_OPTS=!_JAVA_OPTS! !_JAVA_PARAMS!

if defined CUSTOM_MAIN_CLASS (
    set MAIN_CLASS=!CUSTOM_MAIN_CLASS!
) else (
    set MAIN_CLASS=!APP_MAIN_CLASS!
)

rem Call the application and pass all arguments unchanged.
"%_JAVACMD%" !_JAVA_OPTS! !SMILE_OPTS! -cp "%APP_CLASSPATH%" %MAIN_CLASS% !_APP_ARGS!

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


:add_java
  set _JAVA_PARAMS=!_JAVA_PARAMS! %*
exit /B 0


:add_app
  set _APP_ARGS=!_APP_ARGS! %*
exit /B 0


rem Processes incoming arguments and places them in appropriate global variables
:process_args
  :param_loop
  shift
  call set _PARAM1=%%0
  set "_TEST_PARAM=%~0"

  if ["!_PARAM1!"]==[""] goto param_afterloop

  if "!_TEST_PARAM!"=="-main" (
    call set CUSTOM_MAIN_CLASS=%%1
    shift
    goto param_loop
  )

  if "!_TEST_PARAM!"=="-java-home" (
    set "JAVA_HOME=%~1"
    set "_JAVACMD=%~1\bin\java.exe"
    shift
    goto param_loop
  )

  if "!_TEST_PARAM!"=="-jvm-debug" (
    set "_JAVA_PARAMS=!_JAVA_PARAMS! -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=%~1"
    shift
    goto param_loop
  )

  if "!_TEST_PARAM:~0,2!"=="-J" (
    rem strip -J prefix
    call set _TEST_PARAM=!_TEST_PARAM:~2!
    if not "!_TEST_PARAM:~0,5!" == "-XX:+" if not "!_TEST_PARAM:~0,5!" == "-XX:-" if "!_TEST_PARAM:~0,3!" == "-XX" (
      rem special handling for -J-XX since '=' gets parsed away
      for /F "delims== tokens=1,*" %%G in ("!_TEST_PARAM!") DO (
        call set _TEST_PARAM=!_TEST_PARAM!=%%1
        shift
      )
    )
    set _JAVA_PARAMS=!_JAVA_PARAMS! !_TEST_PARAM!
    goto param_loop
  )

  if "!_TEST_PARAM:~0,2!"=="-D" (
    rem test if this was double-quoted property "-Dprop=42"
    for /F "delims== tokens=1,*" %%G in ("!_TEST_PARAM!") DO (
      if not ["%%H"] == [""] (
        set _JAVA_PARAMS=!_JAVA_PARAMS! !_PARAM1!
      ) else if [%1] neq [] (
        rem it was a normal property: -Dprop=42 or -Drop="42"
        call set _PARAM1=%%0=%%1
        set _JAVA_PARAMS=!_JAVA_PARAMS! !_PARAM1!
        shift
      )
    )
    goto param_loop
  )

  set _APP_ARGS=!_APP_ARGS! !_PARAM1!

  goto param_loop
  :param_afterloop

exit /B 0
"""
}
