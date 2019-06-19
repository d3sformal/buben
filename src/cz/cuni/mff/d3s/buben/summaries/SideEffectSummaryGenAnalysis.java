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
package cz.cuni.mff.d3s.buben.summaries;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.List;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Iterator;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.analysis.ExplodedControlFlowGraph;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

import cz.cuni.mff.d3s.buben.Configuration;
import cz.cuni.mff.d3s.buben.StaticAnalysisContext;
import cz.cuni.mff.d3s.buben.Utils;
import cz.cuni.mff.d3s.buben.common.ProgramPoint;
import cz.cuni.mff.d3s.buben.common.AllocationSite;
import cz.cuni.mff.d3s.buben.common.ClassName;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.Expression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.ExpressionUtils;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.FieldAccessExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.ArrayAccessExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.LocalVarExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.ClassNameExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.NewObjectExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.NewArrayExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.AssignmentStatement;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.ConstantExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.ReturnValueExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.ArithmeticExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.SpecialExpression;
import cz.cuni.mff.d3s.buben.analysis.SymbolicFieldArrayWriteAnalysis;
import cz.cuni.mff.d3s.buben.analysis.SymbolicLocalVarWriteAnalysis;
import cz.cuni.mff.d3s.buben.analysis.SymbolicInvokeArgumentsCollector;
import cz.cuni.mff.d3s.buben.analysis.SymbolicReturnValuesCollector;
import cz.cuni.mff.d3s.buben.analysis.SymbolicAccessPathAliasAnalysis;
import cz.cuni.mff.d3s.buben.analysis.SymbolicNewObjectsCollector;
import cz.cuni.mff.d3s.buben.analysis.SymbolicNewArraysCollector;
import cz.cuni.mff.d3s.buben.analysis.SynchronizedAccessAnalysis;
import cz.cuni.mff.d3s.buben.dynamic.FieldWriteInfo;
import cz.cuni.mff.d3s.buben.dynamic.ArrayWriteInfo;
import cz.cuni.mff.d3s.buben.dynamic.CallResultInfo;
import cz.cuni.mff.d3s.buben.dynamic.DynamicInputOutputCollector;
import cz.cuni.mff.d3s.buben.wala.WALAUtils;


public class SideEffectSummaryGenAnalysis
{
	// method signature to the current summary
	private static Map<String, MethodSideEffectSummary> mthSig2Summary;
	
	// method signature to the object
	private static Map<String, IMethod> mthSig2Obj;
	
	// method signature to unique reference
	private static Map<String, MethodReference> mthSig2Ref;

	// method signature to SSA IR
	private static Map<String, IR> mthSig2IR;


	static
	{
		mthSig2Summary = new HashMap<String, MethodSideEffectSummary>();
		mthSig2Obj = new HashMap<String, IMethod>();		
		mthSig2Ref = new HashMap<String, MethodReference>();
		mthSig2IR = new HashMap<String, IR>();
	}

	
	public static void analyzeProgram(StaticAnalysisContext staCtx) throws Exception
	{
		// list elements are method signatures
		List<String> mthWorklist = new LinkedList<String>();
		
		// put all reachable pure Java library methods into the worklist
		for (Iterator<CGNode> cgnIt = staCtx.clGraph.iterator(); cgnIt.hasNext(); )
		{
			CGNode cgn = cgnIt.next();
			
			IMethod mth = cgn.getMethod();
			
			String methodSig = mth.getSignature();
			
			mthSig2Obj.put(methodSig, mth);
			mthSig2Ref.put(methodSig, mth.getReference());
			
			// use results of the dynamic analysis for native methods
			if (mth.isNative()) 
			{
				MethodSideEffectSummary nativeMthSumm = extractSummaryForNativeExternMethod(methodSig, mth, staCtx);
				
				mthSig2Summary.put(methodSig, nativeMthSumm);

				continue;
			}

			// use results of the dynamic analysis for library methods that access external entities (according to configuration)
			// initialize/bootstrap summaries for these methods
			if (Configuration.isExternalAccessMethod(methodSig))
			{
				MethodSideEffectSummary externMthSumm = extractSummaryForNativeExternMethod(methodSig, mth, staCtx);
				
				mthSig2Summary.put(methodSig, externMthSumm);
			}
		
			// we ignore synthetic methods (fake root)
			if (mth.isSynthetic()) continue;

			// we ignore abstract methods
			if (mth.isAbstract()) continue;
			
			// skip methods that do not belong to any library
			if ( ! Configuration.isLibraryMethod(methodSig) ) continue;
			
			mthSig2IR.put(methodSig, cgn.getIR());
			
			if ( ! mthWorklist.contains(methodSig) ) mthWorklist.add(methodSig);
		}
		
		// perform worklist algorithm over the method signatures
		while ( ! mthWorklist.isEmpty() )
		{
			String curMthSig = mthWorklist.remove(0);
			
			IMethod curMth = mthSig2Obj.get(curMthSig);
			IR curMthIR = mthSig2IR.get(curMthSig);
			
			MethodSideEffectSummary curMthOrigSumm = getSummaryForMethod(curMthSig);

			// perform simple intra-procedural analysis of the given method to compute its summary 
				// find all side effects (field write, array element update, newly allocated objects, returned parameters)
				// when processing a given method consider also nested calls (available summaries for them) transitively
			MethodSideEffectSummary curMthNewSumm = computeSummaryForMethod(curMthSig, curMth, curMthIR, staCtx);
		
			if ( ! curMthOrigSumm.equals(curMthNewSumm) )
			{
				// summary for the method changed in this iteration
				
				// add callers into the worklist to ensure that we soundly recompute their summaries				
				for ( CGNode curMthNode : staCtx.clGraph.getNodes(mthSig2Ref.get(curMthSig)) )
				{
					Iterator<CGNode> callerNodesIt = staCtx.clGraph.getPredNodes(curMthNode);
				
					while (callerNodesIt.hasNext())
					{
						CGNode callerNode = callerNodesIt.next();
						
						String callerMthSig = callerNode.getMethod().getSignature();
						
						// ignore synthetic methods (fake root)
						if (callerNode.getMethod().isSynthetic()) continue;

						// ignore abstract methods
						if (callerNode.getMethod().isAbstract()) continue;
						
						// ignore methods that do not belong to any library
						// we do not have to compute summaries for callers that are not library methods
						if ( ! Configuration.isLibraryMethod(callerMthSig) ) continue;
												
						if ( ! mthWorklist.contains(callerMthSig) ) mthWorklist.add(callerMthSig);
					}
				}
			}
			
			mthSig2Summary.put(curMthSig, curMthNewSumm);
		}
	}

