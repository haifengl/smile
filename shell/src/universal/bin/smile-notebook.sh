#!/bin/bash

while [ $# -ne 0 ]
do
    arg="$1"
    case "$arg" in
        --install-almond)
            installAlmond=true
            ;;
        *)
            echo "Unknown argument $arg"
            ;;
    esac
    shift
done

if [ "$installAlmond" == true ]
then

  if [ -x ./coursier ]
  then
    curl -Lo coursier https://git.io/coursier-cli
    chmod +x coursier
  fi

  SCALA_VERSION=2.13.1 ALMOND_VERSION=0.9.1

  ./coursier bootstrap -r jitpack \
    -i user \
    -I user:sh.almond:scala-kernel-api_$SCALA_VERSION:$ALMOND_VERSION \
    sh.almond:scala-kernel_$SCALA_VERSION:$ALMOND_VERSION \
    --sources \
    --default=true \
    -f -o almond-scala-2.13

  ./almond-scala-2.13 --install --force --id scala213 --display-name "Scala 2.13"
fi

jupyter lab