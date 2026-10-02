#!/bin/sh

../jextract-25/bin/jextract \
  --include-dir deep/libtorch/include \
  --output deep/src/main/java \
  --target-package smile.torch \
  --library smile_torch \
  deep/src/main/cpp/smile_torch.h
