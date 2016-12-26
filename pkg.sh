#!/bin/bash
check_error() {
  if [ $? -ne 0 ]; then
    echo "$1 return code was not zero but $retval"
    exit
  fi
}

rm -rf shell/src/universal/doc/api/
CLASSPATH=$CLASSPATH:$HOME/.ivy2/cache/org.swinglabs/swingx/jars/*
javadoc -source "1.8" -bottom '<script src="{@docRoot}/../../js/google-analytics.js" type="text/javascript"></script>' -Xdoclint:none -doctitle "Smile &mdash; Statistical Machine Intelligence and Learning Engine" -d shell/src/universal/doc/api/java  -subpackages smile -sourcepath math/src/main/java:data/src/main/java:core/src/main/java:graph/src/main/java:interpolation/src/main/java:nlp/src/main/java:plot/src/main/java
check_error "javadoc"

sbt clean

sbt universal:packageZipTarball
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

#sbt publish-signed
#sbt ++2.10.6 scala/publish-signed
#sbt ++2.12.1 scala/publish-signed
#check_error "Publishing"
