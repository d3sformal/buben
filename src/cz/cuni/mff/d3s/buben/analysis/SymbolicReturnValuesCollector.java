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
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.List;
import java.util.LinkedHashMap;

import cz.cuni.mff.d3s.buben.StaticAnalysisContext;
import cz.cuni.mff.d3s.buben.Utils;
import cz.cuni.mff.d3s.buben.common.ProgramPoint;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.ExecutionVisitor;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.Expression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.LocalVarExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.NewObjectExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.NewArrayExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.SymbolicByteCodeInterpreter;


public class SymbolicReturnValuesCollector implements ExecutionVisitor
{
	// map from program points that correspond to return instructions to lists of possibly returned values
	protected static Map<ProgramPoint, Set<Expression>> pp2ReturnValues;
	
	static
	{
		pp2ReturnValues = new LinkedHashMap<ProgramPoint, Set<Expression>>();
	}
	

	public static void analyzeProgram(StaticAnalysisContext staCtx, List<String> methodSigPrefixes) throws Exception
	{
		// for each program point that corresponds to return instruction, collect the list of symbolic expressions that represent possibly returned values
		
		SymbolicReturnValuesCollector retvalCollector = new SymbolicReturnValuesCollector();
		
		SymbolicByteCodeInterpreter.processReachableMethods(staCtx, retvalCollector, methodSigPrefixes);
	}
	
	public static Set<Expression> getValuesForReturnPoint(ProgramPoint pp)
	{
		return pp2ReturnValues.get(pp);
	}
	
	public static void printMethodReturnValues()
	{
		System.out.println("RETURN VALUES");
		System.out.println("=============");

		// sort the set of program points
		Set<ProgramPoint> progPoints = new TreeSet<ProgramPoint>();
		progPoints.addAll(pp2ReturnValues.keySet());
		
		for (ProgramPoint pp : progPoints)
		{
			if (Utils.isJavaStandardLibraryMethod(pp.methodSig)) continue;
			
			Set<Expression> values = pp2ReturnValues.get(pp);

			System.out.println(pp.methodSig + ":" + pp.insnPos);
			
			for (Expression val : values)
			{
				System.out.println("\t " + val);
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
		// we must create a singleton list here
		Set<Expression> values = new HashSet<Expression>();
		values.add(retValue);
		
		pp2ReturnValues.put(pp, values);
	}
	
	public void visitStoreInsn(ProgramPoint pp, LocalVarExpression localVar, Expression newValue)
	{
	}
}
