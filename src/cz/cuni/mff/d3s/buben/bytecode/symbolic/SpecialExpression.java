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

public class SpecialExpression extends Expression
{
	public static final SpecialExpression NULL = new SpecialExpression("null");
	
	public static final SpecialExpression EXCEPTION = new SpecialExpression("[exception]");

	
	private String symbol;
	
	private SpecialExpression(String symb)
	{
		this.symbol = symb;
	}
	
	public boolean equals(Object obj)
	{
		if (obj == null) return false;
		
		if ( ! (obj instanceof SpecialExpression) ) return false;
		
		SpecialExpression other = (SpecialExpression) obj;
		
		if ( ! this.symbol.equals(other.symbol) ) return false;
		
		return true;
	}
	
	public int hashCode()
	{
		return this.symbol.hashCode();
	}

	protected String createStringRepr()
	{
		return symbol;
	}
}
