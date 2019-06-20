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
import java.util.List;
import java.util.Iterator;

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;

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

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.ipa.callgraph.CGNode;

import cz.cuni.mff.d3s.buben.Configuration;
import cz.cuni.mff.d3s.buben.StaticAnalysisContext;
import cz.cuni.mff.d3s.buben.Utils;
import cz.cuni.mff.d3s.buben.wala.WALAUtils;
import cz.cuni.mff.d3s.buben.dynamic.*;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.ConstantExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.HeapReferenceExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.SpecialExpression;


public class NativeExternAbstractionGenerator
{
	public static void replaceStatements(StaticAnalysisContext staCtx, Set<String> tgtMethodSignatures, List<String> tgtClassPrefixes) throws Exception
	{
		// we use a simple algorithm that is most probably very inefficient
		// for each class that contains some method in the call graph, we perform the following:
		// 1) load the whole class file, 2) modify statements in each method of the class (replace original bytecode), and 3) save the result into file
		// abstracted statements: method invocation, new object, field access (over native/extern classes)
		// our plan was to implement some optimizations when really needed (use caching of some kind, etc)

		// class names
		Set<String> processedClasses = new HashSet<String>();
				
		// process methods reachable in the call graph
		for (Iterator<CGNode> cgnIt = staCtx.clGraph.iterator(); cgnIt.hasNext(); )
		{
			IMethod mth = cgnIt.next().getMethod();

			// fake root method
			if ( ! (mth instanceof IBytecodeMethod) ) continue;

			IClass cls = mth.getDeclaringClass();
			
			String clsName = WALAUtils.getClassName(cls);
			
			// skip classes that belong to Java standard library and internal classes (e.g., from the package sun.*)
				// we can safely ignore all side effects that are internal to such library classes (and their bytecode cannot be updated anyway)
				// this also ensures that we keep only writes to fields and arrays defined in classes that belong to custom libraries
			if (Utils.isJavaStandardLibraryClass(clsName)) continue;
			if (Utils.isJavaInternalLibraryClass(clsName)) continue;

			// skip model classes from the package "com.ibm.wala"
			if (WALAUtils.isSyntheticModelClass(clsName)) continue;
			
			if (processedClasses.contains(clsName)) continue;
			processedClasses.add(clsName);
			
			String clsFileName = clsName.replace('.', '/') + ".class";
			
			String clsFilePath = Configuration.targetClassPath + File.separator + clsFileName;
			
			// create input stream for loading bytecode of the class
			InputStream ins = new FileInputStream(clsFilePath);

			// load the class and modify its bytecode
			ClassReader cr = new ClassReader(ins);
			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
			ClassVisitor cv = new MethodAbstractionCV(clsName, tgtMethodSignatures, tgtClassPrefixes, cw);
			try
			{
				cr.accept(cv, 0);

				ins.close();
	
				// save the class to file
				byte[] b = cw.toByteArray();
				FileOutputStream outf = new FileOutputStream(clsFilePath);
				outf.write(b);
				outf.close();
			}
			catch (Exception ex)
			{
				System.err.println("Exception: " + ex.getMessage());
				ex.printStackTrace();
			}
		}
	}
	
	static class MethodAbstractionCV extends ClassVisitor
	{
		private String curClassName;
		
		private Set<String> targetMethodSignatures;
		private List<String> targetClassPrefixes;

		public static ASMifier debugASM;
		

		public MethodAbstractionCV(String clsName, Set<String> tgtMthSigs, List<String> tgtClsPfxs, ClassVisitor cv)
		{
			super(Opcodes.ASM5, cv);
			this.curClassName = clsName;
			this.targetMethodSignatures = tgtMthSigs;
			this.targetClassPrefixes = tgtClsPfxs;
		}
		
		public MethodVisitor visitMethod(int mthAccess, String mthName, String mthDesc, String mthSignature, String[] mthExceptions)
		{
			MethodVisitor mv = cv.visitMethod(mthAccess, mthName, mthDesc, mthSignature, mthExceptions);

			if (mv == null) return null;
			
			String fullMthName = curClassName + "." + mthName;

			// we do not replace any statements in library methods
			// still we must remove the bytecode instructions JSR and RET
			if ( ! Configuration.isApplicationMethod(fullMthName) ) return new JumpAbstractionMV(mv);

			// we also do not replace any statements in methods that access external entities
			// still we must remove the bytecode instructions JSR and RET
			if ( Configuration.isExternalAccessMethod(fullMthName) ) return new JumpAbstractionMV(mv);

			String mthSig = curClassName + "." + mthName + mthDesc;
	
			debugASM = null;

			if (Configuration.DEBUG)
			{
				// possible additional filter
				//if (mthSig.startsWith("..."))

				// we need to have a new clean ASMifier just for each tracked method
				debugASM = new ASMifier();

				System.err.println("[DEBUG] creating new ASMifier instance: " + debugASM.hashCode());

				// we have to wrap TraceMethodVisitor by StatementAbstractionMV so that delegated calls on MethodVisitor are traced 
				return new StatementAbstractionMV(mthSig, targetMethodSignatures, targetClassPrefixes, new TraceMethodVisitor(mv, debugASM));
			}
			else
			{
				return new StatementAbstractionMV(mthSig, targetMethodSignatures, targetClassPrefixes, mv);
			}
		}
	}
	
