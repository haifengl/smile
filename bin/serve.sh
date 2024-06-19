#!/bin/bash

cd chat
npm run build
cd ../
sbt serve/run