	public static MethodSideEffectSummary getSummaryForMethod(String mthSig)
	{
		MethodSideEffectSummary mthSumm = mthSig2Summary.get(mthSig);
		
		if (mthSumm == null)
		{
			mthSumm = new MethodSideEffectSummary(mthSig, new HashMap<FieldAccessExpression, Set<Expression>>(), new HashMap<ArrayAccessExpression, Set<Expression>>(), new HashSet<NewObjectExpression>(), new HashSet<NewArrayExpression>(), new HashSet<Integer>(), new HashSet<Expression>(), new HashSet<FieldAccessExpression>(), new HashSet<ArrayAccessExpression>());
			mthSig2Summary.put(mthSig, mthSumm);
		}
		
		return mthSumm;
	}
	
	public static void printMethodSummaries()
	{
		System.out.println("METHOD SUMMARIES");
		System.out.println("=================");
		
		// sort the set of method signatures
		Set<String> methodSigs = new TreeSet<String>();
		methodSigs.addAll(mthSig2Summary.keySet());
		
		for (String mthSig : methodSigs)
		{
			if (Utils.isJavaStandardLibraryMethod(mthSig)) continue;
			
			MethodSideEffectSummary mthSumm = mthSig2Summary.get(mthSig);

			System.out.println(mthSig);
			
			System.out.println("\t updated fields:");
			for (Map.Entry<FieldAccessExpression, Set<Expression>> meF2V : mthSumm.updatedFields2Values.entrySet())
			{
				FieldAccessExpression fieldExpr = meF2V.getKey();
				Set<Expression> feNewValues = meF2V.getValue();
				
				System.out.println("\t\t " + fieldExpr);
				
				for (Expression nVal : feNewValues) 
				{
					System.out.println("\t\t\t " + nVal);
				}
			}
			
			System.out.println("\t updated arrays:");
			for (Map.Entry<ArrayAccessExpression, Set<Expression>> meA2V : mthSumm.updatedArrays2Values.entrySet())
			{
				ArrayAccessExpression arrayExpr = meA2V.getKey();
				Set<Expression> aeNewValues = meA2V.getValue();
				
				System.out.println("\t\t " + arrayExpr);
				
				for (Expression nVal : aeNewValues) 
				{
					System.out.println("\t\t\t " + nVal);
				}
			}			
			
			System.out.println("\t new objects:");
			for (NewObjectExpression newObj : mthSumm.newObjects) 
			{
				System.out.println("\t\t " + newObj);
			}
		
			System.out.println("\t new arrays:");
			for (NewArrayExpression newArray : mthSumm.newArrays) 
			{
				System.out.println("\t\t " + newArray);
			}
		
			// just indexes (integer values) so we do not have to use a new line for each of them
			System.out.println("\t returned parameters = " + mthSumm.returnedParams);
			
			System.out.println("\t return values:");
			for (Expression retVal : mthSumm.returnValues) 
			{
				System.out.println("\t\t " + retVal);
			}
			
			System.out.println("\t unsynchronized field accesses:");
			for (FieldAccessExpression fieldExpr : mthSumm.unsynchFields) 
			{
				System.out.println("\t\t " + fieldExpr);
			}
			
			System.out.println("\t unsynchronized array accesses:");
			for (ArrayAccessExpression arrayExpr : mthSumm.unsynchArrays) 
			{
				System.out.println("\t\t " + arrayExpr);
			}
		}
		
		System.out.println("");
	}

