#!/bin/bash

cd tmp_roadrunner/RoadRunner

source msetup

BUBEN_DIR=$HOME/projects/buben


# original programs

# batik
rrrun -classpath=$BUBEN_DIR/benchmarks/batik/batik-all.jar:$UBEN_DIR/benchmarks/batik/crimson-1.1.3.jar:$UBEN_DIR/benchmarks/batik/xalan-2.6.0.jar:$BUBEN_DIR/benchmarks/batik/xerces_2_5_0.jar:$BUBEN_DIR/benchmarks/batik/xml-apis.jar:$BUBEN_DIR/benchmarks/batik/xml-apis-ext.jar -tool=FT2 org.apache.batik.apps.rasterizer.Main -d output -scriptSecurityOff $BUBEN_DIR/benchmarks/batik/dat/svg2.svg

# jspider
rrrun -classpath=$BUBEN_DIR/benchmarks/jspider/commons-logging.jar:$BUBEN_DIR/benchmarks/jspider/javax.jms.jar:$BUBEN_DIR/benchmarks/jspider/javax.mail.jar:$BUBEN_DIR/benchmarks/jspider/jspider.jar:$BUBEN_DIR/benchmarks/jspider/junit.jar:$BUBEN_DIR/benchmarks/jspider/log4j-1.2.8.jar:$BUBEN_DIR/benchmarks/jspider/unittests.jar:$BUBEN_DIR/benchmarks/jspider/velocity-dep-1.3.jar -tool=FT2 net.javacoding.jspider.JSpiderTool download http://d3s.mff.cuni.cz/~parizek index.html

# lusearch
mkdir $BUBEN_DIR/benchmarks/lusearch/rrclasses
javac -cp $BUBEN_DIR/benchmarks/lusearch/lucene-core-2.4.jar:$BUBEN_DIR/benchmarks/lusearch/lucene-demos-2.4.jar -sourcepath $BUBEN_DIR/benchmarks/lusearch/src -d $BUBEN_DIR/benchmarks/lusearch/rrclasses org/dacapo/lusearch/Search.java
rrrun -classpath=$BUBEN_DIR/benchmarks/lusearch/lucene-core-2.4.jar:$BUBEN_DIR/benchmarks/lusearch/lucene-demos-2.4.jar -tool=FT2 org.dacapo.lusearch.Search -index $BUBEN_DIR/benchmarks/lusearch/dat/index-default -queries $BUBEN_DIR/benchmarks/lusearch/dat/query -output lusearch.out -totalqueries 2 -threads 2

# pmd
rrrun -classpath=$BUBEN_DIR/benchmarks/pmd/asm-3.1.jar:$BUBEN_DIR/benchmarks/pmd/jaxen-1.1.1.jar:$BUBEN_DIR/benchmarks/pmd/junit-3.8.1.jar:$BUBEN_DIR/benchmarks/pmd/pmd-4.2.5.jar:$BUBEN_DIR/benchmarks/pmd/xercesImpl.jar:$BUBEN_DIR/benchmarks/pmd/xml-apis.jar -tool=FT2 net.sourceforge.pmd.PMD $BUBEN_DIR/benchmarks/pmd/dat/net/sourceforge/pmd/ast/Token.java text $BUBEN_DIR/benchmarks/pmd/dat/rulesets/basic.xml $BUBEN_DIR/benchmarks/pmd/dat/rulesets/braces.xml $BUBEN_DIR/benchmarks/pmd/dat/rulesets/imports.xml -debug

# specjbb
rrrun -classpath=$BUBEN_DIR/benchmarks/specjbb/jbb.jar -tool=FT2 spec.jbb.JBBmain -propfile $BUBEN_DIR/benchmarks/specjbb/dat/SPECjbb.props

# sunflow
rrrun -classpath=$BUBEN_DIR/benchmarks/sunflow/janino-2.5.15.jar:$BUBEN_DIR/benchmarks/sunflow/sunflow-0.07.2.jar -tool=FT2 org.sunflow.Benchmark -bench 2 8


# abstracted programs

# batik
rrrun -classpath=$BUBEN_DIR/benchmarks/batik/classes:$BUBEN_DIR/externals/jpf-core/build/jpf.jar -tool=FT2 org.apache.batik.apps.rasterizer.Main -d output -scriptSecurityOff $BUBEN_DIR/benchmarks/batik/dat/svg2.svg

# jspider
rrrun -classpath=$BUBEN_DIR/benchmarks/jspider/classes:$BUBEN_DIR/externals/jpf-core/build/jpf.jar -tool=FT2 net.javacoding.jspider.JSpiderTool download http://d3s.mff.cuni.cz/~parizek index.html

# lusearch
rrrun -classpath=$BUBEN_DIR/benchmarks/lusearch/classes:$BUBEN_DIR/externals/jpf-core/build/jpf.jar -tool=FT2 org.dacapo.lusearch.Search -index $BUBEN_DIR/benchmarks/lusearch/dat/index-default -queries $BUBEN_DIR/benchmarks/lusearch/dat/query -output lusearch.out -totalqueries 2 -threads 2

# pmd
rrrun -classpath=$BUBEN_DIR/benchmarks/pmd/classes:$BUBEN_DIR/externals/jpf-core/build/jpf.jar -tool=FT2 net.sourceforge.pmd.PMD $BUBEN_DIR/benchmarks/pmd/dat/net/sourceforge/pmd/ast/Token.java text $BUBEN_DIR/benchmarks/pmd/dat/rulesets/basic.xml $BUBEN_DIR/benchmarks/pmd/dat/rulesets/braces.xml $BUBEN_DIR/benchmarks/pmd/dat/rulesets/imports.xml -debug

# specjbb
rrrun -classpath=$BUBEN_DIR/benchmarks/specjbb/classes:$BUBEN_DIR/externals/jpf-core/build/jpf.jar -tool=FT2 spec.jbb.JBBmain -propfile $BUBEN_DIR/benchmarks/specjbb/dat/SPECjbb.props

# sunflow
rrrun -classpath=$BUBEN_DIR/benchmarks/sunflow/classes:$BUBEN_DIR/externals/jpf-core/build/jpf.jar -tool=FT2 org.sunflow.Benchmark -bench 2 8

