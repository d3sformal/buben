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
package cz.cuni.mff.d3s.buben.transform;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Stack;

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;

import com.ibm.wala.classLoader.IClass;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.Label;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import cz.cuni.mff.d3s.buben.Configuration;
import cz.cuni.mff.d3s.buben.Utils;
import cz.cuni.mff.d3s.buben.StaticAnalysisContext;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.*;
import cz.cuni.mff.d3s.buben.summaries.*;
import cz.cuni.mff.d3s.buben.wala.WALAUtils;


public class LibraryMethodAbstractionGenerator
{
	public static void replaceMethodBytecode(String methodSig) throws Exception
	{
		String clsName = Utils.extractClassName(methodSig);

		String clsFileName = clsName.replace('.', '/') + ".class";

		String clsFilePath = Configuration.targetClassPath + File.separator + clsFileName;
	
		// we use a simple algorithm (possibly very inefficient) for each method:
		// 1) load the whole class file, 2) modify the given method (replace original bytecode), and 3) save the result into file
		// our plan was to implement some optimizations when really needed (use caching of some kind, etc)

		// create input stream for loading bytecode of the class
		InputStream ins = new FileInputStream(clsFilePath);
		
		// load the class and modify its bytecode
		ClassReader cr = new ClassReader(ins);
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		ClassVisitor cv = new MethodAbstractionCV(clsName, methodSig, cw);
		cr.accept(cv, 0);

		ins.close();

		// save the class to file
		byte[] b = cw.toByteArray();
		FileOutputStream outf = new FileOutputStream(clsFilePath);
		outf.write(b);
		outf.close();
	}

	static class MethodAbstractionCV extends ClassVisitor
	{
		private String className;

		private String targetMethodSig;

		private String superClassPlainName;

		public static ASMifier debugASM;


		public MethodAbstractionCV(String clsName, String tgtMthSig, ClassVisitor cv)
		{
			super(Opcodes.ASM5, cv);
			this.className = clsName;
			this.targetMethodSig = tgtMthSig;
		}

		public void visit(int version, int clsAccess, String clsName, String clsSignature, String superClsName, String[] clsInterfaces)
		{
			cv.visit(version, clsAccess, clsName, clsSignature, superClsName, clsInterfaces);

			this.superClassPlainName = Utils.getPlainClassName(superClsName);
		}

