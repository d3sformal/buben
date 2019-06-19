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
import java.util.Stack;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import cz.cuni.mff.d3s.buben.Configuration;
import cz.cuni.mff.d3s.buben.Utils;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.*;
import cz.cuni.mff.d3s.buben.summaries.*;


public class ASMUtils implements Opcodes
{
	public static void generateBeginAtomic(MethodVisitor mv)
	{
		mv.visitMethodInsn(INVOKESTATIC, Configuration.VERIFY_CLASS.replace('.', '/'), "beginAtomic", "()V", false);
	}
	
	public static void generateEndAtomic(MethodVisitor mv)
	{
		mv.visitMethodInsn(INVOKESTATIC, Configuration.VERIFY_CLASS.replace('.', '/'), "endAtomic", "()V", false);
	}

	public static void generateLocalIntegerVarStore(MethodVisitor mv, int varSlot)
	{
		mv.visitVarInsn(ISTORE, varSlot);
	}
	
	public static void generateLocalIntegerVarLoad(MethodVisitor mv, int varSlot)
	{
		mv.visitVarInsn(ILOAD, varSlot);
	}
	
	public static void generateLocalVarLoad(MethodVisitor mv, Type varType, int varSlot)
	{
		if (varType.getSort() != Type.OBJECT)
		{
			mv.visitVarInsn(varType.getOpcode(ILOAD), varSlot);
			return;
		}
		
		// otherwise it must be reference
		mv.visitVarInsn(ALOAD, varSlot);
	}

	public static void generateLoadIntegerConstant(MethodVisitor mv, int val)
	{
		if (val == 0) mv.visitInsn(ICONST_0);
		else if (val == 1) mv.visitInsn(ICONST_1);
		else if (val == 2) mv.visitInsn(ICONST_2);
		else if (val == 3) mv.visitInsn(ICONST_3);
		else if (val == 4) mv.visitInsn(ICONST_4);
		else if (val == 5) mv.visitInsn(ICONST_5);
		else mv.visitIntInsn(BIPUSH, val);
	}
	
	public static void generateArrayElementLoad(MethodVisitor mv, Type elemType)
	{
		if (elemType.getSort() != Type.OBJECT)
		{
			mv.visitInsn(elemType.getOpcode(IALOAD));
			return;
		}
		
		// otherwise it must be reference
		mv.visitInsn(AALOAD);
	}
	
	public static void generateArrayElementStore(MethodVisitor mv, Type elemType)
	{
		if (elemType.getSort() != Type.OBJECT)
		{
			mv.visitInsn(elemType.getOpcode(IASTORE));
			return;
		}
		
		// otherwise it must be reference
		mv.visitInsn(AASTORE);
	}
	
	public static void generateReturnForType(MethodVisitor mv, Type retType)
	{
		if (retType == Type.VOID_TYPE) mv.visitInsn(RETURN);
		else if ((retType.getSort() == Type.ARRAY) || (retType.getSort() == Type.OBJECT)) mv.visitInsn(ARETURN);
		else mv.visitInsn(retType.getOpcode(IRETURN));
	}
	
	public static void generateLoadDefaultValue(MethodVisitor mv, Type type)
	{
		generateLoadDefaultValue(mv, type, new Stack<String>());
	}

