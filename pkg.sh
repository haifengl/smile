#!/bin/sh
javadoc -source "1.7" -Xdoclint:none -doctitle "SMILE -- Statistical Machine Intelligence and Learning Engine" -classpath ~/.ivy2/cache/org.swinglabs/swingx/jars/* -d src/universal/doc/api/java  -subpackages smile -sourcepath math/src/main/java:data/src/main/java:core/src/main/java:graph/src/main/java:interpolation/src/main/java:nlp/src/main/java:plot/src/main/java
sbt universal:packageZipTarball
sbt universal:package-osx-dmg
