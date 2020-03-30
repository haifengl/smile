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

get_kotlin_cmd() {
  if [[ -n "$KOTLIN_HOME" ]] && [[ -x "$KOTLIN_HOME/bin/kotlinc-jvm" ]];  then
    echo "$KOTLIN_HOME/bin/kotlinc-jvm"
  else
    echo "kotlinc-jvm"
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
  execRunner "$kotlin_cmd" \
    -J-D"smile.home=$smile_home" \
    -classpath "$app_classpath" \
    -jvm-target 1.8 \
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

declare kotlin_cmd=$(get_kotlin_cmd)

run "$@"