		public MethodVisitor visitMethod(int mthAccess, String mthName, String mthDesc, String mthSignature, String[] mthExceptions)
		{
			String curMethodSig = className + "." + mthName + mthDesc;

			MethodVisitor mv = cv.visitMethod(mthAccess, mthName, mthDesc, mthSignature, mthExceptions);

			// the current method is not the one we need to replace (abstract)
			if ( ! targetMethodSig.equals(curMethodSig) ) 
			{
				// still we must remove the bytecode instructions JSR and RET
				return new JumpAbstractionMV(mv);
			}

			if (Configuration.DEBUG)
			{
				// possible additional filter
				//if (curMethodSig.startsWith("..."))

				// we need to have clean ASMifier for each traced method
				MethodAbstractionCV.debugASM = new ASMifier();

				System.err.println("[DEBUG] creating TraceMethodVisitor for the method " + curMethodSig + ": ASMifier = " + MethodAbstractionCV.debugASM.hashCode());

				mv = new TraceMethodVisitor(mv, debugASM);
			}

			MethodSideEffectSummary mthSumm = SideEffectSummaryGenAnalysis.getSummaryForMethod(targetMethodSig);

			Type[] mthParamTypes = Type.getArgumentTypes(mthDesc);

			int mthParamCount = Type.getArgumentsAndReturnSizes(mthDesc) >> 2;
			
			mv.visitCode();

			// constructor has to call the "<init>" method of the superclass right at the beginning
			if (mthName.equals("<init>"))
			{
				mv.visitVarInsn(Opcodes.ALOAD, 0);

				String mthInitSig = ObjectTypesData.getConstructorSignatureForClass(superClassPlainName);

				if (mthInitSig == null)
				{
					mv.visitInsn(Opcodes.POP);
				}

				if (mthInitSig != null)
				{
					String mthInitDesc = Utils.extractMethodParamRetDescriptor(mthInitSig);
				
					ASMUtils.generateInvokeConstructorWithParams(mv, superClassPlainName, mthInitDesc, new Stack<String>());
				}
			}

			int nextLocalVarSlot = 0;

			// some original local variables are omitted from the generated abstraction so we have to use translation
			Map<Expression, Expression> curLocalVarTrans = new HashMap<Expression, Expression>();

			// initialize translation for "this" and method call parameters (which should all be preserved)
			
			// check if we have an instance method
			if ( (mthAccess & Opcodes.ACC_STATIC) == 0 )
			{
				LocalVarExpression thisExpr = new LocalVarExpression(0, "local0", className, true);

				curLocalVarTrans.put(thisExpr, thisExpr);

				nextLocalVarSlot = 1;
			}

			for (int k = 0; k < mthParamTypes.length; k++)
			{
				Type paramType = mthParamTypes[k];
				String paramTypeStr = Utils.getPlainTypeName(paramType.getDescriptor());
				
				LocalVarExpression paramExpr = new LocalVarExpression(nextLocalVarSlot, "local"+nextLocalVarSlot, paramTypeStr, true);

				curLocalVarTrans.put(paramExpr, paramExpr);

				if (Utils.isTypeWithSizeTwoWords(paramTypeStr)) nextLocalVarSlot += 2;
				else nextLocalVarSlot += 1;
			}

			// simulate all visible side effects of the original method (e.g., field writes)
			// we use information available in the computed summaries

			// whole method body is atomic except unsynchronized writes at the end
			ASMUtils.generateBeginAtomic(mv);

			// process object field writes
			for (FieldAccessExpression tgtFieldExpr : mthSumm.updatedFields2Values.keySet())
			{
				// unsynchronized field write accesses do not have to be generated also inside the atomic block (for the second time)
				if (mthSumm.unsynchFields.contains(tgtFieldExpr)) continue;

				nextLocalVarSlot = generateFieldUpdateNewValues(mv, mthSumm, tgtFieldExpr, nextLocalVarSlot, curLocalVarTrans);
			}
			
			// process array element writes
			for (ArrayAccessExpression tgtArrayExpr : mthSumm.updatedArrays2Values.keySet())
			{
				// unsynchronized array element write accesses do not have to be generated also inside the atomic block (for the second time)
				if (mthSumm.unsynchArrays.contains(tgtArrayExpr)) continue;

				nextLocalVarSlot = generateArrayElementUpdateNewValues(mv, mthSumm, tgtArrayExpr, nextLocalVarSlot, curLocalVarTrans);
			}

			// end the atomic block
			ASMUtils.generateEndAtomic(mv);

			// unsynchronized writes to fields			
			for (FieldAccessExpression tgtUnsynchFieldExpr : mthSumm.unsynchFields)
			{
				nextLocalVarSlot = generateFieldUpdateNewValues(mv, mthSumm, tgtUnsynchFieldExpr, nextLocalVarSlot, curLocalVarTrans);
			}
			
			// unsynchronized writes to arrays
			for (ArrayAccessExpression tgtUnsynchArrayExpr : mthSumm.unsynchArrays)
			{
				nextLocalVarSlot = generateArrayElementUpdateNewValues(mv, mthSumm, tgtUnsynchArrayExpr, nextLocalVarSlot, curLocalVarTrans);
			}
			
			// process the returned parameters and other expressions
			
			int retValsCount = mthSumm.returnedParams.size() + mthSumm.returnValues.size();
			
			// we have at least one possible return value (that was collected by the static and dynamic analysis)
			if (retValsCount > 0)
			{
				ASMUtils.generateChoiceInteger(mv, 0, retValsCount - 1);
			
				int rChoiceVarSlot = nextLocalVarSlot;	
				ASMUtils.generateLocalIntegerVarStore(mv, rChoiceVarSlot);
				nextLocalVarSlot++;
		
				// generate if-else statement between all returned values (together for parameters and other expressions)

				Label lblRetEnd = new Label();

				int i = 0;

				// loop over all returned parameters
				for (Integer retParamIdxObj : mthSumm.returnedParams)
				{
					ASMUtils.generateLocalIntegerVarLoad(mv, rChoiceVarSlot);

					ASMUtils.generateLoadIntegerConstant(mv, i);
					
					Label lblBranch = new Label();
					mv.visitJumpInsn(Opcodes.IF_ICMPNE, lblBranch);
				
					int retParamIdx = retParamIdxObj.intValue();

					// check if we return "this" from an instance method
					if ( ( (mthAccess & Opcodes.ACC_STATIC) == 0 ) && (retParamIdx == 0) )
					{
						Type mthThisType = Type.getObjectType(Utils.getInternalClassName(className));
						
						ASMUtils.generateLocalVarLoad(mv, mthThisType, 0);
					}
					else
					{
						// we have to skip the implicit "this" parameter in the case of instance methods
						if ( (mthAccess & Opcodes.ACC_STATIC) == 0 ) retParamIdx--;

						ASMUtils.generateLocalVarLoad(mv, mthParamTypes[retParamIdx], retParamIdx);
					}
			
					Type mthRetType = Type.getReturnType(mthDesc);
					ASMUtils.generateReturnForType(mv, mthRetType);
				
					mv.visitJumpInsn(Opcodes.GOTO, lblRetEnd);
					mv.visitLabel(lblBranch);

					i++;
				}
			
				// loop over all returned expressions
				for (Expression retExpr : mthSumm.returnValues)
				{
					ASMUtils.generateLocalIntegerVarLoad(mv, rChoiceVarSlot);

					ASMUtils.generateLoadIntegerConstant(mv, i);
					
					Label lblBranch = new Label();
					mv.visitJumpInsn(Opcodes.IF_ICMPNE, lblBranch);
				
					nextLocalVarSlot = updateLocalVarTranslationInfo(retExpr, nextLocalVarSlot, curLocalVarTrans);
					Expression trRetExpr = translateLocalVars(retExpr, curLocalVarTrans);

					ASMUtils.generateLoadExpression(mv, trRetExpr);
				
					Type mthRetType = Type.getReturnType(mthDesc);
					ASMUtils.generateReturnForType(mv, mthRetType);
				
					mv.visitJumpInsn(Opcodes.GOTO, lblRetEnd);
					mv.visitLabel(lblBranch);

					i++;
				}

				mv.visitLabel(lblRetEnd);
			}

			// default returned values (void, 0, or "null")
			Type mthDefRetType = Type.getReturnType(mthDesc);
			ASMUtils.generateLoadDefaultValue(mv, mthDefRetType);
			ASMUtils.generateReturnForType(mv, mthDefRetType);

			if (Configuration.DEBUG)
			{
				// possible additional filter
				//if (curMethodSig.startsWith("..."))

				System.err.println("[DEBUG] ASMifier " + MethodAbstractionCV.debugASM.hashCode() + " is going to print code for the method " + curMethodSig);

				System.err.flush();

				java.io.PrintWriter pwErr = new java.io.PrintWriter(System.err);

				MethodAbstractionCV.debugASM.print(pwErr);

				pwErr.flush();

				System.err.println("[DEBUG] printing finished");
			}

			// value of "nextLocalVarSlot" is equal to the number of local variables
			mv.visitMaxs(6, nextLocalVarSlot);
			mv.visitEnd();
				
			// we replace the method's bytecode so it does not have to be processed further
			return null;
		}
		
