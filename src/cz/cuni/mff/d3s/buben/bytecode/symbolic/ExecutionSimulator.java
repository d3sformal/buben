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
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeBT.*;
import com.ibm.wala.types.TypeReference;

import cz.cuni.mff.d3s.buben.Configuration;
import cz.cuni.mff.d3s.buben.Utils;
import cz.cuni.mff.d3s.buben.StaticAnalysisContext;
import cz.cuni.mff.d3s.buben.common.ProgramPoint;
import cz.cuni.mff.d3s.buben.common.ClassName;
import cz.cuni.mff.d3s.buben.common.AllocationSite;
import cz.cuni.mff.d3s.buben.wala.WALAUtils;


public class ExecutionSimulator
{
	public static void processMethod(IBytecodeMethod mth, InterpretationContext iptCtx, ExecutionVisitor execVisitor, StaticAnalysisContext staCtx) throws Exception
	{
		String mthSig = mth.getSignature();

		IInstruction[] mthInstructions = mth.getInstructions();

		// get positions (starting indexes) of exception handlers
		Set<Integer> exceptionHandlersIndexes = new HashSet<Integer>();
		ExceptionHandler[][] handlers = mth.getHandlers();
		for (int i = 0; i < handlers.length; i++)
		{
			for (int j = 0; j < handlers[i].length; j++)
			{
				int ehIndex = handlers[i][j].getHandler();
				exceptionHandlersIndexes.add(ehIndex);
			}
		}

		// types for local variable names
		Map<String, String> localVars2TypeNames = new HashMap<String, String>();
		
		// save types of method parameters (including "this")
		
		int paramSlot = 0;
		
		for (int paramIndex = 0; paramIndex < mth.getNumberOfParameters(); paramIndex++)
		{
			TypeReference paramTypeRef = mth.getParameterType(paramIndex);
			String paramTypeName = WALAUtils.getTypeNameStr(paramTypeRef);
		
			localVars2TypeNames.put("local"+paramSlot, paramTypeName);

			if (Utils.isTypeWithSizeTwoWords(paramTypeName)) paramSlot += 2;
			else paramSlot += 1;
		}

		// loop through all Shrike bytecode instructions within every control-flow branch
			// process each relevant instruction (some are ignored)
		while (iptCtx.getInsnIndex() < mthInstructions.length)
		{
			// load all active branches for the current instruction and merge them
			iptCtx.setActiveControlFlowBranch();

			// just for caching to avoid method calls
			int insnIndex = iptCtx.getInsnIndex();

			Instruction insn = (Instruction) mthInstructions[insnIndex];

			short insnOpcode = insn.getOpcode();
			
			int insnPos = WALAUtils.getInsnBytecodePos(mth, insnIndex);
			
			ProgramPoint insnPP = new ProgramPoint(mthSig, insnIndex, insnPos);

			if (Configuration.DEBUG)
			{
				System.out.println("insn: " + insn);
				iptCtx.printExprStack("before");
			}
			
			// just for caching
			int nextInsnIndex = iptCtx.getNextInsnIndex();

			if (insn instanceof ArrayLengthInstruction)
			{
				// remove "arrayObj" from the top of the stack and put there "arrayObj.length"
				
				Expression arrayObj = iptCtx.removeExprFromStack();
				
				// modeling query for array length by a read field access
				
				String arrayObjType = ExpressionUtils.getArrayExprTypeName(arrayObj);
				
				iptCtx.addExprToStack(new FieldAccessExpression(arrayObj, arrayObjType, "length", "int", false));				
				
				execVisitor.visitGetInsn(insnPP, arrayObj, arrayObjType, "length", "int", false);
			}
			
			if (insn instanceof ArrayLoadInstruction)
			{
				Expression indexExpr = iptCtx.removeExprFromStack();
				
				Expression arrayObj = iptCtx.removeExprFromStack();
				
				String arrayClassName = ExpressionUtils.getArrayExprTypeName(arrayObj);
				
				String elementType = Utils.getElementTypeFromArrayClassName(arrayClassName);
				
				// add "arrayObj[index]" to the stack
				iptCtx.addExprToStack(new ArrayAccessExpression(arrayObj, arrayClassName, indexExpr, elementType));
				
				execVisitor.visitArrayLoadInsn(insnPP, arrayObj, arrayClassName, indexExpr, elementType);
			}
			
			if (insn instanceof ArrayStoreInstruction)
			{
				Expression newValue = iptCtx.removeExprFromStack();
				
				Expression indexExpr = iptCtx.removeExprFromStack();
					
				Expression arrayObj = iptCtx.removeExprFromStack();
				
				String arrayClassName = ExpressionUtils.getArrayExprTypeName(arrayObj);

				String elementType = Utils.getElementTypeFromArrayClassName(arrayClassName);
				
				execVisitor.visitArrayStoreInsn(insnPP, arrayObj, arrayClassName, indexExpr, elementType, newValue);
			}
			
			if (insn instanceof BinaryOpInstruction)
			{
				BinaryOpInstruction binOpInsn = (BinaryOpInstruction) insn;
				
				Expression value2 = iptCtx.removeExprFromStack();
				
				Expression value1 = iptCtx.removeExprFromStack();
				
				// add whole expression to the stack
				
				String arithmOp = "";
				switch (binOpInsn.getOperator())
				{
					case ADD:
						arithmOp = "+";
						break;
					case SUB:
						arithmOp = "-";
						break;
					case MUL:
						arithmOp = "*";
						break;
					case DIV:
						arithmOp = "/";
						break;
					case REM:
						arithmOp = "%";
						break;
					case AND:
						arithmOp = "&";
						break;
					case OR:
						arithmOp = "|";
						break;
					case XOR:
						arithmOp = "^";
						break;
					default:
						arithmOp = "";
				}
				
				iptCtx.addExprToStack(new ArithmeticExpression(arithmOp, value1, value2));
			}
			
			if (insn instanceof ComparisonInstruction)
			{
				Expression value2 = iptCtx.removeExprFromStack();
				
				Expression value1 = iptCtx.removeExprFromStack();
				
				// we model this using subtraction
				iptCtx.addExprToStack(new ArithmeticExpression("-", value1, value2));
			}
			
			if (insn instanceof ConditionalBranchInstruction)
			{
				ConditionalBranchInstruction condbrInsn = (ConditionalBranchInstruction) insn;
				
				Expression value2 = null;
				if ((insnOpcode >= 153) && (insnOpcode <= 158)) value2 = new ConstantExpression(new Integer(0));
				else value2 = iptCtx.removeExprFromStack();
								
				Expression value1 = iptCtx.removeExprFromStack();
				
				// create conditional expression
				
				String relOp = "";
				switch (condbrInsn.getOperator()) 
				{
					case EQ:
						relOp = "=";
						break;
					case GE:
						relOp = ">=";
						break;
					case GT:
						relOp = ">";
						break;
					case LE:
						relOp = "<=";
						break;
					case LT:
						relOp = "<";
						break;
					case NE:
						relOp = "!=";
						break;
					default:
						relOp = "";
				}

				int condJumpTarget = condbrInsn.getTarget();

				// backjumps do not correspond to new branches
				// some preceding goto instruction represents the start of a new branch in this case
				if (condJumpTarget > insnIndex)
				{
					// create new branch and make it active from the next instruction
					// schedule the other branch at the instruction marked by jump target
					iptCtx.startNewControlFlowBranch(condJumpTarget, false);
				}
			}
			
			if (insn instanceof ConstantInstruction)
			{
				ConstantInstruction constInsn = (ConstantInstruction) insn;
				
				// add string representation of the constant to the stack
				
				if (constInsn.getValue() == null)
				{
					iptCtx.addExprToStack(SpecialExpression.NULL);
				}
				else if (constInsn.getValue() instanceof ConstantInstruction.ClassToken)
				{
					ConstantInstruction.ClassToken clsToken = (ConstantInstruction.ClassToken) constInsn.getValue();
					
					String constValueClsName = Utils.getPlainTypeName(clsToken.getTypeName());

					iptCtx.addExprToStack(new ConstantExpression(new ClassNameExpression(constValueClsName)));
				}
				else if (constInsn.getValue() instanceof String)
				{
					String constValueStr = (String) constInsn.getValue();
					
					iptCtx.addExprToStack(new ConstantExpression(constValueStr));
				}
				else // numeric constant
				{
					Object constValueNum = constInsn.getValue();
					
					iptCtx.addExprToStack(new ConstantExpression(constValueNum));
				}
			}
			
			if (insn instanceof DupInstruction)
			{
				boolean skip = false;
				
				// this takes care of the way javac compiles synchronized blocks
				// we assume that all relevant instructions belong to the same control-flow branch
				if ((insnIndex + 2 < mthInstructions.length) && (mthInstructions[insnIndex + 2] instanceof MonitorInstruction)) skip = true;
				
				if ( ! skip )
				{
					DupInstruction dupInsn = (DupInstruction) insn;

					// we assume that relevant stack elements are of type 1 (int, reference)
						// in the current version, we ignore values of type 2 (long, double)

					// dup, dup_x1, dup_x2
					if (dupInsn.getSize() == 1)
					{
						Expression dupValue = iptCtx.getExprFromStack();

						// make duplicate at the proper depth

						if (dupInsn.getDelta() == 0)
						{
							iptCtx.addExprToStack(dupValue);
						}
						else if (dupInsn.getDelta() == 1)
						{
							iptCtx.insertExprToStack(dupValue, 2);
						}
						else if (dupInsn.getDelta() == 2)
						{
							iptCtx.insertExprToStack(dupValue, 3);
						}
					}
					
					// dup2, dup2_x1, dup2_x2
					if (dupInsn.getSize() == 2)
					{
						// duplicate two values at the proper depth (top of the stack for "dup2")

						Expression dupValue1 = iptCtx.getExprFromStack(0);
						Expression dupValue2 = iptCtx.getExprFromStack(1);

						if (dupInsn.getDelta() == 0)
						{
							iptCtx.addExprToStack(dupValue2);
							iptCtx.addExprToStack(dupValue1);
						}
						else if (dupInsn.getDelta() == 1)
						{
							iptCtx.insertExprToStack(dupValue2, 3);
							iptCtx.insertExprToStack(dupValue1, 3);
						}
						else if (dupInsn.getDelta() == 2)
						{
							iptCtx.insertExprToStack(dupValue2, 4);
							iptCtx.insertExprToStack(dupValue1, 4);
						}
					}
				}
			}
			
			if (insn instanceof GetInstruction)
			{
				GetInstruction getInsn = (GetInstruction) insn;
				
				String classNameStr = Utils.getPlainClassName(getInsn.getClassType());
				
				String fieldName = getInsn.getFieldName();
				
				String fieldTypeStr = Utils.getPlainTypeName(getInsn.getFieldType());
				
				Expression obj = null;
				
				if (getInsn.isStatic())
				{
					obj = new ClassNameExpression(classNameStr);
				}
				else
				{
					obj = iptCtx.removeExprFromStack();
				}
				
				// put "obj.fieldname" to the stack
				iptCtx.addExprToStack(new FieldAccessExpression(obj, classNameStr, fieldName, fieldTypeStr, getInsn.isStatic()));
				
				execVisitor.visitGetInsn(insnPP, obj, classNameStr, fieldName, fieldTypeStr, getInsn.isStatic());
			}
			
			if (insn instanceof GotoInstruction)
			{
				GotoInstruction gotoInsn = (GotoInstruction) insn;
				
				int prevInsnIndex = insnIndex - 1;
				Instruction prevInsn = null;
				short prevInsnOpcode = -1;
				
				// "goto" may be the first bytecode instruction
				if (prevInsnIndex >= 0)
				{
					prevInsn = (Instruction) mthInstructions[prevInsnIndex];
					prevInsnOpcode = prevInsn.getOpcode();
				}

				int gotoJumpTarget = gotoInsn.getLabel();

				if ((prevInsnIndex >= 0) && (prevInsnOpcode == com.ibm.wala.shrikeBT.Constants.OP_monitorexit) && (gotoJumpTarget > insnIndex))
				{
					// skip monitor exit for exceptions thrown inside the synchronized block
					// we do not have to consider backjumps here
					iptCtx.setNextInsnIndex(gotoJumpTarget);
				}
				else
				{
					// all other cases that are forward jumps
					// backjumps do not correspond to new branches
					
					if (gotoJumpTarget > insnIndex)
					{
						// create new branch and make it active from the next instruction
						// suspend the current branch (so that it will not continue by the next instruction index)
						iptCtx.startNewControlFlowBranch(gotoJumpTarget, true);
					}
				}
			}
			
			if (insn instanceof InstanceofInstruction)
			{
				Expression obj = iptCtx.removeExprFromStack();
				
				// we model this with a constant value 1 (true) that represents a successful comparison
				// we ignore the possibility of a negative result (0, false)
				iptCtx.addExprToStack(new ConstantExpression(new Integer(1)));
			}
			
			if (insn instanceof InvokeInstruction)
			{
				InvokeInstruction invokeInsn = (InvokeInstruction) insn;

				String ownerClassNameStr = Utils.getPlainClassName(invokeInsn.getClassType());
				
				String tgtMethodName = invokeInsn.getMethodName();
		
				String tgtMethodSig = ownerClassNameStr + "." + tgtMethodName + invokeInsn.getMethodSignature();

				boolean isStaticCall = (invokeInsn.getInvocationCode() == IInvokeInstruction.Dispatch.STATIC);
				
				int tgtMethodParamCount = 0;
				if (isStaticCall) tgtMethodParamCount = invokeInsn.getPoppedCount();
				else tgtMethodParamCount = invokeInsn.getPoppedCount() - 1; 
				
				List<Expression> tgtMethodParams = new ArrayList<Expression>();
				
				// record and remove actual parameters
				for (int i = 1; i <= tgtMethodParamCount; i++)
				{
					Expression param = iptCtx.removeExprFromStack();
					tgtMethodParams.add(0, param);
				}
				
				Expression targetObj = null;
				if (isStaticCall) targetObj = new ClassNameExpression(ownerClassNameStr);
				else targetObj = iptCtx.removeExprFromStack();
				
				if (WALAUtils.hasMethodReturnValue(tgtMethodSig, ownerClassNameStr, staCtx.cha))
				{
					// put the symbol "retval <method sig>" on the stack (indicates presence of a returned value)
					Expression retVal = new ReturnValueExpression(tgtMethodSig);
					iptCtx.addExprToStack(retVal);
				}

				// instance constructor
				// we avoid the case when the "<init>" method is called on a local variable (such as calls of "<init>" on the superclass via "this/local0")
				if (tgtMethodName.equals("<init>") && (targetObj instanceof NewObjectExpression))
				{
					NewObjectExpression noExpr = (NewObjectExpression) targetObj;

					noExpr.mthInitDesc = invokeInsn.getMethodSignature();
				}
				
				execVisitor.visitInvokeInsn(insnPP, tgtMethodSig, isStaticCall, targetObj, tgtMethodParams);
			}
           
			if (insn instanceof InvokeDynamicInstruction)
			{
				InvokeDynamicInstruction invokeDynInsn = (InvokeDynamicInstruction) insn;
		
				String tgtMethodName = invokeDynInsn.getMethodName();
		
				String tgtMethodDesc = invokeDynInsn.getMethodSignature();

				String tgtMethodNameDesc = tgtMethodName + tgtMethodDesc;
	
				String ownerClassNameStr = DynamicInvokeResolver.getTargetClassName(tgtMethodName, tgtMethodDesc);

				System.out.println("[INFO] invoke dynamic: ownerClassName = " + ownerClassNameStr + ", tgtMethodName = " + tgtMethodName + ", tgtMethodDesc = " + tgtMethodDesc + ", invocationCode = " + invokeDynInsn.getInvocationCode() + ", poppedCount = " + invokeDynInsn.getPoppedCount());

				String tgtMethodSig = ownerClassNameStr + "." + tgtMethodName + tgtMethodDesc;

				boolean isStaticCall = (invokeDynInsn.getInvocationCode() == IInvokeInstruction.Dispatch.STATIC);
				
				int tgtMethodParamCount = 0;
				if (isStaticCall) tgtMethodParamCount = invokeDynInsn.getPoppedCount();
				else tgtMethodParamCount = invokeDynInsn.getPoppedCount() - 1; 
				
				List<Expression> tgtMethodParams = new ArrayList<Expression>();
		
				// record and remove actual parameters
				for (int i = 1; i <= tgtMethodParamCount; i++)
				{
					Expression param = iptCtx.removeExprFromStack();
					tgtMethodParams.add(0, param);
				}
				
				Expression targetObj = null;
				if (isStaticCall) targetObj = new ClassNameExpression(ownerClassNameStr);
				else targetObj = iptCtx.removeExprFromStack();
				
				if (WALAUtils.hasMethodReturnValue(tgtMethodSig, ownerClassNameStr, staCtx.cha))
				{
					// put the symbol "retval <method sig>" on the stack (indicates presence of a returned value)
					Expression retVal = new ReturnValueExpression(tgtMethodSig);
					iptCtx.addExprToStack(retVal);
				}

				execVisitor.visitInvokeInsn(insnPP, tgtMethodSig, isStaticCall, targetObj, tgtMethodParams);
			}

			if (insn instanceof LoadInstruction)
			{
				LoadInstruction loadInsn = (LoadInstruction) insn;
				
				int varSlot = loadInsn.getVarIndex();
					
				// we use artificial local variable names ("localX")
				String varName = "local" + varSlot;
					
				String varTypeStr = localVars2TypeNames.get(varName);
				if (varTypeStr == null) varTypeStr = Utils.getPlainTypeName(loadInsn.getType());

				boolean isMthParam = varSlot <= WALAUtils.getMethodParamMaxSlot(mth);

				LocalVarExpression varExpr = new LocalVarExpression(varSlot, varName, varTypeStr, isMthParam);
					
				// add variable name to the stack
				iptCtx.addExprToStack(varExpr);
					
				execVisitor.visitLoadInsn(insnPP, varExpr);
			}
			
			if (insn instanceof MonitorInstruction)
			{
				Expression lockObj = null;
				
				// bytecode instructions load, store, and dup just preceding the monitor instruction (enter/exit) do not have any effect (ignored, skipped)
				// we just have to remove the lock object from the expression stack

				if (insnOpcode == com.ibm.wala.shrikeBT.Constants.OP_monitorenter)
				{
					lockObj = iptCtx.removeExprFromStack();
				}
			}
			
			if (insn instanceof NewInstruction)
			{
				NewInstruction newInsn = (NewInstruction) insn;
				
				ClassName objClassName = ClassName.createFromString(Utils.getPlainTypeName(newInsn.getType()));
				AllocationSite objAllocSite = new AllocationSite(insnPP);
				
				if ((insnOpcode == com.ibm.wala.shrikeBT.Constants.OP_anewarray) || (insnOpcode == com.ibm.wala.shrikeBT.Constants.OP_newarray)) 
				{
					NewArrayExpression newArrayObj = new NewArrayExpression(objClassName, objAllocSite);

					Expression arraySizeExpr = iptCtx.removeExprFromStack();
					
					iptCtx.addExprToStack(newArrayObj);

					newArrayObj.arrayLength = arraySizeExpr;
	
					execVisitor.visitNewArrayInsn(insnPP, newArrayObj);
					
					// modeling update of array length by a write field access
					execVisitor.visitPutInsn(insnPP, newArrayObj, objClassName.getAsString(), "length", "int", false, arraySizeExpr);
				}
				else
				{
					NewObjectExpression newObj = new NewObjectExpression(objClassName, objAllocSite);

					iptCtx.addExprToStack(newObj);
	
					execVisitor.visitNewObjectInsn(insnPP, newObj);
				}				
			}
			
			if (insn instanceof PopInstruction)
			{
				iptCtx.removeExprFromStack();
			}
			
			if (insn instanceof PutInstruction)
			{
				PutInstruction putInsn = (PutInstruction) insn;

				String classNameStr = Utils.getPlainClassName(putInsn.getClassType());
				
				String fieldName = putInsn.getFieldName();
				
				String fieldTypeStr = Utils.getPlainTypeName(putInsn.getFieldType());
								
				Expression newValue = iptCtx.removeExprFromStack();
				
				Expression obj = null;

				if (putInsn.isStatic())
				{					
					obj = new ClassNameExpression(classNameStr);
				}					
				else
				{
					obj = iptCtx.removeExprFromStack();
				}
				
				execVisitor.visitPutInsn(insnPP, obj, classNameStr, fieldName, fieldTypeStr, putInsn.isStatic(), newValue);
			}
			
			if (insn instanceof ReturnInstruction)
			{
				Expression retValue = null;
				
				// check whether this method returns something
				if (mth.getReturnType() != TypeReference.Void)
				{					
					// pop "returned expression" from the top of the stack
					retValue = iptCtx.removeExprFromStack();
				}

				Instruction nextInsn = null;
				if (iptCtx.getNextInsnIndex() < mthInstructions.length) nextInsn = (Instruction) mthInstructions[iptCtx.getNextInsnIndex()];

				// handle catch blocks (where the exception object simply appears on the stack)
				if ((nextInsn != null) && (nextInsn instanceof StoreInstruction))
				{
					iptCtx.addExprToStack(SpecialExpression.EXCEPTION);
				}
				
				execVisitor.visitReturnInsn(insnPP, retValue);
			}
			
			if (insn instanceof ShiftInstruction)
			{
				ShiftInstruction shiftInsn = (ShiftInstruction) insn;
				
				Expression value2 = iptCtx.removeExprFromStack();
				
				Expression value1 = iptCtx.removeExprFromStack();
				
				// add whole expression to the stack
				
				String shiftOp = "";
				switch (shiftInsn.getOperator())
				{
					case SHL:
						shiftOp = "<<";
						break;
					case SHR:
						shiftOp = ">>";
						break;
					case USHR:
						shiftOp = ">>";
						break;
					default:
						shiftOp = "";
				}
				
				iptCtx.addExprToStack(new ArithmeticExpression(shiftOp, value1, value2));
			}
			
			if (insn instanceof StoreInstruction)
			{
				StoreInstruction storeInsn = (StoreInstruction) insn;

				// check whether this store is at the beginning of an exception handler				
				if (exceptionHandlersIndexes.contains(insnIndex))
				{
					// handle catch blocks (where the exception object simply appears on the stack)
					iptCtx.addExprToStack(SpecialExpression.EXCEPTION);
				}
				
				boolean skip = false;
				
				// this takes care of the way javac compiles synchronized blocks
				// we assume that all relevant instructions belong to the same control-flow branch
				if ((insnIndex + 1 < mthInstructions.length) && (mthInstructions[insnIndex + 1] instanceof MonitorInstruction)) skip = true;
				
				if ( ! skip )
				{
					Expression newValue = iptCtx.removeExprFromStack();

					int varSlot = storeInsn.getVarIndex();
					
					// we use artificial local variable names ("localX")
					String varName = "local" + varSlot;
					
					String varTypeStr = null;					
					if (newValue == SpecialExpression.NULL) varTypeStr = Utils.getPlainTypeName(storeInsn.getType());
					else if (newValue == SpecialExpression.EXCEPTION) varTypeStr = "java.lang.Throwable";
					else varTypeStr = ExpressionUtils.getExprTypeName(newValue);

					localVars2TypeNames.put(varName, varTypeStr);
					
					boolean isMthParam = varSlot <= WALAUtils.getMethodParamMaxSlot(mth);

					LocalVarExpression varExpr = new LocalVarExpression(varSlot, varName, varTypeStr, isMthParam);
					
					execVisitor.visitStoreInsn(insnPP, varExpr, newValue);
				}
			}
			
			if (insn instanceof SwapInstruction)
			{
				Expression value1 = iptCtx.removeExprFromStack();
				
				Expression value2 = iptCtx.removeExprFromStack();
				
				// put values back in swapped order
				iptCtx.addExprToStack(value1);
				iptCtx.addExprToStack(value2);
			}
			
			if (insn instanceof SwitchInstruction)
			{
				SwitchInstruction switchInsn = (SwitchInstruction) insn;
				
				Expression testedValue = iptCtx.removeExprFromStack();
				
				List<Integer> caseValues = new ArrayList<Integer>();
				List<Integer> caseTargets = new ArrayList<Integer>();
				
				int casesNum = switchInsn.getCasesAndLabels().length / 2;
				
				for (int i = 0; i < casesNum; i++)
				{
					caseValues.add(switchInsn.getCasesAndLabels()[i*2]);
					caseTargets.add(switchInsn.getCasesAndLabels()[i*2+1]);
				}

				// check if there exist at least two cases in the given SWITCH
				// the case with the smallest target (offset) corresponds to the first newly created branch
				// we assume that the first branch starts immediately after the switch instruction
					// it seems to be the usual case
				// the end of the first newly created branch is the second smallest offset (target/label) in the list (and so on)
				if (casesNum > 1)
				{
					List<Integer> copyTargetsOrdered = new ArrayList<Integer>(caseTargets);
					Collections.sort(copyTargetsOrdered);

					for (int i = 0; i < copyTargetsOrdered.size(); i++)
					{
						// the branch will really start (become active)at location that is further ahead
						iptCtx.prepareNewControlFlowBranch(copyTargetsOrdered.get(i));
					}
				}
			}
			
			if (insn instanceof ThrowInstruction)
			{
				Expression excObj = iptCtx.removeExprFromStack();
			}
			
			if (insn instanceof UnaryOpInstruction)
			{
				Expression value = iptCtx.removeExprFromStack();

				// add expression "(0 - value)" to the stack 
				iptCtx.addExprToStack(new ArithmeticExpression("-", new ConstantExpression(new Integer(0)), value));
			}

			if (Configuration.DEBUG)
			{
				iptCtx.printExprStack("after");
			}
	
			iptCtx.mergeIdenticalExprStacks();

			// we must loop over all variants (copies) of the expression stack in the current branch (path)
				// until the current instruction in the current branch is fully processed (all expression stacks)
			iptCtx.moveToNextExprStack();
			if (iptCtx.isCurrentBranchInsnProcessed()) iptCtx.moveToNextInsn();
		}
	}
}

