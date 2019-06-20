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
package cz.cuni.mff.d3s.buben;

import java.util.List;
import java.util.ArrayList;

import java.io.FileReader;
import java.io.BufferedReader;


public class Utils
{
	public static String extractClassName(String methodSig)
	{
		int k = methodSig.lastIndexOf('.');
		String className = methodSig.substring(0, k);
		return className;
	}
	
	public static String extractPlainMethodName(String methodSig)
	{
		int k1 = methodSig.lastIndexOf('.');
		int k2 = methodSig.indexOf('(');
		String methodName = methodSig.substring(k1+1,k2);
		return methodName;
	}
	
	public static String extractMethodParamRetDescriptor(String fullMethodSig)
	{
		int k = fullMethodSig.indexOf('(');
		String mthParamRetDesc = fullMethodSig.substring(k);
		return mthParamRetDesc;
	}

	public static String extractMethodDescriptor(String fullMethodSig)
	{
		int k = fullMethodSig.lastIndexOf('.');
		String mthDesc = fullMethodSig.substring(k+1);
		return mthDesc;
	}
	
	public static String extractMethodReturnType(String fullMethodSig)
	{
		int k = fullMethodSig.indexOf(')');
		String mthRetType = fullMethodSig.substring(k+1);
		return mthRetType;
	}
	
	public static String getPlainTypeName(String internalTypeName)
	{
		int curPos = 0;
		
		// count array dimensions
		int arrayDims = 0;
		while (internalTypeName.charAt(curPos) == '[')
		{
			curPos++;
			arrayDims++;
		}
		
		String plainTypeName = "";
		
		switch (internalTypeName.charAt(curPos))
		{
			case 'Z':
				plainTypeName = "boolean";
				break;
			case 'B':
				plainTypeName = "byte";
				break;
			case 'C':
				plainTypeName = "char";
				break;
			case 'I':
				plainTypeName = "int";
				break;
			case 'S':
				plainTypeName = "short";
				break;
			case 'J':
				plainTypeName = "long";
				break;
			case 'F':
				plainTypeName = "float";
				break;
			case 'D':
				plainTypeName = "double";
				break;
			case 'V':
				plainTypeName = "void";
				break;
			default: // references
				// handles type name both with and without the character 'L' at the beginning
				plainTypeName = getPlainClassName(internalTypeName.substring(curPos));
				break;
		}
		
		for (int i = 0; i < arrayDims; i++)
		{
			plainTypeName = plainTypeName + "[]";
		}
		
		return plainTypeName;
	}
	
	public static String getPlainClassName(String internalClassName)
	{
		int startPos = 0;
		int endPos = internalClassName.length();
		
		// skip the "L" character at the beginning if present
		if (internalClassName.charAt(startPos) == 'L') startPos++;

		// omit the ";" character at the end if present
		if (internalClassName.charAt(endPos - 1) == ';') endPos--;
		
		return internalClassName.substring(startPos, endPos).replace('/', '.');
	}
	
	public static String getInternalTypeName(String plainTypeName)
	{
		// count array dimensions
		int arrayDims = 0;
		int k = plainTypeName.indexOf('[');
		if (k > 0) arrayDims = (plainTypeName.length() - k) / 2;
	
		String internalTypeName = "";

		if (plainTypeName.startsWith("boolean")) internalTypeName = "Z";
		if (plainTypeName.startsWith("byte")) internalTypeName = "B";
		if (plainTypeName.startsWith("char")) internalTypeName = "C";
		if (plainTypeName.startsWith("int")) internalTypeName = "I";
		if (plainTypeName.startsWith("short")) internalTypeName = "S";
		if (plainTypeName.startsWith("long")) internalTypeName = "J";
		if (plainTypeName.startsWith("float")) internalTypeName = "F";
		if (plainTypeName.startsWith("double")) internalTypeName = "D";
		if (plainTypeName.startsWith("void")) internalTypeName = "V";

		if (internalTypeName.equals(""))
		{
			// we have a reference type (plain class name)

			int clsNameEndPos = plainTypeName.length() - 2 * arrayDims;
	
			String plainClassName = plainTypeName.substring(0, clsNameEndPos);

			internalTypeName = getInternalClassName(plainClassName);
		}
	
		for (int i = 0; i < arrayDims; i++)
		{
			internalTypeName = "[" + internalTypeName;
		}

		return internalTypeName;
	}
	
	public static String getInternalClassName(String plainClassName)
	{
		return getInternalClassName(plainClassName, true);
	}
	
	public static String getInternalClassName(String plainClassName, boolean withSemicolon)
	{
		return "L" + plainClassName.replace('.', '/') + (withSemicolon ? ";" : "");
	}
	
