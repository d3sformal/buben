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
import java.util.Set;
import java.util.HashSet;


public class Configuration
{
	public static boolean DEBUG = false;

	// application class with the "main" method
	public static String targetMainClassName;
	
	// directory that contains Java classes in a binary format (compiled)
	public static String targetClassPath;

	// command-line arguments for the "main" method
	public static List<String> runtimeCmdArgs;

	// exclusion file identifies library classes that are ignored by static analysis (WALA)
	public static String walaExclusionFilePath;

	// these strings represent prefixes of full signatures for library methods
	// all other methods belong to the client program (mostly application classes)
	public static List<String> libraryMethodPrefixes;
	
	// these strings represent prefixes of fully qualified names of application classes
	// some classes in the list may belong to the application only partially (for example, when they contain also library methods)
	public static List<String> applicationClassPrefixes;
	
	// prefixes of methods that access (communicate with) external entities such as file or network
	public static List<String> extaccessMethodPrefixes;
	
	// prefixes of fully qualified names of classes whose all methods access external entities
	// it should be a sublist of "extaccessMethodPrefixes" (it is user's responsibility to specify both lists correctly)
	public static List<String> extaccessClassPrefixes;
	
	// classes that represent unit tests for the application (extend particular superclass or properly annotated, can be executed using the JUnit API)
	public static List<String> testClassNames;

	// driver classes that contain the "main" procedure (they can be executed directly) and also represent tests
	public static List<String> driverClassNames;

	// maximal allowed number of possible return values from a library method
	public static int maxReturnValues;
	
	public static String JDI_PORT = "45123";
	
	public static String VERIFY_CLASS = "gov.nasa.jpf.vm.Verify";

	public static int DEFAULT_MAX_RETURN_VALUES = 256;

	// set of native methods that cannot be abstracted in any way
	public static Set<String> nativeMethodsToIgnore;


	static
	{
		nativeMethodsToIgnore = new HashSet<String>();

		prepareListOfIgnoredNativeMethods();
	}

	
	public static boolean isLibraryMethod(String methodSig)
	{
		if (Utils.isMethodSignatureWithPrefix(methodSig, libraryMethodPrefixes)) return true;
		
		return false;	
	}
	
	public static boolean isApplicationMethod(String methodSig)
	{
		boolean isAppCls = false;
		
		if (Utils.isMethodSignatureWithPrefix(methodSig, applicationClassPrefixes)) isAppCls = true;

		if (isAppCls)
		{
			return ( ! isLibraryMethod(methodSig) );
		}
		
		return false;	
	}
	
	public static boolean isExternalAccessClass(String className)
	{
		return Utils.isClassNameWithPrefix(className, extaccessClassPrefixes);
	}
	
	public static boolean isExternalAccessMethod(String methodSig)
	{
		if (Utils.isMethodSignatureWithPrefix(methodSig, extaccessMethodPrefixes)) return true;
		
		return false;	
	}

	public static boolean isLibraryClass(String className)
	{
		boolean isAppCls = Utils.isClassNameWithPrefix(className, applicationClassPrefixes);
		
		return ( ! isAppCls );
	}

	public static boolean isLibraryInnerClassName(String typeName)
	{
		if (Utils.isInnerClassName(typeName))
		{
			String outerClsName = Utils.extractOuterClassName(typeName);

			if (isLibraryClass(outerClsName)) return true;
		}

		return false;
	}

	private static void prepareListOfIgnoredNativeMethods()
	{
		nativeMethodsToIgnore.add("java.lang.Object.wait(J)V");
		nativeMethodsToIgnore.add("java.lang.Object.notify()V");
		nativeMethodsToIgnore.add("java.lang.Object.notifyAll()V");
		nativeMethodsToIgnore.add("java.lang.Thread.start()V");
		nativeMethodsToIgnore.add("java.lang.Thread.join()V");
	}
}
