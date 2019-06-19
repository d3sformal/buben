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

import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;

import cz.cuni.mff.d3s.buben.Utils;


public class ExpressionUtils
{
	public static Expression extractRootObjectAccessPath(Expression accessExpr)
	{
		if (accessExpr instanceof FieldAccessExpression)
		{
			FieldAccessExpression fieldExpr = (FieldAccessExpression) accessExpr;
			
			return extractRootObjectAccessPath(fieldExpr.targetObj);
		}
		
		if (accessExpr instanceof ArrayAccessExpression)
		{
			ArrayAccessExpression arrayExpr = (ArrayAccessExpression) accessExpr;
			
			return extractRootObjectAccessPath(arrayExpr.targetArrayObj);
		}
		
		// local variable, class name, special
		return accessExpr;
	}
	
	public static Expression extractRootObjectFieldAccessPath(FieldAccessExpression faExpr)
	{
		if (faExpr.targetObj instanceof FieldAccessExpression)
		{
			FieldAccessExpression feTargetObj = (FieldAccessExpression) faExpr.targetObj;
			
			return extractRootObjectFieldAccessPath(feTargetObj);
		}
		
		// local variable, array element, class name, special
		return faExpr.targetObj;
	}
	
	public static void extractRootExprsForAccessPaths(Expression expr, Set<Expression> roots)
	{
		if (expr instanceof LocalVarExpression) roots.add(expr);
		
		if (expr instanceof NewObjectExpression) roots.add(expr);
		if (expr instanceof NewArrayExpression) roots.add(expr);

		if (expr instanceof FieldAccessExpression)
		{
			FieldAccessExpression fieldExpr = (FieldAccessExpression) expr;
			
			extractRootExprsForAccessPaths(fieldExpr.targetObj, roots);
		}
		
		if (expr instanceof ArrayAccessExpression)
		{
			ArrayAccessExpression arrayExpr = (ArrayAccessExpression) expr;
			
			extractRootExprsForAccessPaths(arrayExpr.targetArrayObj, roots);
		}
	
		if (expr instanceof ArithmeticExpression)
		{
			ArithmeticExpression arithmExpr = (ArithmeticExpression) expr;
		
			extractRootExprsForAccessPaths(arithmExpr.value1, roots);
			extractRootExprsForAccessPaths(arithmExpr.value2, roots);
		}
	}

	public static FieldAccessExpression replaceRootObjectAccessPath(FieldAccessExpression fieldExpr, Expression newRootExpr)
	{
		// default
		Expression newTargetObj = newRootExpr;
		
		if (fieldExpr.targetObj instanceof FieldAccessExpression)
		{
			newTargetObj = replaceRootObjectAccessPath((FieldAccessExpression) fieldExpr.targetObj, newRootExpr);
		}
		
		if (fieldExpr.targetObj instanceof ArrayAccessExpression)
		{
			newTargetObj = replaceRootObjectAccessPath((ArrayAccessExpression) fieldExpr.targetObj, newRootExpr);
		}
		
		return new FieldAccessExpression(newTargetObj, fieldExpr.className, fieldExpr.fieldName, fieldExpr.fieldType, fieldExpr.isStatic);
	}
	
	public static ArrayAccessExpression replaceRootObjectAccessPath(ArrayAccessExpression arrayExpr, Expression newRootExpr)
	{
		// default
		Expression newTargetArrayObj = newRootExpr;
		
		if (arrayExpr.targetArrayObj instanceof FieldAccessExpression)
		{
			newTargetArrayObj = replaceRootObjectAccessPath((FieldAccessExpression) arrayExpr.targetArrayObj, newRootExpr);
		}
		
		if (arrayExpr.targetArrayObj instanceof ArrayAccessExpression)
		{
			newTargetArrayObj = replaceRootObjectAccessPath((ArrayAccessExpression) arrayExpr.targetArrayObj, newRootExpr);
		}
		
		return new ArrayAccessExpression(newTargetArrayObj, arrayExpr.arrayClassName, arrayExpr.elementIndex, arrayExpr.elementType);	
	}
	
	public static void dropLocalVarsNotParams(Set<Expression> expressions)
	{
		Iterator<Expression> it = expressions.iterator();
		
		while (it.hasNext())
		{
			Expression expr = it.next();

			if (containsAccessPathFromLocalVar(expr)) it.remove();
		}
	}
	
	public static boolean containsAccessPathFromLocalVar(Expression expr)
	{
		Set<Expression> roots = new HashSet<Expression>();

		extractRootExprsForAccessPaths(expr, roots);
		
		for (Expression rootExpr : roots)
		{
			if (rootExpr instanceof LocalVarExpression)
			{
				LocalVarExpression lvExpr = (LocalVarExpression) rootExpr;
			
				if ( ! lvExpr.isMthParam ) return true; 
			}
		}

		return false;
	}
	
	public static boolean containsAccessPathFromLocalExpr(Expression expr)
	{
		Set<Expression> roots = new HashSet<Expression>();

		extractRootExprsForAccessPaths(expr, roots);
		
		for (Expression rootExpr : roots)
		{
			if (rootExpr instanceof LocalVarExpression)
			{
				LocalVarExpression lvExpr = (LocalVarExpression) rootExpr;
			
				if ( ! lvExpr.isMthParam ) return true; 
			}
	
			if (rootExpr instanceof NewObjectExpression) return true;
			if (rootExpr instanceof NewArrayExpression) return true;
		}
		
		return false;
	}

