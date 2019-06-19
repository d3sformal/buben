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
package cz.cuni.mff.d3s.buben.wala;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import java.io.File;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeDynamicInstruction;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.config.AnalysisScopeReader;

import cz.cuni.mff.d3s.buben.Configuration;
import cz.cuni.mff.d3s.buben.Utils;
import cz.cuni.mff.d3s.buben.StaticAnalysisContext;


public class WALAUtils
{
	public static Map<String, IClass> clsName2Obj;
	
	public static Map<TypeReference, String> typeRef2Name;
	
	public static Map<IClass, String> clsObj2Name;
	
	public static Map<String, CGNode> mthSig2CGNode;


	public static Set<String> abstractedMethodsXML;


	static
	{
		clsName2Obj = new HashMap<String, IClass>();
		
		typeRef2Name = new HashMap<TypeReference, String>();
		
		clsObj2Name = new HashMap<IClass, String>();
		
		mthSig2CGNode = new HashMap<String, CGNode>();

		abstractedMethodsXML = new HashSet<String>();
	}
	
	
	public static StaticAnalysisContext initLibrary() throws Exception
	{
		AnalysisScope scope = createAnalysisScope(Configuration.targetClassPath, Configuration.walaExclusionFilePath);

		IClassHierarchy cha = makeClassHierarchy(scope);

		Iterable<Entrypoint> entryPoints = Util.makeMainEntrypoints(scope, cha, Utils.getInternalClassName(Configuration.targetMainClassName, false));	
			
		AnalysisOptions options = new AnalysisOptions(scope, entryPoints);
		options.setHandleStaticInit(true);
		
		AnalysisCache cache = new AnalysisCache();			

		StaticAnalysisContext staCtx = new StaticAnalysisContext();
		staCtx.scope = scope;
		staCtx.cha = cha;
		staCtx.options = options;
		staCtx.cache = cache;
		
		prepareListOfMethodsAbstractedInXML();

		return staCtx;
	}
	
	private static AnalysisScope createAnalysisScope(String targetClassPath, String walaExclusionFilePath) throws Exception
	{
		AnalysisScope scope = null;
		
		scope = AnalysisScopeReader.readJavaScope("primordial.txt", new File(walaExclusionFilePath), WALAUtils.class.getClassLoader());

		AnalysisScopeReader.processScopeDefLine(scope, WALAUtils.class.getClassLoader(), "Application,Java,binaryDir,"+targetClassPath);

		return scope;
	}

	private static IClassHierarchy makeClassHierarchy(AnalysisScope scope) throws Exception
	{	
		IClassHierarchy cha = ClassHierarchy.make(scope);
		
		return cha;
	}


	public static void buildCallGraphPointers(StaticAnalysisContext staCtx) throws Exception
	{
		SSAPropagationCallGraphBuilder cgBuilder = null;
		
		// standard context-insensitive exhaustive pointer analysis (andersen)
		cgBuilder = Util.makeZeroCFABuilder(staCtx.options, staCtx.cache, staCtx.cha, staCtx.scope);
		
		CallGraph clGraph = cgBuilder.makeCallGraph(staCtx.options, null);
	
		if (Configuration.DEBUG) printCallGraph(clGraph, 5);
		
		if (Configuration.DEBUG) printAllMethodsInCallGraph(clGraph);

		loadMethodNodesCache(clGraph);
		
		if (Configuration.DEBUG) printAllMethodsIR(clGraph);
		
		staCtx.clGraph = clGraph;
	}
	
	
	public static String getClassName(TypeReference typeRef)
	{
		return getTypeNameStr(typeRef);
	}
	
	public static String getClassName(IClass cls) throws Exception
	{
		if (cls == null) return null;
		
		if (clsObj2Name.containsKey(cls)) return clsObj2Name.get(cls);
		
		String cnStr = getTypeNameStr(cls.getName());
		
		clsObj2Name.put(cls, cnStr);
					
		return cnStr;
	}
	
	public static String getTypeNameStr(TypeName typeName) throws Exception
	{
		String internalTypeNameStr = typeName.toString();

		return Utils.getPlainTypeName(internalTypeNameStr);
	}

	public static String getTypeNameStr(TypeReference typeRef)
	{
		if (typeRef == null) return null;
		
		if (typeRef2Name.containsKey(typeRef)) 
		{
			return typeRef2Name.get(typeRef);
		}
		
		try
		{
			TypeName typeClass = typeRef.getName();
			
			String typeStr = getTypeNameStr(typeClass);

			typeRef2Name.put(typeRef, typeStr);
	
			return typeStr;
		}
		catch (Exception ex) { ex.printStackTrace(); }
		
		return null;
	}

