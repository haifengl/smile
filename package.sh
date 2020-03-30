#!/bin/bash

check_error() {
  if [ $? -ne 0 ]; then
    echo "$1 return code was not zero but $retval"
    exit
  fi
}

if [[ "$OSTYPE" == "darwin"* ]]; then
    export JAVA_HOME=`/usr/libexec/java_home -v 1.8`
fi

rm -rf docs/2.0/api
export CLASSPATH=$CLASSPATH:$HOME/.ivy2/cache/org.swinglabs/swingx/jars/*:$HOME/.ivy2/cache/org.slf4j/slf4j-api/jars/*:$HOME/.ivy2/cache/com.github.fommil.netlib/core/jars/*:$HOME/.ivy2/cache/net.sourceforge.f2j/arpack_combined_all/jars/*
javadoc -source "1.8" --allow-script-in-comments -bottom '<script src="{@docRoot}/../../js/google-analytics.js" type="text/javascript"></script>' -Xdoclint:none -doctitle "Smile &mdash; Statistical Machine Intelligence and Learning Engine" -d docs/2.0/api/java  -subpackages smile -sourcepath math/src/main/java:netlib/src/main/java:data/src/main/java:core/src/main/java:graph/src/main/java:interpolation/src/main/java:nlp/src/main/java:plot/src/main/java
check_error "javadoc"

sbt clean

sbt universal:packageBin
check_error "sbt universal:packageBin"

while true; do
    read -p "Do you want to publish it? " ans
    case $ans in
        [Yy]* )
            sbt publishSigned
            check_error "sbt publishSigned"

            git checkout scala-2.12
            git merge master
            sbt ++2.12.11 scala/publishSigned
            check_error "sbt ++2.12.11 scala/publishSigned"
            sbt ++2.12.11 json/publishSigned
            check_error "sbt ++2.12.11 json/publishSigned"
            sbt ++2.12.11 vega/publishSigned
            check_error "sbt ++2.12.11 vega/publishSigned"
            sbt ++2.12.11 cas/publishSigned
            check_error "sbt ++2.12.11 cas/publishSigned"

            git checkout scala-2.11
            git merge master
            sbt ++2.11.12 scala/publishSigned
            check_error "sbt ++2.11.12 scala/publishSigned"
            sbt ++2.11.12 json/publishSigned
            check_error "sbt ++2.11.12 json/publishSigned"
            sbt ++2.11.12 vega/publishSigned
            check_error "sbt ++2.11.12 vega/publishSigned"
            sbt ++2.11.12 cas/publishSigned
            check_error "sbt ++2.11.12 cas/publishSigned"

            git checkout master
            break;;
        [Nn]* ) exit;;
        * ) echo "Please answer yes or no.";;
    esac
done
