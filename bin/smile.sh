#!/bin/bash

sbt studio/stage
cd studio/target/universal/stage/bin
./smile
cd ../../../../..
