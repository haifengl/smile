#!/bin/bash

while [ $# -ne 0 ]
do
    arg="$1"
    case "$arg" in
        --install)
            installJupyter=true
            installAlmond=true
            installKotlin=true
            installClojure=true
            ;;
        --install-almond)
            installAlmond=true
            ;;
        --install-kotlin)
            installKotlin=true
            ;;
        --install-clojure)
            installClojure=true
            ;;
        --install-scijava)
            installSciJava=true
            ;;
        *)
            echo "Unknown argument $arg"
            ;;
    esac
    shift
done

if [ "$installJupyter" == true ]
then
    conda create --name smile-env
    conda install --name smile-env -c conda-forge jupyterlab
fi

if [ "$installKotlin" == true ]
then
    conda install --name smile-env -c jetbrains kotlin-jupyter-kernel
fi

if [ "$installClojure" == true ]
then
    conda install --name smile-env -c simplect clojupyter
fi

if [ "$installSciJava" == true ]
then
    conda install --name smile-env -c conda-forge scijava-jupyter-kernel
fi

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

source activate smile-env
jupyter lab
