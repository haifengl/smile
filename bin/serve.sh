#!/bin/bash

cd chat
npm install
npm run build
cd ../
sbt serve/stage
serve/target/universal/stage/bin/smile-serve -J-Dakka.http.server.interface=0.0.0.0 --model model/Llama-3-8B-Instruct --tokenizer model/Llama-3-8B-Instruct/tokenizer.model