	private static MethodSideEffectSummary computeSummaryForMethod(String mthSig, IMethod mth, IR mthIR, StaticAnalysisContext staCtx) throws Exception
	{
		Map<FieldAccessExpression, Set<Expression>> mthUpdatedFields2Values = new HashMap<FieldAccessExpression, Set<Expression>>();
		Map<ArrayAccessExpression, Set<Expression>> mthUpdatedArrays2Values = new HashMap<ArrayAccessExpression, Set<Expression>>();
		Set<NewObjectExpression> mthNewObjects = new HashSet<NewObjectExpression>();
		Set<NewArrayExpression> mthNewArrays = new HashSet<NewArrayExpression>();
		ReturnInfo mthRetInfo = new ReturnInfo(new HashSet<Integer>(), new HashSet<Expression>());
		Set<FieldAccessExpression> mthUnsynchFields = new HashSet<FieldAccessExpression>();
		Set<ArrayAccessExpression> mthUnsynchArrays = new HashSet<ArrayAccessExpression>();
		
		SSAInstruction[] instructions = mthIR.getInstructions();
		
		Set<ProgramPoint> mthLockedPoints = SynchronizedAccessAnalysis.getLockedPointsForMethod(mthSig);
		
		Set<Expression> mthAssignSourcesFA = SymbolicFieldArrayWriteAnalysis.getSourcesForAssignmentsInMethod(mthSig);
		Set<Expression> mthAssignSourcesLV = SymbolicLocalVarWriteAnalysis.getSourcesForAssignmentsInMethod(mthSig);
		Set<Expression> mthAssignSources = new HashSet<Expression>();
		mthAssignSources.addAll(mthAssignSourcesFA);
		mthAssignSources.addAll(mthAssignSourcesLV);

		for (int insnIndex = 0; insnIndex < instructions.length; insnIndex++)
		{
			SSAInstruction insn = instructions[insnIndex];
			
			if (insn == null) continue;
			
			int insnPos = WALAUtils.getInsnBytecodePos(mth, insnIndex);
			
			ProgramPoint insnPP = new ProgramPoint(mthSig, insnIndex, insnPos);
			
			// collect field write accesses
			if (insn instanceof SSAPutInstruction)
			{
				SSAPutInstruction putInsn = (SSAPutInstruction) insn;
				
				// for the field write access, get the symbolic expression (field access path) based on the program point				
				AssignmentStatement fwriteStmt = SymbolicFieldArrayWriteAnalysis.getSymbolicAssignmentForProgramPoint(insnPP);
				
				// if this cast is violated then something is really wrong
				FieldAccessExpression destFieldExpr = (FieldAccessExpression) fwriteStmt.dest;
				
				Expression dfeRoot = ExpressionUtils.extractRootObjectAccessPath(destFieldExpr);
				
				// create the set of possible aliases (with respect to local variables used as roots in access paths)
				
				List<FieldAccessExpression> aliasedFieldExprs = new ArrayList<FieldAccessExpression>();
				aliasedFieldExprs.add(destFieldExpr);
				
				// get all may-aliases to the root (when it is a local variable)
				if (dfeRoot instanceof LocalVarExpression)
				{
					Set<Expression> aliasedRoots = SymbolicAccessPathAliasAnalysis.getAliasesForLocalVariable(mthSig, (LocalVarExpression) dfeRoot);
					
					for (Expression ar : aliasedRoots)
					{
						FieldAccessExpression aliasedFE = ExpressionUtils.replaceRootObjectAccessPath(destFieldExpr, ar);
						aliasedFieldExprs.add(aliasedFE);
					}
				}
				
				// the root of every field access path is either a local variable (including "this" and other method arguments) or a class object (for static fields), and maybe it could also be a newly allocated object
				
				for (FieldAccessExpression fieldExpr : aliasedFieldExprs)
				{
					Expression feRoot = ExpressionUtils.extractRootObjectAccessPath(fieldExpr);
					
					Set<Expression> feNewValues = mthUpdatedFields2Values.get(fieldExpr);
						
					if (feNewValues == null) feNewValues = new HashSet<Expression>();
						
					// save all possible new symbolic values
						
					feNewValues.add(fwriteStmt.source);
						
					if (fwriteStmt.source instanceof LocalVarExpression)
					{
						Set<Expression> newValuesFromAliases = new HashSet<Expression>();

						Set<Expression> aliasedValues = SymbolicAccessPathAliasAnalysis.getAliasesForLocalVariable(mthSig, (LocalVarExpression) fwriteStmt.source);
						newValuesFromAliases.addAll(aliasedValues);

						// compensate for imprecise alias analysis: keep just those possible new values (of the field) whose prefix is the source expression used in some assignment within the method

						ExpressionUtils.filterAccessPathsByPrefixes(newValuesFromAliases, mthAssignSources);
						
						feNewValues.addAll(newValuesFromAliases);
					}
					
					// we remove local variables that are not method parameters
					// all possible new values of such local variables have been identified through aliasing 
					ExpressionUtils.dropLocalVarsNotParams(feNewValues);

					// we support arrays with multiple dimensions
					//ExpressionUtils.dropMultidimensionalArrays(feNewValues);
					
					// make sure the set of possible new values does not contain the target field access path (may happen due to aliasing)
					feNewValues.remove(fieldExpr);

					// we track only accesses to fields of objects that are given as method parameters (excluding "this")
					
					boolean ignoreField = false;

					if (ExpressionUtils.containsAccessPathFromLocalExpr(fieldExpr)) ignoreField = true;

					if ( ( ! mth.isStatic() ) && ExpressionUtils.containsAccessPathFromThis(fieldExpr)) ignoreField = true;
					
					if ( ! ignoreField )
					{
						mthUpdatedFields2Values.put(fieldExpr, feNewValues);
					
						// we have an unsynchronized access
						if ( ! mthLockedPoints.contains(insnPP) ) mthUnsynchFields.add(fieldExpr);
					}
				}
			}
			
			// collect array element write accesses
			if (insn instanceof SSAArrayStoreInstruction)
			{
				SSAArrayStoreInstruction arstoreInsn = (SSAArrayStoreInstruction) insn;
				
				// for the array element write access, get the symbolic expression (array access path) based on the program point				
				AssignmentStatement arwriteStmt = SymbolicFieldArrayWriteAnalysis.getSymbolicAssignmentForProgramPoint(insnPP);
				
				// if this cast is violated then something is really wrong
				ArrayAccessExpression destArrayExpr = (ArrayAccessExpression) arwriteStmt.dest;
				
				Expression daeRoot = ExpressionUtils.extractRootObjectAccessPath(destArrayExpr);
				
				// create the set of possible aliases (with respect to local variables used as roots in access paths)
				
				List<ArrayAccessExpression> aliasedArrayExprs = new ArrayList<ArrayAccessExpression>();
				aliasedArrayExprs.add(destArrayExpr);
				
				// get all may-aliases to the root (when it is a local variable)
				if (daeRoot instanceof LocalVarExpression)
				{
					Set<Expression> aliasedRoots = SymbolicAccessPathAliasAnalysis.getAliasesForLocalVariable(mthSig, (LocalVarExpression) daeRoot);
					
					for (Expression ar : aliasedRoots)
					{
						ArrayAccessExpression aliasedAE = ExpressionUtils.replaceRootObjectAccessPath(destArrayExpr, ar);
						aliasedArrayExprs.add(aliasedAE);
					}
				}
				
				// the root of every field access path is either a local variable (including "this" and other method arguments) or a class object (for static fields), and maybe it could also be a newly allocated object
								
				for (ArrayAccessExpression arrayExpr : aliasedArrayExprs)
				{
					Expression aeRoot = ExpressionUtils.extractRootObjectAccessPath(arrayExpr);
					
					Set<Expression> aeNewValues = mthUpdatedArrays2Values.get(arrayExpr);
					
					if (aeNewValues == null) aeNewValues = new HashSet<Expression>();
					
					// save all possible new symbolic values
					
					aeNewValues.add(arwriteStmt.source);
					
					if (arwriteStmt.source instanceof LocalVarExpression)
					{
						Set<Expression> newValuesFromAliases = new HashSet<Expression>();

						Set<Expression> aliasedValues = SymbolicAccessPathAliasAnalysis.getAliasesForLocalVariable(mthSig, (LocalVarExpression) arwriteStmt.source);
						newValuesFromAliases.addAll(aliasedValues);

						// compensate for imprecise alias analysis: keep just those possible new values (of the array element) whose prefix is the source expression used in some assignment

						ExpressionUtils.filterAccessPathsByPrefixes(newValuesFromAliases, mthAssignSources);
						
						aeNewValues.addAll(newValuesFromAliases);
					}
										
					// we remove local variables that are not method parameters
					// all possible new values of such local variables have been identified through aliasing 
					ExpressionUtils.dropLocalVarsNotParams(aeNewValues);

					// we support arrays with multiple dimensions
					//ExpressionUtils.dropMultidimensionalArrays(aeNewValues);
		
					// make sure the set of possible new values does not contain the target array element access path (may happen due to aliasing)
					aeNewValues.remove(arrayExpr);
	
					// we track only accesses to elements of arrays that are given as method parameters (excluding "this")
					
					boolean ignoreArray = false;

					if (ExpressionUtils.containsAccessPathFromLocalExpr(arrayExpr)) ignoreArray = true;

					if ( ( ! mth.isStatic() ) && ExpressionUtils.containsAccessPathFromThis(arrayExpr)) ignoreArray = true;
					
					if ( ! ignoreArray )
					{
						mthUpdatedArrays2Values.put(arrayExpr, aeNewValues);
					
						// we have an unsynchronized access
						if ( ! mthLockedPoints.contains(insnPP) ) mthUnsynchArrays.add(arrayExpr);
					}
				}
			}

			// collect new heap objects/arrays
			if (insn instanceof SSANewInstruction)
			{
				SSANewInstruction newInsn = (SSANewInstruction) insn;
				
				if (newInsn.getConcreteType().isArrayType())
				{
					// for the newly allocated array, get the symbolic expression based on the program point				
					NewArrayExpression newArray = SymbolicNewArraysCollector.getNewArrayForProgramPoint(insnPP);

					mthNewArrays.add(newArray);
				}
				else
				{
					// for the newly allocated object, get the symbolic expression based on the program point				
					NewObjectExpression newObj = SymbolicNewObjectsCollector.getNewObjectForProgramPoint(insnPP);

					mthNewObjects.add(newObj);
				}
			}
			
			// get possible sources of returned value for every return instruction and merge into the result for the whole method
				// not sure if there can be at most one return instruction in the SSA IR or multiple return instructions (but one exit block)
			if (insn instanceof SSAReturnInstruction)
			{
				SSAReturnInstruction returnInsn = (SSAReturnInstruction) insn;
				
				// does not return void
				if (returnInsn.getResult() != -1)
				{
					ReturnInfo insnRI = computeReturnValueSources(mthSig, mthIR, insnPP, returnInsn.getResult(), staCtx, mthAssignSources);
					
					for (Integer retParamIdx : insnRI.params) mthRetInfo.params.add(retParamIdx);
					
					for (Expression retValue : insnRI.values) mthRetInfo.values.add(retValue);
				}
			}
			
			// summary must capture information (field writes, new values, allocated objects) for all nested method calls (transitively)			
			if (insn instanceof SSAInvokeInstruction)
			{
				SSAInvokeInstruction invokeInsn = (SSAInvokeInstruction) insn;
						
				// process callees for this invoke
				for ( CGNode mthNode : staCtx.clGraph.getNodes(mthSig2Ref.get(mthSig)) )
				{
					for ( CGNode calleeNode : staCtx.clGraph.getPossibleTargets(mthNode, invokeInsn.getCallSite()) )
					{
						String calleeMthSig = calleeNode.getMethod().getSignature();
						
						// get current summary for target callee method
						MethodSideEffectSummary calleeMthSumm = getSummaryForMethod(calleeMthSig);
						
						// propagate information about the target nested method (callee) into the summary for current method
							// approach: replace the formal parameter in the callee field access expression with the corresponding actual argument in the caller field access expression
						
						// field accesses in the callee method
						for (Map.Entry<FieldAccessExpression, Set<Expression>> calleeUF2V : calleeMthSumm.updatedFields2Values.entrySet())
						{
							FieldAccessExpression calleeFieldExpr = calleeUF2V.getKey();
							Set<Expression> calleeNewValues = calleeUF2V.getValue();
							
							Expression cfeRoot = ExpressionUtils.extractRootObjectAccessPath(calleeFieldExpr);
							
							FieldAccessExpression curMthFieldExpr = null;
							
							if (cfeRoot instanceof LocalVarExpression)
							{
								LocalVarExpression rootLV = (LocalVarExpression) cfeRoot;
								
								// check whether it is a formal parameter of the callee method
								if (rootLV.isMthParam)
								{
									Expression actualArg = SymbolicInvokeArgumentsCollector.getArgumentForInvokePoint(insnPP, WALAUtils.getMethodParamIndex(calleeMthSig, rootLV.varSlot, staCtx.cha));
								
									// check whether the actual argument for the callee is also a formal parameter of the current method (excluding "this")
									
									boolean ignoreArg = false;

									if (ExpressionUtils.containsAccessPathFromLocalExpr(actualArg)) ignoreArg = true;

									if ( ( ! mth.isStatic() ) && ExpressionUtils.containsAccessPathFromThis(actualArg)) ignoreArg = true;

									// we also do not create field access expressions over null values
									if (actualArg == SpecialExpression.NULL) ignoreArg = true;
					
									if ( ! ignoreArg )
									{
										curMthFieldExpr = ExpressionUtils.replaceRootObjectAccessPath(calleeFieldExpr, actualArg);
									}
								}
							}
							
							if (cfeRoot instanceof ClassNameExpression)
							{
								curMthFieldExpr = calleeFieldExpr;
							}
							
							if (curMthFieldExpr != null)
							{
								Set<Expression> curMthNewValues = mthUpdatedFields2Values.get(curMthFieldExpr);
								
								if (curMthNewValues == null) curMthNewValues = new HashSet<Expression>();
								
								curMthNewValues.addAll(calleeNewValues);
								
								mthUpdatedFields2Values.put(curMthFieldExpr, curMthNewValues);
								
								if (calleeMthSumm.unsynchFields.contains(calleeFieldExpr)) mthUnsynchFields.add(curMthFieldExpr);
							}
						}
						
						// array element accesses in the callee method
						for (Map.Entry<ArrayAccessExpression, Set<Expression>> calleeUA2V : calleeMthSumm.updatedArrays2Values.entrySet())
						{
							ArrayAccessExpression calleeArrayExpr = calleeUA2V.getKey();
							Set<Expression> calleeNewValues = calleeUA2V.getValue();
							
							Expression caeRoot = ExpressionUtils.extractRootObjectAccessPath(calleeArrayExpr);
							
							ArrayAccessExpression curMthArrayExpr = null;
						
							if (caeRoot instanceof LocalVarExpression)
							{
								LocalVarExpression rootLV = (LocalVarExpression) caeRoot;
								
								// check whether it is a formal parameter of the callee method
								if (rootLV.isMthParam)
								{
									Expression actualArg = SymbolicInvokeArgumentsCollector.getArgumentForInvokePoint(insnPP, WALAUtils.getMethodParamIndex(calleeMthSig, rootLV.varSlot, staCtx.cha));
								
									// check whether the actual argument for the callee is also a formal parameter of the current method (excluding "this")
	
									boolean ignoreArg = false;

									if (ExpressionUtils.containsAccessPathFromLocalExpr(actualArg)) ignoreArg = true;

									if ( ( ! mth.isStatic() ) && ExpressionUtils.containsAccessPathFromThis(actualArg)) ignoreArg = true;
						
									// we also do not create array element access expressions over null values
									if (actualArg == SpecialExpression.NULL) ignoreArg = true;

									if ( ! ignoreArg )
									{
										curMthArrayExpr = ExpressionUtils.replaceRootObjectAccessPath(calleeArrayExpr, actualArg);
									}
								}
							}
							
							if (caeRoot instanceof ClassNameExpression)
							{
								curMthArrayExpr = calleeArrayExpr;
							}
							
							if (curMthArrayExpr != null)
							{
								Set<Expression> curMthNewValues = mthUpdatedArrays2Values.get(curMthArrayExpr);
								
								if (curMthNewValues == null) curMthNewValues = new HashSet<Expression>();
								
								curMthNewValues.addAll(calleeNewValues);
								
								mthUpdatedArrays2Values.put(curMthArrayExpr, curMthNewValues);
								
								if (calleeMthSumm.unsynchArrays.contains(calleeArrayExpr)) mthUnsynchArrays.add(curMthArrayExpr);
							}
						}
						
						// newly allocated objects in the callee method
						for (NewObjectExpression calleeNewObj : calleeMthSumm.newObjects)
						{
							mthNewObjects.add(calleeNewObj);
						}

						// newly allocated arrays in the callee method
						for (NewArrayExpression calleeNewArray : calleeMthSumm.newArrays)
						{
							mthNewArrays.add(calleeNewArray);
						}
					}
				}
			}
		}

		// each return expression is directly replaced by the currently known set of return values for the respective target callee method

		for (Map.Entry<FieldAccessExpression, Set<Expression>> me : mthUpdatedFields2Values.entrySet())
		{
			Set<Expression> exprs = me.getValue();

			expandReturnExpressions(exprs);
		}

		for (Map.Entry<ArrayAccessExpression, Set<Expression>> me : mthUpdatedArrays2Values.entrySet())
		{
			Set<Expression> exprs = me.getValue();

			expandReturnExpressions(exprs);
		}
		
		expandReturnExpressions(mthRetInfo.values);

		// we drop accesses to fields of inner classes whose parent classes do not belong to the set of "application classes" (i.e., when the given parent class belongs to some library)
		
		dropLibraryInnerClasses(mthUpdatedFields2Values.keySet());
		
		for (Map.Entry<FieldAccessExpression, Set<Expression>> me : mthUpdatedFields2Values.entrySet())
		{
			Set<Expression> exprs = me.getValue();

			dropLibraryInnerClasses(exprs);
		}
	
		dropLibraryInnerClasses(mthUpdatedArrays2Values.keySet());
		
		for (Map.Entry<ArrayAccessExpression, Set<Expression>> me : mthUpdatedArrays2Values.entrySet())
		{
			Set<Expression> exprs = me.getValue();

			dropLibraryInnerClasses(exprs);
		}

		dropLibraryInnerClasses(mthNewObjects);
	
		dropLibraryInnerClasses(mthNewArrays);

		dropLibraryInnerClasses(mthRetInfo.values);
	
		dropLibraryInnerClasses(mthUnsynchFields);

		dropLibraryInnerClasses(mthUnsynchArrays);

		// keep only those newly allocated objects that are used either (1) possible new values in some write/update of a field, respectively array element or (ii) returned from the method

		Set<Expression> visibleOutputs = new HashSet<Expression>();
	
		for (Map.Entry<FieldAccessExpression, Set<Expression>> me : mthUpdatedFields2Values.entrySet())
		{
			Set<Expression> newValues = me.getValue();

			visibleOutputs.addAll(newValues);
		}
		
		for (Map.Entry<ArrayAccessExpression, Set<Expression>> me : mthUpdatedArrays2Values.entrySet())
		{
			Set<Expression> newValues = me.getValue();

			visibleOutputs.addAll(newValues);
		}

		visibleOutputs.addAll(mthRetInfo.values);

		mthNewObjects.retainAll(visibleOutputs);
		mthNewArrays.retainAll(visibleOutputs);

		// create the actual summary

		MethodSideEffectSummary mthSumm = new MethodSideEffectSummary(mthSig, mthUpdatedFields2Values, mthUpdatedArrays2Values, mthNewObjects, mthNewArrays, mthRetInfo.params, mthRetInfo.values, mthUnsynchFields, mthUnsynchArrays);
		
		return mthSumm;
	}
	
