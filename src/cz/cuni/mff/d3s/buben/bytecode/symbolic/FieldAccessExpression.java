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

public class FieldAccessExpression extends Expression
{
	public Expression targetObj;
	public String className;
	public String fieldName;
	public String fieldType;
	public boolean isStatic;
	
	private int hc;
	
	public FieldAccessExpression(Expression obj, String cname, String fname, String ftype, boolean st)
	{
		this.targetObj = obj;
		this.className = cname;
		this.fieldName = fname;
		this.fieldType = ftype;
		this.isStatic = st;
		
		hc = 0;
	}
	
	public boolean equals(Object obj)
	{
		if (obj == null) return false;
		
		if ( ! (obj instanceof FieldAccessExpression) ) return false;
		
		FieldAccessExpression other = (FieldAccessExpression) obj;
		
		if ( ! this.targetObj.equals(other.targetObj) ) return false;
		if ( ! this.className.equals(other.className) ) return false;
		if ( ! this.fieldName.equals(other.fieldName) ) return false;
		if ( ! this.fieldType.equals(other.fieldType) ) return false;
		
		return true;
	}
	
	public int hashCode()
	{
		if (hc == 0)
		{
			hc = hc * 31 + this.targetObj.hashCode();
			hc = hc * 31 + this.className.hashCode();
			hc = hc * 31 + this.fieldName.hashCode();
			hc = hc * 31 + this.fieldType.hashCode();
		}
		
		return hc;
	}

	protected String createStringRepr()
	{
		return targetObj.toString() + "." + fieldName;
	}
}
