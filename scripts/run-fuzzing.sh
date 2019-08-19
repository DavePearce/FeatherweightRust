#!/bin/bash

declare -a SPACES=(
    #"-verbose true -i 1 -n 2 -d 2 -w 2 -c 2 -e 4208" # 1,2,2,2,d2    
    "-verbose true -i 2 -n 2 -d 2 -w 2 -c 2 -e 11280" # 2,2,2,2,d2
    #    "-verbose true -i 1 -n 2 -d 2 -w 3 -c 2 -e 34038368 -f 0 -l 10000"
)

# check(new ProgramSpace(1, 1, 2, 2), 2, 1400);
# check(new ProgramSpace(1, 2, 2, 2), 2, 4208);
# check(new ProgramSpace(2, 2, 2, 2), 2, 11280);
# check(new ProgramSpace(1, 2, 2, 3), 2, 34038368);
# check(new ProgramSpace(1, 3, 2, 3), 2, 76524416);

CLASSPATH=target/classes:lib/jmodelgen-0.4.1.jar

# Use nice to boost process priority.
for space in "${SPACES[@]}"
do
    echo "SPACE: $space"
    java -Xmx4G -cp $CLASSPATH featherweightrust.testing.experiments.FuzzTestingExperiment $space
done
