#!/bin/bash

# In a script, history expansion is turned off by default, enable it with
set -o history -o histexpand
# Fail fast, exit safely, and prevent hidden errors from executing downstream
set -euo pipefail

if [[ "$OSTYPE" == "darwin"* ]]; then
    export JAVA_HOME=`/usr/libexec/java_home -v 25`
fi

sbt clean
rm -rf doc/*
rm -rf website/_site/api
sbt unidoc
mv target/javaunidoc doc/java

sbt json/doc
find doc/json -name '*.html' -exec bin/gtag.sh {} \;

sbt scala/doc
find doc/scala -name '*.html' -exec bin/gtag.sh {} \;

./gradlew :kotlin:dokkaGenerate
find doc/kotlin -name '*.html' -exec bin/gtag.sh {} \;

cd website
npm install
npm run deploy
mkdir -p _site/api
mv ../doc/* _site/api/

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
            find doc/clojure -name '*.html' -exec tidy -m {} \;
            find doc/clojure -name '*.html' -exec bin/gtag.sh {} \;
            mv doc/clojure website/_site/api/

            break;;
        [Nn]* ) break;;
        * ) echo "Please answer yes or no.";;
    esac
done
