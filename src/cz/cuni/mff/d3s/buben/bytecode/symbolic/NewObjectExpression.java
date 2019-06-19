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

import cz.cuni.mff.d3s.buben.common.ClassName;
import cz.cuni.mff.d3s.buben.common.AllocationSite;


public class NewObjectExpression extends Expression
{
	public ClassName className;
	public AllocationSite allocSite;
	
	// by default, this is "()V", i.e. for the case of a constructor without arguments
	public String mthInitDesc;


	public NewObjectExpression(ClassName cname, AllocationSite as)
	{
		this.className = cname;
		this.allocSite = as;

		this.mthInitDesc = "()V";
	}
	
	public boolean equals(Object obj)
	{
		if (obj == null) return false;
		
		if ( ! (obj instanceof NewObjectExpression) ) return false;
		
		NewObjectExpression other = (NewObjectExpression) obj;
		
		if ( ! this.className.equals(other.className) ) return false;
		if ( ! this.allocSite.equals(other.allocSite) ) return false;
		if ( ! this.mthInitDesc.equals(other.mthInitDesc) ) return false;

		return true;
	}
	
	public int hashCode()
	{
		int hc = 0;

		hc = hc * 31 + this.className.hashCode();
		hc = hc * 31 + this.allocSite.hashCode();
		hc = hc * 31 + this.mthInitDesc.hashCode();

		return hc;
	}

	protected String createStringRepr()
	{
		return "newobj <" + className.toString() + mthInitDesc + " @ " + allocSite.toString() + ">";
	}
}
