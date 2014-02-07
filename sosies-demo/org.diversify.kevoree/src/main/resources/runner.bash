#!/bin/bash

ringo_home="./ringo/"
mdms_home="./mdms/"

ringo_path="${ringo_home}/bin/"

rm -rf ./mdms.db

${ringo_path}ringo-admin install emilis/ctlr-sqlite
cp ${ringo_home}packages/ctlr-sqlite/jars/* ${ringo_home}lib/
# then you need to move the jar from packages/ctlr-sqlite/jars to lib/
# (in $RINGO_HOME, default is /usr/share/ringojs/)
${ringo_path}ringo-admin install ringo/stick
${ringo_path}ringo ${mdms_home}tools/initdb.js
${ringo_path}ringo ${mdms_home}tools/fakedb.js
${ringo_path}ringo ${mdms_home}main.js $1
