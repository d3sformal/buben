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
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;

import cz.cuni.mff.d3s.buben.StaticAnalysisContext;
import cz.cuni.mff.d3s.buben.Utils;
import cz.cuni.mff.d3s.buben.common.ProgramPoint;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.ExecutionVisitor;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.Expression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.LocalVarExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.FieldAccessExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.ArrayAccessExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.NewObjectExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.NewArrayExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.SpecialExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.SymbolicByteCodeInterpreter;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.AssignmentStatement;


public class SymbolicAccessPathAliasAnalysis
{
	// for each method signature, this data structure maps local variables to sets of possibly may-aliased expressions
	protected static Map<String, Map<LocalVarExpression, Set<Expression>>> mth2LocalVarAliases;
	
	// list of symbolic assignment statements for each method (signature)
	protected static Map<String, List<AssignmentStatement>> mth2SymbAssignments; 
	
	static
	{
		mth2LocalVarAliases = new HashMap<String, Map<LocalVarExpression, Set<Expression>>>();
		mth2SymbAssignments = new HashMap<String, List<AssignmentStatement>>();
	}
	
	
	public static void analyzeProgram(StaticAnalysisContext staCtx, List<String> methodSigPrefixes) throws Exception
	{
		// for each local variable, collect the list of possibly aliased expressions
	
		// step 1: first gather all symbolic assignment statements in each method
		
		AssignmentsCollector assignCollector = new AssignmentsCollector();
		
		SymbolicByteCodeInterpreter.processReachableMethods(staCtx, assignCollector, methodSigPrefixes);
		
		// step 2: for each local variable, compute the sets of possibly aliased symbolic expressions
			// we iterate over the list of assignments and over the intermediate sets until a fixpoint is reached 
		
		Set<String> methodSigs = mth2SymbAssignments.keySet();
		
		for (String mthSig : methodSigs)
		{
			Map<LocalVarExpression, Set<Expression>> lv2AliasedExprs = new HashMap<LocalVarExpression, Set<Expression>>();
			
			List<AssignmentStatement> assignments = mth2SymbAssignments.get(mthSig);
			
			// initialize facts based on direct assignments			
			for (AssignmentStatement assignStmt : assignments)
			{
				if (assignStmt.source instanceof LocalVarExpression)
				{
					LocalVarExpression lv = (LocalVarExpression) assignStmt.source;
						
					Set<Expression> aliases = lv2AliasedExprs.get(lv);
						
					if (aliases == null)
					{
						aliases = new HashSet<Expression>();
						lv2AliasedExprs.put(lv, aliases);
					}
						
					if ( ! aliases.contains(assignStmt.dest) ) aliases.add(assignStmt.dest);
				}
				
				if (assignStmt.dest instanceof LocalVarExpression)
				{
					LocalVarExpression lv = (LocalVarExpression) assignStmt.dest;
						
					Set<Expression> aliases = lv2AliasedExprs.get(lv);
						
					if (aliases == null)
					{
						aliases = new HashSet<Expression>();
						lv2AliasedExprs.put(lv, aliases);
					}
					
					// null values are not real and useful aliases
					if (assignStmt.source != SpecialExpression.NULL)
					{
						if ( ! aliases.contains(assignStmt.source) ) aliases.add(assignStmt.source);
					}
				}
			}

			boolean changed = false;
			
			// we must propagate aliases transitively
			while (true)
			{
				Set<LocalVarExpression> localVars = lv2AliasedExprs.keySet();
				
				for (LocalVarExpression lv : localVars)
				{
					Set<Expression> oldAliases = lv2AliasedExprs.get(lv);
					
					Set<Expression> newAliases = new HashSet<Expression>();
					
					for (Expression oldAE : oldAliases)
					{
						if (oldAE instanceof LocalVarExpression)
						{
							Set<Expression> candidateAliases = lv2AliasedExprs.get((LocalVarExpression) oldAE);
							
							for (Expression cndAE : candidateAliases)
							{
								// we have found a new aliased expression
								if ( ! oldAliases.contains(cndAE) )
								{
									newAliases.add(cndAE);
									changed = true;
								}
							}								
						}
					}					
					
					// we have to replace the old set of aliases if we have found some new
					if ( ! newAliases.isEmpty() )
					{
						newAliases.addAll(oldAliases);
					
						lv2AliasedExprs.put(lv, newAliases);
					}
				}
				
				// we have the fixpoint
				if ( ! changed ) break;
				
				changed = false;
			}
			
			mth2LocalVarAliases.put(mthSig, lv2AliasedExprs);
		}
	}
	
