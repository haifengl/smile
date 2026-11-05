#!/bin/bash

set -euo pipefail
./gradlew :serve:build -x test -Dquarkus.profile=local
sbt studio/Universal/stage
target/out/jvm/u/smile-studio/universal/stage/bin/smile
