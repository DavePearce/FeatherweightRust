#!/bin/sh

CLASSPATH=target/classes:lib/jmodelgen-0.4.1.jar

java -Xmx16G -cp $CLASSPATH featherweightrust.testing.experiments.ModelCheckingExperiment
