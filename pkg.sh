#!/bin/bash
check_error() {
  if [ $? -ne 0 ]; then
    echo "$1 return code was not zero but $retval"
    exit
  fi
}

rm -rf shell/src/universal/doc/api/
javadoc -source "1.7" -Xdoclint:none -doctitle "SMILE &mdash; Statistical Machine Intelligence and Learning Engine" -classpath ~/.ivy2/cache/org.swinglabs/swingx/jars/* -d shell/src/universal/doc/api/java  -subpackages smile -sourcepath math/src/main/java:data/src/main/java:core/src/main/java:graph/src/main/java:interpolation/src/main/java:nlp/src/main/java:plot/src/main/java
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

#sbt publishSigned
check_error "Publishing"
