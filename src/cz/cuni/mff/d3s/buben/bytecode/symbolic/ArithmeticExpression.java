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

public class ArithmeticExpression extends Expression
{
	public String operator;
	public Expression value1;
	public Expression value2;
	
	private int hc;
	
	
	public ArithmeticExpression(String op, Expression v1, Expression v2)
	{
		this.operator = op;
		this.value1 = v1;
		this.value2 = v2;
	}
	
	public boolean equals(Object obj)
	{
		if (obj == null) return false;
		
		if ( ! (obj instanceof ArithmeticExpression) ) return false;
		
		ArithmeticExpression other = (ArithmeticExpression) obj;
		
		if ( ! this.operator.equals(other.operator) ) return false;
		if ( ! this.value1.equals(other.value1) ) return false;
		if ( ! this.value2.equals(other.value2) ) return false;
		
		return true;
	}
	
	public int hashCode()
	{
		if (hc == 0)
		{
			hc = this.operator.hashCode();
			hc = hc * 31 + this.value1.hashCode();
			hc = hc * 31 + this.value2.hashCode();
		}
		
		return hc;
	}

	protected String createStringRepr()
	{
		return "(" + value1 + " " + operator + " " + value2 + ")";
	}
}
