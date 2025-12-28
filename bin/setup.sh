#!/bin/bash

sudo apt update
sudo apt install -y libopenblas-dev libarpack2

cd chat
npm install

cd ../web
npm install

