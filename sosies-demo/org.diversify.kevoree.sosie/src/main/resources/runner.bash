#!/bin/bash

# $1 path to store sosie $1 : url to the sosie

function getSosie() { # <url of the sosie> <folder to store the sosie>
	wget "$1" --content-disposition -O /tmp/sosie.zip
	tar -xvf /tmp/sosie.zip -C "$2/"
	rm -rf /tmp/sosie.zip
}

function run() { # <output folder> <folder of the sosie> <mdms port> <redis server host> <redis server port>
	cd "$1"
	rm -rf mdms
	git clone https://github.com/maxleiko/mdms-ringojs.git mdms

	ringo_home="$2/"
	mdms_home="$PWD/mdms/"

	ringo_path="${ringo_home}/bin/"

rm "${mdms_home}/config.json"
cat > "${mdms_home}/config.json" << EOF
{
    "redis-server": "$4",
    "redis-port":   $5
}
EOF

	redis-cli FLUSHDB
	${ringo_path}ringo-admin install ringo/stick
	${ringo_path}ringo ${mdms_home}tools/fakedb.js
	${ringo_path}ringo ${mdms_home}main.js -p "$3"
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
        run "$2" "$3" "$4" "$5" "$6"
    ;;
    kill)
        killProcess "$2"
    ;;
    clean)
        clean "$2"
    ;;
    *)
        echo "Usage: $0 {get <sosie url> <output folder> | run <output folder> <sosie folder> <network port> <redis server host> <redis server port> | kill <network port>| clean <sosie folder>}"
        exit 1
    ;;
esac
