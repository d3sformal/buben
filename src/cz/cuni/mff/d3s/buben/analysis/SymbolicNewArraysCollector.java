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
import java.util.Set;
import java.util.TreeSet;
import java.util.List;

import cz.cuni.mff.d3s.buben.StaticAnalysisContext;
import cz.cuni.mff.d3s.buben.Utils;
import cz.cuni.mff.d3s.buben.common.ProgramPoint;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.ExecutionVisitor;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.Expression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.NewObjectExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.NewArrayExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.LocalVarExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.SymbolicByteCodeInterpreter;


public class SymbolicNewArraysCollector implements ExecutionVisitor
{
	// map from program points to symbolic expressions that represent newly allocated array objects (at the respective location)
	protected static Map<ProgramPoint, NewArrayExpression> pp2NewArray;
	
	static
	{
		pp2NewArray = new LinkedHashMap<ProgramPoint, NewArrayExpression>();
	}
	
	
	public static void analyzeProgram(StaticAnalysisContext staCtx, List<String> methodSigPrefixes) throws Exception
	{
		// for each program point that corresponds to new array object allocation, we gather the symbolic expression that represents the new array
		
		SymbolicNewArraysCollector arrayCollector = new SymbolicNewArraysCollector();
		
		SymbolicByteCodeInterpreter.processReachableMethods(staCtx, arrayCollector, methodSigPrefixes);
	}
	
	public static NewArrayExpression getNewArrayForProgramPoint(ProgramPoint pp)
	{
		return pp2NewArray.get(pp);
	}
	
	public static void printNewArrays()
	{
		System.out.println("NEW ARRAYS");
		System.out.println("==========");
		
		Set<ProgramPoint> progPoints = new TreeSet<ProgramPoint>();
		progPoints.addAll(pp2NewArray.keySet());
		
		for (ProgramPoint pp : progPoints)
		{
			if (Utils.isJavaStandardLibraryMethod(pp.methodSig)) continue;
	
			NewArrayExpression newArray = pp2NewArray.get(pp);

			System.out.println(pp.methodSig + ":" + pp.insnPos);
			
			System.out.println("\t " + newArray.toString());
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
	}
	
	public void visitLoadInsn(ProgramPoint pp, LocalVarExpression localVar)
	{
	}
	
	public void visitNewObjectInsn(ProgramPoint pp, NewObjectExpression newObj)
	{
	}
		
	public void visitNewArrayInsn(ProgramPoint pp, NewArrayExpression newArray)
	{
		pp2NewArray.put(pp, newArray);
	}

	public void visitPutInsn(ProgramPoint pp, Expression obj, String className, String fieldName, String fieldType, boolean isStatic, Expression newValue)
	{
	}
	
	public void visitReturnInsn(ProgramPoint pp, Expression retValue)
	{
	}
	
	public void visitStoreInsn(ProgramPoint pp, LocalVarExpression localVar, Expression newValue)
	{
	}

}
