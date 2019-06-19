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
import java.util.Iterator;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAMonitorInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.ssa.analysis.ExplodedControlFlowGraph;
import com.ibm.wala.dataflow.graph.BitVectorSolver;
import com.ibm.wala.dataflow.graph.BitVectorFramework;
import com.ibm.wala.dataflow.graph.IKilldallFramework;
import com.ibm.wala.dataflow.graph.AbstractMeetOperator;
import com.ibm.wala.dataflow.graph.ITransferFunctionProvider;
import com.ibm.wala.dataflow.graph.BitVectorKillGen;
import com.ibm.wala.dataflow.graph.BitVectorUnion;
import com.ibm.wala.dataflow.graph.BitVectorIdentity; 
import com.ibm.wala.fixpoint.BitVectorVariable;
import com.ibm.wala.fixpoint.UnaryOperator;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.intset.BitVector;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.OrdinalSetMapping;
import com.ibm.wala.util.intset.MutableMapping;

import cz.cuni.mff.d3s.buben.StaticAnalysisContext;
import cz.cuni.mff.d3s.buben.Utils;
import cz.cuni.mff.d3s.buben.common.ProgramPoint;
import cz.cuni.mff.d3s.buben.wala.WALAUtils;


public class SynchronizedAccessAnalysis
{
	// for each method (signature), a set of program points inside a synchronized block over some lock
	protected static Map<String, Set<ProgramPoint>> mth2LockedProgPoints;

	static
	{
		mth2LockedProgPoints = new HashMap<String, Set<ProgramPoint>>();
	}

	
	public static void analyzeProgram(StaticAnalysisContext staCtx, List<String> methodSigPrefixes) throws Exception
	{
		// for each method M (whose prefix is in the set) find each program point P in M such that (1) a lock over some heap object (arbitrary) is held just before P and (2) the lock is both acquired and released in M
		
		// method signatures
		Set<String> processedMethods = new HashSet<String>();
		
		// analyze methods reachable in the call graph
		for (Iterator<CGNode> cgnIt = staCtx.clGraph.iterator(); cgnIt.hasNext(); )
		{
			CGNode cgn = cgnIt.next();
			
			IMethod mth = cgn.getMethod();
		
			// we skip native methods and abstract methods
			if (mth.isNative() || mth.isAbstract()) continue;
			
			String mthSig = mth.getSignature();
	
			// skip methods whose prefix is not in the set (to save memory)
			if ( ! Utils.isMethodSignatureWithPrefix(mthSig, methodSigPrefixes) ) continue;

			if (processedMethods.contains(mthSig)) continue;
			processedMethods.add(mthSig);
			
			IR mthIR = cgn.getIR();
			
			// create the intra-procedural control flow graph of the given method
			ExplodedControlFlowGraph mthCFG = ExplodedControlFlowGraph.make(mthIR);
				
			MethodLockedPoints mlp = new MethodLockedPoints(mth, mthCFG, mthIR, staCtx.cha);
			BitVectorSolver<IExplodedBasicBlock> solverMLP = mlp.analyze();
			
			// collect analysis results: all program points with some bit representing a lock set to '1'
			
			Set<ProgramPoint> lockedProgPoints = new HashSet<ProgramPoint>();
			
			for (IExplodedBasicBlock ebb : mthCFG) 
			{
				int insnPos = WALAUtils.getInsnBytecodePos(mth, ebb.getFirstInstructionIndex());
			
				ProgramPoint pp = new ProgramPoint(mthSig, ebb.getFirstInstructionIndex(), insnPos);
			
				IntSet out = solverMLP.getOut(ebb).getValue();
				if (out != null)
				{
					// some bits are set for this program point (i.e., some monitor is locked there)
					if ( ! out.isEmpty() )
					{
						lockedProgPoints.add(pp);
					}
				}
			}
			
			mth2LockedProgPoints.put(mthSig, lockedProgPoints);
		}
	}
	
	public static Set<ProgramPoint> getLockedPointsForMethod(String mthSig)
	{
		return mth2LockedProgPoints.get(mthSig);
	}

	public static void printLockedProgPoints()
	{
		System.out.println("LOCKED PROGRAM POINTS");
		System.out.println("=====================");
		
		// sort the set of method signatures
		Set<String> methodSigs = new TreeSet<String>();
		methodSigs.addAll(mth2LockedProgPoints.keySet());
		
		for (String mthSig : methodSigs)
		{
			if (Utils.isJavaStandardLibraryMethod(mthSig)) continue;
			
			System.out.println(mthSig);
			
			Set<ProgramPoint> lockedPPs = mth2LockedProgPoints.get(mthSig);
			
			for (ProgramPoint pp : lockedPPs) 
			{
				System.out.println("\t" + pp.toString());
			}
		}
		
		System.out.println("");
	}
	
	
	static class MethodLockedPoints
	{
		private IMethod mth;
		
		private Graph<IExplodedBasicBlock> mthCFG;
		
		private IR mthIR;
		
		private IClassHierarchy cha;

		private DefUse mthDU;
		
