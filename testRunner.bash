#!/bin/bash

function runTest() {
	diversifyFolder="$1"
	
	cd /tmp
	rm -rf testsDiversify 
	mkdir testsDiversify
	cd testsDiversify
	
	git clone https://github.com/maxleiko/mdms-ringojs.git mdms
	
	ringo_home="./ringo/"
	mdms_home="./mdms/"

	ringo_path="${ringo_home}/bin/"
	
	rm ${diversifyFolder}/test_results
	touch ${diversifyFolder}/test_results
	
	for sosie in `ls ${diversifyFolder}` ; do
		cp -R ${diversifyFolder}/${sosie} "./ringo"
		
		#${ringo_path}ringo-admin install emilis/ctlr-sqlite
		#cp ${ringo_home}packages/ctlr-sqlite/jars/* ${ringo_home}lib/
		#${ringo_path}ringo-admin install ringo/stick
		#${ringo_path}ringo ${mdms_home}tools/initdb.js
		
		redis-cli FLUSHDB
		${ringo_path}ringo ${mdms_home}tools/fakedb.js
		${ringo_path}ringo ${mdms_home}main.js 2>&1  1>/dev/null &
		
		cd ${mdms_home}
		mvn test >  ../${sosie}_testResults
		cd ..
		
		result=` cat ${sosie}_testResults | grep "BUILD SUCCESS"`
		if [ "${result}" == "" ] ; then
			echo "${sosie}" >> ${diversifyFolder}/test_results		
		fi
		
		pid=`ps axu | grep ringo | grep java | cut -d' ' -f3`
		
		if [ "${pid}" != "" ] ; then
			kill -9 ${pid} 2>&1  1>/dev/null
		elif [ "${pid}" == "" ] ; then
			pid=`ps axu | grep ringo | grep java | cut -d' ' -f2`
			kill -9 ${pid} 2>&1  1>/dev/null
		fi
		
		rm -rf ${ringo_home}
		#rm -rf ./mdms.db
		
	done
}

#listConfigurations
#composeSosies
#deployOnMaven
#buildRegularWithRhinoSosies
#deployRegularOnMaven
runTest "$1"
