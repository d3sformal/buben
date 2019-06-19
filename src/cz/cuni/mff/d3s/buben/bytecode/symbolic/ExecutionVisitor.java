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

import java.util.List;

import cz.cuni.mff.d3s.buben.common.ProgramPoint;


public interface ExecutionVisitor
{
	void visitArrayLoadInsn(ProgramPoint pp, Expression arrayObj, String arrayClassName, Expression elementIndex, String elementType);
	
	void visitArrayStoreInsn(ProgramPoint pp, Expression arrayObj, String arrayClassName, Expression elementIndex, String elementType, Expression newValue);

	void visitGetInsn(ProgramPoint pp, Expression obj, String className, String fieldName, String fieldType, boolean isStatic);

	void visitInvokeInsn(ProgramPoint pp, String methodSig, boolean isStaticCall, Expression obj, List<Expression> arguments);
	
	void visitLoadInsn(ProgramPoint pp, LocalVarExpression localVar);
	
	void visitNewObjectInsn(ProgramPoint pp, NewObjectExpression newObj);
		
	void visitNewArrayInsn(ProgramPoint pp, NewArrayExpression newArray);
	
	void visitPutInsn(ProgramPoint pp, Expression obj, String className, String fieldName, String fieldType, boolean isStatic, Expression newValue);
	
	void visitReturnInsn(ProgramPoint pp, Expression retValue);
	
	void visitStoreInsn(ProgramPoint pp, LocalVarExpression localVar, Expression newValue);
}
