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
package cz.cuni.mff.d3s.buben.analysis;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

import cz.cuni.mff.d3s.buben.Configuration;
import cz.cuni.mff.d3s.buben.StaticAnalysisContext;
import cz.cuni.mff.d3s.buben.Utils;
import cz.cuni.mff.d3s.buben.common.ProgramPoint;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.ExecutionVisitor;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.Expression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.LocalVarExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.NewObjectExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.NewArrayExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.SymbolicByteCodeInterpreter;


public class SymbolicMethodLocationsCollector implements ExecutionVisitor
{
	// map from method signature to a list of invoke locations (program points)
	protected static Map<String, List<ProgramPoint>> mthSig2InvokeLocs;

	// map from method signature to a list of return locations within its code
	protected static Map<String, List<ProgramPoint>> mthSig2ReturnLocs;
	
	static
	{
		mthSig2InvokeLocs = new LinkedHashMap<String, List<ProgramPoint>>();
		mthSig2ReturnLocs = new LinkedHashMap<String, List<ProgramPoint>>();
	}


	public static void analyzeProgram(StaticAnalysisContext staCtx) throws Exception
	{
		SymbolicMethodLocationsCollector locsCollector = new SymbolicMethodLocationsCollector();
		
		SymbolicByteCodeInterpreter.processReachableMethods(staCtx, locsCollector, new ArrayList<String>());
	}
	
	public static List<ProgramPoint> getInvokeLocationsForMethod(String methodSig)
	{
		List<ProgramPoint> locations = mthSig2InvokeLocs.get(methodSig);
		
		// there does not exist any location where this method is possibly called
		// create an empty set for the purpose of future queries on this method signature
		if (locations == null)
		{
			locations = new ArrayList<ProgramPoint>();
			mthSig2InvokeLocs.put(methodSig, locations);
		}
	
		return locations;
	}
	
	public static List<ProgramPoint> getReturnLocationsForMethod(String methodSig)
	{
		List<ProgramPoint> locations = mthSig2ReturnLocs.get(methodSig);
		
		// there should be at least one return location in every method

		return locations;
	}

	public static void printInvokeLocations()
	{
		System.out.println("INVOKE LOCATIONS");
		System.out.println("================");

		// sort the set of method signatures
		Set<String> methodSigs = new TreeSet<String>();
		methodSigs.addAll(mthSig2InvokeLocs.keySet());
		
		for (String mthSig : methodSigs)
		{
			if (Utils.isJavaStandardLibraryMethod(mthSig)) continue;
			
			List<ProgramPoint> locations = mthSig2InvokeLocs.get(mthSig);

			System.out.println(mthSig);
			
			for (ProgramPoint loc : locations)
			{
				System.out.println("\t " + loc);
			}
		}
		
		System.out.println("");
	}
	
	public static void printReturnLocations()
	{
		System.out.println("RETURN LOCATIONS");
		System.out.println("================");

		// sort the set of method signatures
		Set<String> methodSigs = new TreeSet<String>();
		methodSigs.addAll(mthSig2ReturnLocs.keySet());
		
		for (String mthSig : methodSigs)
		{
			if (Utils.isJavaStandardLibraryMethod(mthSig)) continue;
			
			List<ProgramPoint> locations = mthSig2ReturnLocs.get(mthSig);

			System.out.println(mthSig);
			
			for (ProgramPoint loc : locations)
			{
				System.out.println("\t " + loc);
			}
		}
		
		System.out.println("");
	}

	
	public void visitArrayLoadInsn(ProgramPoint pp, Expression arrayObj, String arrayClassName, Expression elementIndex, String elementType)
	{
	}
	
	public void visitArrayStoreInsn(ProgramPoint pp, Expression arrayObj, String arrayClassName, Expression elementIndex, String elementType, Expression newValue)
	{
	}
	
	public void visitGetInsn(ProgramPoint pp, Expression obj, String className, String fieldName, String fieldType, boolean isStatic)
	{
	}
	
	public void visitInvokeInsn(ProgramPoint pp, String methodSig, boolean isStaticCall, Expression obj, List<Expression> arguments)
	{
		List<ProgramPoint> invokeLocations = mthSig2InvokeLocs.get(methodSig);
		
		if (invokeLocations == null)
		{
			invokeLocations = new ArrayList<ProgramPoint>();
			mthSig2InvokeLocs.put(methodSig, invokeLocations);
		}
		
		invokeLocations.add(pp);
	}
	
	public void visitLoadInsn(ProgramPoint pp, LocalVarExpression localVar)
	{
	}
	
	public void visitNewObjectInsn(ProgramPoint pp, NewObjectExpression newObj)
	{
	}
		
	public void visitNewArrayInsn(ProgramPoint pp, NewArrayExpression newArray)
	{
	}

	public void visitPutInsn(ProgramPoint pp, Expression obj, String className, String fieldName, String fieldType, boolean isStatic, Expression newValue)
	{
	}
	
	public void visitReturnInsn(ProgramPoint pp, Expression retValue)
	{
		List<ProgramPoint> returnLocations = mthSig2ReturnLocs.get(pp.methodSig);
		
		if (returnLocations == null)
		{
			returnLocations = new ArrayList<ProgramPoint>();
			mthSig2ReturnLocs.put(pp.methodSig, returnLocations);
		}
		
		returnLocations.add(pp);
	}
	
	public void visitStoreInsn(ProgramPoint pp, LocalVarExpression localVar, Expression newValue)
	{
	}
}
