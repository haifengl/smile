#!/bin/bash

check_error() {
  if [ $? -ne 0 ]; then
    echo "$1 return code was not zero but $retval"
    exit
  fi
}

if [[ "$OSTYPE" == "darwin"* ]]; then
    export JAVA_HOME=`/usr/libexec/java_home -v 1.8`
fi

sbt clean
rm -rf doc/api/*
sbt unidoc
mv target/javaunidoc doc/api/java

cd kotlin
gradle dokkaHtml

cd ../clojure
lein codox

cd ../web
npx @11ty/eleventy

cd ..
sbt universal:packageBin
check_error "sbt universal:packageBin"

while true; do
    read -p "Do you want to publish it? " ans
    case $ans in
        [Yy]* )
            sbt publishSigned
            check_error "sbt publishSigned"

            git checkout scala-2.12
            check_error "git checkout scala-2.12"
            git pull
            check_error "git pull"
            git merge master
            check_error "git merge master"
            sbt ++2.12.11 scala/publishSigned
            check_error "sbt ++2.12.11 scala/publishSigned"
            sbt ++2.12.11 json/publishSigned
            check_error "sbt ++2.12.11 json/publishSigned"
            sbt ++2.12.11 spark/publishSigned
            check_error "sbt ++2.12.11 spark/publishSigned"

            git checkout scala-2.11
            check_error "git checkout scala-2.11"
            git pull
            check_error "git pull"
            git merge master
            check_error "git merge master"
            sbt ++2.11.12 scala/publishSigned
            check_error "sbt ++2.11.12 scala/publishSigned"
            sbt ++2.11.12 json/publishSigned
            check_error "sbt ++2.11.12 json/publishSigned"
            sbt ++2.11.12 spark/publishSigned
            check_error "sbt ++2.11.12 spark/publishSigned"

            git checkout master
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
            gradle publishMavenJavaPublicationToMavenRepository
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
            lein deploy clojars
            check_error "lein deploy clojars"
            cd ..
            break;;
        [Nn]* ) break;;
        * ) echo "Please answer yes or no.";;
    esac
done
