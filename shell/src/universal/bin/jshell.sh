#!/usr/bin/env bash

###  ------------------------------- ###
###  Helper methods for BASH scripts ###
###  ------------------------------- ###

realpath () {
(
  TARGET_FILE="$1"

  cd "$(dirname "$TARGET_FILE")"
  TARGET_FILE=$(basename "$TARGET_FILE")

  COUNT=0
  while [ -L "$TARGET_FILE" -a $COUNT -lt 100 ]
  do
      TARGET_FILE=$(readlink "$TARGET_FILE")
      cd "$(dirname "$TARGET_FILE")"
      TARGET_FILE=$(basename "$TARGET_FILE")
      COUNT=$(($COUNT + 1))
  done

  if [ "$TARGET_FILE" == "." -o "$TARGET_FILE" == ".." ]; then
    cd "$TARGET_FILE"
    TARGET_FILEPATH=
  else
    TARGET_FILEPATH=/$TARGET_FILE
  fi

  echo "$(pwd -P)/$TARGET_FILE"
)
}

# Detect if we should use JAVA_HOME or just try PATH.
get_jshell_cmd() {
  # High-priority override for Jlink images
  if [[ -n "$bundled_jvm" ]];  then
    echo "$bundled_jvm/bin/jshell"
  elif [[ -n "$JAVA_HOME" ]] && [[ -x "$JAVA_HOME/bin/jshell" ]];  then
    echo "$JAVA_HOME/bin/jshell"
  else
    echo "jshell"
  fi
}

execRunner () {
  # print the arguments one to a line, quoting any containing spaces
  [[ $verbose || $debug ]] && echo "# Executing command line:" && {
    for arg; do
      if printf "%s\n" "$arg" | grep -q ' '; then
        printf "\"%s\"\n" "$arg"
      else
        printf "%s\n" "$arg"
      fi
    done
    echo ""
  }

  # we use "exec" here for our pids to be accurate.
  exec "$@"
}

# Actually runs the script.
run() {
  execRunner "$jshell_cmd" \
    -R-D"smile.home=$smile_home" \
    --class-path "$app_classpath" \
    "$@"

  local exit_code=$?
  exit $exit_code
}

###  ------------------------------- ###
###  Main script                     ###
###  ------------------------------- ###

if [[ "$OSTYPE" == "darwin"* ]]; then
    export JAVA_HOME=`/usr/libexec/java_home -v 11+`
fi

declare -r real_script_path="$(realpath "$0")"
declare -r app_home="$(realpath "$(dirname "$real_script_path")")"
declare -r smile_home="${app_home}/../"
declare -r lib_dir="$(realpath "${app_home}/../lib")"
declare -r app_classpath="$lib_dir/*"

declare jshell_cmd=$(get_jshell_cmd)

run "$@"
