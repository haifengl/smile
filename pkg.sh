#!/bin/bash
check_error() {
  if [ $? -ne 0 ]; then
    echo "$1Javadoc return code was not zero but $retval"
    exit
  fi
}

rm -rf src/universal/doc/api/
javadoc -source "1.7" -Xdoclint:none -doctitle "SMILE &mdash; Statistical Machine Intelligence and Learning Engine" -classpath ~/.ivy2/cache/org.swinglabs/swingx/jars/* -d src/universal/doc/api/java  -subpackages smile -sourcepath math/src/main/java:data/src/main/java:core/src/main/java:graph/src/main/java:interpolation/src/main/java:nlp/src/main/java:plot/src/main/java
check_error "Javadoc"

sbt universal:packageZipTarball
check_error "Packaging .tgz"

sbt universal:package-osx-dmg
check_error "Packaging .dmg"

#sbt publishSigned
check_error "Publishing"
