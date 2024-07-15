#!/bin/bash

cd chat
npm install
npm run build
cd ../
sbt serve/stage
serve/target/universal/stage/bin/smile-serve "$@"
