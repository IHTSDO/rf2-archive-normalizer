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

effectiveTime="-t 20171010"
deltaArchive="/Users/Peter/Google Drive/017_Drugs_ReModelling/2017/Disposition_Import_mkv.zip"
#relIds="/Users/Peter/Google Drive/017_Drugs_ReModelling/2017/disposition_rel_sctids.txt"
relIds="/Users/Peter/Google Drive/017_Drugs_ReModelling/2017/additional_rel_sctids.txt"
descIds="/Users/Peter/Google Drive/017_Drugs_ReModelling/2017/disposition_desc_sctids.txt"
# suppressedIds="-s 312414008,414058001"
filterIds="-f 312414008,414058001"
releaseLocation="/Users/Peter/tmp/20180131_flat "
memParams="-Xms6g -Xmx10g"

set -x;
#[-p previousRelease] [-r relationshipSCTIDs file] [-a deltaArchive] [-r relationshipId file] [-d descriptionId file]
java -jar ${memParams} ${debugParams} target/rf2-archive-normalizer.jar -p ${releaseLocation} -a "${deltaArchive}" -r "${relIds}" -d "${descIds}" ${suppressedIds} ${filterIds} ${effectiveTime} 


