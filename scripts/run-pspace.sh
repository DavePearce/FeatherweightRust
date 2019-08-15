#!/bin/sh

CLASSPATH=target/classes:lib/jmodelgen-0.4.1.jar

java -Xmx4G -cp $CLASSPATH featherweightrust.core.ProgramSpace
