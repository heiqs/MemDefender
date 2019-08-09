#!/bin/bash
#
# Java Live Object Monitor bootstrap script
#
# $1 - a colon separated list of source code root directory paths for the application
# $2 - a colon separated list to the applications jar paths (containing the main class)
# $3 - qualified name to the main class using java package notion
# [$4 - parameters passed to the application]



LOM_HOME=../SoftwareAgingRCA
LOM_LIB=$LOM_HOME/lib
LOM_BIN=$LOM_HOME/dist/LiveObjectMonitor.jar

AGENT=$LOM_LIB/allocation.jar

ASM_LIBS=$LOM_LIB/asm-4.0.jar:$LOM_LIB/asm-analysis-4.0.jar:$LOM_LIB/asm-commons-4.0.jar:$LOM_LIB/asm-tree-4.0.jar:$LOM_LIB/asm-util-4.0.jar:$LOM_LIB/asm-xml-4.0.jar

LOM_CLASSPATH=$ASM_LIBS:$LOM_LIB/allocation.jar:$LOM_LIB/guava-r06.jar:$LOM_LIB/jarjar-1.0.jar

java $JAVA_OPTS -classpath $LOM_CLASSPATH -javaagent:$AGENT -jar $LOM_BIN $@
