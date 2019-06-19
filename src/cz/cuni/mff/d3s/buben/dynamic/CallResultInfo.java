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

import java.util.List;

import cz.cuni.mff.d3s.buben.bytecode.symbolic.Expression;


public class CallResultInfo
{
	public Expression returnValue;

	
	public CallResultInfo()
	{
	}
	
	public boolean equals(Object obj)
	{
		if (obj == null) return false;
		
		if ( ! (obj instanceof CallResultInfo) ) return false;
		
		CallResultInfo other = (CallResultInfo) obj;
		
		if (this.returnValue == null)
		{
			if (other.returnValue != null) return false;
		}
		else
		{
			if ( ! this.returnValue.equals(other.returnValue) ) return false;
		}
		
		return true;
	}
	
	public int hashCode()
	{
		int hc = 0;
		
		if (this.returnValue != null) hc = hc * 31 + this.returnValue.hashCode();
		
		return hc;
	}

	public String toString()
	{
		StringBuffer sb = new StringBuffer();
		
		sb.append("(...)");
		
		sb.append(" -> ");
		
		if (returnValue != null) sb.append(returnValue.toString());
		else sb.append("void");

		return sb.toString();
	}
}
