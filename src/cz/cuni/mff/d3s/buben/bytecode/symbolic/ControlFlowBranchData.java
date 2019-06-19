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


public class ControlFlowBranchData
{
	// default value is -1 (when the resume location is not yet known/available, e.g. during the first phase of branch span)
	public int resumeInsnIndex;

	public int curInsnIndex;
	public int nextInsnIndex;

	// every branch has possibly several variants (distinct copies) of the expression stack that may appear at specific points during execution of the respective branch (at specific nodes in the CFG of a given procedure/method)

	// several full variants of an expression stack
	public List<LinkedList<Expression>> fullExprStacks;

	// position in the list of stacks
	public int curStackNum;


	public ControlFlowBranchData()
	{
		resumeInsnIndex = -1;

		curInsnIndex = -1;
		nextInsnIndex = -1;

		fullExprStacks = new ArrayList<LinkedList<Expression>>();

		curStackNum = -1;
	}
}
