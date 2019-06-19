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
package cz.cuni.mff.d3s.buben.summaries;

import java.util.Set;
import java.util.List;
import java.util.Map;

import cz.cuni.mff.d3s.buben.common.AllocationSite;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.Expression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.FieldAccessExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.ArrayAccessExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.NewArrayExpression;
import cz.cuni.mff.d3s.buben.bytecode.symbolic.NewObjectExpression;


/**
 * Summary for the given method (signature) and nested calls (transitively).
 */
public class MethodSideEffectSummary
{
	public String methodSig;
	
	// complete field access paths updated in the method
	// the set of possible new values for each updated field
	public Map<FieldAccessExpression, Set<Expression>> updatedFields2Values;
	
	// complete array access expressions updated in the method
	// the set of possible new values for each updated array element
	public Map<ArrayAccessExpression, Set<Expression>> updatedArrays2Values;
	
	// newly allocated objects
	public Set<NewObjectExpression> newObjects;

	// newly allocated arrays
	public Set<NewArrayExpression> newArrays;
	
	// parameters that may be returned from the method
	// index of every such parameter
	public Set<Integer> returnedParams;
	
	// returned expressions
	// provides also information saying whether the returned value may be a new object/array (allocated inside the method)
	public Set<Expression> returnValues;
	
	// list of field access paths that are updated outside of any synchronized block in the method's code
	public Set<FieldAccessExpression> unsynchFields;
	
	// list of array element access expressions that are updated outside of any synchronized block in the method's code
	public Set<ArrayAccessExpression> unsynchArrays;
	
	
	public MethodSideEffectSummary(String mthSig, Map<FieldAccessExpression, Set<Expression>> updatedFields2Values, Map<ArrayAccessExpression, Set<Expression>> updatedArrays2Values, Set<NewObjectExpression> newObjects, Set<NewArrayExpression> newArrays, Set<Integer> returnedParams, Set<Expression> returnValues, Set<FieldAccessExpression> unsynchFields, Set<ArrayAccessExpression> unsynchArrays)
	{
		this.methodSig = mthSig;
		
		this.updatedFields2Values = updatedFields2Values;
		this.updatedArrays2Values = updatedArrays2Values;
		
		this.newObjects = newObjects;
		this.newArrays = newArrays;
	
		this.returnedParams = returnedParams;
		this.returnValues = returnValues;
		
		this.unsynchFields = unsynchFields;
		this.unsynchArrays = unsynchArrays;
	}
	
	public boolean equals(Object obj)
	{
		if (obj == null) return false;
		
		if ( ! (obj instanceof MethodSideEffectSummary) ) return false;

		MethodSideEffectSummary other = (MethodSideEffectSummary) obj;

		if ( ! this.methodSig.equals(other.methodSig) ) return false;
		
		if ( ! this.updatedFields2Values.equals(other.updatedFields2Values) ) return false;
		if ( ! this.updatedArrays2Values.equals(other.updatedArrays2Values) ) return false;
		
		if ( ! this.newObjects.equals(other.newObjects) ) return false;
		if ( ! this.newArrays.equals(other.newArrays) ) return false;
	
		if ( ! this.returnedParams.equals(other.returnedParams) ) return false;
		if ( ! this.returnValues.equals(other.returnValues) ) return false;
		
		if ( ! this.unsynchFields.equals(other.unsynchFields) ) return false;
		if ( ! this.unsynchArrays.equals(other.unsynchArrays) ) return false;
		
		return true;
	}
	
	public int hashCode()
	{
		int hc = 0;
		
		hc = methodSig.hashCode();
		
		hc = hc * 31 + updatedFields2Values.hashCode();
		hc = hc * 31 + updatedArrays2Values.hashCode();
		
		hc = hc * 31 + newObjects.hashCode();
		hc = hc * 31 + newArrays.hashCode();
	
		hc = hc * 31 + returnedParams.hashCode();
		hc = hc * 31 + returnValues.hashCode();
		
		hc = hc * 31 + unsynchFields.hashCode();
		hc = hc * 31 + unsynchArrays.hashCode();
		
		return hc;
	}
}
