#!/bin/bash

./gradlew :serve:quarkusBuild -x test
java -jar serve/build/quarkus-app/quarkus-run.jar -Dquarkus.http.host=0.0.0.0 -Dquarkus.http.port=8888 -Djava.util.logging.manager=org.jboss.logmanager.LogManager --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --enable-native-access ALL-UNNAMED
