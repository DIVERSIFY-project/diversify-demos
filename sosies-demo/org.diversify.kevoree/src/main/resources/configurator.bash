#!/bin/bash

git clone https://github.com/maxleiko/mdms-ringojs.git mdms
wget  "http://ringojs.org/downloads/ringojs-0.10.tar.gz" --content-disposition -O ringojs.tgz

tar -xvpzf ringojs.tgz

mv ringojs-0.10 ringo

ringo_home="./ringo/"
mdms_home="./mdms/"

# TODO get location of sosie jar file
sosie="./js2.jar"

rm -rf ${ringo_home}/lib/ivy/rhino-1.7R5-SNAPSHOT.jar
cp  ${sosie} ${ringo_home}/lib/ivy/rhino-1.7R5-SNAPSHOT.jar

