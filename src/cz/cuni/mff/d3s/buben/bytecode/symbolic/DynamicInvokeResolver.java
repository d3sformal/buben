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

public class DynamicInvokeResolver
{
	public static String getTargetClassName(String methodName, String methodDesc)
	{
		// static fixed translation table
		
		if (methodName.equals("updateSystemColors")) return "java.awt.SystemColor";
		
		if (methodName.equals("queryFrom")) return "java.time.temporal.TemporalQuery";
		
		if (methodName.equals("run")) return "java.lang.Runnable";
		
		if (methodName.equals("createValue")) return "javax.swing.UIDefaults";
		
		if (methodName.equals("compare")) return "java.util.Comparator";
	
		if (methodName.equals("apply"))
		{
			if (methodDesc.endsWith("LongFunction;")) return "java.util.function.LongFunction";
		}

		return null;
	}
}