	private static ReturnInfo computeReturnValueSources(String mthSig, IR mthIR, ProgramPoint retInsnPP, int retValNum, StaticAnalysisContext staCtx, Set<Expression> mthAssignSources)
	{
		// these data structures contain SSA value numbers
		Set<Integer> processedVals = new HashSet<Integer>();
		List<Integer> valWorklist = new LinkedList<Integer>();
		
		valWorklist.add(retValNum);
		
		Set<Integer> returnedParams = new HashSet<Integer>();
		Set<Expression> returnValues = new HashSet<Expression>();
		
		// add return values that are defined explicitly in the source code (text) of the current method
			// note that a returned value may also be a newly allocated object/array
		
		Set<Expression> curMthRetVals = new HashSet<Expression>();

		Set<Expression> explicitRetVals = SymbolicReturnValuesCollector.getValuesForReturnPoint(retInsnPP);
		
		curMthRetVals.addAll(explicitRetVals);

		// we consider aliases of the return values explicitly defined in the source code
		for (Expression explRV : explicitRetVals)
		{
			Expression ervRoot = ExpressionUtils.extractRootObjectAccessPath(explRV);
		
			if (ervRoot instanceof LocalVarExpression)
			{
				Set<Expression> retValuesFromAliases = new HashSet<Expression>();

				Set<Expression> aliasedRoots = SymbolicAccessPathAliasAnalysis.getAliasesForLocalVariable(mthSig, (LocalVarExpression) ervRoot);

				for (Expression ar : aliasedRoots)
				{
					Expression aliasedRV = null;

					if (explRV instanceof FieldAccessExpression)
					{
						aliasedRV = ExpressionUtils.replaceRootObjectAccessPath((FieldAccessExpression) explRV, ar);
					}
		
					if (explRV instanceof ArrayAccessExpression)
					{
						aliasedRV = ExpressionUtils.replaceRootObjectAccessPath((ArrayAccessExpression) explRV, ar);
					}

					if (explRV instanceof LocalVarExpression)
					{
						aliasedRV = ar;
					}
			
					if (aliasedRV != null) retValuesFromAliases.add(aliasedRV);
				}

				// compensate for imprecise alias analysis: keep just those possible return values whose prefix is the source expression used in some assignment within the method

				ExpressionUtils.filterAccessPathsByPrefixes(retValuesFromAliases, mthAssignSources);
						
				curMthRetVals.addAll(retValuesFromAliases);
			}
		}
				
		// we remove local variables that are not method parameters
		// all possible return values propagated through such local variables have been identified through aliasing 
		ExpressionUtils.dropLocalVarsNotParams(curMthRetVals);

		returnValues.addAll(curMthRetVals);
		
		DefUse mthDU = new DefUse(mthIR);

		// add more possibly returned parameters and return values by traversing the def-use chains (transitively, over nested method calls)
		while ( ! valWorklist.isEmpty() )
		{
			int curValNum = valWorklist.remove(0);
			
			if (processedVals.contains(curValNum)) continue;
		
			processedVals.add(curValNum);
			
			SSAInstruction curValDefInsn = mthDU.getDef(curValNum);
		
			if (curValDefInsn == null)
			{
				// method parameter or constant value
				
				if (mthIR.getSymbolTable().isParameter(curValNum))
				{
					// if the "def" location for the returned variable is the start (entry cfg block) then some parameter is returned from the method

					// value numbers for method parameters start at 1
					// parameter indexes start at 0
					returnedParams.add(curValNum - 1);
				}
			}
			
			if (curValDefInsn instanceof SSACheckCastInstruction)
			{
				// typically follows an invoke instruction
				
				SSACheckCastInstruction castInsn = (SSACheckCastInstruction) curValDefInsn;
				
				if ( ! valWorklist.contains(castInsn.getVal()) ) valWorklist.add(castInsn.getVal());	
			}
				
			if (curValDefInsn instanceof SSAInvokeInstruction)
			{
				SSAInvokeInstruction invokeInsn = (SSAInvokeInstruction) curValDefInsn;
				
				// populate summary for the current return variable based on all the possibly called methods
				
				// process callees for this invoke
				for ( CGNode mthNode : staCtx.clGraph.getNodes(mthSig2Ref.get(mthSig)) )
				{
					for ( CGNode tgtNode : staCtx.clGraph.getPossibleTargets(mthNode, invokeInsn.getCallSite()) )
					{
						String tgtMthSig = tgtNode.getMethod().getSignature();
						
						// get current summary for target method (callee)
						MethodSideEffectSummary tgtMthSumm = getSummaryForMethod(tgtMthSig);
						
						// gather all parameters of the callee method that could be returned from it						
						// for each index stored in "tgtMthSumm.retParams" add the SSA value of actual parameter to the valWorklist
						for (Integer paramIdx : tgtMthSumm.returnedParams)
						{
							int actParamValNum = invokeInsn.getUse(paramIdx.intValue());
							
							if ( ! valWorklist.contains(actParamValNum) ) valWorklist.add(actParamValNum);
						}
						
						// in this case, any value possibly returned from the callee method (target) can be returned also from the current one
						for (Expression rv : tgtMthSumm.returnValues)
						{
							returnValues.add(rv);
						}
					}
				}
			}
			
			if (curValDefInsn instanceof SSAPhiInstruction)
			{
				SSAPhiInstruction phiInsn = (SSAPhiInstruction) curValDefInsn;
				
				// insert all source value numbers over all incoming edges ("uses") to the valWorklist
				
				for (int i = 0; i < phiInsn.getNumberOfUses(); i++)
				{
					int sourceValNum = phiInsn.getUse(i);
					
					if ( ! valWorklist.contains(sourceValNum) ) valWorklist.add(sourceValNum);
				}
			}
		}
		
		return new ReturnInfo(returnedParams, returnValues);
	}

