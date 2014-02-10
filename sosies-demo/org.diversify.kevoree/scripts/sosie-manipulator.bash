#!/bin/bash

function getSosies() { # <file which list the sosies to get (urls)> <folder to store sosies>
	for url in `cat "$1"` ; do
		getSosie "$url" "$2"
	done
}

function getSosie() { # <url of the sosies> <folder to store sosies>
	wget "$1" --content-disposition -O /tmp/sosie.zip
	tar -xvf /tmp/sosie.zip -C "$2/"
	rm -rf /tmp/sosie.zip
}

#function runAll() { # folder of the sosies
#	for sosie in `ls -d "$1"/*` ; do
#		run "$sosie"
#	done
#}

function run() { # folder of the sosie
	cd /tmp
	rm -rf tests
	mkdir tests
	cd tests
	git clone https://github.com/maxleiko/mdms-ringojs.git mdms
	
	ringo_home="$1/"
	mdms_home="$PWD/mdms/"

	ringo_path="${ringo_home}/bin/"
	
	redis-cli FLUSHDB
	${ringo_path}ringo-admin install ringo/stick
	${ringo_path}ringo ${mdms_home}tools/fakedb.js
	${ringo_path}ringo ${mdms_home}main.js
}


RETVAL=0
   case "$1" in
   	  get)
   	  	getSosie "$2" "$3"
   	  	;;
      getAll)
         getSosies "$2" "$3"
         ;;
      run)
      	run "$2"
      	;;
#      runAll)
#      	runAll "$2"
#      	;;
	  *)
         echo "Usage: $0 {get <sosie url> <output folder> | getAll <file listing sosies urls> <output folder> |run <sosie folder>}"
         exit 1
         ;;
      esac
   exit $RETVAL
