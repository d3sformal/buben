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

import cz.cuni.mff.d3s.buben.Utils;
import cz.cuni.mff.d3s.buben.common.ClassName;
import cz.cuni.mff.d3s.buben.common.AllocationSite;


public class NewArrayExpression extends Expression
{
	public ClassName arrayClassName;
	public AllocationSite allocSite;
	
	// the default length is 0
	public Expression arrayLength;


	public NewArrayExpression(ClassName acname, AllocationSite as)
	{
		this.arrayClassName = acname;
		this.allocSite = as;

		this.arrayLength = new ConstantExpression(new Integer(0));
	}
	
	public boolean equals(Object obj)
	{
		if (obj == null) return false;
		
		if ( ! (obj instanceof NewArrayExpression) ) return false;
		
		NewArrayExpression other = (NewArrayExpression) obj;
		
		if ( ! this.arrayClassName.equals(other.arrayClassName) ) return false;
		if ( ! this.allocSite.equals(other.allocSite) ) return false;
		if ( ! this.arrayLength.equals(other.arrayLength) ) return false;

		return true;
	}
	
	public int hashCode()
	{
		int hc = 0;

		hc = hc * 31 + this.arrayClassName.hashCode();
		hc = hc * 31 + this.allocSite.hashCode();
		hc = hc * 31 + this.arrayLength.hashCode();

		return hc;
	}

	protected String createStringRepr()
	{
		return "newarray <" + Utils.getElementTypeFromArrayClassName(arrayClassName.toString()) + "[" + arrayLength.toString() + "]" + " @ " + allocSite.toString() + ">";
	}
}
