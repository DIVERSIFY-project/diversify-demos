#!/bin/bash

function listConfigurations() { # <ringo sosies folder> <rhino sosies folder>
	rm configurations
	rm mvnconfigurations
	touch configurations
	touch mvnconfigurations
	for ringo in `ls -d $1/*` ; do
		ringo_basename=`basename $ringo`

		for rhino in `ls -d $2/*` ; do
			rhino_basename=`basename $rhino`
			echo "mvn:org.diversify:composed-sosie:1-${ringo_basename}${rhino_basename}" >> mvnconfigurations
			echo "http://sd-35000.dedibox.fr:8080/archiva/repository/internal/org/diversify/composed-sosie/1-${ringo_basename}${rhino_basename}/composed-sosie-1-${ringo_basename}${rhino_basename}.zip" >> configurations
		done
	done
}

function buildRegularWithRhinoSosies() { # <rhino sosies folder> <output folder>
	workingDirectory="$2"
	mkdir -p "${workingDirectory}"
	cd "${workingDirectory}"
	
	wget "http://ringojs.org/downloads/ringojs-0.10.tar.gz" --content-disposition -O ${workingDirectory}/ringojs.tgz
	tar -xvpzf ringojs.tgz
	mv ringojs-0.10 ringo
	
	ringo="${workingDirectory}/ringo"
	
	for rhino in `ls -d $1/*` ; do
		rhino_basename=`basename $rhino`
		folder="${workingDirectory}REGULAR${rhino_basename}"
		mkdir ${folder}
		cp -R ${ringo}/* $folder
		sosie=`ls ${rhino}/*.jar`
		rm -rf ${folder}/lib/ivy/rhino-1.7R5-SNAPSHOT.jar
		cp  ${sosie} ${folder}/lib/ivy/rhino-1.7R5-SNAPSHOT.jar
	done
	
	rm -rf ringo
	rm -rf ringojs.tgz
	
}

function composeSosies() { # <ringo sosies folder> <rhino sosies folder> <output folder>
	cd /tmp
	workingDirectory="$3"
	mkdir ${workingDirectory}
	for ringo in `ls -d $1/*` ; do
		ringo_basename=`basename $ringo`
	
		for rhino in `ls -d $2/*` ; do
			rhino_basename=`basename $rhino`
			folder="${workingDirectory}${ringo_basename}${rhino_basename}"
			mkdir ${folder}
			cp -R ${ringo}/* $folder

			sosie=`ls ${rhino}/*.jar`

			rm -rf ${folder}/lib/ivy/rhino-1.7R5-SNAPSHOT.jar
			cp  ${sosie} ${folder}/lib/ivy/rhino-1.7R5-SNAPSHOT.jar
		done
	done
}

function deployOnMaven() { # <sosies folder> <artifactId>
	cd $1
	for sosie in `ls -d $1/*` ; do
		sosie_basename=`basename $sosie`
		tar -zcvpf /tmp/sosie.tgz $sosie_basename
		sosie="/tmp/sosie.tgz"
		mvn deploy:deploy-file -Dfile=${sosie} -DgroupId=org.diversify -DartifactId=$2 -Dversion=1-${sosie_basename} -Dpackaging=zip -DrepositoryId=diversify -Durl=http://sd-35000.dedibox.fr:8080/archiva/repository/internal/
	
		rm -rf /tmp/sosie.tgz
	done
}

function deployRegularOnMaven() {
	cd /tmp
	git clone https://github.com/mozilla/rhino.git
	cd rhino/
	ant jar
	mvn deploy:deploy-file -Dfile=build/rhino1_7R5pre/js.jar -DgroupId=org.diversify -DartifactId=rhino -Dversion=1-REGULAR -Dpackaging=jar -DrepositoryId=diversify -Durl=http://sd-35000.dedibox.fr:8080/archiva/repository/internal/
	cd ..
	rm -rf rhino

	wget "http://ringojs.org/downloads/ringojs-0.10.tar.gz" --content-disposition -O /tmp/ringo.tgz
	mvn deploy:deploy-file -Dfile=/tmp/ringo.tgz -DgroupId=org.diversify -DartifactId=ringo -Dversion=1-REGULAR -Dpackaging=zip -DrepositoryId=diversify -Durl=http://sd-35000.dedibox.fr:8080/archiva/repository/internal/
}

function runTest() { # <sosies folder>
	workingDirectory=`pwd`
	resultDirectory=${workingDirectory}/results
	mkdir ${resultDirectory}
	
	diversifyFolder="$1"
	
	cd /tmp
	rm -rf tests
	mkdir tests
	cd tests
	git clone https://github.com/maxleiko/mdms-ringojs.git mdms
	
	ringo_home="$PWD/ringo/"
	mdms_home="$PWD/mdms/"

	ringo_path="${ringo_home}/bin/"
	
	rm ${workingDirectory}/test_results_ko
	touch ${workingDirectory}/test_results_ko
	
	for sosie in `ls ${diversifyFolder}/` ; do 
		cp -R ${diversifyFolder}/${sosie} "${ringo_home}"

		redis-cli FLUSHDB
		${ringo_path}ringo-admin install ringo/stick
		${ringo_path}ringo ${mdms_home}tools/fakedb.js
		(${ringo_path}ringo ${mdms_home}main.js 2>&1 | tee ${resultDirectory}/${sosie}_server.log) &
		
		cd ${mdms_home}
		mvn test >  ${resultDirectory}/${sosie}_testResults
		cd ..
		
		result=` cat ${resultDirectory}/${sosie}_testResults | grep "BUILD SUCCESS"`
		if [ "${result}" == "" ] ; then
			echo "${sosie}" >> ${workingDirectory}/test_results_ko		
		fi
		
		kill -9 `ps axu | grep "java -Xbootclasspath/p:/tmp/tests/ringo/lib/js.jar -jar /tmp/tests/ringo/run.jar /tmp/tests/mdms/main.js" | awk '{ print $2 }'`
		
		rm -rf ${ringo_home}
		redis-cli FLUSHDB
	done
}

RETVAL=0
   case "$1" in
      list)
         listConfigurations "$2" "$3" "$4"
         ;;
      composeRegular)
      	buildRegularWithRhinoSosies "$2" "$3"
      	;;
      compose)       
		echo "We will remove the ${workingDirectory}"
		echo "Are you ok ? (Ctrl - C if not)"
		read  
		rm -rf ${workingDirectory}                   
		buildRegularWithRhinoSosies "$2" "$4"
        composeSosies "$2" "$3" "$4"
         ;;
      deploy)
#         deployRegularOnMaven
         deployOnMaven "$2" "ringo"
#         deployOnMaven "$3" "rhino"
#         deployOnMaven "$4" "composed-sosie"
         ;;
      runTest)
         runTest "$2"
         ;;
      *)
         echo "Usage: $0 {list <ringo sosies folder> <rhino sosies folder> |composeRegular <rhino sosies folder> <outputFolder>|compose <ringo sosies folder> <rhino sosies folder> <output folder>|deploy <ringo sosies folder> <rhino sosies folder> <sosies folder>|runTest <sosies folder>}"
         exit 1
         ;;
      esac
   exit $RETVAL
