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

public class AssignmentStatement
{
	// symbolic expressions of any kind (field access path, local variable, array element, etc)
	public Expression source;
	public Expression dest;
	
	public AssignmentStatement(Expression s, Expression d)
	{
		this.source = s;
		this.dest = d;
	}
	
	public boolean equals(Object obj)
	{
		if (obj == null) return false;
		
		if ( ! (obj instanceof AssignmentStatement) ) return false;
		
		AssignmentStatement other = (AssignmentStatement) obj;
		
		if ( ! this.source.equals(other.source) ) return false;
		if ( ! this.dest.equals(other.dest) ) return false;
		
		return true;
	}
	
	public int hashCode()
	{
		return this.source.hashCode() * 31 + this.dest.hashCode();
	}

	public String toString()
	{
		return dest.toString() + " := " + source;
	}
}
