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
import java.util.HashSet;
import java.util.TreeSet;
import java.util.List;

import cz.cuni.mff.d3s.buben.StaticAnalysisContext;
import cz.cuni.mff.d3s.buben.Utils;
import cz.cuni.mff.d3s.buben.common.ProgramPoint;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.ExecutionVisitor;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.Expression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.FieldAccessExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.ArrayAccessExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.LocalVarExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.NewObjectExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.NewArrayExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.SymbolicByteCodeInterpreter;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.AssignmentStatement;


public class SymbolicLocalVarWriteAnalysis
{
	// map from program points to objects that represent assignments (symbolic expressions for source and destination)
	protected static Map<ProgramPoint, AssignmentStatement> pp2SymbAssign;
	
	static
	{
		pp2SymbAssign = new LinkedHashMap<ProgramPoint, AssignmentStatement>();
	}
	
	
	public static void analyzeProgram(StaticAnalysisContext staCtx, List<String> methodSigPrefixes) throws Exception
	{
		// for each program point that corresponds to local variable update, collect the destination local variable and the symbolic expression that represents source of the respective assignment
		
		AssignSourceDestinationCollector assignSrcDstCollector = new AssignSourceDestinationCollector();
		
		SymbolicByteCodeInterpreter.processReachableMethods(staCtx, assignSrcDstCollector, methodSigPrefixes);
	}
	
	public static AssignmentStatement getSymbolicAssignmentForProgramPoint(ProgramPoint pp)
	{
		return pp2SymbAssign.get(pp);
	}

	public static Set<Expression> getSourcesForAssignmentsInMethod(String tgtMthSig)
	{
		Set<Expression> sources = new HashSet<Expression>();

		for (ProgramPoint pp : pp2SymbAssign.keySet())
		{
			if (pp.methodSig.equals(tgtMthSig))
			{
				AssignmentStatement assignStmt = pp2SymbAssign.get(pp);

				sources.add(assignStmt.source);
			}
		}

		return sources;
	}
	
	public static void printSymbolicAssignments()
	{
		System.out.println("SYMBOLIC ASSIGNMENTS TO LOCAL VARS");
		System.out.println("==================================");
		
		Set<ProgramPoint> progPoints = new TreeSet<ProgramPoint>();
		progPoints.addAll(pp2SymbAssign.keySet());
		
		for (ProgramPoint pp : progPoints)
		{
			if (Utils.isJavaStandardLibraryMethod(pp.methodSig)) continue;
	
			AssignmentStatement symbAssign = pp2SymbAssign.get(pp);

			System.out.println(pp.methodSig + ":" + pp.insnPos);
			
			System.out.println("\t " + symbAssign.toString());
		}
		
		System.out.println("");
	}
	
	
	static class AssignSourceDestinationCollector implements ExecutionVisitor
	{
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
		}
		
		public void visitStoreInsn(ProgramPoint pp, LocalVarExpression localVar, Expression newValue)
		{
			AssignmentStatement symbAssign = new AssignmentStatement(newValue, localVar);
			
			// save the local variable expression (destination) and possible new value (source access expression)			
			pp2SymbAssign.put(pp, symbAssign);
		}
	}
}
