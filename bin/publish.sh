#!/bin/bash

# In a script, history expansion is turned off by default, enable it with
set -o history -o histexpand
# Fail fast, exit safely, and prevent hidden errors from executing downstream
set -euo pipefail

if [[ "$OSTYPE" == "darwin"* ]]; then
    export JAVA_HOME=`/usr/libexec/java_home -v 25`
fi

sbt clean
rm -rf target/docs
rm -rf website/_site/api
sbt unidoc
mkdir -p target/docs
# sbt 2.x writes unidoc output under target/out/jvm/scala-<version>/<project>/javaunidoc
# (sbt 1.x used target/javaunidoc), so locate it instead of hardcoding the path.
UNIDOC_DIR=$(find target/out -type d -path '*/smile/javaunidoc' -print -quit)
if [[ -z "$UNIDOC_DIR" ]]; then
    echo "ERROR: unidoc output not found under target/out" >&2
    exit 1
fi
mv "$UNIDOC_DIR" target/docs/java

sbt json/doc
find target/docs/json -name '*.html' -exec bin/gtag.sh {} \;

sbt scala/doc
find target/docs/scala -name '*.html' -exec bin/gtag.sh {} \;

./gradlew :kotlin:dokkaGenerate
find target/docs/kotlin -name '*.html' -exec bin/gtag.sh {} \;

cd website
npm install
npm run deploy
mkdir -p _site/api
mv ../target/docs/* _site/api/

# build binary package
cd ..
./gradlew :serve:build -Dquarkus.profile=default
sbt studio/Universal/packageBin

while true; do
    read -p "Do you want to publish smile? (yes/no): " ans
    case $ans in
        [Yy]* )
            sbt publishSigned

            sbt ++2.13.18 scala/publishSigned
            sbt ++2.13.18 json/publishSigned
            # sbt ++2.13.18 spark/publishSigned
            break;;
        [Nn]* ) exit 0;;
        * ) echo "Please answer yes or no.";;
    esac
done

while true; do
    read -p "Do you want to release to the Central Repository? (yes/no): " ans
    case $ans in
        [Yy]* )
            sbt sonaRelease
            break;;
        [Nn]* ) exit 0;;
        * ) echo "Please answer yes or no.";;
    esac
done

while true; do
    read -p "Do you want to publish smile-clojure? (yes/no): " ans
    case $ans in
        [Yy]* )
            cd clojure
            ./lein test

            ./lein codox

            ./lein deploy clojars

            cd ..
            find target/docs/clojure -name '*.html' -exec tidy -m {} \;
            find target/docs/clojure -name '*.html' -exec bin/gtag.sh {} \;
            mv target/docs/clojure website/_site/api/

            break;;
        [Nn]* ) break;;
        * ) echo "Please answer yes or no.";;
    esac
done