	public static boolean containsAccessPathFromThis(Expression expr)
	{
		Set<Expression> roots = new HashSet<Expression>();

		extractRootExprsForAccessPaths(expr, roots);
		
		for (Expression rootExpr : roots)
		{
			if (rootExpr instanceof LocalVarExpression)
			{
				LocalVarExpression lvExpr = (LocalVarExpression) rootExpr;
		
				return (lvExpr.varSlot == 0);
			}
	
			if (rootExpr instanceof NewObjectExpression) return false;
			if (rootExpr instanceof NewArrayExpression) return false;
		}
	
		return false;	
	}

	public static void filterAccessPathsByPrefixes(Set<Expression> expressions, Set<Expression> availablePrefixes)
	{
		Iterator<Expression> it = expressions.iterator();		
		
		while (it.hasNext())
		{
			Expression expr = it.next();

			String exprStr = expr.toString();

			boolean keep = false;

			// look for prefixes of the expression
			// keep the expression if we find some
			
			for (Expression prefix : availablePrefixes)
			{
				if (exprStr.startsWith(prefix.toString()))
				{
					keep = true;
					break;
				}
			}

			if ( ! keep ) it.remove();
		}
	}
	
	public static void dropMultidimensionalArrays(Set<Expression> expressions)
	{
		Iterator<Expression> it = expressions.iterator();
		
		while (it.hasNext())
		{
			Expression expr = it.next();

			if (expr instanceof NewObjectExpression)
			{
				NewObjectExpression noExpr = (NewObjectExpression) expr;

				if (noExpr.className.getAsString().endsWith("[][]")) it.remove();
			}
		}
	}

	public static String getExprTypeName(Expression expr)
	{
		if (expr instanceof ArrayAccessExpression)
		{
			ArrayAccessExpression arrExpr = (ArrayAccessExpression) expr;
			
			return arrExpr.elementType;
		}
		
		if (expr instanceof FieldAccessExpression)
		{
			FieldAccessExpression fieldExpr = (FieldAccessExpression) expr;
			
			return fieldExpr.fieldType;
		}
		
		if (expr instanceof LocalVarExpression)
		{
			LocalVarExpression lvExpr = (LocalVarExpression) expr;
			
			return lvExpr.varType;
		}
		
		if (expr instanceof NewArrayExpression)
		{
			NewArrayExpression naExpr = (NewArrayExpression) expr;
			
			return naExpr.arrayClassName.getAsString();
		}
		
		if (expr instanceof NewObjectExpression)
		{
			NewObjectExpression noExpr = (NewObjectExpression) expr;
			
			return noExpr.className.getAsString();
		}
	
		if (expr instanceof ReturnValueExpression)
		{
			ReturnValueExpression retExpr = (ReturnValueExpression) expr;
			
			String mthRetType = Utils.extractMethodReturnType(retExpr.methodSig);
			
			return Utils.getPlainTypeName(mthRetType);
		}

		if (expr instanceof ConstantExpression)
		{
			ConstantExpression constExpr = (ConstantExpression) expr;

			if (constExpr.value instanceof Integer) return "int";
			if (constExpr.value instanceof Long) return "long";
			if (constExpr.value instanceof Float) return "float";
			if (constExpr.value instanceof Double) return "double";

			if (constExpr.value instanceof String) return "java.lang.String";

			if (constExpr.value instanceof ClassNameExpression) return "java.lang.Class";
		}

		if (expr instanceof ArithmeticExpression)
		{
			ArithmeticExpression arithmExpr = (ArithmeticExpression) expr;

			// we assume that both operands have the same type
			return getExprTypeName(arithmExpr.value1);
		}
		
		// dummy value
		// execution should not reach this point anyway		
		return null;
	}

	public static String getArrayExprTypeName(Expression arrayObjExpr)
	{
		return getExprTypeName(arrayObjExpr);
	}

	public static int getNumericConstantValue(ConstantExpression constExpr)
	{
		if (constExpr.value instanceof Number)
		{
			Number num = (Number) constExpr.value;

			return num.intValue();
		}

		return 0;
	}

	public static boolean checkLocalityOfArrayElementIndex(ArrayAccessExpression arrayExpr)
	{
		if (arrayExpr.elementIndex instanceof LocalVarExpression)
		{
			LocalVarExpression lvElemIndex = (LocalVarExpression) arrayExpr.elementIndex;

			// it is a method call argument (local0...localN)
			if (lvElemIndex.isMthParam) return false;
		}

		if (arrayExpr.elementIndex instanceof FieldAccessExpression)
		{
			FieldAccessExpression faElemIdx = (FieldAccessExpression) arrayExpr.elementIndex;

			Expression rootObj = extractRootObjectFieldAccessPath(faElemIdx);

			if (rootObj instanceof LocalVarExpression)
			{
				LocalVarExpression lvRootObj = (LocalVarExpression) rootObj;

				// it is a field access path that starts with a method call argument (including "this")
				if (lvRootObj.isMthParam) return false;
			}
		}

		return true;
	}
}