	public static IMethod findMethod(String tgtMethodSig, String ownerClassName, IClassHierarchy cha) throws Exception
	{
		IMethod tgtMth = null;

		CGNode cgn = mthSig2CGNode.get(tgtMethodSig);

		if (cgn != null) 
		{
			// typical case (fast path)
			tgtMth = cgn.getMethod();
		}
		else // cgn == null
		{
			// may happen for methods that are not in the call graph
				// example: InterruptedException.printStackTrace() that returns void (nothing)
			
			IClass cls = findClass(ownerClassName, cha);

			// cls == null for unknown class (e.g., due to exclusions)

			while (cls != null)
			{
				String tgtMthDesc = Utils.extractMethodDescriptor(tgtMethodSig);

				String tgtMethodSigCls = getClassName(cls) + "." + tgtMthDesc;
	
				for (IMethod mth : cls.getDeclaredMethods()) 
				{
					String mthSig = mth.getSignature();
					
					if (mthSig.equals(tgtMethodSigCls))
					{
						tgtMth = mth;
						break;
					}
				}

				if (tgtMth != null) break;
			
				cls = cls.getSuperclass();
			}
		}

		return tgtMth;
	}
		
	public static boolean hasMethodReturnValue(String tgtMethodSig, String ownerClassName, IClassHierarchy cha) throws Exception
	{
		// fast track (resolving the obvious cases)
		if (Utils.isMethodWithoutReturnValue(tgtMethodSig)) return false;

		IMethod tgtMth = findMethod(tgtMethodSig, ownerClassName, cha);

		// safe default answer if we do not know for sure
		// unknown class or strange library class/method
		if (tgtMth == null) return true;

		return (tgtMth.getReturnType() != TypeReference.Void);
	}

	public static int getMethodParamMaxSlot(IMethod mth) throws Exception
	{
		int maxSlot = -1;

		for (int i = 0; i < mth.getNumberOfParameters(); i++)
		{
			TypeReference paramType = mth.getParameterType(i);

			if ((paramType == TypeReference.Long) || (paramType == TypeReference.Double)) maxSlot += 2;
			else maxSlot += 1;
		}

		return maxSlot;
	}

	public static int getMethodParamIndex(String methodSig, int tgtParamSlot, IClassHierarchy cha) throws Exception
	{
		IMethod mth = findMethod(methodSig, Utils.extractClassName(methodSig), cha);
	
		int curSlot = 0;

		for (int i = 0; i < mth.getNumberOfParameters(); i++)
		{
			if (curSlot == tgtParamSlot) return i;

			TypeReference paramType = mth.getParameterType(i);

			if ((paramType == TypeReference.Long) || (paramType == TypeReference.Double)) curSlot += 2;
			else curSlot += 1;
		}

		return -1;
	}

	public static IClass findClass(String className, IClassHierarchy cha) throws Exception
	{
		if (className.endsWith("[]")) return null;
		
		if (clsName2Obj.containsKey(className)) 
		{
			return clsName2Obj.get(className);
		}
		
		Iterator<IClass> clsIt = cha.iterator();
		while (clsIt.hasNext())
		{
			IClass cls = clsIt.next();
			
			if (className.equals(getClassName(cls)))
			{
				clsName2Obj.put(className, cls);
				return cls;
			}
		}
		
		return null;
	}

	public static boolean existsInstanceField(String className, String tgtFieldName, IClassHierarchy cha) throws Exception
	{
		IClass cls = findClass(className, cha);
		
		for (IField instanceField : cls.getAllInstanceFields())
		{
			String fieldName = instanceField.getName().toUnicodeString();
			
			if (tgtFieldName.equals(fieldName)) return true;
		}
		
		return false;
	}	
	
	public static int getInsnBytecodePos(CGNode mthNode, int insnIndex)
	{
		return getInsnBytecodePos(mthNode.getMethod(), insnIndex);
	}
	
	public static int getInsnBytecodePos(IMethod mth, int insnIndex)
	{
		try
		{
			return ((IBytecodeMethod) mth).getBytecodeIndex(insnIndex);
		}
		catch (Exception ex)
		{
			return -1;
		}
	}
	
	private static void loadMethodNodesCache(CallGraph clGraph)
	{
		for (CGNode node : clGraph)
		{
			String methodSig = node.getMethod().getSignature();
			
			mthSig2CGNode.put(methodSig, node);
		}
	}
	
	public static CGNode getNodeForMethod(String methodSig)
	{
		return mthSig2CGNode.get(methodSig);	
	}
	
	public static Set<String> collectNativeMethods(StaticAnalysisContext staCtx)
	{
		return collectNativeMethodsWithCallers(staCtx, null);
	}

