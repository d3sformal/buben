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
package cz.cuni.mff.d3s.buben.jpf;

import java.util.Set;

import java.io.File;
import java.io.FileOutputStream;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import gov.nasa.jpf.vm.MJIEnv;

import cz.cuni.mff.d3s.buben.Utils;
import cz.cuni.mff.d3s.buben.transform.ASMUtils;


/**
 * Tool expects plain method names combined with descriptors in this format: doSmth(int,java.lang.String,java.lang.Object[])long.
 */
public class NativePeerGenerator
{
	public static final String PEER_PACKAGE_NAME = "cz.cuni.mff.d3s.buben.jpf.vm";
	public static final String PEER_SUPERCLS_NAME = "gov.nasa.jpf.vm.NativePeer";
	public static final String PEER_METHOD_DESC_PREFIX = "Lgov/nasa/jpf/vm/MJIEnv;I";
	public static final String PEER_METHOD_ANNOTATION = "gov.nasa.jpf.annotation.MJI";
	public static final String MJIENV_CLASS = "gov.nasa.jpf.vm.MJIEnv";


	public static void createNativePeer(String className, Set<String> methodsPlainNameDesc, String clsFileDir) throws Exception
	{
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

		// class header
	
		String peerClsName = PEER_PACKAGE_NAME + ".JPF_" + className.replace('.', '_'); 

		cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER, peerClsName.replace('.', '/'), null, PEER_SUPERCLS_NAME.replace('.', '/'), null);

		// instance constructor
	
		MethodVisitor mvCtor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		mvCtor.visitCode();
		mvCtor.visitVarInsn(Opcodes.ALOAD, 0);
		mvCtor.visitMethodInsn(Opcodes.INVOKESPECIAL, "gov/nasa/jpf/vm/NativePeer", "<init>", "()V", false);
		mvCtor.visitInsn(Opcodes.RETURN);
		mvCtor.visitMaxs(1,1);
		mvCtor.visitEnd();

		// create all peer methods

		for (String mthNameDesc : methodsPlainNameDesc)
		{
			int k1 = mthNameDesc.indexOf('(');
			int k2 = mthNameDesc.indexOf(')');

			String mthName = mthNameDesc.substring(0, k1);

			String[] mthArgTypes = null;
			if (k1 + 1 < k2) mthArgTypes = mthNameDesc.substring(k1 + 1, k2).split(",");
			else mthArgTypes = new String[0];

			String mthRetType = mthNameDesc.substring(k2 + 1);

			// create the mangled name of the peer method (following the MJI guidelines)

			String peerMthName = "";
			
			peerMthName += mthName;
			
			peerMthName += "__";

			for (String mthArgType : mthArgTypes)
			{
				peerMthName += Utils.getInternalTypeName(mthArgType).replace('/', '_').replace(";", "_2").replace("[", "_3");
			}

			peerMthName += "__";

			peerMthName += Utils.getInternalTypeName(mthRetType).replace('/', '_').replace(";", "_2").replace("[", "_3");

			// create descriptor for the peer method

			String peerMthDesc = "";
			
			peerMthDesc += "(";

			peerMthDesc += PEER_METHOD_DESC_PREFIX;

			for (String mthArgType : mthArgTypes)
			{
				// ITN = internal type name
				String argITN = Utils.getInternalTypeName(mthArgType);

				if ((argITN.charAt(0) == 'L') || (argITN.charAt(0) == '['))
				{
					// objects/arrays modeled by "int" (reference, heap address)
					peerMthDesc += "I";
				}
				else
				{
					// primitive types and void
					peerMthDesc += argITN;
				}
			}

			peerMthDesc += ")";

			// ITN = internal type name
			String retITN = Utils.getInternalTypeName(mthRetType);

			if ((retITN.charAt(0) == 'L') || (retITN.charAt(0) == '['))
			{
				// objects/arrays modeled by "int" (reference, heap address)
				peerMthDesc += "I";
			}
			else
			{
				// primitive types and void
				peerMthDesc += retITN;
			}

			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, peerMthName, peerMthDesc, null, null);

			// required @MJI annotation