	public static void generateLoadDefaultValue(MethodVisitor mv, Type type, Stack<String> usedInitSigs)
	{
		if (type == Type.VOID_TYPE) return;
		
		if (type.getSort() == Type.OBJECT)
		{
			Set<String> allMatchingPlainTypeNames = new HashSet<String>();
	
			String plainTypeName = Utils.getPlainTypeName(type.getInternalName());

			if (ObjectTypesData.isInterfaceType(plainTypeName))
			{
				// interfaces do not have constructors, which means we have to look into their implementing classes

				// get all implementing classes (where each of them must have some constructor)
				Set<String> implClasses = ObjectTypesData.getImplementingClassesForInterface(plainTypeName);

				if (implClasses != null) allMatchingPlainTypeNames.addAll(implClasses);
			}
			else
			{
				allMatchingPlainTypeNames.add(plainTypeName);
			}

			// we maintain the set of already used constructors (their signatures) to avoid recursion
			// each constructor is used at most once at each path in the tree of method calls, when creating a top-level object
			
			String mthInitSig = ObjectTypesData.getConstructorSignature(allMatchingPlainTypeNames, usedInitSigs);

			if (mthInitSig == null)
			{
				mv.visitInsn(ACONST_NULL);

				return;
			}

			// block this constructor for nested method calls on the same path in the call tree
			usedInitSigs.push(mthInitSig);

			String mthInitDesc = Utils.extractMethodParamRetDescriptor(mthInitSig);

			generateNewObjectInit(mv, plainTypeName, mthInitDesc, usedInitSigs, false);

			// we do not want to block this constructor for other paths in the method call tree
			usedInitSigs.pop();

			return;
		}
	
		if (type.getSort() == Type.ARRAY)
		{
			String elementTypeName = Utils.getElementTypeFromArrayClassName(Utils.getPlainTypeName(type.getInternalName()));
				
			generateNewArray(mv, elementTypeName, new ConstantExpression(new Integer(0)));

			return;
		}
		
		// numeric values (primitive types)
		
		if (type.getSize() == 2)
		{
			if (type.getSort() == Type.LONG) mv.visitLdcInsn(new Long(0));
			if (type.getSort() == Type.DOUBLE) mv.visitLdcInsn(new Double(0));

			return;
		}
		
		if (type.getSort() == Type.FLOAT)
		{
			mv.visitLdcInsn(new Float(0));
			return;
		}
		
		// the default case
		mv.visitInsn(ICONST_0);
	}
	
	public static void generateNewObjectInit(MethodVisitor mv, String className, String mthInitDesc)
	{
		generateNewObjectInit(mv, className, mthInitDesc, new Stack<String>(), false);
	}
	
	public static void generateNewObjectInit(MethodVisitor mv, String className, String mthInitDesc, boolean skipNew)
	{
		generateNewObjectInit(mv, className, mthInitDesc, new Stack<String>(), skipNew);
	}

	public static void generateNewObjectInit(MethodVisitor mv, String className, String mthInitDesc, Stack<String> usedInitSigs, boolean skipNew)
	{
		/*
		// we use the value "null" to model new objects of classes marked as accessing external entities
		if (Configuration.isExternalAccessClass(className))
		{
			mv.visitInsn(ACONST_NULL);
			return;
		}
		*/

		// we try to create real new objects even for classes marked as accessing external entities
	
		// for cases where the NEW instruction is generated elsewhere to avoid recursion involving this method and visitTypeInsn
		if ( ! skipNew )
		{
			try
			{
				mv.visitTypeInsn(NEW, className.replace('.', '/'));
			}
			catch (Exception ex)
			{
				System.out.println("[DEBUG] failed visitTypeInsn: className = " + className);
				throw ex;
			}
		}

		// we do not generate (in our abstraction process) calls to instance constructors of inner classes whose parent class belongs to some library
		if (Configuration.isLibraryInnerClassName(className)) return;

		mv.visitInsn(DUP);

		generateInvokeConstructorWithParams(mv, className, mthInitDesc, usedInitSigs);
	}

	public static void generateInvokeConstructorWithParams(MethodVisitor mv, String className, String mthInitDesc, Stack<String> usedInitSigs)
	{
		// load arguments for instance constructor
		// we use default values of particular types (0, null)

		Type[] mthInitParamTypes = Type.getArgumentTypes(mthInitDesc);

		for (int j = 0; j < mthInitParamTypes.length; j++)
		{
			Type paramType = mthInitParamTypes[j];

			String paramTypePlainName = null;
			
			// internal name can be safely acquired only for object/reference types
			// when asked for internal name of primitive types ASM throws exception
			if (paramType.getSort() == Type.OBJECT)
			{
				paramTypePlainName = Utils.getPlainTypeName(paramType.getInternalName());
			}

			// if this argument for constructor has the same type as the object being created (i.e., recursion may occur when generating the abstraction) then we use the constant "null" as the argument's value
			if ((paramTypePlainName != null) && className.equals(paramTypePlainName))
			{
				mv.visitInsn(ACONST_NULL);
			}
			else
			{
				// the prevailing scenario
				generateLoadDefaultValue(mv, paramType, usedInitSigs);
			}
		}		

		mv.visitMethodInsn(INVOKESPECIAL, className.replace('.', '/'), "<init>", mthInitDesc, false);
	}
		
