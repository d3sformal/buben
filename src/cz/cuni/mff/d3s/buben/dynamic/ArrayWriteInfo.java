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
package cz.cuni.mff.d3s.buben.dynamic;

import cz.cuni.mff.d3s.buben.bytecode.symbolic.Expression;


public class ArrayWriteInfo
{
	public String className;
	public ReferenceID objectRef;
	
	public int elementIndex;
	
	public String elementType;
	
	public Expression newValue;
	
	
	public ArrayWriteInfo(String cName, ReferenceID oRef, int eIdx, String eType, Expression nVal)
	{
		this.className = cName;
		this.objectRef = oRef;
		this.elementIndex = eIdx;
		this.elementType = eType;
		this.newValue = nVal;
	}
	
	public boolean equals(Object obj)
	{
		if (obj == null) return false;
		
		if ( ! (obj instanceof ArrayWriteInfo) ) return false;
		
		ArrayWriteInfo other = (ArrayWriteInfo) obj;
		
		if ( ! this.className.equals(other.className) ) return false;
		if ( ! this.objectRef.equals(other.objectRef) ) return false;
		if (this.elementIndex != other.elementIndex) return false;
		if ( ! this.elementType.equals(other.elementType) ) return false;
		if ( ! this.newValue.equals(other.newValue) ) return false;
		
		return true;
	}
	
	public int hashCode()
	{
		int hc = 0;
		
		hc = hc * 31 + this.className.hashCode();
		hc = hc * 31 + this.objectRef.hashCode();
		hc = hc * 31 + this.elementIndex;
		hc = hc * 31 + this.elementType.hashCode();
		hc = hc * 31 + this.newValue.hashCode();
		
		return hc;
	}

	public String toString()
	{
		StringBuffer sb = new StringBuffer();
		
		sb.append(className);
		sb.append("@");
		sb.append(objectRef);
		sb.append("[");
		sb.append(elementIndex);
		sb.append("]");
		sb.append(" := ");
		sb.append(newValue.toString());
		
		return sb.toString();
	}
}
