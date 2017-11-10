#!/bin/bash
check_error() {
  if [ $? -ne 0 ]; then
    echo "$1 return code was not zero but $retval"
    exit
  fi
}

rm -rf shell/src/universal/doc/api/
export CLASSPATH=$CLASSPATH:$HOME/.ivy2/cache/org.swinglabs/swingx/jars/*:$HOME/.ivy2/cache/org.slf4j/slf4j-api/jars/*:$HOME/.ivy2/cache/com.github.fommil.netlib/core/jars/*:$HOME/.ivy2/cache/net.sourceforge.f2j/arpack_combined_all/jars/*
javadoc -source "1.8" --allow-script-in-comments -bottom '<script src="{@docRoot}/../../js/google-analytics.js" type="text/javascript"></script>' -Xdoclint:none -doctitle "Smile &mdash; Statistical Machine Intelligence and Learning Engine" -d shell/src/universal/doc/api/java  -subpackages smile -sourcepath math/src/main/java:netlib/src/main/java:data/src/main/java:core/src/main/java:graph/src/main/java:interpolation/src/main/java:nlp/src/main/java:plot/src/main/java
check_error "javadoc"

sbt clean

sbt universal:packageBin
check_error "Packaging .tgz"

if [[ "$OSTYPE" == "linux-gnu" ]]; then
    sbt docker:publishLocal 
    check_error "Packaging .tgz"
elif [[ "$OSTYPE" == "darwin"* ]]; then
    sbt universal:package-osx-dmg
    check_error "Packaging .dmg"
elif [[ "$OSTYPE" == "cygwin" ]]; then
    sbt windows:packageBin
    check_error "Packaging .msi"
elif [[ "$OSTYPE" == "msys" ]]; then
    sbt windows:packageBin
    check_error "Packaging .msi"
else
    echo "Unsupported OS: $OSTYPE"
fi

#sbt publishSigned
#sbt ++2.10.6 scala/publishSigned
#sbt ++2.11.11 scala/publishSigned
#check_error "Publishing"
