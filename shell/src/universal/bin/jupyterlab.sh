#!/bin/bash

Help() {
  echo "Smile Notebooks - Statistical Machine Intelligence & Learning Engine"
  echo
  echo "Syntax: jupyterlab.sh [options]"
  echo "options:"
  echo "--update           Update conda environment smile-env."
  echo "--remove           Remove conda environment smile-env."
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

  SCALA_VERSION=3.4.2 ALMOND_VERSION=0.14.0
  ./coursier launch "almond:$ALMOND_VERSION" --scala $SCALA_VERSION -- \
    --install \
    --id scala3 \
    --display-name "Scala (3.4)" \
    --env "JAVA_OPTS=-XX:MaxRAMPercentage=80.0" \
    --variable-inspector \
    </dev/null 2>&1 | grep -v '^Download'
}

conda_auto_env() {
  if [ -e "$1/environment.yml" ]; then
    # echo "$1/environment.yml file found"
    SMILE_ENV=$(head -n 1 "$1/environment.yml" | cut -f2 -d ' ')
    # Check if you are already in the environment
    if [[ $PATH != *$SMILE_ENV* ]]; then
      # Check if the environment exists
      source activate $SMILE_ENV
      if [ $? -eq 0 ]; then
        :
      else
        # Create the environment and activate
        echo "Creating conda environment $SMILE_ENV..."
        conda env create -f "$1/environment.yml"
        install_almond
        source activate $SMILE_ENV
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
        --update)
            updateSmileEnv=true
            ;;
        --remove)
            removeSmileEnv=true
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

declare -r real_script_path="$(realpath "$0")"
declare -r app_home="$(realpath "$(dirname "$real_script_path")")"

conda_auto_env $app_home

if [ "$updateSmileEnv" == true ]
then
    conda env update --file $app_home/environment.yml --prune
    exit
fi

if [ "$removeSmileEnv" == true ]
then
    conda activate
    conda remove --name $SMILE_ENV --all
    exit
fi

jupyter lab --notebook-dir="$app_home/.." --ip 0.0.0.0
