#!/bin/sh

CLASSPATH=target/classes:lib/jmodelgen-0.4.1.jar

# Use nice to boost process priority.
java -Xmx4G -cp $CLASSPATH featherweightrust.testing.experiments.FuzzTestingExperiment