		// SSA value numbers of lock variables (monitors)
		protected OrdinalSetMapping<Integer> mthLockVarsNumbering;
		
		
		public MethodLockedPoints(IMethod mth, Graph<IExplodedBasicBlock> mthCFG, IR mthIR, IClassHierarchy cha)
		{
			this.mth = mth;
			this.mthCFG = mthCFG;
			this.mthIR = mthIR;			
			this.cha = cha;
			
			this.mthDU = new DefUse(mthIR);
			
			createMethodLockVariablesNumbering();
		}
	
		private void createMethodLockVariablesNumbering()
		{
			mthLockVarsNumbering = new MutableMapping<Integer>(new Integer[1]);
			
			// we use the index 1 for "this" (in the case of synchronized instance methods) or the class (in the case of synchronized static methods)
			mthLockVarsNumbering.add(1);
				
			SSAInstruction[] instructions = mthIR.getInstructions();
			for (int i = 0; i < instructions.length; i++) 
			{
				SSAInstruction insn = instructions[i];
					
				if (insn instanceof SSAMonitorInstruction)
				{
					SSAMonitorInstruction monInsn = (SSAMonitorInstruction) insn;					
					
					// filter out "this" in the case of instance methods
					// SSA value number for "this" is 1
					if ( mth.isStatic() || (monInsn.getRef() != 1) )
					{
						// we increment the SSA value by 2 to avoid collision with the receiver object ("this") or the class object (both in the case of synchronized methods)
						mthLockVarsNumbering.add(monInsn.getRef() + 2);
					}
				}
			} 
		}
		
		class TransferFunctionsMLP implements ITransferFunctionProvider<IExplodedBasicBlock, BitVectorVariable> 
		{
			public AbstractMeetOperator<BitVectorVariable> getMeetOperator() 
			{
				// here it is not particularly important which operator is used because, at every meet point, the corresponding elements in all bitvectors should have the same value (due to properly nested synchronized blocks)
				// synchronized blocks in Java programs must be properly nested inside control structures (and vice versa)
				return BitVectorUnion.instance();
			}
	
			public UnaryOperator<BitVectorVariable> getEdgeTransferFunction(IExplodedBasicBlock src, IExplodedBasicBlock dst) 
			{
				return BitVectorIdentity.instance();
			}
			
			public UnaryOperator<BitVectorVariable> getNodeTransferFunction(IExplodedBasicBlock ebb) 
			{
				SSAInstruction insn = ebb.getInstruction();
				
				if (insn instanceof SSAMonitorInstruction)
				{
					SSAMonitorInstruction monInsn = (SSAMonitorInstruction) insn;
					
					BitVector gen = new BitVector();
					BitVector kill = new BitVector();
					
					// default corresponds to lock variable "this"
					int lvarIndex = 1; 

					// SSA value number for "this" is 1
					if ( mth.isStatic() || (monInsn.getRef() != 1) )
					{
						// see above for explanation of the "+2" increment operation 
						lvarIndex = mthLockVarsNumbering.getMappedIndex(monInsn.getRef() + 2);						
					}
					
					if (monInsn.isMonitorEnter()) gen.set(lvarIndex);
					else kill.set(lvarIndex);
					
					return new BitVectorKillGen(kill, gen);
				}
				else
				{
					// identity function for all other instructions
					return BitVectorIdentity.instance();
				}
			}
			
			public boolean hasEdgeTransferFunctions() 
			{
				return true;
			}
			
			public boolean hasNodeTransferFunctions() 
			{
				return true;
			}
		}
		
		class BitVectorSolverMLP extends BitVectorSolver<IExplodedBasicBlock>
		{
			public BitVectorSolverMLP(IKilldallFramework<IExplodedBasicBlock, BitVectorVariable> problem) 
			{
				super(problem);
			}
			
			@Override
			protected BitVectorVariable makeNodeVariable(IExplodedBasicBlock n, boolean IN)
			{
				BitVectorVariable V = new BitVectorVariable();

				try
				{
					// in the case of synchronized methods, the initial data-flow fact has the value "1" for the first bit (meaning that all program points are in a locked region)
					if (mth.isSynchronized())
					{
						V.set(1);
					}
				}
				catch (Exception ex) { ex.printStackTrace(); }
				
				return V;
			}
			
			@Override
			protected BitVectorVariable makeEdgeVariable(IExplodedBasicBlock src, IExplodedBasicBlock dst) 
			{
				BitVectorVariable V = new BitVectorVariable();

				try
				{
					// in the case of synchronized methods, the initial data-flow fact has the value "1" for the first bit (meaning that all program points are in a locked region)
					if (mth.isSynchronized())
					{
						V.set(1);
					}
				}
				catch (Exception ex) { ex.printStackTrace(); }
				
				return V;
			}
		}
	 		
		public BitVectorSolver<IExplodedBasicBlock> analyze() 
		{
			BitVectorFramework<IExplodedBasicBlock, Integer> framework = new BitVectorFramework<IExplodedBasicBlock, Integer>(mthCFG, new TransferFunctionsMLP(), mthLockVarsNumbering);
			
			BitVectorSolver<IExplodedBasicBlock> solver = new BitVectorSolverMLP(framework);
			
			try
			{
				solver.solve(null);
			}
			catch (Exception e) {}
			
			return solver;
		} 
	}
}
