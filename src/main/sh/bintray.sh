#!/bin/bash
#ARG1: UserName
#ARG2: RepoName
#ARG3: PackName
#ARG4: FindPath
set -eu
IFS='
'
curl -X POST --user "$1:${TOKEN}" -H 'Content-Type: application/json' -d "{\"name\":\"${CI_COMMIT_ID}\"}" "https://api.bintray.com/packages/$1/$2/$3/versions"
for FILE in `find "$4"` ; do
	curl -X PUT --user "$1:${TOKEN}" --data-binary @"${FILE}" "https://api.bintray.com/content/$1/$2/$3/${CI_COMMIT_ID}/${FILE##*/};publish=1"
done
