#!/bin/bash
set -e;

while getopts ":cd" opt
do
	case $opt in
		c)
			mvn clean package
		;;
		d)
  			debugParams="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8080"
		;;
		help|\?)
			echo -e "Usage: [-c] [-d]"
			echo -e "\t c - (re)compile the tool"
			echo -e "\t d - debug mode, allows IDE to connect on debug port"
			exit 0
		;;
	esac
done

effectiveTime="-t 20170628"
deltaArchive="-d /Users/Peter/Google\ Drive/017_Drugs_ReModelling/2017/Disposition_Import_mkii.zip"
releaseLocation="-p /Users/Peter/tmp/20170731_flat"
memParams="-Xms6g -Xmx10g"
e
set -x;
#[-p previousRelease] [-r relationshipSCTIDs file] [-d deltaArchive]
java -jar ${memParams} ${debugParams} target/rf2-archive-normalizer.jar ${releaseLocation} ${deltaArchive} ${effectiveTime} 


