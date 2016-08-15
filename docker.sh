#!/bin/bash

sbt docker:publishLocal
docker images -q --filter "dangling=true" | xargs docker rmi
docker run -it --rm haifengl/smile
