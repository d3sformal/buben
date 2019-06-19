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

public class HeapReferenceExpression extends Expression
{
	public long objectID;
	public String descriptor;
	
	public HeapReferenceExpression(long objID, String desc)
	{
		this.objectID = objID;
		this.descriptor = desc;
	}
	
	public boolean equals(Object obj)
	{
		if (obj == null) return false;
		
		if ( ! (obj instanceof HeapReferenceExpression) ) return false;
		
		HeapReferenceExpression other = (HeapReferenceExpression) obj;
		
		if (this.objectID != other.objectID) return false;
		if ( ! this.descriptor.equals(other.descriptor) ) return false;
		
		return true;
	}
	
	public int hashCode()
	{
		return (int) this.objectID;
	}

	protected String createStringRepr()
	{
		return descriptor + "@" + objectID;
	}
}