	public static String getElementTypeFromArrayClassName(String plainArrayClassName)
	{
		// remove "[]" from the end
		return plainArrayClassName.substring(0, plainArrayClassName.length() - 2);
	}
	
	public static String getArrayClassNameFromElementType(String plainElementTypeName)
	{
		return plainElementTypeName + "[]";
	}

	public static boolean isPlainArrayTypeName(String plainTypeName)
	{
		return plainTypeName.contains("[]");
	}

	public static String createArrayObjectDescriptor(String elementTypeName, int arrayLength)
	{
		return elementTypeName + "[" + arrayLength + "]";
	}
	
	public static boolean isArrayObjectDescriptor(String descriptor)
	{
		int k1 = descriptor.indexOf('[');
		int k2 = descriptor.indexOf(']');

		// we require at least one digit in the brackets
		return (k1 > 0) && (k2 > 0) && (k2 > k1 + 1);
	}

	public static String getElementTypeFromArrayObjectDescriptor(String descriptor)
	{
		int k = descriptor.indexOf('[');

		return descriptor.substring(0, k);
	}

	public static int getLengthFromArrayObjectDescriptor(String descriptor)
	{
		int k1 = descriptor.indexOf('[');
		int k2 = descriptor.indexOf(']');

		String lengthStr = descriptor.substring(k1+1, k2);

		return Integer.parseInt(lengthStr);
	}

	public static boolean isPrimitiveType(String plainTypeName)
	{
		if (plainTypeName.equals("boolean")) return true;
		if (plainTypeName.equals("byte")) return true;
		if (plainTypeName.equals("char")) return true;
		if (plainTypeName.equals("int")) return true;
		if (plainTypeName.equals("short")) return true;
		if (plainTypeName.equals("long")) return true;
		if (plainTypeName.equals("float")) return true;
		if (plainTypeName.equals("double")) return true;

		return false;
	}

	public static boolean isTypeWithSizeTwoWords(String plainTypeName)
	{
		if (plainTypeName.equals("long")) return true;
		if (plainTypeName.equals("double")) return true;

		return false;
	}

	public static boolean isJavaStandardLibraryMethod(String methodSig)
	{
		if (methodSig.startsWith("java.")) return true;
		if (methodSig.startsWith("javax.")) return true;
		if (methodSig.startsWith("org.xml.sax.")) return true;
		if (methodSig.startsWith("jdk.net.")) return true;

		return false;
	}
	
	public static boolean isJavaStandardLibraryClass(String clsName)
	{
		if (clsName.startsWith("java.")) return true;
		if (clsName.startsWith("javax.")) return true;
		if (clsName.startsWith("org.xml.sax.")) return true;
		if (clsName.startsWith("jdk.net.")) return true;

		return false;
	}
	
	public static boolean isJavaInternalLibraryClass(String clsName)
	{
		if (clsName.startsWith("sun.")) return true;

		return false;
	}

	public static boolean isInnerClassName(String clsName)
	{
		return (clsName.indexOf('$') >= 0);
	}

	public static String extractOuterClassName(String innerClsName)
	{
		int k = innerClsName.indexOf('$');

		// we do not have an inner class name actually
		if (k == -1) return innerClsName;

		String outerClsName = innerClsName.substring(0, k);

		return outerClsName;
	}

	public static boolean isClassNameWithPrefix(String className, List<String> prefixes)
	{
		for (String prefix : prefixes)
		{
			if (className.startsWith(prefix)) return true;
		}
		
		return false;	
	}

	public static boolean isMethodSignatureWithPrefix(String methodSig, List<String> prefixes)
	{
		for (String prefix : prefixes)
		{
			if (methodSig.startsWith(prefix)) return true;
		}
		
		return false;	
	}

	public static boolean isMethodWithoutCallArgumentsReturnValue(String fullMethodSig)
	{
		if (fullMethodSig.endsWith("()V")) return true;

		return false;
	}

	public static boolean isMethodWithoutReturnValue(String fullMethodSig)
	{
		if (fullMethodSig.endsWith(")V")) return true;

		return false;
	}

	public static int extractMethodParamCount(String fullMethodSig)
	{
		int k1 = fullMethodSig.indexOf('(');
		int k2 = fullMethodSig.indexOf(')');

		if (k1 + 1 >= k2) return 0;

		String paramDescStr = fullMethodSig.substring(k1+1, k2);

		String[] paramDescArr = paramDescStr.split(",");

		return paramDescArr.length;
	}

	public static List<String> loadTextFileAsStringPerLine(String fileName) throws Exception
	{
		List<String> lines = new ArrayList<String>();

		if (fileName == null) return lines;

		// read all lines
		BufferedReader br = new BufferedReader(new FileReader(fileName));
		String line = null;
		while ( (line = br.readLine()) != null ) lines.add(line);
		br.close();

		return lines;
	}
}
