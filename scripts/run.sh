#!/bin/sh

files=$(ls *.rs)

for f in $files
do
    echo -n "$f"
    rustc $f 2> /dev/null
    rc=$?
    if [[ $rc != 0 ]];
    then
	echo ""
    else
	echo " ... [OK]";
    fi
done
    
