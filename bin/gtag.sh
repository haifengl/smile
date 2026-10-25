#!/bin/bash

echo $1
if [[ "$OSTYPE" == "darwin"* ]]; then
  sed -i '' -e '/<head>/a\
<script async src="https://www.googletagmanager.com/gtag/js?id=G-57GD08QCML"></script>\
<script type="text/javascript" src="/api/java/script-files/gtag.js"></script>' "$1"
else
  sed -i -e '/<head>/a\
<script async src="https://www.googletagmanager.com/gtag/js?id=G-57GD08QCML"></script>\
<script type="text/javascript" src="/api/java/script-files/gtag.js"></script>' "$1"
fi
