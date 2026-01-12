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
    export JAVA_HOME=`/usr/libexec/java_home -v 25`
fi

sbt clean
rm -rf doc/api/*
sbt unidoc
check_error "!!"
mv target/javaunidoc doc/api/java

sbt json/doc
check_error "!!"
find doc/api/json -name '*.html' -exec bin/gtag.sh {} \;

sbt scala/doc
check_error "!!"
find doc/api/scala -name '*.html' -exec bin/gtag.sh {} \;

./gradlew :kotlin:dokkaGenerate
check_error "!!"
find doc/api/kotlin -name '*.html' -exec bin/gtag.sh {} \;

#cd clojure
#./lein codox
#check_error "!!"
#cd ..
#find doc/api/clojure -name '*.html' -exec tidy -m {} \;
#find doc/api/clojure -name '*.html' -exec bin/gtag.sh {} \;

cd chat
npm run build
check_error "!!"

cd website
npm run deploy
check_error "!!"

# build binary package
cd ..
./gradlew :serve:build
sbt studio/Universal/packageBin
check_error "!!"

while true; do
    read -p "Do you want to publish smile? (yes/no): " ans
    case $ans in
        [Yy]* )
            sbt publishSigned
            check_error "sbt publish"

            sbt ++2.13.18 scala/publishSigned
            check_error "sbt scala/publish"
            sbt ++2.13.18 json/publishSigned
            check_error "sbt json/publish"
            # sbt ++2.13.18 spark/publishSigned
            # check_error "sbt spark/publish"
            break;;
        [Nn]* ) break;;
        * ) echo "Please answer yes or no.";;
    esac
done

while true; do
    read -p "Do you want to release to the Central Repository? (yes/no): " ans
    case $ans in
        [Yy]* )
            sbt sonaRelease
            check_error "sbt sonaRelease"
            break;;
        [Nn]* ) break;;
        * ) echo "Please answer yes or no.";;
    esac
done

while true; do
    read -p "Do you want to publish smile-clojure? (yes/no): " ans
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
