#!/bin/bash

sbt shell/stage
cd shell/target/universal/stage/
bin/smile
cd ../../../..
