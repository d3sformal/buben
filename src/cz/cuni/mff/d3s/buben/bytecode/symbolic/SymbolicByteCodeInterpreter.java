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

import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;
import java.util.List;

import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.shrikeBT.*;

import cz.cuni.mff.d3s.buben.Configuration;
import cz.cuni.mff.d3s.buben.Utils;
import cz.cuni.mff.d3s.buben.StaticAnalysisContext;


public class SymbolicByteCodeInterpreter
{
	public static void processReachableMethods(StaticAnalysisContext staCtx, ExecutionVisitor execVisitor, List<String> methodSigPrefixes) throws Exception
	{
		if (Configuration.DEBUG)
		{
			System.out.println("symbolic bytecode analysis (interpretation): start");
		}
			
		InterpretationContext iptCtx = new InterpretationContext();
		
		// method signatures
		Set<String> processedMethods = new HashSet<String>();
		
		// process methods reachable in the call graph
		for (Iterator<CGNode> cgnIt = staCtx.clGraph.iterator(); cgnIt.hasNext(); )
		{
			IMethod mth = cgnIt.next().getMethod();

			// fake root method
			if ( ! (mth instanceof IBytecodeMethod) ) continue;

			IBytecodeMethod bcMth = (IBytecodeMethod) mth;
		
			// we skip native methods and abstract methods
			if (bcMth.isNative() || bcMth.isAbstract()) continue;
			
			String mthSig = bcMth.getSignature();
	
			// skip methods whose prefix is not in the set (to save memory)
			if ( ! Utils.isMethodSignatureWithPrefix(mthSig, methodSigPrefixes) ) continue;

			if (processedMethods.contains(mthSig)) continue;
			processedMethods.add(mthSig);
			
			iptCtx.reset();
			
			if (Configuration.DEBUG)
			{
				System.out.println("current method signature: " + mthSig);
			}
			
			// loop through all Shrike bytecode instructions and process each relevant one (some are ignored)
			ExecutionSimulator.processMethod(bcMth, iptCtx, execVisitor, staCtx);
		}

		if (Configuration.DEBUG)
		{
			System.out.println("symbolic bytecode analysis (interpretation): finish");
			System.out.println("");
		}		
	}	
}
