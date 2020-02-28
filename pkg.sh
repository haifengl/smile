#!/bin/bash
check_error() {
  if [ $? -ne 0 ]; then
    echo "$1 return code was not zero but $retval"
    exit
  fi
}

rm -rf docs/2.0/api
export CLASSPATH=$CLASSPATH:$HOME/.ivy2/cache/org.swinglabs/swingx/jars/*:$HOME/.ivy2/cache/org.slf4j/slf4j-api/jars/*:$HOME/.ivy2/cache/com.github.fommil.netlib/core/jars/*:$HOME/.ivy2/cache/net.sourceforge.f2j/arpack_combined_all/jars/*
javadoc -source "1.8" --allow-script-in-comments -bottom '<script src="{@docRoot}/../../js/google-analytics.js" type="text/javascript"></script>' -Xdoclint:none -doctitle "Smile &mdash; Statistical Machine Intelligence and Learning Engine" -d docs/2.0/api/java  -subpackages smile -sourcepath math/src/main/java:netlib/src/main/java:data/src/main/java:core/src/main/java:graph/src/main/java:interpolation/src/main/java:nlp/src/main/java:plot/src/main/java
check_error "javadoc"

sbt clean

sbt universal:packageBin
check_error "Packaging .tgz"

#if [[ "$OSTYPE" == "linux-gnu" ]]; then
#    sbt docker:publishLocal 
#    check_error "Packaging .tgz"
#elif [[ "$OSTYPE" == "darwin"* ]]; then
#    sbt universal:packageOsxDmg
#    check_error "Packaging .dmg"
#elif [[ "$OSTYPE" == "cygwin" ]]; then
#    sbt windows:packageBin
#    check_error "Packaging .msi"
#elif [[ "$OSTYPE" == "msys" ]]; then
#    sbt windows:packageBin
#    check_error "Packaging .msi"
#else
#    echo "Unsupported OS: $OSTYPE"
#fi

#sbt publishSigned

#git checkout scala-2.12
#sbt ++2.12.10 scala/publishSigned
#sbt ++2.12.10 json/publishSigned
#sbt ++2.12.10 vega/publishSigned

#git checkout scala-2.11
#sbt ++2.11.12 scala/publishSigned
#sbt ++2.11.12 json/publishSigned
#sbt ++2.11.12 vega/publishSigned
