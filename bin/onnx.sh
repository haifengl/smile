#!/bin/sh

jextract-25/bin/jextract \
  --include-dir onnxruntime-osx-x86_64-1.23.2/include \
  --output smile/core/src/main/java \
  --target-package smile.onnx.foreign \
  --library onnxruntime \
  onnxruntime-osx-x86_64-1.23.2/include/onnxruntime_c_api.h
