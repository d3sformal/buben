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

import cz.cuni.mff.d3s.buben.Utils;


public class ObjectTypesData
{
	// map from class names to constructor signatures
	private static Map<String, Set<String>> clsName2ConstructorSignatures;

	private static Set<String> interfaceTypeNames;

	// map from interface name to the set of implementing classes
	private static Map<String, Set<String>> itfName2ImplementingClasses;

	static
	{
		clsName2ConstructorSignatures = new HashMap<String, Set<String>>();
		interfaceTypeNames = new HashSet<String>();
		itfName2ImplementingClasses = new HashMap<String, Set<String>>();
	}


	public static String getConstructorSignatureForClass(String className)
	{
		Set<String> clsNames = new HashSet<String>();
		clsNames.add(className);

		return getConstructorSignature(clsNames, new Stack<String>());
	}

	public static String getConstructorSignature(Set<String> classNames, Stack<String> mthSigsToIgnore)
	{
		for (String clsName : classNames)
		{
			Set<String> constructorSigs = clsName2ConstructorSignatures.get(clsName);

			// no information about constructors is now available
			if (constructorSigs == null) return null;
			if (constructorSigs.isEmpty()) return null;

			// take the constructor with the least number of parameters (ideally the non-parametric constructor)
			
			int minConstrParamNum = Integer.MAX_VALUE;
			String selectedConstrSig = null;
		
			for (String constrSig : constructorSigs)
			{
				if (mthSigsToIgnore.contains(constrSig)) continue;

				int paramNum = Utils.extractMethodParamCount(constrSig);

				if (paramNum < minConstrParamNum)
				{
					minConstrParamNum = paramNum;
					selectedConstrSig = constrSig;
				}
			}

			// all constructors for this class were ignored
			if (selectedConstrSig == null) continue;

			return selectedConstrSig;
		}

		// all possible constructors for all relevant classes were ignored
		return null;
	}

	public static void addConstructorSignature(String className, String mthInitSig)
	{
		Set<String> constrSigs = clsName2ConstructorSignatures.get(className);

		if (constrSigs == null)
		{
			constrSigs = new HashSet<String>();
			clsName2ConstructorSignatures.put(className, constrSigs);
		}

		constrSigs.add(mthInitSig);
	}

	public static void storeInterfaceTypeNames(Set<String> itfNames)
	{
		interfaceTypeNames.addAll(itfNames);
	}

	public static boolean isInterfaceType(String className)
	{
		return interfaceTypeNames.contains(className);
	}

	public static void storeImplementingClassesForInterface(String itfName, Set<String> implClassNames)
	{
		itfName2ImplementingClasses.put(itfName, implClassNames);
	}

	public static Set<String> getImplementingClassesForInterface(String itfName)
	{
		// returns all the class names

		Set<String> implClassNames = itfName2ImplementingClasses.get(itfName);

		// no information available
		if (implClassNames == null) return null;
		if (implClassNames.isEmpty()) return null;

		return implClassNames;
	}

}