	static class StatementAbstractionMV extends JumpAbstractionMV
	{
		private String curMethodSig;

		private Set<String> targetMethodSignatures;
		private List<String> targetClassPrefixes;
	
		// this label will point to the next instruction after the first JSR within the method code
		// it will be used as the target of all RET instructions
		private Label lblAfterFirstJSR = null;

		public StatementAbstractionMV(String curMthSig, Set<String> tgtMthSigs, List<String> tgtClsPfxs, MethodVisitor mv)
		{
			super(mv);
			this.curMethodSig = curMthSig;
			this.targetMethodSignatures = tgtMthSigs;
			this.targetClassPrefixes = tgtClsPfxs;
		}

		public void visitTypeInsn(int insnOpcode, String internalTypeName)
		{
			if (insnOpcode == Opcodes.NEW)
			{
				String plainTypeName = Utils.getPlainClassName(internalTypeName);
	
				// special handling of classes where all methods are native or access external entities

				if (Utils.isClassNameWithPrefix(plainTypeName, targetClassPrefixes))
				{
					// generate the NEW bytecode instruction
					super.visitTypeInsn(insnOpcode, internalTypeName);

					// we replace also calls of instance constructors for all classes that manipulate with external entities

					/*
					String mthInitSig = ObjectTypesData.getConstructorSignatureForClass(plainTypeName);

					if (mthInitSig == null) System.err.println("[DEBUG] constructor not available (unknown) for the class '" + plainTypeName + "' when processing the method '" + curMethodSig + "'");

					String mthInitDesc = Utils.extractMethodParamRetDescriptor(mthInitSig);

					ASMUtils.generateNewObjectInit(this, plainTypeName, mthInitDesc, true);
					*/

					return;
				}
			}

			// all other cases
			super.visitTypeInsn(insnOpcode, internalTypeName);
		}

		public void visitFieldInsn(int insnOpcode, String ownerClsInternalName, String fieldName, String fieldDesc)
		{
			// ignore reads and writes to fields of classes where all methods are native or they access external entities

			String plainClassName = Utils.getPlainClassName(ownerClsInternalName);
	
			if (Utils.isClassNameWithPrefix(plainClassName, targetClassPrefixes))
			{
				if ((insnOpcode == Opcodes.GETFIELD) || (insnOpcode == Opcodes.GETSTATIC))
				{
					// remove the object reference from the stack frame
					if (insnOpcode == Opcodes.GETFIELD) visitInsn(Opcodes.POP);

					Type fieldType = Type.getType(fieldDesc);

					// model the result by the default value of a given type
					ASMUtils.generateLoadDefaultValue(this, fieldType);

					return;
				}
	
				if ((insnOpcode == Opcodes.PUTFIELD) || (insnOpcode == Opcodes.PUTSTATIC))
				{
					// remove the new value from the stack frame
					visitInsn(Opcodes.POP);

					// remove the object reference from the stack frame
					if (insnOpcode == Opcodes.PUTFIELD) visitInsn(Opcodes.POP);

					return;
				}
			}

			// all other cases
			super.visitFieldInsn(insnOpcode, ownerClsInternalName, fieldName, fieldDesc);
		}

