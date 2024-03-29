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
package cz.cuni.mff.d3s.buben.common;

/**
 * Possible symbolic values: numeric constant, local variable name, heap object identification, field access path, array element access expression, complex arithmetic expression.
 * See the package <code>bytecode.symbolic</code>.
 */ 
public abstract class SymbolicValue
{
	protected String strRep;
	
	protected SymbolicValue()
	{
		this.strRep = null;
	}
	
	public String toString()
	{
		if (strRep == null) strRep = createStringRepr();
		
		return strRep;
	}
	
	protected abstract String createStringRepr();
}

