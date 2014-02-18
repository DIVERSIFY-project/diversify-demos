#!/bin/bash

# $1 path to store sosie $1 : url to the sosie

function getSosie() { # <url of the sosie> <folder to store the sosie>
	wget "$1" --content-disposition -O /tmp/sosie.zip
	tar -xvf /tmp/sosie.zip -C "$2/"
	rm -rf /tmp/sosie.zip
}

function run() { # folder of the sosie
	cd /tmp
	rm -rf mdms
	git clone https://github.com/maxleiko/mdms-ringojs.git mdms

	ringo_home="$1/"
	mdms_home="$PWD/mdms/"

	ringo_path="${ringo_home}/bin/"

	redis-cli FLUSHDB
	${ringo_path}ringo-admin install ringo/stick
	${ringo_path}ringo ${mdms_home}tools/fakedb.js
	${ringo_path}ringo ${mdms_home}main.js -p "$2"
}

function killProcess() { # <network port>
    kill -9 `ps axu | grep "java -Xbootclasspath/p:" | grep "run.jar" | grep "mdms/main.js" | grep " -p $1"| awk '{ print $2 }'`
}

function clean() { # sosie folder
    rm -rf "$1"
    rm -rf "/tmp/mdms"
}

case "$1" in
    get)
        getSosie "$2" "$3"
    ;;
    run)
        run "$2" "$3"
    ;;
    kill)
        killProcess "$2"
    ;;
    clean)
        clean "$2"
    ;;
    *)
        echo "Usage: $0 {get <sosie url> <output folder> | run <sosie folder> <network port> | kill <network port>| clean <sosie folder>}"
        exit 1
    ;;
esac
