@echo off
call sbt studio/Universal/stage || exit /b %errorlevel%
target/out/jvm/u/smile-studio/universal/stage/bin/smile
