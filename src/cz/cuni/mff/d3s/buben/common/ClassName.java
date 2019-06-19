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

public class ClassName extends ObjectID
{
	private String nameStr;
	
	private ClassName(String nameStr)
	{
		super();
		
		this.nameStr = nameStr;
	}
	
	public static ClassName createFromString(String nameStr)
	{
		return new ClassName(nameStr);
	}

	public String getAsString()
	{
		return this.nameStr;
	}
	
	public boolean equals(Object obj)
	{
		if (obj == null) return false;
		
		if ( ! (obj instanceof ClassName) ) return false;
		
		ClassName other = (ClassName) obj;
		
		if ( ! this.nameStr.equals(other.nameStr) ) return false;
		
		return true;
	}
	
	public int hashCode()
	{
		return this.nameStr.hashCode();
	}

	protected String createStringRepr()
	{
		return this.nameStr;
	}
}