		public int generateFieldUpdateNewValues(MethodVisitor mv, MethodSideEffectSummary mthSumm, FieldAccessExpression tgtFieldExpr, int nextLocalVarSlot, Map<Expression, Expression> curLocalVarTrans)
		{
			Set<Expression> newValues = mthSumm.updatedFields2Values.get(tgtFieldExpr);

			if (newValues.size() == 0) return nextLocalVarSlot;

			nextLocalVarSlot = updateLocalVarTranslationInfo(tgtFieldExpr, nextLocalVarSlot, curLocalVarTrans);
			FieldAccessExpression trTgtFieldExpr = (FieldAccessExpression) translateLocalVars(tgtFieldExpr, curLocalVarTrans);

			ASMUtils.generateChoiceInteger(mv, 0, newValues.size() - 1);

			int fChoiceVarSlot = nextLocalVarSlot;	
			ASMUtils.generateLocalIntegerVarStore(mv, fChoiceVarSlot);
			nextLocalVarSlot++;

			// generate if-else statement between all values

			Label lblEnd = new Label();

			// loop over all branches
			int i = 0;
			for (Expression newExpr : newValues)
			{
				ASMUtils.generateLocalIntegerVarLoad(mv, fChoiceVarSlot);

				ASMUtils.generateLoadIntegerConstant(mv, i);
					
				Label lblBranch = new Label();
				mv.visitJumpInsn(Opcodes.IF_ICMPNE, lblBranch);
				
				nextLocalVarSlot = updateLocalVarTranslationInfo(newExpr, nextLocalVarSlot, curLocalVarTrans);
				Expression trNewExpr = translateLocalVars(newExpr, curLocalVarTrans);

				ASMUtils.generateLoadExpression(mv, trNewExpr);
				
				ASMUtils.generateFieldWrite(mv, trTgtFieldExpr);

				mv.visitJumpInsn(Opcodes.GOTO, lblEnd);
				mv.visitLabel(lblBranch);

				i++;
			}

			mv.visitLabel(lblEnd);

			return nextLocalVarSlot;
		}
		
