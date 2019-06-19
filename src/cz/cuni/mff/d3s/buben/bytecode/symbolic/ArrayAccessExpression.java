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

public class ArrayAccessExpression extends Expression
{
	public Expression targetArrayObj;
	public String arrayClassName;
	public Expression elementIndex;
	public String elementType;
	
	private int hc;
	
	public ArrayAccessExpression(Expression arrayObj, String arrClsName, Expression idx, String type)
	{
		this.targetArrayObj = arrayObj;
		this.arrayClassName = arrClsName;
		this.elementIndex = idx;
		this.elementType = type;
		
		hc = 0;
	}
	
	public boolean equals(Object obj)
	{
		if (obj == null) return false;
		
		if ( ! (obj instanceof ArrayAccessExpression) ) return false;
		
		ArrayAccessExpression other = (ArrayAccessExpression) obj;
		
		if ( ! this.targetArrayObj.equals(other.targetArrayObj) ) return false;
		if ( ! this.arrayClassName.equals(other.arrayClassName) ) return false;
		if ( ! this.elementIndex.equals(other.elementIndex) ) return false;
		if ( ! this.elementType.equals(other.elementType) ) return false;
		
		return true;
	}
	
	public int hashCode()
	{
		if (hc == 0)
		{
			hc = hc * 31 + this.targetArrayObj.hashCode();
			hc = hc * 31 + this.arrayClassName.hashCode();
			hc = hc * 31 + this.elementIndex.hashCode();
			hc = hc * 31 + this.elementType.hashCode();
		}
		
		return hc;
	}

	protected String createStringRepr()
	{
		return targetArrayObj.toString() + "[" + elementIndex.toString() + "]";
	}
}