	public static void generateNewArray(MethodVisitor mv, String elementTypeName, Expression arrayLength)
	{
		/*
		// we use the value "null" to model new arrays whose element types are classes marked as accessing external entities
		if (Configuration.isExternalAccessClass(elementTypeName))
		{
			mv.visitInsn(ACONST_NULL);
			return;
		}
		*/

		// we try to create real new arrays even for element types being classes marked as accessing external entities

		generateLoadExpression(mv, arrayLength);
	
		if (elementTypeName.equals("boolean")) { mv.visitIntInsn(NEWARRAY, T_BOOLEAN); return; }
		if (elementTypeName.equals("byte")) { mv.visitIntInsn(NEWARRAY, T_BYTE); return; }
		if (elementTypeName.equals("short")) { mv.visitIntInsn(NEWARRAY, T_SHORT); return; }
		if (elementTypeName.equals("char")) { mv.visitIntInsn(NEWARRAY, T_CHAR); return; }
		if (elementTypeName.equals("int")) { mv.visitIntInsn(NEWARRAY, T_INT); return; }
		if (elementTypeName.equals("long")) { mv.visitIntInsn(NEWARRAY, T_LONG); return; }
		if (elementTypeName.equals("float")) { mv.visitIntInsn(NEWARRAY, T_FLOAT); return; }
		if (elementTypeName.equals("double")) { mv.visitIntInsn(NEWARRAY, T_DOUBLE); return; }

		// object/reference
		mv.visitTypeInsn(ANEWARRAY, elementTypeName.replace('.', '/'));
	}

