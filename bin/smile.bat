@echo off
call ./gradlew :serve:build -x test "-Dquarkus.profile=local" || exit /b %errorlevel%
call sbt studio/Universal/stage || exit /b %errorlevel%
target/out/jvm/u/smile-studio/universal/stage/bin/smile
