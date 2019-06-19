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
package cz.cuni.mff.d3s.buben.bytecode.symbolic;

import java.util.List;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.TreeMap;

import java.io.PrintStream;


public class InterpretationContext
{
	// mapping from bytecode instruction indexes to the lists of control-flow branches that resume (again becomes active) at the given location
	// control-flow branch resumes at a bytecode position (index) that is the target of a forward jump
	// branch can be suspended at the position of a conditional branch instruction or a goto instruction
	TreeMap<Integer, List<ControlFlowBranchData>> insnIndex2ActiveBranches;

	// there can always be at most one active branch
	private ControlFlowBranchData activeBranch = null;


	public void reset()
	{
		insnIndex2ActiveBranches = new TreeMap<Integer, List<ControlFlowBranchData>>();

		ControlFlowBranchData initialBranch = new ControlFlowBranchData();

		initialBranch.resumeInsnIndex = 0;

		initialBranch.curInsnIndex = 0;
		initialBranch.nextInsnIndex = 1;

		initialBranch.fullExprStacks.add(new LinkedList<Expression>());

		initialBranch.curStackNum = 0;

		// we have to add even the initial branch so that it becomes active right at the start of the loop over bytecode instructions
		addFutureActiveBranch(0, initialBranch);

		// we have to set the initial branch as active
		// there must exist an active branch when analysis of method starts
		activeBranch = initialBranch;
	}

	public int getInsnIndex()
	{
		return activeBranch.curInsnIndex;
	}

	public int getNextInsnIndex()
	{
		return activeBranch.nextInsnIndex;
	}

	public void setNextInsnIndex(int insnIdx)
	{
		activeBranch.nextInsnIndex = insnIdx;
	}

	public boolean isCurrentBranchInsnProcessed()
	{
		if (activeBranch.curStackNum >= activeBranch.fullExprStacks.size()) return true;

		return false;
	}

	public void moveToNextExprStack()
	{
		activeBranch.curStackNum++;
	}

	public void moveToNextInsn()
	{
		activeBranch.curInsnIndex = activeBranch.nextInsnIndex;
		activeBranch.nextInsnIndex = activeBranch.curInsnIndex + 1;
		activeBranch.curStackNum = 0;
	}

	public void printCurrentBranchInfo(PrintStream ps)
	{
		ps.println("active branch:");
		ps.println("  resume insn index = " + activeBranch.resumeInsnIndex);
		ps.println("  cur insn index = " + activeBranch.curInsnIndex);

		for (int i = 0; i < activeBranch.fullExprStacks.size(); i++)
		{
			ps.println("  expr stack " + i + " : " + activeBranch.fullExprStacks.get(i));
		}
		
		ps.println("  cur stack num = " + activeBranch.curStackNum);
	}

	public void addExprToStack(Expression expr)
	{
		LinkedList<Expression> exprStack = getCurrentExprStack();

		exprStack.addFirst(expr);
	}

	public void insertExprToStack(Expression expr, int depth)
	{
		LinkedList<Expression> exprStack = getCurrentExprStack();

		exprStack.add(depth, expr);
	}

	public Expression removeExprFromStack()
	{
		LinkedList<Expression> exprStack = getCurrentExprStack();

		return exprStack.removeFirst();
	}

	public Expression getExprFromStack()
	{
		LinkedList<Expression> exprStack = getCurrentExprStack();

		return exprStack.getFirst();
	}

	public Expression getExprFromStack(int offset)
	{
		LinkedList<Expression> exprStack = getCurrentExprStack();

		return exprStack.get(offset);
	}

	public void printExprStack(String prefix)
	{
		LinkedList<Expression> exprStack = getCurrentExprStack();

		System.out.println(prefix);
		
		for (int i = 0; i < exprStack.size(); i++)
		{
			Expression expr = exprStack.get(i);

			if (i == 0) System.out.println("top:    " + expr);
			else if (i == exprStack.size() - 1) System.out.println("bot:    " + expr);
			else System.out.println("        " + expr);
		}
	}

	private LinkedList<Expression> getCurrentExprStack()
	{
		return activeBranch.fullExprStacks.get(activeBranch.curStackNum);
	}
	
