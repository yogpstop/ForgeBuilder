#!/bin/bash
set -eu
diff -rNdu --strip-trailing-cr --no-ignore-file-name-case *-"$1/java" "$1/java" |
	grep -Pv "^[^@\-\+ ]" |
	perl -pe "s~^---[ \t\f]+.*-$1/java(.+?)\s+\d{4}-\d\d-\d\d\s+\d\d:\d\d:\d\d\.\d+\s+(-|\+)\d{4}[ \t\f]*$~--- a\1~g" |
	perl -pe "s~^\+\+\+[ \t\f]+$1/java(.+?)\s+\d{4}-\d\d-\d\d\s+\d\d:\d\d:\d\d\.\d+\s+(-|\+)\d{4}[ \t\f]*$~+++ b\1~g" >"patch/$1.patch.new"