			AnnotationVisitor av = mv.visitAnnotation(Utils.getInternalTypeName(PEER_METHOD_ANNOTATION), true);
			av.visitEnd();

			// method body

			mv.visitCode();

			// return default value (0, null, void)

			Type retTypeObj = Type.getType(retITN);

			generateLoadDefaultValueMJI(mv, retTypeObj);
			
			generateReturnForTypeMJI(mv, retTypeObj);

			// completion of the method code

			mv.visitMaxs(1,1);

			mv.visitEnd();
		}

		cw.visitEnd();

		// save the class to file
	
		String clsFilePath = clsFileDir + File.separator + PEER_PACKAGE_NAME.replace('.', File.separatorChar) + File.separator + "JPF_" + className.replace('.', '_') + ".class"; 

		// create all the necessary directories
		new File(clsFilePath).getParentFile().mkdirs();

		byte[] ba = cw.toByteArray();
		FileOutputStream outf = new FileOutputStream(clsFilePath);
		outf.write(ba);
		outf.close();
	}
	
	public static void generateLoadDefaultValueMJI(MethodVisitor mv, Type type)
	{
		if (type == Type.VOID_TYPE) return;
		
		if (type.getSort() == Type.OBJECT)
		{
			// objects modeled by "int" (reference, heap address)
	
			String plainClassName = Utils.getPlainTypeName(type.getInternalName());

			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitLdcInsn(plainClassName);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MJIENV_CLASS.replace('.', '/'), "newObject", "(Ljava/lang/String;)I", false);

			return;
		}

		if (type.getSort() == Type.ARRAY)
		{
			// arrays modeled by "int" (reference, heap address)
	
			String elementTypeName = Utils.getElementTypeFromArrayClassName(Utils.getPlainTypeName(type.getInternalName()));

			mv.visitVarInsn(Opcodes.ALOAD, 1);

			if ( ! Utils.isPrimitiveType(elementTypeName) ) mv.visitLdcInsn(elementTypeName);
	
			mv.visitInsn(Opcodes.ICONST_0);

			if (elementTypeName.equals("boolean"))
			{
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MJIENV_CLASS.replace('.', '/'), "newBooleanArray", "(I)I", false);
				return;
			}
	
			if (elementTypeName.equals("byte"))
			{
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MJIENV_CLASS.replace('.', '/'), "newByteArray", "(I)I", false);
				return;
			}

			if (elementTypeName.equals("char"))
			{
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MJIENV_CLASS.replace('.', '/'), "newCharArray", "(I)I", false);
				return;
			}

			if (elementTypeName.equals("short"))
			{
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MJIENV_CLASS.replace('.', '/'), "newShortArray", "(I)I", false);
				return;
			}

			if (elementTypeName.equals("int"))
			{
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MJIENV_CLASS.replace('.', '/'), "newIntArray", "(I)I", false);
				return;
			}

			if (elementTypeName.equals("long"))
			{
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MJIENV_CLASS.replace('.', '/'), "newLongArray", "(I)I", false);
				return;
			}

			if (elementTypeName.equals("float"))
			{
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MJIENV_CLASS.replace('.', '/'), "newFloatArray", "(I)I", false);
				return;
			}

			if (elementTypeName.equals("double"))
			{
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MJIENV_CLASS.replace('.', '/'), "newDoubleArray", "(I)I", false);
				return;
			}

			// array of objects (references)
	
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MJIENV_CLASS.replace('.', '/'), "newObjectArray", "(Ljava/lang/String;I)I", false);
		
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
		
		// the default case (int, char, short, byte, boolean)
		mv.visitInsn(Opcodes.ICONST_0);
	}

	public static void generateReturnForTypeMJI(MethodVisitor mv, Type retType)
	{
		if (retType == Type.VOID_TYPE)
		{
			mv.visitInsn(Opcodes.RETURN);
		}
		else if ((retType.getSort() == Type.ARRAY) || (retType.getSort() == Type.OBJECT))
		{
			// objects/arrays modeled by "int" (reference, heap address)
	
			mv.visitInsn(Opcodes.IRETURN);
		}
		else
		{
			mv.visitInsn(retType.getOpcode(Opcodes.IRETURN));
		}
	}

}