	public static Set<String> collectNativeMethodsWithCallers(StaticAnalysisContext staCtx, List<String> callerClsPrefixes)
	{
		Set<String> nativeMethods = new HashSet<String>();
	
		for (CGNode curMthNode : staCtx.clGraph)
		{
			IMethod mth = curMthNode.getMethod();
		
			if (mth.isNative())
			{
				if (existsCallerWithPrefix(staCtx, curMthNode, callerClsPrefixes))
				{
					nativeMethods.add(mth.getSignature());
				}
			}			
		}

		// we have to include also native methods that are modeled by WALA (e.g., java.lang.System.arraycopy)
		nativeMethods.addAll(abstractedMethodsXML);
		
		// we have to remove multithreading-related methods (Thread.start, Object.wait, etc)
		nativeMethods.removeAll(Configuration.nativeMethodsToIgnore);

		return nativeMethods;
	}
	
	public static Set<String> collectMethodsWithPrefixes(StaticAnalysisContext staCtx, List<String> mthPrefixes)
	{
		return collectMethodsWithPrefixesCallers(staCtx, mthPrefixes, null);
	}

	public static Set<String> collectMethodsWithPrefixesCallers(StaticAnalysisContext staCtx, List<String> mthPrefixes, List<String> callerClsPrefixes)
	{
		Set<String> methodSigs = new HashSet<String>();
	
		for (CGNode curMthNode : staCtx.clGraph)
		{
			IMethod curMth = curMthNode.getMethod();
			
			String curMthSig = curMth.getSignature();
		
			for (String mthPf : mthPrefixes)
			{
				if (curMthSig.startsWith(mthPf))
				{
					if (existsCallerWithPrefix(staCtx, curMthNode, callerClsPrefixes))
					{
						methodSigs.add(curMthSig);
						break;
					}
				}
			}
		}
	
		// we have to ensure that multithreading-related methods (Thread.start, Object.wait, etc) are not present in the returned set
		methodSigs.removeAll(Configuration.nativeMethodsToIgnore);

		return methodSigs;
	}
	
	public static boolean existsCallerWithPrefix(StaticAnalysisContext staCtx, CGNode curMthNode, List<String> callerClsPrefixes)
	{
		if (callerClsPrefixes == null) return true;

		Iterator<CGNode> callerNodesIt = staCtx.clGraph.getPredNodes(curMthNode);
		
		while (callerNodesIt.hasNext())
		{
			CGNode callerNode = callerNodesIt.next();

			String callerMthSig = callerNode.getMethod().getSignature();

			for (String clsPf : callerClsPrefixes)
			{
				if (callerMthSig.startsWith(clsPf)) return true;
			}
		}

		return false;
	}
	
	public static Set<String> collectPublicInstanceConstructors(StaticAnalysisContext staCtx)
	{
		Set<String> constructorSigs = new HashSet<String>();
	
		for (IClass cls : staCtx.cha)
		{
			for (IMethod mth : cls.getDeclaredMethods()) 
			{
				String mthSig = mth.getSignature();

				if (mth.isInit() && mth.isPublic()) constructorSigs.add(mthSig);
			}
		}
		
		return constructorSigs;
	}

	public static Set<String> collectAllInstanceConstructorsWithPrefixes(StaticAnalysisContext staCtx, List<String> clsPrefixes)
	{
		Set<String> constructorSigs = new HashSet<String>();
	
		for (IClass cls : staCtx.cha)
		{
			for (IMethod mth : cls.getDeclaredMethods()) 
			{
				String mthSig = mth.getSignature();

				if (mth.isInit())
				{
					for (String clsPf : clsPrefixes)
					{
						if (mthSig.startsWith(clsPf)) constructorSigs.add(mthSig);
					}
				}
			}
		}
		
		return constructorSigs;
	}
	
	public static Set<String> collectInterfaces(StaticAnalysisContext staCtx) throws Exception
	{
		Set<String> interfaceNames = new HashSet<String>();
	
		for (IClass cls : staCtx.cha)
		{
			if (cls.isInterface())
			{
				interfaceNames.add(getClassName(cls));
			}
		}

		return interfaceNames;
	}

	public static Set<String> findImplementingClasses(StaticAnalysisContext staCtx, String itfName) throws Exception
	{
		Set<String> implClassNames = new HashSet<String>();

		IClass itfClsObj = findClass(itfName, staCtx.cha);

		for (IClass cls : staCtx.cha)
		{
			// we want only real concrete classes
			if ( ! cls.isReferenceType() ) continue;
			if (cls.isAbstract() || cls.isInterface() || cls.isArrayClass()) continue;

			if (staCtx.cha.implementsInterface(cls, itfClsObj))
			{
				implClassNames.add(getClassName(cls));
			}
		}

		return implClassNames;
	}

	
	private static void printCallGraph(CallGraph clGraph, int maxLevel)
	{
		System.out.println("CALL GRAPH");
		System.out.println("==========");

		CGNode entryNode = clGraph.getFakeRootNode();
		printCallGraphNode(clGraph, entryNode, "method: ", maxLevel, 0);

		System.out.println("");
	}

