#!/bin/bash

# In a script, history expansion is turned off by default, enable it with
set -o history -o histexpand

check_error() {
  local retval=$?
  if [ $retval -ne 0 ]; then
    echo "'$1' returns code $retval"
    exit $retval
  fi
}

if [[ "$OSTYPE" == "darwin"* ]]; then
    export JAVA_HOME=`/usr/libexec/java_home -v 1.8`
fi

sbt clean
rm -rf doc/api/*
sbt unidoc
check_error "!!"
mv target/javaunidoc doc/api/java

sbt ++3.3.4 json/doc
check_error "!!"
find doc/api/json -name '*.html' -exec bin/gtag.sh {} \;

sbt ++3.3.4 scala/doc
check_error "!!"
find doc/api/scala -name '*.html' -exec bin/gtag.sh {} \;

cd kotlin
./gradlew dokkaHtml
check_error "!!"
find doc/api/kotlin -name '*.html' -exec bin/gtag.sh {} \;

# build smile-kotlin.jar and copy it to shell
rm ../shell/src/universal/bin/smile-kotlin-*.jar
./gradlew build
check_error "!!"

cd ../clojure
./lein codox
check_error "!!"
find doc/api/clojure -name '*.html' -exec tidy -m {} \;
find doc/api/clojure -name '*.html' -exec bin/gtag.sh {} \;

cd ../web
npm run deploy
check_error "!!"

# build shell's smile.zip
cd ..
sbt shell/Universal/packageBin
check_error "!!"

while true; do
    read -p "Do you want to publish it? " ans
    case $ans in
        [Yy]* )
            sbt publishSigned
            check_error "!!"

            sbt ++3.3.4 scala/publishSigned
            check_error "!!"
            sbt ++3.3.4 json/publishSigned
            check_error "!!"
            # sbt ++3.3.4 spark/publishSigned
            # check_error "!!"
            break;;
        [Nn]* ) break;;
        * ) echo "Please answer yes or no.";;
    esac
done

while true; do
    read -p "Do you want to publish smile-kotlin? " ans
    case $ans in
        [Yy]* )
            cd kotlin
            ./gradlew publishMavenJavaPublicationToMavenRepository
            check_error "gradle publish"
            cd ..
            break;;
        [Nn]* ) break;;
        * ) echo "Please answer yes or no.";;
    esac
done

while true; do
    read -p "Do you want to publish smile-clojure? " ans
    case $ans in
        [Yy]* )
            cd ../clojure
            ./lein deploy clojars
            check_error "lein deploy clojars"
            cd ..
            break;;
        [Nn]* ) break;;
        * ) echo "Please answer yes or no.";;
    esac
done
