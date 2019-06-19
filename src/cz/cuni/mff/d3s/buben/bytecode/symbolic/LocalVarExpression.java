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

public class LocalVarExpression extends Expression
{
	public int varSlot;
	public String varName;
	public String varType;

	public boolean isMthParam;

	
	public LocalVarExpression(int slot, String name, String type, boolean param)
	{
		this.varSlot = slot;
		this.varName = name;
		this.varType = type;

		this.isMthParam = param;
	}
	
	public boolean equals(Object obj)
	{
		if (obj == null) return false;
		
		if ( ! (obj instanceof LocalVarExpression) ) return false;
		
		LocalVarExpression other = (LocalVarExpression) obj;
		
		if (this.varSlot != other.varSlot) return false;
		if ( ! this.varName.equals(other.varName) ) return false;
		if ( ! this.varType.equals(other.varType) ) return false;
		if (this.isMthParam != other.isMthParam) return false;
		
		return true;
	}
	
	public int hashCode()
	{
		return (this.varName.hashCode() * 31 + this.varType.hashCode()) * 31 + (this.isMthParam ? 1 : 0);
	}

	protected String createStringRepr()
	{
		return varName;
	}
}