		public void visitMethodInsn(int insnOpcode, String ownerClsInternalName, String mthName, String mthDesc, boolean isItfOwner)
		{
			String mthSig = Utils.getPlainClassName(ownerClsInternalName) + "." + mthName + mthDesc;

			Type[] mthParamTypes = Type.getArgumentTypes(mthDesc);

			int mthParamCount = Type.getArgumentsAndReturnSizes(mthDesc) >> 2;
		
			String ownerClsPlainName = Utils.getPlainClassName(ownerClsInternalName);

			// NOTE this code fragment is not needed anymore because we do not perform any special handling of instance constructors even for classes that manipulate with external entities
			/*
			if (Utils.isClassNameWithPrefix(ownerClsPlainName, targetClassPrefixes))
			{
				if (mthName.startsWith("<init>"))
				{
					// pop concrete arguments from the dynamic stack 

					for (int j = mthParamTypes.length - 1; j >= 0; j--)
					{
						Type paramType = mthParamTypes[j];
						
						if (paramType.getSize() == 2) visitInsn(Opcodes.POP2);
						else visitInsn(Opcodes.POP);
					}

					// pop the method call receiver from the dynamic stack
					visitInsn(Opcodes.POP);

					return;
				}
			}
			*/

			// process methods that are not intercepted
			if ( ! targetMethodSignatures.contains(mthSig) )
			{
				super.visitMethodInsn(insnOpcode, ownerClsInternalName, mthName, mthDesc, isItfOwner);
				return;
			}
		
			// we replace also the calls of instance constructors for all classes that manipulate with external entities
				// performing just all the side effects (updates to object fields and array elements) is apparently sufficient
				// goal: to avoid problems with calls of some other constructors by a constructor visible from the application
				// constructors do not really have to be invoked for the classes that manipulate with external entities

			// we skip the actual call of the method (i.e., we omit the call from the abstract program) and model its side effects using the recorded information (by dynamic analysis)
			
			Set<FieldWriteInfo> fldwInfos = DynamicInputOutputCollector.getFieldUpdatesForMethod(mthSig);
			Set<ArrayWriteInfo> arrwInfos = DynamicInputOutputCollector.getArrayElementUpdatesForMethod(mthSig);
			
			// processing writes to object fields and array elements
			// step 1: method arguments
			
			// algorithm: loop over the types of method call parameters, and for each of them check whether there is some recorded write to a field/element of the corresponding object/array

			// respect the order of call arguments on the dynamic stack
			for (int j = mthParamTypes.length - 1; j >= 0; j--)
			{
				Type paramType = mthParamTypes[j];
				
				String paramTypeStr = Utils.getPlainTypeName(paramType.getDescriptor());
				
				for (FieldWriteInfo fwInfo : fldwInfos)
				{
					if (fwInfo.className.equals(paramTypeStr))
					{
						// duplicate the argument value (object reference) on dynamic stack
						// we want to preserve it for possible other field writes
						if (paramType.getSize() == 2) visitInsn(Opcodes.DUP2);
						else visitInsn(Opcodes.DUP);
						
						generateFieldUpdate(fwInfo);
					}
				}
				
				for (ArrayWriteInfo awInfo : arrwInfos)
				{
					if (awInfo.className.equals(paramTypeStr))
					{
						// duplicate the argument value (array reference) on dynamic stack
						// we want to preserve it for possible other array element writes
						if (paramType.getSize() == 2) visitInsn(Opcodes.DUP2);
						else visitInsn(Opcodes.DUP);
						
						generateArrayElementUpdate(awInfo);
					}
				}
				
				// pop the concrete argument value from the dynamic stack
				if (paramType.getSize() == 2) visitInsn(Opcodes.POP2);
				else visitInsn(Opcodes.POP);
			}

			// pop the method call receiver from the dynamic stack
			if (insnOpcode != Opcodes.INVOKESTATIC) visitInsn(Opcodes.POP);

			// process method call parameters and return values

			Type retType = Type.getReturnType(mthDesc);
	
			String retTypeStr = Utils.getPlainTypeName(retType.getDescriptor());

			Set<CallResultInfo> callrInfos = DynamicInputOutputCollector.getCallResultsForMethod(mthSig);
			
			if (mthSig.startsWith("java.lang.Object.clone"))
			{
				// we need to handle the call of Object.clone() in a special way
				// consider just expressions of the same type as the class in which the enclosing "clone" method is defined
					// rationale: the Object.clone() method should be called only within the "clone" method of some other class
				// otherwise the set of possible return values is too large (from all invocations of "Object.clone" in the whole program)
					// nevertheless we still have to limit the size of the filtered set of possible return values (because in some cases the full set would be too large)

				String enclosingClassName = Utils.extractClassName(curMethodSig);

				Set<CallResultInfo> filteredCRIs = new HashSet<CallResultInfo>();

				for (CallResultInfo cri : callrInfos)
				{
					// this should be valid for all possible return values of "clone()"
					HeapReferenceExpression heapExpr = (HeapReferenceExpression) cri.returnValue;

					if (heapExpr.descriptor.equals(enclosingClassName) && (filteredCRIs.size() < Configuration.maxReturnValues))
					{
						filteredCRIs.add(cri);
					}
				}

				callrInfos = filteredCRIs;
			}

			// check whether method returns something
			if (retType != Type.VOID_TYPE)
			{
				Label lblEndCR = new Label();

				// check if we have at least one possible value
				if (callrInfos.size() > 0)
				{
					ASMUtils.generateChoiceInteger(this, 0, callrInfos.size() - 1);
			
					// generate if-else statement between all the possible results (return values)
			
					int i = 0;

					// in practice, there will always be one match between 'i' and the non-deterministically selected integer value

					// loop over all possible results
					for (Iterator<CallResultInfo> it = callrInfos.iterator(); it.hasNext(); )
					{
						CallResultInfo crInfo = it.next();
			
						// duplicate the current value of the non-deterministically selected integer on the dynamic stack
						// we want to preserve it for possible other results (otherwise the IF_ICMPNE bytecode instruction would remove it)
						visitInsn(Opcodes.DUP);

						ASMUtils.generateLoadIntegerConstant(this, i);
				
						Label lblBranch = new Label();
						visitJumpInsn(Opcodes.IF_ICMPNE, lblBranch);

						// we have the match
						// therefore we should pop the integer value (the result of non-deterministic choice) from the dynamic stack frame
						visitInsn(Opcodes.POP);
				
						// push the selected return value to the dynamic stack
						ASMUtils.generateLoadExpression(this, crInfo.returnValue);
				
						// processing writes to object fields and array elements
						// step 2: returned value
			
						for (FieldWriteInfo fwInfo : fldwInfos)
						{
							if (fwInfo.className.equals(retTypeStr))
							{
								// duplicate the return value (object reference) on dynamic stack
								// we want to preserve it for possible other field writes and store
								if (retType.getSize() == 2) visitInsn(Opcodes.DUP2);
								else visitInsn(Opcodes.DUP);

								generateFieldUpdate(fwInfo);
							}
						}
				
						for (ArrayWriteInfo awInfo : arrwInfos)
						{
							if (awInfo.className.equals(retTypeStr))
							{
								// duplicate the return value (array reference) on dynamic stack
								// we want to preserve it for possible other array element writes and store
								if (retType.getSize() == 2) visitInsn(Opcodes.DUP2);
								else visitInsn(Opcodes.DUP);
					
								generateArrayElementUpdate(awInfo);
							}
						}

						visitJumpInsn(Opcodes.GOTO, lblEndCR);

						visitLabel(lblBranch);
			
						i++;
					}

					// there was no match (generated bytecode must consider also this hypothetical situation that will not happen in practice)
					// we should pop the integer value (the result of non-deterministic choice) from the dynamic stack frame
					visitInsn(Opcodes.POP);
				}

				// default case: when there is no match to non-deterministic choice or we have no information about possible return values
	
				// load dummy value on the stack frame

				if (retType.getSort() == Type.OBJECT)
				{
					// create new object with default values of all fields
	
					ASMUtils.generateLoadDefaultValue(this, retType);
				}
				else if (retType.getSort() == Type.ARRAY)
				{
					// create new array (empty with length 0)

					String elementTypeName = Utils.getElementTypeFromArrayClassName(retTypeStr);
					
					ASMUtils.generateNewArray(this, elementTypeName, new ConstantExpression(new Integer(0)));
				}
				else // primitive type
				{
					// load the default value of the corresponding type

					ASMUtils.generateLoadDefaultValue(this, retType);
				}

				visitLabel(lblEndCR);
			}
		}