	public static void generateChoiceInteger(MethodVisitor mv, int min, int max)
	{
		generateLoadIntegerConstant(mv, min);
		generateLoadIntegerConstant(mv, max);
		mv.visitMethodInsn(INVOKESTATIC, Configuration.VERIFY_CLASS.replace('.', '/'), "getInt", "(II)I", false);
	}

	
	/**
	 * It returns a type of the loaded expressions.
	 */
	public static String generateLoadExpression(MethodVisitor mv, Expression expr)
	{
		// it may be a complex expression (constant, local variable, field access path, array element, arithmetic)

		if (expr instanceof ConstantExpression)
		{
			ConstantExpression constExpr = (ConstantExpression) expr;
			
			if (constExpr.value instanceof ClassNameExpression)
			{
				ClassNameExpression clsExpr = (ClassNameExpression) constExpr.value;
				
				mv.visitLdcInsn(Type.getObjectType(Utils.getInternalClassName(clsExpr.className)));
				
				return clsExpr.className;
			}
			else if (constExpr.value instanceof Integer)
			{
				Integer intExpr = (Integer) constExpr.value;

				generateLoadIntegerConstant(mv, intExpr.intValue());

				return "int";
			}
			else
			{
				mv.visitLdcInsn(constExpr.value);
				
				return constExpr.value.getClass().getName();
			}
		}
		
		if (expr instanceof LocalVarExpression)
		{
			LocalVarExpression lvExpr = (LocalVarExpression) expr;
			
			generateLocalVarLoad(mv, Type.getType(Utils.getInternalTypeName(lvExpr.varType)), lvExpr.varSlot);
			
			return lvExpr.varType;
		}
		
		if (expr instanceof NewObjectExpression)
		{
			NewObjectExpression noExpr = (NewObjectExpression) expr;
	
			String classNameStr = noExpr.className.getAsString();

			generateNewObjectInit(mv, classNameStr, noExpr.mthInitDesc);
			
			return classNameStr;
		}
	
		if (expr instanceof NewArrayExpression)
		{
			NewArrayExpression naExpr = (NewArrayExpression) expr;
	
			String arrayClassNameStr = naExpr.arrayClassName.getAsString();

			String elementTypeName = Utils.getElementTypeFromArrayClassName(arrayClassNameStr);

			generateNewArray(mv, elementTypeName, naExpr.arrayLength);
			
			return arrayClassNameStr;
		}

		if (expr instanceof HeapReferenceExpression)
		{
			HeapReferenceExpression heapExpr = (HeapReferenceExpression) expr;

			// create new objects (fresh) with the same descriptors

			if (Utils.isArrayObjectDescriptor(heapExpr.descriptor))
			{
				String elementTypeName = Utils.getElementTypeFromArrayObjectDescriptor(heapExpr.descriptor);
				
				int arrayLengthNum = Utils.getLengthFromArrayObjectDescriptor(heapExpr.descriptor);
				Expression arrayLengthExpr = new ConstantExpression(new Integer(arrayLengthNum));
				
				generateNewArray(mv, elementTypeName, arrayLengthExpr);

				return Utils.getArrayClassNameFromElementType(elementTypeName);
			}
			else
			{
				String className = heapExpr.descriptor;

				String mthInitSig = ObjectTypesData.getConstructorSignatureForClass(className);

				if (mthInitSig == null)
				{
					// fallback: use the default constructor
					// it should hopefully work in most cases
					mthInitSig = className + ".<init>()V";
				}

				String mthInitDesc = Utils.extractMethodParamRetDescriptor(mthInitSig);

				generateNewObjectInit(mv, className, mthInitDesc);

				return className;
			}
		}
		
		if (expr instanceof SpecialExpression)
		{
			if (expr == SpecialExpression.NULL) mv.visitInsn(Opcodes.ACONST_NULL);
			
			return null;
		}
		
		if (expr instanceof FieldAccessExpression)
		{
			FieldAccessExpression fieldExpr = (FieldAccessExpression) expr;
			
			generateReadAccessPath(mv, fieldExpr);
			
			return fieldExpr.fieldType;
		}
		
		if (expr instanceof ArrayAccessExpression)
		{
			ArrayAccessExpression arrayExpr = (ArrayAccessExpression) expr;
			
			generateReadAccessPath(mv, arrayExpr);
			
			return arrayExpr.elementType;
		}
		
		if (expr instanceof ArithmeticExpression)
		{
			ArithmeticExpression arithmExpr = (ArithmeticExpression) expr;
			
			String type1 = generateLoadExpression(mv, arithmExpr.value1);
			String type2 = generateLoadExpression(mv, arithmExpr.value2);
			
			return generateArithmeticOperation(mv, type1, type2, arithmExpr.operator);
		}

		if (expr instanceof ReturnValueExpression)
		{
			ReturnValueExpression retExpr = (ReturnValueExpression) expr;
	
			// extract type from the method signature
			String internalRetTypeName = Utils.extractMethodReturnType(retExpr.methodSig);
			String plainRetTypeName = Utils.getPlainTypeName(internalRetTypeName);

			Type retType = Type.getType(internalRetTypeName);

			// we inspect the available summary of the callee method to see if there are defined some return values
			// if the set of possible return values from callee is not empty, then we use the first element (to avoid the possibility of nested non-deterministic choices)
			// otherwise we use the default value of a given type

			MethodSideEffectSummary calleeMthSumm = SideEffectSummaryGenAnalysis.getSummaryForMethod(retExpr.methodSig);
			
			if ( ! calleeMthSumm.returnValues.isEmpty() )
			{
				// get the first element
				Expression calleeRetValue = calleeMthSumm.returnValues.iterator().next();

				generateLoadExpression(mv, calleeRetValue);
			}
			else
			{
				generateLoadDefaultValue(mv, retType);
			}

			return plainRetTypeName;
		}
		
		// dummy value
		// execution should not reach this point anyway
		return null;
	}
	
	public static void generateFieldWrite(MethodVisitor mv, FieldAccessExpression tgtFieldExpr)
	{
		generateReadAccessPath(mv, tgtFieldExpr.targetObj);
			
		if (tgtFieldExpr.isStatic)
		{
			// there is no target object reference in this case, i.e. no need to swap the reference with new value

			mv.visitFieldInsn(Opcodes.PUTSTATIC, Utils.getInternalClassName(tgtFieldExpr.className), tgtFieldExpr.fieldName, Utils.getInternalTypeName(tgtFieldExpr.fieldType));
		}
		else
		{
			// swap target object reference and new value
			mv.visitInsn(Opcodes.SWAP);
	
			mv.visitFieldInsn(Opcodes.PUTFIELD, Utils.getInternalClassName(tgtFieldExpr.className), tgtFieldExpr.fieldName, Utils.getInternalTypeName(tgtFieldExpr.fieldType));
		}
	}
	