		public int generateArrayElementUpdateNewValues(MethodVisitor mv, MethodSideEffectSummary mthSumm, ArrayAccessExpression tgtArrayExpr, int nextLocalVarSlot, Map<Expression, Expression> curLocalVarTrans)
		{
			Set<Expression> newValues = mthSumm.updatedArrays2Values.get(tgtArrayExpr);

			if (newValues.size() == 0) return nextLocalVarSlot;

			nextLocalVarSlot = updateLocalVarTranslationInfo(tgtArrayExpr, nextLocalVarSlot, curLocalVarTrans);
			ArrayAccessExpression trTgtArrayExpr = (ArrayAccessExpression) translateLocalVars(tgtArrayExpr, curLocalVarTrans);

			ASMUtils.generateChoiceInteger(mv, 0, newValues.size() - 1);

			int aChoiceVarSlot = nextLocalVarSlot;	
			ASMUtils.generateLocalIntegerVarStore(mv, aChoiceVarSlot);
			nextLocalVarSlot++;

			// generate if-else statement between all values

			Label lblEnd = new Label();

			// loop over all branches
			int i = 0;
			for (Expression newExpr : newValues)
			{
				ASMUtils.generateLocalIntegerVarLoad(mv, aChoiceVarSlot);

				ASMUtils.generateLoadIntegerConstant(mv, i);
				
				Label lblBranch = new Label();
				mv.visitJumpInsn(Opcodes.IF_ICMPNE, lblBranch);
				
				nextLocalVarSlot = updateLocalVarTranslationInfo(newExpr, nextLocalVarSlot, curLocalVarTrans);
				Expression trNewExpr = translateLocalVars(newExpr, curLocalVarTrans);

				ASMUtils.generateLoadExpression(mv, trNewExpr);
				
				ASMUtils.generateArrayElementWrite(mv, trTgtArrayExpr);

				mv.visitJumpInsn(Opcodes.GOTO, lblEnd);
				mv.visitLabel(lblBranch);

				i++;
			}

			mv.visitLabel(lblEnd);

			return nextLocalVarSlot;
		}