	public static Set<Expression> getAliasesForLocalVariable(String methodSig, LocalVarExpression lv)
	{
		Map<LocalVarExpression, Set<Expression>> lv2AliasedExprs = mth2LocalVarAliases.get(methodSig);
		
		if (lv2AliasedExprs == null)
		{
			lv2AliasedExprs = new HashMap<LocalVarExpression, Set<Expression>>();
			mth2LocalVarAliases.put(methodSig, lv2AliasedExprs);
		}

		Set<Expression> aliases = lv2AliasedExprs.get(lv);
		
		// there do not exist aliases for the local variable
		// create an empty set for the purpose of future queries on this variable
		if (aliases == null)
		{
			aliases = new HashSet<Expression>();
			lv2AliasedExprs.put(lv, aliases);
		}
		
		return aliases;
	}

	public static void printLocalVarAliases()
	{
		System.out.println("LOCAL VARIABLE ALIASES");
		System.out.println("======================");

		// sort the set of method signatures
		Set<String> methodSigs = new TreeSet<String>();
		methodSigs.addAll(mth2LocalVarAliases.keySet());
		
		for (String mthSig : methodSigs)
		{
			if (Utils.isJavaStandardLibraryMethod(mthSig)) continue;
			
			System.out.println(mthSig);
			
			Map<LocalVarExpression, Set<Expression>> lv2AliasedExprs = mth2LocalVarAliases.get(mthSig);
			
			for (LocalVarExpression lv : lv2AliasedExprs.keySet()) 
			{
				System.out.print("\t" + lv.toString() + " : ");
				
				boolean first = true;
				
				for (Expression ae : lv2AliasedExprs.get(lv))
				{
					if ( ! first ) System.out.print(", ");
					first = false;
					
					System.out.print(ae.toString());
				}
				
				System.out.println("");
			}
		}
		
		System.out.println("");
	}
	
	
	static class AssignmentsCollector implements ExecutionVisitor
	{
		public void visitArrayLoadInsn(ProgramPoint pp, Expression arrayObj, String arrayClassName, Expression elementIndex, String elementType)
		{
		}
		
		public void visitArrayStoreInsn(ProgramPoint pp, Expression arrayObj, String arrayClassName, Expression elementIndex, String elementType, Expression newValue)
		{
			// save the assignment statement "array element access expression (destination) := possible new value (source access expression)"
			
			List<AssignmentStatement> assignments = getAssignListForMethod(pp.methodSig);
			
			ArrayAccessExpression arrayElement = new ArrayAccessExpression(arrayObj, arrayClassName, elementIndex, elementType);
			AssignmentStatement symbAssign = new AssignmentStatement(newValue, arrayElement);
			
			assignments.add(symbAssign);
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
			// save the assignment statement "object field access expression (destination) := possible new value (source access expression)"
			
			List<AssignmentStatement> assignments = getAssignListForMethod(pp.methodSig);
			
			FieldAccessExpression objField = new FieldAccessExpression(obj, className, fieldName, fieldType, isStatic);			
			AssignmentStatement symbAssign = new AssignmentStatement(newValue, objField);
			
			assignments.add(symbAssign);
		}
		
		public void visitReturnInsn(ProgramPoint pp, Expression retValue)
		{
		}
		
		public void visitStoreInsn(ProgramPoint pp, LocalVarExpression localVar, Expression newValue)
		{
			// save the assignment statement "local variable expression (destination) := possible new value (source access expression)"
			
			List<AssignmentStatement> assignments = getAssignListForMethod(pp.methodSig);
			
			AssignmentStatement symbAssign = new AssignmentStatement(newValue, localVar);
			
			assignments.add(symbAssign);		
		}
		
		private List<AssignmentStatement> getAssignListForMethod(String mthSig)
		{
			List<AssignmentStatement> assignments = mth2SymbAssignments.get(mthSig);
			
			if (assignments == null)
			{
				assignments = new ArrayList<AssignmentStatement>();
				mth2SymbAssignments.put(mthSig, assignments);				
			}
			
			return assignments;
		}
	}
}