	private static MethodSideEffectSummary extractSummaryForNativeExternMethod(String mthSig, IMethod mth, StaticAnalysisContext staCtx) throws Exception
	{
		// for each reference type used in the method signature, record all parameters of this type
		
		Map<String, Set<Integer>> typeName2ParamSlots = new HashMap<String, Set<Integer>>();
		
		int paramSlot = 0;

		for (int paramIndex = 0; paramIndex < mth.getNumberOfParameters(); paramIndex++)
		{
			TypeReference paramTypeRef = mth.getParameterType(paramIndex);
			String paramTypeName = WALAUtils.getTypeNameStr(paramTypeRef);
			
			Set<Integer> slots = typeName2ParamSlots.get(paramTypeName);
			if (slots == null)
			{
				slots = new HashSet<Integer>();
				typeName2ParamSlots.put(paramTypeName, slots);
			}
			
			slots.add(paramSlot);

			if (Utils.isTypeWithSizeTwoWords(paramTypeName)) paramSlot += 2;
			else paramSlot += 1;
		}
		
		// process the recorded information (by dynamic analysis / JDI)
		
		Map<FieldAccessExpression, Set<Expression>> mthUpdatedFields2Values = new HashMap<FieldAccessExpression, Set<Expression>>();
		Map<ArrayAccessExpression, Set<Expression>> mthUpdatedArrays2Values = new HashMap<ArrayAccessExpression, Set<Expression>>();
		ReturnInfo mthRetInfo = new ReturnInfo(new HashSet<Integer>(), new HashSet<Expression>());
		
		// updated fields
		Set<FieldWriteInfo> fwriteInfos = DynamicInputOutputCollector.getFieldUpdatesForMethod(mthSig);
		for (FieldWriteInfo fwInfo : fwriteInfos)
		{
			if (WALAUtils.existsInstanceField(fwInfo.className, fwInfo.fieldName, staCtx.cha))
			{
				// take all method parameters (local variables) of the same type (reference) as possible target objects
			
				Set<Integer> paramTypeSlots = typeName2ParamSlots.get(fwInfo.className);
			
				// it may be a field update on a returned object (that is newly allocated in the method)
				if (paramTypeSlots == null) continue;
				
				for (Integer slot : paramTypeSlots)
				{
					FieldAccessExpression instanceFieldExpr = new FieldAccessExpression(new LocalVarExpression(slot, "local"+slot, fwInfo.className, true), fwInfo.className, fwInfo.fieldName, fwInfo.fieldType, false);
			
					Set<Expression> feNewValues = new HashSet<Expression>();
					feNewValues.add(fwInfo.newValue);
			
					mthUpdatedFields2Values.put(instanceFieldExpr, feNewValues);
				}
			}
			else // static field
			{
				FieldAccessExpression staticFieldExpr = new FieldAccessExpression(new ClassNameExpression(fwInfo.className), fwInfo.className, fwInfo.fieldName, fwInfo.fieldType, true);
			
				Set<Expression> feNewValues = new HashSet<Expression>();
				feNewValues.add(fwInfo.newValue);
			
				mthUpdatedFields2Values.put(staticFieldExpr, feNewValues);
			}
		}
		
		// updated array elements
		Set<ArrayWriteInfo> arwriteInfos = DynamicInputOutputCollector.getArrayElementUpdatesForMethod(mthSig);
		for (ArrayWriteInfo awInfo : arwriteInfos)
		{
			// take all method parameters (local variables) of the same type (reference) as possible target objects
			
			Set<Integer> paramTypeSlots = typeName2ParamSlots.get(awInfo.className);
	
			// it may be an element update on a returned array (that is newly allocated in the method)
			if (paramTypeSlots == null) continue;

			for (Integer slot : paramTypeSlots)
			{
				ArrayAccessExpression arrayExpr = new ArrayAccessExpression(new LocalVarExpression(slot, "local"+slot, awInfo.className, true), awInfo.className, new ConstantExpression(awInfo.elementIndex), awInfo.elementType);
			
				Set<Expression> aeNewValues = new HashSet<Expression>();
				aeNewValues.add(awInfo.newValue);
			
				mthUpdatedArrays2Values.put(arrayExpr, aeNewValues);
			}
		}
		
		// returned value (result)
		Set<CallResultInfo> callrInfos = DynamicInputOutputCollector.getCallResultsForMethod(mthSig);
		for (CallResultInfo crInfo : callrInfos)
		{
			mthRetInfo.values.add(crInfo.returnValue);
		}
		
		// create actual summary object
		MethodSideEffectSummary mthSumm = new MethodSideEffectSummary(mthSig, mthUpdatedFields2Values, mthUpdatedArrays2Values, new HashSet<NewObjectExpression>(), new HashSet<NewArrayExpression>(), mthRetInfo.params, mthRetInfo.values, new HashSet<FieldAccessExpression>(), new HashSet<ArrayAccessExpression>());
		
		return mthSumm;
	}