	public static void generateArrayElementWrite(MethodVisitor mv, ArrayAccessExpression tgtArrayExpr)
	{
		// check whether element index contains either (1) local variable that is not a method parameter or (2) field access path rooted by a local variable that is not a method parameter
		boolean isLocalElemIndex = ExpressionUtils.checkLocalityOfArrayElementIndex(tgtArrayExpr);

		generateReadAccessPath(mv, tgtArrayExpr.targetArrayObj);
		
		// swap target array object reference and new value
		mv.visitInsn(Opcodes.SWAP);
	
		if ( ! isLocalElemIndex )
		{
			// in this case, element index can be either (1) just some method parameter or (2) a field access path with some method parameter as its root
			
			generateLoadExpression(mv, tgtArrayExpr.elementIndex);
		}
		else
		{
			generateArrayElementIndexChoice(mv, tgtArrayExpr);
		}
		
		// swap the index expression and new value
		mv.visitInsn(Opcodes.SWAP);
		
		Type arrayElemType = Type.getType(Utils.getInternalTypeName(tgtArrayExpr.elementType));
			
		generateArrayElementStore(mv, arrayElemType);
	}
	
	public static void generateReadAccessPath(MethodVisitor mv, Expression accessExpr)
	{
		if (accessExpr instanceof FieldAccessExpression)
		{
			FieldAccessExpression fieldExpr = (FieldAccessExpression) accessExpr;
		
			generateReadAccessPath(mv, fieldExpr.targetObj);
			
			if (fieldExpr.isStatic)
			{
				mv.visitFieldInsn(Opcodes.GETSTATIC, Utils.getInternalClassName(fieldExpr.className), fieldExpr.fieldName, Utils.getInternalTypeName(fieldExpr.fieldType));
			}
			else
			{
				mv.visitFieldInsn(Opcodes.GETFIELD, Utils.getInternalClassName(fieldExpr.className), fieldExpr.fieldName, Utils.getInternalTypeName(fieldExpr.fieldType));
			}
		}
		
		if (accessExpr instanceof ArrayAccessExpression)
		{
			ArrayAccessExpression arrayExpr = (ArrayAccessExpression) accessExpr;
		
			generateReadAccessPath(mv, arrayExpr.targetArrayObj);
			
			// check whether element index contains either (1) local variable that is not a method parameter or (2) field access path rooted by a local variable that is not a method parameter
			boolean isLocalElemIndex = ExpressionUtils.checkLocalityOfArrayElementIndex(arrayExpr);

			if ( ! isLocalElemIndex )
			{
				// in this case, element index can be either (1) just some method parameter or (2) a field access path with some method parameter as its root
				
				generateLoadExpression(mv, arrayExpr.elementIndex);
			}
			else
			{
				generateArrayElementIndexChoice(mv, arrayExpr);
			}

			Type arrayElemType = Type.getType(Utils.getInternalTypeName(arrayExpr.elementType));
			
			generateArrayElementLoad(mv, arrayElemType);
		}
		
		// root of the access path (local variable, new object)
		
		if (accessExpr instanceof LocalVarExpression)
		{
			LocalVarExpression lvExpr = (LocalVarExpression) accessExpr;
		
			mv.visitVarInsn(Opcodes.ALOAD, lvExpr.varSlot);
		}
		
		if (accessExpr instanceof NewObjectExpression)
		{
			NewObjectExpression noExpr = (NewObjectExpression) accessExpr;
			
			String classNameStr = noExpr.className.getAsString();

			generateNewObjectInit(mv, classNameStr, noExpr.mthInitDesc);
		}

		if (accessExpr instanceof NewArrayExpression)
		{
			NewArrayExpression naExpr = (NewArrayExpression) accessExpr;
			
			String arrayClassNameStr = naExpr.arrayClassName.getAsString();

			String elementTypeName = Utils.getElementTypeFromArrayClassName(arrayClassNameStr);

			generateNewArray(mv, elementTypeName, naExpr.arrayLength);
		}

	}
	
