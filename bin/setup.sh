#!/bin/bash

# Install OpenBLAS and ARPACK
sudo apt update
sudo apt install -y libopenblas-dev libarpack2

# Install Quarkus
curl -Ls https://sh.jbang.dev | bash -s - trust add https://repo1.maven.org/maven2/io/quarkus/quarkus-cli/
curl -Ls https://sh.jbang.dev | bash -s - app install --fresh --force quarkus@quarkusio

cd chat
npm install

cd ../web
npm install