	private static void expandReturnExpressions(Set<Expression> expressions)
	{
		// values that we substitute for the expanded return expressions
		Set<Expression> substReturnValues = new HashSet<Expression>();
		
		Iterator<Expression> it = expressions.iterator();		
		
		while (it.hasNext())
		{
			Expression expr = it.next();

			if (expr instanceof ReturnValueExpression)
			{
				ReturnValueExpression retExpr = (ReturnValueExpression) expr;

				// use currently known return values from the summary of target callee method

				MethodSideEffectSummary calleeMthSumm = mthSig2Summary.get(retExpr.methodSig);

				// summary not yet available (postpone replacement to some future iteration of the worklist algorithm)
				if (calleeMthSumm == null) continue;

				substReturnValues.addAll(calleeMthSumm.returnValues);

				it.remove();
			}
		}

		expressions.addAll(substReturnValues);
	}
	
	private static void dropLibraryInnerClasses(Set<? extends Expression> expressions)
	{
		Iterator<? extends Expression> it = expressions.iterator();		
		
		while (it.hasNext())
		{
			Expression expr = it.next();

			if (containsLibraryInnerClass(expr)) it.remove();
		}
	}
	
	private static boolean containsLibraryInnerClass(Expression expr)
	{
		// look for inner classes whose parent classes do not belong to the set of "application classes"
			// that means when the given parent class belongs to some library

		if (expr instanceof FieldAccessExpression)
		{
			FieldAccessExpression fieldExpr = (FieldAccessExpression) expr;

			if (Configuration.isLibraryInnerClassName(fieldExpr.className)) return true;
	
			if (Configuration.isLibraryInnerClassName(fieldExpr.fieldType)) return true;

			return containsLibraryInnerClass(fieldExpr.targetObj);
		}

		if (expr instanceof ArrayAccessExpression)
		{
			ArrayAccessExpression arrayExpr = (ArrayAccessExpression) expr;

			if (Configuration.isLibraryInnerClassName(arrayExpr.arrayClassName)) return true;
		
			if (Configuration.isLibraryInnerClassName(arrayExpr.elementType)) return true;
	
			if (containsLibraryInnerClass(arrayExpr.targetArrayObj)) return true;
	
			if (containsLibraryInnerClass(arrayExpr.elementIndex)) return true;
		}
		
		if (expr instanceof NewObjectExpression)
		{
			NewObjectExpression newObjExpr = (NewObjectExpression) expr;
		
			if (Configuration.isLibraryInnerClassName(newObjExpr.className.getAsString())) return true;
		}
		
		if (expr instanceof NewArrayExpression)
		{
			NewArrayExpression newArrayExpr = (NewArrayExpression) expr;
		
			if (Configuration.isLibraryInnerClassName(newArrayExpr.arrayClassName.getAsString())) return true;
		}

		if (expr instanceof ClassNameExpression)
		{
			ClassNameExpression clsNameExpr = (ClassNameExpression) expr;
		
			if (Configuration.isLibraryInnerClassName(clsNameExpr.className)) return true;
		}
		
		if (expr instanceof LocalVarExpression)
		{
			LocalVarExpression lvExpr = (LocalVarExpression) expr;
		
			if (Configuration.isLibraryInnerClassName(lvExpr.varType)) return true;
		}
	
		if (expr instanceof ArithmeticExpression)
		{
			ArithmeticExpression arithmExpr = (ArithmeticExpression) expr;

			if (containsLibraryInnerClass(arithmExpr.value1)) return true;
	
			if (containsLibraryInnerClass(arithmExpr.value2)) return true;
		}

		return false;
	}


	static class ReturnInfo
	{	
		// parameters that may be returned from the method
		// index for every such parameter
		public Set<Integer> params;
	
		// returned expressions
		// provides also information saying whether the returned value may be a new object (allocated inside the method)
		public Set<Expression> values;
		
		public ReturnInfo(Set<Integer> ps, Set<Expression> vs)
		{
			this.params = ps;
			this.values = vs;
		}
		
		public boolean equals(Object obj)
		{
			if (obj == null) return false;
			
			if ( ! (obj instanceof ReturnInfo) ) return false;
	
			ReturnInfo other = (ReturnInfo) obj;
	
			if ( ! this.params.equals(other.params) ) return false;
			if ( ! this.values.equals(other.values) ) return false;
			
			return true;		
		}
		
		public int hashCode()
		{
			int hc = 0;
			
			hc = hc * 31 + params.hashCode();
			hc = hc * 31 + values.hashCode();
			
			return hc;	
		}
	}
}
