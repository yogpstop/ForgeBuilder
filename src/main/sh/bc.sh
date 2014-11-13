#!/bin/bash
set -eu
write() {
	echo -n "{\"groupId\":\"com.mod-buildcraft\",\"artifactId\":\"buildcraft\",\"version\":\"" >"$1"
	echo -n "$2" >>"$1"
	echo -n "\",\"forge\":[{\"name\":\"" >>"$1"
	echo -n "$3" >>"$1"
	echo -n "\",\"forgev\":\"" >>"$1"
	echo -n "$4" >>"$1"
	echo -n "\",\"src_base\":\".\"}],\"java\":[\"common\"],\"resources\":[\"buildcraft_resources\"],\"replace\":{\"@VERSION@\":\"{version}\"}}" >>"$1"
}
rm -rf BuildCraft
git clone git://github.com/BuildCraft/BuildCraft -b 3.4.2 --depth 1
write BuildCraft/build.cfg 3.4.2 1.4.7 534
java -jar $1 BuildCraft
rm -rf BuildCraft
git clone git://github.com/BuildCraft/BuildCraft -b 3.2.2 --depth 1
write BuildCraft/build.cfg 3.2.2 1.4.5 443
java -jar $1 BuildCraft
rm -rf BuildCraft
