#!/bin/bash

set -euo pipefail
sbt studio/Universal/stage
target/out/jvm/u/smile-studio/universal/stage/bin/smile