		public int updateLocalVarTranslationInfo(Expression expr, int nextLocalVarSlot, Map<Expression, Expression> curLocalVarTrans)
		{
			if (expr instanceof FieldAccessExpression)
			{
				FieldAccessExpression fieldExpr = (FieldAccessExpression) expr;
		
				nextLocalVarSlot = updateLocalVarTranslationInfo(fieldExpr.targetObj, nextLocalVarSlot, curLocalVarTrans);
			}

			if (expr instanceof ArrayAccessExpression)
			{
				ArrayAccessExpression arrayExpr = (ArrayAccessExpression) expr;
		
				nextLocalVarSlot = updateLocalVarTranslationInfo(arrayExpr.targetArrayObj, nextLocalVarSlot, curLocalVarTrans);

				// array element indexes are handled in a different way
			}

			if (expr instanceof ArithmeticExpression)
			{
				ArithmeticExpression arithmExpr = (ArithmeticExpression) expr;

				nextLocalVarSlot = updateLocalVarTranslationInfo(arithmExpr.value1, nextLocalVarSlot, curLocalVarTrans);
	
				nextLocalVarSlot = updateLocalVarTranslationInfo(arithmExpr.value2, nextLocalVarSlot, curLocalVarTrans);
			}

			if (expr instanceof LocalVarExpression)
			{
				LocalVarExpression lvExpr = (LocalVarExpression) expr;

				Expression translatedExpr = retrieveTranslatedLocalVarExpr(lvExpr, curLocalVarTrans);

				// not yet defined
				if (translatedExpr == null)
				{
					translatedExpr = new LocalVarExpression(nextLocalVarSlot, "local"+nextLocalVarSlot, lvExpr.varType, lvExpr.isMthParam);
					
					if (Utils.isTypeWithSizeTwoWords(lvExpr.varType)) nextLocalVarSlot += 2;
					else nextLocalVarSlot += 1;

					curLocalVarTrans.put(lvExpr, translatedExpr);
				}
			}

			return nextLocalVarSlot;
		}

		public Expression translateLocalVars(Expression expr, Map<Expression, Expression> curLocalVarTrans)
		{
			if (expr instanceof FieldAccessExpression)
			{
				FieldAccessExpression fieldExpr = (FieldAccessExpression) expr;

				Expression trTargetObj = translateLocalVars(fieldExpr.targetObj, curLocalVarTrans);

				return new FieldAccessExpression(trTargetObj, fieldExpr.className, fieldExpr.fieldName, fieldExpr.fieldType, fieldExpr.isStatic);
			}

			if (expr instanceof ArrayAccessExpression)
			{
				ArrayAccessExpression arrayExpr = (ArrayAccessExpression) expr;

				Expression trTargetArrayObj = translateLocalVars(arrayExpr.targetArrayObj, curLocalVarTrans);
	
				// array element indexes are handled in a different way

				return new ArrayAccessExpression(trTargetArrayObj, arrayExpr.arrayClassName, arrayExpr.elementIndex, arrayExpr.elementType);
			}
	
			if (expr instanceof ArithmeticExpression)
			{
				ArithmeticExpression arithmExpr = (ArithmeticExpression) expr;

				Expression trValue1 = translateLocalVars(arithmExpr.value1, curLocalVarTrans);
			
				Expression trValue2 = translateLocalVars(arithmExpr.value2, curLocalVarTrans);

				return new ArithmeticExpression(arithmExpr.operator, trValue1, trValue2);
			}

			if (expr instanceof LocalVarExpression)
			{
				LocalVarExpression lvExpr = (LocalVarExpression) expr;
		
				Expression translatedExpr = retrieveTranslatedLocalVarExpr(lvExpr, curLocalVarTrans);

				return translatedExpr;
			}

			// fallback
			return expr;
		}

		public Expression retrieveTranslatedLocalVarExpr(LocalVarExpression inLocVarExpr, Map<Expression, Expression> curLocalVarTrans)
		{
			// we have to compare only names of local variables because type/class names resolution in WALA produces different (less precise) results than ASM ("java.lang.Object" versus the actual class name)

			for (Expression curSrcExpr : curLocalVarTrans.keySet())
			{
				LocalVarExpression curLocVarExpr = (LocalVarExpression) curSrcExpr;

				if (curLocVarExpr.varName.equals(inLocVarExpr.varName)) return curLocalVarTrans.get(curSrcExpr);
			}

			// translation not found
			return null;
		}
	}
}

