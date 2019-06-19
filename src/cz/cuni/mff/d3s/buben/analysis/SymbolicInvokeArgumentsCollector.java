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
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.TreeSet;

import cz.cuni.mff.d3s.buben.StaticAnalysisContext;
import cz.cuni.mff.d3s.buben.Utils;
import cz.cuni.mff.d3s.buben.common.ProgramPoint;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.ExecutionVisitor;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.Expression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.LocalVarExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.NewObjectExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.NewArrayExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.SymbolicByteCodeInterpreter;


public class SymbolicInvokeArgumentsCollector implements ExecutionVisitor
{
	// map from program points that represent invocations to lists of method call arguments
	protected static Map<ProgramPoint, List<Expression>> pp2InvokeArgs;
	
	static
	{
		pp2InvokeArgs = new LinkedHashMap<ProgramPoint, List<Expression>>();
	}
	

	public static void analyzeProgram(StaticAnalysisContext staCtx, List<String> methodSigPrefixes) throws Exception
	{
		// for each program point that corresponds to method invocation, collect the list of symbolic expressions that represent actual arguments
		
		SymbolicInvokeArgumentsCollector argsCollector = new SymbolicInvokeArgumentsCollector();
		
		SymbolicByteCodeInterpreter.processReachableMethods(staCtx, argsCollector, methodSigPrefixes);  
	}
	
	public static Expression getArgumentForInvokePoint(ProgramPoint pp, int argIndex)
	{
		List<Expression> invokeArgs = pp2InvokeArgs.get(pp);
		
		return invokeArgs.get(argIndex);
	}
	
	public static void printMethodInvokeArguments()
	{
		System.out.println("INVOKE ARGUMENTS");
		System.out.println("================");

		// sort the set of program points
		Set<ProgramPoint> progPoints = new TreeSet<ProgramPoint>();
		progPoints.addAll(pp2InvokeArgs.keySet());
		
		for (ProgramPoint pp : progPoints)
		{
			if (Utils.isJavaStandardLibraryMethod(pp.methodSig)) continue;
			
			List<Expression> arguments = pp2InvokeArgs.get(pp);

			System.out.println(pp.methodSig + ":" + pp.insnPos);
			
			for (Expression arg : arguments)
			{
				System.out.println("\t " + arg);
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
		if (isStaticCall)
		{
			// static call => there is no receiver object
			
			pp2InvokeArgs.put(pp, arguments);
		}
		else
		{
			// instance method call => we must add the receiver object
			
			List<Expression> callRcvArgs = new ArrayList<Expression>();
			callRcvArgs.add(obj);
			callRcvArgs.addAll(arguments);
			
			pp2InvokeArgs.put(pp, callRcvArgs);
		}
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
	}
	
	public void visitStoreInsn(ProgramPoint pp, LocalVarExpression localVar, Expression newValue)
	{
	}
}