		public void visitMaxs(int maxStack, int maxLocals)
		{
			if (Configuration.DEBUG)
			{
				// possible additional filter
				//if (curMethodSig.startsWith("..."))

				if (MethodAbstractionCV.debugASM == null) System.err.println("[NEAG] debugASM == null, curMethodSig = " + curMethodSig);
	
				System.err.println("[DEBUG] printing ASMifier (" + MethodAbstractionCV.debugASM.hashCode() + ") output for method: " + curMethodSig);

				System.err.flush();

				java.io.PrintWriter pwErr = new java.io.PrintWriter(System.err);

				MethodAbstractionCV.debugASM.print(pwErr);

				pwErr.flush();
			}

			super.visitMaxs(maxStack, maxLocals);
		}

		public void visitEnd()
		{
		}

		public void generateFieldUpdate(FieldWriteInfo fwInfo)
		{
			ASMUtils.generateLoadExpression(this, fwInfo.newValue);
					
			if (fwInfo.isStatic)
			{
				visitFieldInsn(Opcodes.PUTSTATIC, Utils.getInternalClassName(fwInfo.className), fwInfo.fieldName, Utils.getInternalTypeName(fwInfo.fieldType));
			}
			else
			{
				visitFieldInsn(Opcodes.PUTFIELD, Utils.getInternalClassName(fwInfo.className), fwInfo.fieldName, Utils.getInternalTypeName(fwInfo.fieldType));
			}
		}
		
		public void generateArrayElementUpdate(ArrayWriteInfo awInfo)
		{
			ASMUtils.generateLoadIntegerConstant(this, awInfo.elementIndex);
						
			ASMUtils.generateLoadExpression(this, awInfo.newValue);
			
			Type arrayElemType = Type.getType(Utils.getInternalTypeName(awInfo.elementType));
			ASMUtils.generateArrayElementStore(this, arrayElemType);
		}
	}
}