	private static void printCallGraphNode(CallGraph clGraph, CGNode node, String prefix, int maxLevel, int curLevel)
	{
		if (curLevel > maxLevel) return;
		
		System.out.println(prefix + node.getMethod().getSignature());
		
		Iterator<CallSiteReference> callSitesIt = node.iterateCallSites();
		while (callSitesIt.hasNext())
		{
			CallSiteReference callSite = callSitesIt.next();
			
			Set<CGNode> targetNodes = clGraph.getPossibleTargets(node, callSite);
			
			for (CGNode tgtNode : targetNodes) printCallGraphNode(clGraph, tgtNode, "\t" + prefix, maxLevel, curLevel + 1);
		}
	}
	
	private static void printAllMethodsInCallGraph(CallGraph clGraph)
	{
		System.out.println("METHODS IN CALL GRAPH");
		System.out.println("=====================");

		for (CGNode node : clGraph) 
		{
			String methodSig = node.getMethod().getSignature();

			System.out.println(methodSig);
		}

		System.out.println("");
	}


	private static void printAllMethodsIR(CallGraph clGraph)
	{
		Set<String> printedMethods = new HashSet<String>();
				
		System.out.println("METHOD SSA IR");
		System.out.println("============="); 
		
		for (CGNode node : clGraph) 
		{
			String methodSig = node.getMethod().getSignature();
			
			if (printedMethods.contains(methodSig)) continue;
			
			printMethodIR(node, methodSig);

			printedMethods.add(methodSig);
		}

		System.out.println("");
	}
	
	private static void printMethodIR(CGNode mthNode, String mthSig)
	{
		IR methodIR = mthNode.getIR();
					
		if (methodIR == null) return;
					
		System.out.println("method signature = " + mthSig);
	   
		SSAInstruction[] instructions = methodIR.getInstructions();
		for (int ssaIndex = 0; ssaIndex < instructions.length; ssaIndex++)
		{
			int insnPos = getInsnBytecodePos(mthNode, ssaIndex);
			if (instructions[ssaIndex] == null) System.out.println("\t " + insnPos + ": null");
			else System.out.println("\t " + insnPos + ": " + instructions[ssaIndex].toString());
		}
	}

	public static boolean isSyntheticModelClass(String clsName)
	{
		if (clsName.startsWith("com.ibm.wala")) return true;

		return false;
	}

	private static void prepareListOfMethodsAbstractedInXML()
	{
		abstractedMethodsXML.add("java.io.FileDescriptor.sync()V");
		abstractedMethodsXML.add("java.io.FileInputStream.available()I");
		abstractedMethodsXML.add("java.io.FileInputStream.close()V");
		abstractedMethodsXML.add("java.io.FileInputStream.read()I");
		abstractedMethodsXML.add("java.io.FileInputStream.readBytes([BII)I");
		abstractedMethodsXML.add("java.io.FileInputStream.skip(J)J");
		abstractedMethodsXML.add("java.io.FileOutputStream.close()V");
		abstractedMethodsXML.add("java.io.RandomAccessFile.length()J");
		abstractedMethodsXML.add("java.lang.Class.forName(Ljava/lang/String;)Ljava/lang/Class;");
		abstractedMethodsXML.add("java.lang.Object.clone()Ljava/lang/Object;");
		abstractedMethodsXML.add("java.lang.Object.getClass()Ljava/lang/Class;");
		abstractedMethodsXML.add("java.lang.Object.hashCode()I");
		abstractedMethodsXML.add("java.lang.Object.notify()V");
		abstractedMethodsXML.add("java.lang.Object.notifyAll()V");
		abstractedMethodsXML.add("java.lang.Object.wait(J)V");
		abstractedMethodsXML.add("java.lang.System.arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V");
		abstractedMethodsXML.add("java.lang.System.currentTimeMillis()J");
		abstractedMethodsXML.add("java.lang.System.getProperty(Ljava/lang/String;)Ljava/lang/String;");
		abstractedMethodsXML.add("java.lang.System.getProperty(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
	}

	public static boolean isMethodAbstractedInXML(String mthSig)
	{
		if (abstractedMethodsXML.contains(mthSig)) return true;

		return false;
	}
}