	public static String generateArithmeticOperation(MethodVisitor mv, String valType1, String valType2, String operator)
	{
		String commonTypeStr = null;
		
		// ordered by numeric range
		if (valType1.equals("double") || valType2.equals("double")) commonTypeStr = "double";
		else if (valType1.equals("float") || valType2.equals("float")) commonTypeStr = "float";
		else if (valType1.equals("long") || valType2.equals("long")) commonTypeStr = "long";
		else if (valType1.equals("int") || valType2.equals("int")) commonTypeStr = "int";
		else if (valType1.equals("boolean") || valType2.equals("boolean")) commonTypeStr = "int";
	
		mv.visitInsn(Opcodes.SWAP);
		generateNumericalTypeConversion(mv, valType1, commonTypeStr);
		mv.visitInsn(Opcodes.SWAP);
		generateNumericalTypeConversion(mv, valType2, commonTypeStr);
		
		Type commonType = null;
		if (commonTypeStr.equals("double")) commonType = Type.DOUBLE_TYPE;
		if (commonTypeStr.equals("float")) commonType = Type.FLOAT_TYPE;
		if (commonTypeStr.equals("long")) commonType = Type.LONG_TYPE;
		if (commonTypeStr.equals("int")) commonType = Type.INT_TYPE;

		if (operator.equals("+")) mv.visitInsn(commonType.getOpcode(IADD));
		if (operator.equals("-")) mv.visitInsn(commonType.getOpcode(ISUB));
		if (operator.equals("*")) mv.visitInsn(commonType.getOpcode(IMUL));
		if (operator.equals("/")) mv.visitInsn(commonType.getOpcode(IDIV));
		if (operator.equals("%")) mv.visitInsn(commonType.getOpcode(IREM));
		if (operator.equals("&")) mv.visitInsn(commonType.getOpcode(IAND));
		if (operator.equals("|")) mv.visitInsn(commonType.getOpcode(IOR));
		if (operator.equals("^")) mv.visitInsn(commonType.getOpcode(IXOR));
		if (operator.equals("<<")) mv.visitInsn(commonType.getOpcode(ISHL));
		if (operator.equals(">>")) mv.visitInsn(commonType.getOpcode(ISHR));

		return commonTypeStr;
	}
	
	public static void generateNumericalTypeConversion(MethodVisitor mv, String srcType, String destType)
	{
		if (destType.equals("double"))
		{
			if (srcType.equals("int")) mv.visitInsn(Opcodes.I2D);
			if (srcType.equals("long")) mv.visitInsn(Opcodes.L2D);
			if (srcType.equals("float")) mv.visitInsn(Opcodes.F2D);
		}
		
		if (destType.equals("float"))
		{
			if (srcType.equals("int")) mv.visitInsn(Opcodes.I2F);
			if (srcType.equals("long")) mv.visitInsn(Opcodes.L2F);
		}

		if (destType.equals("long"))
		{
			if (srcType.equals("int")) mv.visitInsn(Opcodes.I2L);
		}
	}

	public static void generateArrayElementIndexChoice(MethodVisitor mv, ArrayAccessExpression arrayExpr)
	{
		// we need to generate non-deterministic choice over the interval [0, array.length-1]
		
		// generate code for loading the constant 0 and the length of given array on the dynamic stack

		generateLoadIntegerConstant(mv, 0);

		// load the array reference
		
		generateLoadExpression(mv, arrayExpr.targetArrayObj);

		// array.length - 1
		
		mv.visitInsn(ARRAYLENGTH);
		
		generateLoadIntegerConstant(mv, 1);

		mv.visitInsn(ISUB);

		// both arguments to Verify.getInt are on the dynamic stack in the correct order

		mv.visitMethodInsn(INVOKESTATIC, Configuration.VERIFY_CLASS.replace('.', '/'), "getInt", "(II)I", false);
	}
}

