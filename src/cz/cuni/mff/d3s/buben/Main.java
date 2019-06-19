/*
 * Copyright (C) 2019, Charles University.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cz.cuni.mff.d3s.buben;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Date;

import java.io.FileReader;
import java.io.BufferedReader;

import cz.cuni.mff.d3s.buben.analysis.SymbolicFieldArrayWriteAnalysis;
import cz.cuni.mff.d3s.buben.analysis.SymbolicLocalVarWriteAnalysis;
import cz.cuni.mff.d3s.buben.analysis.SymbolicInvokeArgumentsCollector;
import cz.cuni.mff.d3s.buben.analysis.SymbolicReturnValuesCollector;
import cz.cuni.mff.d3s.buben.analysis.SymbolicAccessPathAliasAnalysis;
import cz.cuni.mff.d3s.buben.analysis.SynchronizedAccessAnalysis;
import cz.cuni.mff.d3s.buben.analysis.SymbolicMethodLocationsCollector;
import cz.cuni.mff.d3s.buben.analysis.SymbolicNewObjectsCollector;
import cz.cuni.mff.d3s.buben.analysis.SymbolicNewArraysCollector;
import cz.cuni.mff.d3s.buben.summaries.SideEffectSummaryGenAnalysis;
import cz.cuni.mff.d3s.buben.dynamic.DynamicInputOutputCollector;
import cz.cuni.mff.d3s.buben.transform.LibraryMethodAbstractionGenerator;
import cz.cuni.mff.d3s.buben.transform.NativeExternAbstractionGenerator;
import cz.cuni.mff.d3s.buben.transform.ObjectTypesData;
import cz.cuni.mff.d3s.buben.wala.WALAUtils;
import cz.cuni.mff.d3s.buben.jdi.JDIUtils;
import cz.cuni.mff.d3s.buben.jpf.NativePeerGenerator;


public class Main
{
	public static void main(String[] args)
	{
		String cfgFileName = args[0];
		
		String toolNativesToKeepFileName = null;
		if (args.length > 1) toolNativesToKeepFileName = args[1];
		
		String toolNativesOnlyFileName = null;
		if (args.length > 2) toolNativesOnlyFileName = args[2];
	
		String toolNativesUnsuppFileName = null;
		if (args.length > 3) toolNativesUnsuppFileName = args[3];
	
		String toolProfileDir = null;
		if (args.length > 4) toolProfileDir = args[4];

		try
		{
			loadConfiguration(cfgFileName);
		}
		catch (Exception ex)
		{
			System.err.println("[ERROR] loading configuration failed");
			ex.printStackTrace();
			return;
		}


		StaticAnalysisContext staCtx = null;
		
		// signatures of all native methods (i.e., not just those directly called from application classes)
		Set<String> nativeMethodsAll = new HashSet<String>();

		// prefixes of full names of native methods whose calls should be preserved to enable proper functioning of the verification tool
		Set<String> toolNativeMethodsToKeepPrefixes = new HashSet<String>();
		
		// step 1: preparation
		
		if (Configuration.DEBUG)
		{
			System.out.println("preparation started");
		}
	
		Date startPreparation = new Date();
				
		try
		{
			// init the static analysis library (WALA)
			staCtx = WALAUtils.initLibrary();
		
			// build call graph and compute pointer analysis to identify heap objects
			WALAUtils.buildCallGraphPointers(staCtx);
		
			// identify all native methods reachable in the call graph
			Set<String> nativeMethodsCG = WALAUtils.collectNativeMethods(staCtx);

			nativeMethodsAll.addAll(nativeMethodsCG);

			nativeMethodsAll.addAll(Utils.loadTextFileAsStringPerLine(toolNativesOnlyFileName));

			// load prefixes of native methods from a user-defined file
			toolNativeMethodsToKeepPrefixes.addAll(Utils.loadTextFileAsStringPerLine(toolNativesToKeepFileName));
		}
		catch (Exception ex)
		{
			System.err.println("[ERROR] preparation failed");
			ex.printStackTrace();
			return;
		}

		Date finishPreparation = new Date();
		
		System.out.println("[INFO] preparation: time = " + printTimeDiff(startPreparation, finishPreparation) + " s");
		System.out.println("");
		
		// step 2: run dynamic analysis to collect data about native methods and library methods that communicate with external entities
			// we must perform the dynamic analysis before the static analysis to process all the native methods
	
		if (Configuration.DEBUG)
		{
			System.out.println("dynamic recording started");
		}
		
		Date startRecording = new Date();
		
		try
		{
			recordInputOutput(staCtx, nativeMethodsAll);
		}
		catch (Exception ex)
		{
			System.err.println("[ERROR] dynamic recording failed");
			ex.printStackTrace();
			return;
		}
		
		Date finishRecording = new Date();
		
		System.out.println("[INFO] recording: time = " + printTimeDiff(startRecording, finishRecording) + " s");
		System.out.println("");
		
		// step 3: compute method summaries for pure Java methods using static analysis

		if (Configuration.DEBUG)
		{
			System.out.println("static analysis started");
		}
		
		Date startSummaries = new Date();
		
		try
		{
			computeMethodSummaries(staCtx);
		}
		catch (Exception ex)
		{
			System.err.println("[ERROR] static analysis failed");
			ex.printStackTrace();
			return;
		}
		
		Date finishSummaries = new Date();
		
		long memorySummaries = (Runtime.getRuntime().totalMemory() >> 20);
  		
		System.out.println("[INFO] summaries: time = " + printTimeDiff(startSummaries, finishSummaries) + " s, memory = " + memorySummaries + " MB");
		System.out.println("");
		
		// step 4: transform input program code (generate abstraction for library methods)
		
		if (Configuration.DEBUG)
		{
			System.out.println("program transformation started");
		}
		
		Date startTransformation = new Date();
		
		try
		{
			Set<String> nativeMethodsToReplace = new HashSet<String>(nativeMethodsAll);

			// remove native methods that are important for the verification tool and therefore should not be replaced during transformations

			for (Iterator<String> mthIt = nativeMethodsToReplace.iterator(); mthIt.hasNext(); )
			{
				String natMthSig = mthIt.next();

				boolean prefixFound = false;

				for (String mthPrefix : toolNativeMethodsToKeepPrefixes)
				{
					if (natMthSig.startsWith(mthPrefix))
					{
						prefixFound = true;
						break;
					}
				}

				if (prefixFound) mthIt.remove();
			}

			transformInputProgram(staCtx, nativeMethodsToReplace);

			if (toolNativesUnsuppFileName != null)
			{
				createNativePeersForJPF(toolNativesUnsuppFileName, toolProfileDir);
			}
		}
		catch (Exception ex)
		{
			System.err.println("[ERROR] program transformation failed");
			ex.printStackTrace();
			return;
		}
		
		Date finishTransformation = new Date();
		
		long memoryTransformation = (Runtime.getRuntime().totalMemory() >> 20);
  		
		System.out.println("[INFO] transformation: time = " + printTimeDiff(startTransformation, finishTransformation) + " s, memory = " + memoryTransformation + " MB");
		System.out.println("");
	}
	
	private static void loadConfiguration(String cfgFileName) throws Exception
	{
		List<String> cfgFileLines = Utils.loadTextFileAsStringPerLine(cfgFileName);
		
		// empty data structures to be filled later
		Configuration.runtimeCmdArgs = new ArrayList<String>();
		Configuration.libraryMethodPrefixes = new ArrayList<String>();
		Configuration.applicationClassPrefixes = new ArrayList<String>();
		Configuration.extaccessMethodPrefixes = new ArrayList<String>();
		Configuration.extaccessClassPrefixes = new ArrayList<String>();
		Configuration.testClassNames = new ArrayList<String>();
		Configuration.driverClassNames = new ArrayList<String>();

		// default can be overriden
		Configuration.maxReturnValues = Configuration.DEFAULT_MAX_RETURN_VALUES;
				
		// process individual configuration entries (variables)
		
		for (int i = 0; i < cfgFileLines.size(); i++)
		{
			String cfgLine = cfgFileLines.get(i);
			
			if (cfgLine.startsWith("mainclass="))
			{
				Configuration.targetMainClassName = cfgLine.substring(10);
			}
			
			if (cfgLine.startsWith("classpath="))
			{
				Configuration.targetClassPath = cfgLine.substring(10);
			}
		
			if (cfgLine.startsWith("runtimeargs="))
			{
				// extract strings that represent command-line arguments of the "main" method and put them into the list
				String[] cmdArgsArray = cfgLine.substring(12).split(",");
				for (String ca : cmdArgsArray) Configuration.runtimeCmdArgs.add(ca);
			}
		
			if (cfgLine.startsWith("walaexclfile="))
			{
				Configuration.walaExclusionFilePath = cfgLine.substring(13);
			}
			
			if (cfgLine.startsWith("libmethods="))
			{
				// extract strings that represent prefixes of library method signatures and put them into the list
				String[] libPrefixArray = cfgLine.substring(11).split(",");
				for (String lpf : libPrefixArray) Configuration.libraryMethodPrefixes.add(lpf);
			}
			
			if (cfgLine.startsWith("appclasses="))
			{
				// extract strings that represent prefixes of application class names and put them into the list
				String[] appPrefixArray = cfgLine.substring(11).split(",");
				for (String apf : appPrefixArray) Configuration.applicationClassPrefixes.add(apf);
			}
			
			if (cfgLine.startsWith("externmethods="))
			{
				// extract strings that represent prefixes of methods accessing external entities
				String[] extMthPrefixArray = cfgLine.substring(14).split(",");
				for (String epf : extMthPrefixArray) Configuration.extaccessMethodPrefixes.add(epf);
			}
			
			if (cfgLine.startsWith("externclasses="))
			{
				// extract strings that represent prefixes of classes whose all methods access external entities
				String[] extClsPrefixArray = cfgLine.substring(14).split(",");
				for (String epf : extClsPrefixArray) Configuration.extaccessClassPrefixes.add(epf);
			}
			
			if (cfgLine.startsWith("testclasses="))
			{
				// extract strings that represent names of unit test classes
				String[] testNameArray = cfgLine.substring(12).split(",");
				for (String tn : testNameArray) Configuration.testClassNames.add(tn);
			}
			
			if (cfgLine.startsWith("driverclasses="))
			{
				// extract strings that represent names of driver classes
				String[] driverNameArray = cfgLine.substring(14).split(",");
				for (String dn : driverNameArray) Configuration.driverClassNames.add(dn);
			}

			if (cfgLine.startsWith("maxreturnvals="))
			{
				// extract custom bound for the number of possible return values from a library method
				Configuration.maxReturnValues = Integer.parseInt(cfgLine.substring(14));
			}
		}
	}
	
	
	private static void recordInputOutput(StaticAnalysisContext staCtx, Set<String> nativeMethodsAll) throws Exception
	{
		// identify native methods that must be processed using the dynamic analysis
		// we consider just methods directly invoked from within application classes
		Set<String> nativeMethodsDirect = WALAUtils.collectNativeMethodsWithCallers(staCtx, Configuration.applicationClassPrefixes);

		// identify methods that access (communicate with) external entities
		// we consider just methods directly invoked from within application classes
		Set<String> extaccessMethodsDirect = WALAUtils.collectMethodsWithPrefixesCallers(staCtx, Configuration.extaccessMethodPrefixes, Configuration.applicationClassPrefixes);
		
		// the set of all methods (signatures) that we should intercept
		// eventually contains just methods directly invoked from application classes
		Set<String> interceptionTargetMethods = new TreeSet<String>();
		
		// add native methods (because static analysis cannot handle them)
		interceptionTargetMethods.addAll(nativeMethodsDirect);
		
		// add methods that access external entities (many of them are native too)
		interceptionTargetMethods.addAll(extaccessMethodsDirect);

		// add methods that are abstracted in XML by the WALA library
		interceptionTargetMethods.addAll(WALAUtils.abstractedMethodsXML);
		
		interceptionTargetMethods.addAll(DynamicInputOutputCollector.getInternallyTrackedMethods());

		// drop every method that does not have call parameters and return value
		for (Iterator<String> mthIter = interceptionTargetMethods.iterator(); mthIter.hasNext(); )
		{
			String mthSig = mthIter.next();

			if (Utils.isMethodWithoutCallArgumentsReturnValue(mthSig)) mthIter.remove();
		}

		if (Configuration.DEBUG)
		{
			System.err.println("INTERCEPTION TARGETS");
			System.err.println("====================");
			
			for (String mthSig : interceptionTargetMethods)
			{
				System.err.println(mthSig);
			}
			
			System.err.println("");
		}
		
		if (Configuration.DEBUG)
		{
			System.err.println("NATIVE METHODS");
			System.err.println("==============");
			
			for (String mthSig : nativeMethodsAll)
			{
				System.err.println(mthSig);
			}
			
			System.err.println("");
		}

		SymbolicMethodLocationsCollector.analyzeProgram(staCtx);
		
		if (Configuration.DEBUG)
		{
			SymbolicMethodLocationsCollector.printInvokeLocations();
			SymbolicMethodLocationsCollector.printReturnLocations();
		}
		
		// execute all driver classes with "main"
		for (String driverClsName : Configuration.driverClassNames)
		{
			// each driver class has the "main" procedure and therefore it can be executed directly
			
			if (Configuration.DEBUG)
			{
				System.out.println("current driver class: " + driverClsName);
			}

			// prepare command-line arguments for the executable
			List<String> processCmdArgs = new ArrayList<String>();
			processCmdArgs.add("java");
			processCmdArgs.add(JDIUtils.getAgentConfig());
			processCmdArgs.add("-cp");
			processCmdArgs.add(Configuration.targetClassPath);
			processCmdArgs.add(driverClsName);
			processCmdArgs.addAll(Configuration.runtimeCmdArgs);

			// perform dynamic analysis (JDI) on the existing test program (driver class) to record information about every method in the given list (native methods and library methods that access external entities)

			// record inputs (array content) and updates of heap object fields

			if (Configuration.DEBUG)
			{
				System.err.println("recording inputs and field updates");
			}

			Process proc1 = startExternalProcess(processCmdArgs);

			DynamicInputOutputCollector.collectInputsFieldUpdatesForMethods(proc1, driverClsName, interceptionTargetMethods, nativeMethodsAll);
			
			stopExternalProcess(proc1);

			// record outputs (return value) and updates of array elements
	
			if (Configuration.DEBUG)
			{
				System.err.println("recording outputs and array updates");
			}

			Process proc2 = startExternalProcess(processCmdArgs);
			
			DynamicInputOutputCollector.collectOutputsArrayUpdatesForMethods(proc2, driverClsName, interceptionTargetMethods, nativeMethodsAll);
			
			stopExternalProcess(proc2);
		}

		// execute all unit tests and monitor them by JDI
			// do this in the same way as for driver classes
		for (String testClsName : Configuration.testClassNames)
		{
			// each test class is a JUnit test (it extends particular superclass or has properly annotated methods)
			
			if (Configuration.DEBUG)
			{
				System.out.println("current test class: " + testClsName);
			}
			
			// run the given test using the JUnit API

			// prepare command-line arguments for the executable
			List<String> processCmdArgs = new ArrayList<String>();
			processCmdArgs.add("java");
			processCmdArgs.add(JDIUtils.getAgentConfig());
			processCmdArgs.add("-cp");
			processCmdArgs.add(Configuration.targetClassPath);
			processCmdArgs.add("org.junit.runner.JUnitCore");
			processCmdArgs.add(testClsName);
			processCmdArgs.addAll(Configuration.runtimeCmdArgs);

			// record inputs (array content) and updates of heap object fields

			Process proc1 = startExternalProcess(processCmdArgs);

			DynamicInputOutputCollector.collectInputsFieldUpdatesForMethods(proc1, testClsName, interceptionTargetMethods, nativeMethodsAll);
			
			stopExternalProcess(proc1);

			// record outputs (return value) and updates of array elements

			Process proc2 = startExternalProcess(processCmdArgs);
			
			DynamicInputOutputCollector.collectOutputsArrayUpdatesForMethods(proc2, testClsName, interceptionTargetMethods, nativeMethodsAll);
			
			stopExternalProcess(proc2);
		}
		
		if (Configuration.DEBUG)
		{
			// print recorded information (return values (results) and side effects (updated fields, updated arrays)) for each method
			DynamicInputOutputCollector.printMethodCallResults();
			DynamicInputOutputCollector.printMethodSideEffectsFields();
			DynamicInputOutputCollector.printMethodSideEffectsArrays();
		}
	}
	
	private static void computeMethodSummaries(StaticAnalysisContext staCtx) throws Exception
	{
		// perform symbolic bytecode interpretation (analysis) to get all read and write access expressions (field access paths, array elements, local variables) and other necessary information (return values, new objects)
		
		SymbolicFieldArrayWriteAnalysis.analyzeProgram(staCtx, Configuration.libraryMethodPrefixes);
		
		if (Configuration.DEBUG)
		{
			SymbolicFieldArrayWriteAnalysis.printSymbolicAssignments();
		}
	
		SymbolicLocalVarWriteAnalysis.analyzeProgram(staCtx, Configuration.libraryMethodPrefixes);
		
		if (Configuration.DEBUG)
		{
			SymbolicLocalVarWriteAnalysis.printSymbolicAssignments();
		}
	
		// collect the list of symbolic actual arguments for each invoke instruction
		
		SymbolicInvokeArgumentsCollector.analyzeProgram(staCtx, Configuration.libraryMethodPrefixes);
		
		if (Configuration.DEBUG)
		{
			SymbolicInvokeArgumentsCollector.printMethodInvokeArguments();
		}
		
		// collect the list of symbolic return values for each program point that corresponds to a return instruction
		
		SymbolicReturnValuesCollector.analyzeProgram(staCtx, Configuration.libraryMethodPrefixes);
		
		if (Configuration.DEBUG)
		{
			SymbolicReturnValuesCollector.printMethodReturnValues();
		}
			
		// create the sets of may-aliased access paths for local variables (especially method parameters)
		
		SymbolicAccessPathAliasAnalysis.analyzeProgram(staCtx, Configuration.libraryMethodPrefixes);
		
		if (Configuration.DEBUG)
		{
			SymbolicAccessPathAliasAnalysis.printLocalVarAliases();
		}
		
		// collect the symbolic new object expression for each program point that corresponds to a new object allocation instruction (new)
		
		SymbolicNewObjectsCollector.analyzeProgram(staCtx, Configuration.libraryMethodPrefixes);
		
		if (Configuration.DEBUG)
		{
			SymbolicNewObjectsCollector.printNewObjects();
		}

		// collect the symbolic new array expression for each program point that corresponds to a new array allocation instruction (newarray/anewarray)
		
		SymbolicNewArraysCollector.analyzeProgram(staCtx, Configuration.libraryMethodPrefixes);
		
		if (Configuration.DEBUG)
		{
			SymbolicNewArraysCollector.printNewArrays();
		}
	
		// identify statements (program points) that are outside of any synchronized block in the method code
		
		SynchronizedAccessAnalysis.analyzeProgram(staCtx, Configuration.libraryMethodPrefixes);
		
		if (Configuration.DEBUG)
		{
			SynchronizedAccessAnalysis.printLockedProgPoints();
		}
		
		// compute summary for each pure Java method that is reachable in the call graph and does not belong to libraries (as defined by the list of prefixes)
			// we use results of the dynamic analysis (recorded call parameters-result pairs, updated fields, and updated array elements) as side effect summaries for native methods in the static analysis
		
		SideEffectSummaryGenAnalysis.analyzeProgram(staCtx);
		
		if (Configuration.DEBUG)
		{
			SideEffectSummaryGenAnalysis.printMethodSummaries();
		}
	}
	
	private static void transformInputProgram(StaticAnalysisContext staCtx, Set<String> nativeMethodsReplaced) throws Exception
	{
		Set<String> constructorSigs = WALAUtils.collectPublicInstanceConstructors(staCtx);

		if (Configuration.DEBUG)
		{
			System.err.println("CONSTRUCTOR SIGNATURES");
			System.err.println("======================");
		}

		for (String mthInitSig : constructorSigs)
		{
			String className = Utils.extractClassName(mthInitSig);
	
			if (Configuration.DEBUG) System.err.println(className + " : " + mthInitSig);
		
			ObjectTypesData.addConstructorSignature(className, mthInitSig);
		}

		Set<String> interfaceNames = WALAUtils.collectInterfaces(staCtx);

		ObjectTypesData.storeInterfaceTypeNames(interfaceNames);

		if (Configuration.DEBUG)
		{
			System.err.println("IMPLEMENTATIONS FOR INTERFACES");
			System.err.println("==============================");
		}

		for (String itfName : interfaceNames)
		{
			Set<String> implClassNames = WALAUtils.findImplementingClasses(staCtx, itfName);
	
			if (Configuration.DEBUG) System.err.println(itfName + " : " + implClassNames);

			ObjectTypesData.storeImplementingClassesForInterface(itfName, implClassNames);
		}
	
		// generate abstractions of pure Java library methods that were identified by the user (configuration)

		Set<String> libraryMethods = WALAUtils.collectMethodsWithPrefixes(staCtx, Configuration.libraryMethodPrefixes);

		for (String libMthSig : libraryMethods)
		{
			if (Configuration.DEBUG)
			{
				System.out.println("currently transformed library method: " + libMthSig);
			}
			
			// skip methods defined in classes that belong to Java standard library
				// we can safely ignore all side effects that are internal to such library methods (and their bytecode cannot be updated anyway)
				// this also ensures that we keep only writes to fields and arrays defined in classes that belong to custom libraries
			if (Utils.isJavaStandardLibraryMethod(libMthSig)) continue;
			
			LibraryMethodAbstractionGenerator.replaceMethodBytecode(libMthSig);
		}

		// replace calls of native methods

		if (Configuration.DEBUG)
		{
			System.out.println("transformed native methods: " + nativeMethodsReplaced);
		}
			
		NativeExternAbstractionGenerator.replaceStatements(staCtx, nativeMethodsReplaced, new ArrayList<String>());

		// replace calls of library methods that access external entities (identified by the configuration)
		
		Set<String> extaccessMethods = WALAUtils.collectMethodsWithPrefixes(staCtx, Configuration.extaccessMethodPrefixes);
		
		// we have to include all constructors (even those not in the call graph) because they might be used in the abstract program
		Set<String> extaccessAllInits = WALAUtils.collectAllInstanceConstructorsWithPrefixes(staCtx, Configuration.extaccessClassPrefixes);
		extaccessMethods.addAll(extaccessAllInits);

		if (Configuration.DEBUG)
		{
			System.out.println("transformed methods with external access: " + extaccessMethods);
			System.out.println("classes where all methods perform external access: " + Configuration.extaccessClassPrefixes);
		}
			
		NativeExternAbstractionGenerator.replaceStatements(staCtx, extaccessMethods, Configuration.extaccessClassPrefixes);
	}
	
	private static void createNativePeersForJPF(String unsuppNativesFileName, String jpfProfileDir) throws Exception
	{
		List<String> nativeMths = Utils.loadTextFileAsStringPerLine(unsuppNativesFileName);

		// map from class name to set of methods (name + descriptor)
		Map<String, Set<String>> cls2Mths = new HashMap<String, Set<String>>();

		for (String fullMth : nativeMths)
		{
			int k1 = fullMth.indexOf('(');
			int k2 = fullMth.lastIndexOf('.', k1);

			String className = fullMth.substring(0, k2);

			String mthNameDesc = fullMth.substring(k2+1);

			Set<String> mths = cls2Mths.get(className);

			if (mths == null)
			{
				mths = new HashSet<String>();
				cls2Mths.put(className, mths);
			}

			mths.add(mthNameDesc);
		}

		// create the actual peer classes for all native methods in the given list

		for (Map.Entry<String, Set<String>> me : cls2Mths.entrySet())
		{
			String className = me.getKey();
			Set<String> mthsPlainNameDesc = me.getValue();

			NativePeerGenerator.createNativePeer(className, mthsPlainNameDesc, jpfProfileDir);
		}
	}


	private static Process startExternalProcess(List<String> cmdArgs) throws Exception
	{
		// run the target program in a new instance of JVM (using external process)
		// the new JVM instance will be suspended immediately (waiting for JDI commands to resume it)
		ProcessBuilder pb = new ProcessBuilder(cmdArgs);
		
		// redirect to System.out and System.err of this process
		pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
		pb.redirectError(ProcessBuilder.Redirect.INHERIT);

		// we are not waiting here for the process to finish because we should receive something like the "VM death" event from JDI
		Process proc = pb.start();
			
		// let the started process (JVM) properly initialize before we connect to it using JDI
		Thread.sleep(500);

		return proc;
	}

	private static void stopExternalProcess(Process proc) throws Exception
	{
		// just to be sure (there cannot remain any stale processes)
		proc.destroyForcibly();

		// give time (1000 ms) for cleaning after the old process
		Thread.sleep(1000);
	}

	private static String printTimeDiff(Date start, Date finish)
	{
		long startMS = start.getTime();
		long finishMS = finish.getTime();
	
		long diffMS = finishMS - startMS;
    	
		long diffSeconds = (diffMS / 1000);
		
		return String.valueOf(diffSeconds);
	}
}
