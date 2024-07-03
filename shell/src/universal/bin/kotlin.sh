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
get_java_cmd() {
  # High-priority override for Jlink images
  if [[ -n "$bundled_jvm" ]];  then
    echo "$bundled_jvm/bin/java"
  elif [[ -n "$JAVA_HOME" ]] && [[ -x "$JAVA_HOME/bin/java" ]];  then
    echo "$JAVA_HOME/bin/java"
  else
    echo "java"
  fi
}

get_classpath() {
  JARS=("$lib_dir"/*.jar)
  for index in "${!JARS[@]}" ; do [[ ${JARS[index]} =~ .*(lihaoyi|scala).* ]] && unset -v 'JARS[$index]' ; done
  CLASSPATH=$(JARS=("${JARS[@]}"); IFS=:; echo "${JARS[*]}")
  echo $CLASSPATH:$app_home/smile-kotlin-*.jar
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
  execRunner "$java_cmd" \
    -D"smile.home=$smile_home" \
    -classpath "$app_classpath" \
    -jar $app_home/ki-shell-*.jar \
    "$@"

  local exit_code=$?
  exit $exit_code
}

###  ------------------------------- ###
###  Main script                     ###
###  ------------------------------- ###

declare -r real_script_path="$(realpath "$0")"
declare -r app_home="$(realpath "$(dirname "$real_script_path")")"
declare -r smile_home="${app_home}/../"
declare -r lib_dir="$(realpath "${app_home}/../lib")"
declare -r app_classpath=$(get_classpath)

declare java_cmd=$(get_java_cmd)

run "$@"
