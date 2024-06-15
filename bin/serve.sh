#!/bin/bash

cd chat
npm run build
cd ../
sbt serve/stage
serve/target/universal/stage/bin/smile-serve --model Llama-3-8B-Instruct --tokenizer Llama-3-8B-Instruct/tokenizer.model
