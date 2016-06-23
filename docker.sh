#!/bin/bash

sbt docker:publishLocal
docker run -it haifengl/smile