	public void mergeIdenticalExprStacks()
	{
		List<LinkedList<Expression>> stacks = activeBranch.fullExprStacks;

		int pos = 0;

		while (pos < stacks.size())
		{
			int i = pos + 1;
			while (i < stacks.size())
			{
				if (stacks.get(pos).equals(stacks.get(i))) stacks.remove(i);
				else i++;
			}

			pos++;
		}

		if (activeBranch.curStackNum >= stacks.size()) activeBranch.curStackNum = stacks.size();
	}

	private void addFutureActiveBranch(int resumeInsnIndex, ControlFlowBranchData branchData)
	{
		List<ControlFlowBranchData> branches = insnIndex2ActiveBranches.get(resumeInsnIndex);

		if (branches == null)
		{
			branches = new ArrayList<ControlFlowBranchData>();
			insnIndex2ActiveBranches.put(resumeInsnIndex, branches);
		}

		branches.add(branchData);
	}

	public void startNewControlFlowBranch(int jumpTarget, boolean suspendCurrentBranch)
	{
		// current branch must be suspended upon execution of the GOTO bytecode instruction
		// the next instruction should be a target of some jump instruction executed before

		if ( ! suspendCurrentBranch )
		{
			// create new branch and make it active from the next instruction (when curStackNum is 0)
	
			ControlFlowBranchData newBranch = new ControlFlowBranchData();

			newBranch.resumeInsnIndex = activeBranch.nextInsnIndex;

			// fresh copy of the full expression stack from the currently active branch
			// we need to make "deep" copies here because lists are mutable

			for (LinkedList<Expression> stack : activeBranch.fullExprStacks)
			{
				LinkedList<Expression> stackCopy = new LinkedList<Expression>(stack);

				newBranch.fullExprStacks.add(stackCopy);
			}

			newBranch.curStackNum = 0;

			addFutureActiveBranch(newBranch.resumeInsnIndex, newBranch);
		}

		// schedule the currently active branch to resume at the given jump target

		addFutureActiveBranch(jumpTarget, activeBranch);
	}

	public void prepareNewControlFlowBranch(int startInsnIndex)
	{
		// start index means the location where the new branch becomes active

		ControlFlowBranchData newBranch = new ControlFlowBranchData();

		newBranch.resumeInsnIndex = startInsnIndex;

		// fresh copy of the full expression stack from the currently active branch
		// we need to make "deep" copies here because lists are mutable

		for (LinkedList<Expression> stack : activeBranch.fullExprStacks)
		{
			LinkedList<Expression> stackCopy = new LinkedList<Expression>(stack);

			newBranch.fullExprStacks.add(stackCopy);
		}

		newBranch.curStackNum = 0;

		addFutureActiveBranch(newBranch.resumeInsnIndex, newBranch);
	}

	public void setActiveControlFlowBranch()
	{
		// load all active branches for the current instruction and merge them

		List<ControlFlowBranchData> scheduledBranches = insnIndex2ActiveBranches.remove(activeBranch.curInsnIndex);

		// nothing will happen (i.e., active branches will not change)
		if ((scheduledBranches == null) || scheduledBranches.isEmpty()) return;

		// if there are multiple branches scheduled to become active at the current bytecode position then we merge them all

		ControlFlowBranchData newActiveBranch = new ControlFlowBranchData();

		newActiveBranch.resumeInsnIndex = -1;

		// set indexes for the current instruction and next instruction properly
		
		newActiveBranch.curInsnIndex = activeBranch.curInsnIndex;
		newActiveBranch.nextInsnIndex = newActiveBranch.curInsnIndex + 1;

		// when multiple control-flow branches are merged/joined, we just make a union of their lists of expression stacks
		// if multiple variants of an expression stack in the new active branch have the same content, they are merged in order to avoid blow-up

		for (ControlFlowBranchData cfbd : scheduledBranches)
		{
			for (LinkedList<Expression> stack : cfbd.fullExprStacks)
			{
				// ignore empty stacks (if there are some non-empty)
				if (stack.isEmpty()) continue;

				if ( ! newActiveBranch.fullExprStacks.contains(stack) )
				{
					// we need to make "deep" copies here because lists are mutable

					LinkedList<Expression> stackCopy = new LinkedList<Expression>(stack);
						
					newActiveBranch.fullExprStacks.add(stackCopy);
				}
			}
		}

		// make sure at least one stack exists
		if (newActiveBranch.fullExprStacks.isEmpty())
		{
			newActiveBranch.fullExprStacks.add(new LinkedList<Expression>());
		}

		newActiveBranch.curStackNum = 0;

		// set the new active branch
		activeBranch = newActiveBranch;
	}

}

