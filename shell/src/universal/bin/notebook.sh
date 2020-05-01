#!/bin/bash

Help() {
  echo "Smile Notebooks - Statistical Machine Intelligence & Learning Engine"
  echo
  echo "Syntax: notebook.sh [options]"
  echo "options:"
  echo "--install-scijava  Intall SciJava kernel (requires Java 11+)."
  echo "--help             Print this Help."
  echo
}

realpath () {
(
  TARGET_FILE="$1"
  CHECK_CYGWIN="$2"

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

  # make sure we grab the actual windows path, instead of cygwin's path.
  if [[ "x$CHECK_CYGWIN" == "x" ]]; then
    echo "$(pwd -P)/$TARGET_FILE"
  else
    echo $(cygwinpath "$(pwd -P)/$TARGET_FILE")
  fi
)
}

install_almond() {
  if [ ! -x ./coursier ]
  then
      curl -Lo coursier https://git.io/coursier-cli
      chmod +x coursier
  fi

  SCALA_VERSION=2.13.1 ALMOND_VERSION=0.9.1

  ./coursier bootstrap \
      -r jitpack \
      -i user \
      -I user:sh.almond:scala-kernel-api_$SCALA_VERSION:$ALMOND_VERSION \
      sh.almond:scala-kernel_$SCALA_VERSION:$ALMOND_VERSION \
      --sources \
      --default=true \
      -f -o almond-scala-2.13

  ./almond-scala-2.13 --install --force --id scala213 --display-name "Scala (2.13)" \
      --command "java -XX:MaxRAMPercentage=80.0 -jar almond-scala-2.13 --id scala213 --display-name 'Scala (2.13)'" \
      --copy-launcher \
      --metabrowse

  rm -f almond-scala-2.13 coursier
}

conda_auto_env() {
  if [ -e "$1/environment.yml" ]; then
    # echo "environment.yml file found"
    ENV=$(head -n 1 "$1/environment.yml" | cut -f2 -d ' ')
    # Check if you are already in the environment
    if [[ $PATH != *$ENV* ]]; then
      # Check if the environment exists
      source activate $ENV
      if [ $? -eq 0 ]; then
        :
      else
        # Create the environment and activate
        echo "Creating '$ENV'..."
        conda env create -f "$1/environment.yml"
        install_almond
        source activate $ENV
      fi
    fi
  fi
}

while [ $# -ne 0 ]
do
    arg="$1"
    case "$arg" in
        -h|--help)
            Help
            exit;;
        --install-scijava)
            installSciJava=true
            ;;
        *)
            echo "Unknown argument $arg"
            ;;
    esac
    shift
done

if ! type "conda" > /dev/null; then
  echo "'conda' is not available. Please check your PATH or install Anaconda."
  exit
fi

if [ "$installSciJava" == true ]
then
    conda install --name smile-env -c conda-forge scijava-jupyter-kernel
fi

declare -r real_script_path="$(realpath "$0")"
declare -r app_home="$(realpath "$(dirname "$real_script_path")")"

conda_auto_env $app_home
jupyter lab --notebook-dir="$app_home/.."
