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

rm -rf doc/api
export CLASSPATH=$CLASSPATH:$HOME/.ivy2/cache/org.swinglabs/swingx/jars/*:$HOME/.ivy2/cache/org.slf4j/slf4j-api/jars/*:$HOME/.ivy2/cache/org.bytedeco/javacpp/jars/*:$HOME/.ivy2/cache/org.bytedeco/arpack-ng/jars/*:$HOME/.ivy2/cache/org.bytedeco/openblas/jars/*:$HOME/.ivy2/cache/org.apache.commons/commons-csv/jars/*:$HOME/.ivy2/cache/org.apache.arrow/arrow-memory/jars/*:$HOME/.ivy2/cache/org.apache.arrow/arrow-vector/jars/*:$HOME/.ivy2/cache/org.apache.hadoop/hadoop-common/jars/*:$HOME/.ivy2/cache/org.apache.avro/avro/jars/*:$HOME/.ivy2/cache/org.apache.parquet/parquet-common/jars/*:$HOME/.ivy2/cache/org.apache.parquet/parquet-column/jars/*:$HOME/.ivy2/cache/org.apache.parquet/parquet-hadoop/jars/*:$HOME/.ivy2/cache/com.epam/parso/jars/*:$HOME/.ivy2/cache/com.fasterxml.jackson.core/jackson-core/bundles/*:$HOME/.ivy2/cache/com.fasterxml.jackson.core/jackson-annotations/bundles/*:$HOME/.ivy2/cache/com.fasterxml.jackson.core/jackson-databind/bundles/*

javadoc -source "1.8" --allow-script-in-comments -bottom '<script src="{@docRoot}/../../js/google-analytics.js" type="text/javascript"></script>' -Xdoclint:none -doctitle "Smile &mdash; Statistical Machine Intelligence and Learning Engine" -d doc/api/java  -subpackages smile -sourcepath math/src/main/java:data/src/main/java:io/src/main/java:core/src/main/java:graph/src/main/java:interpolation/src/main/java:nlp/src/main/java:plot/src/main/java
check_error "javadoc"

sbt clean

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
