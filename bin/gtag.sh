#!/bin/bash

echo $1
sed -i '' -e '/<head>/a\
<script async src="https://www.googletagmanager.com/gtag/js?id=G-57GD08QCML"></script>\
<script type="text/javascript" src="/api/java/script-dir/gtag.js"></script>' $1
