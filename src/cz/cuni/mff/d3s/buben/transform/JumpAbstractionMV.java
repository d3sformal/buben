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
package cz.cuni.mff.d3s.buben.transform;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.Label;


/**
 * Utility method visitor that we use to remove bytecode instructions JSR and RET.
 */
public class JumpAbstractionMV extends MethodVisitor
{
	// this label will point to the next instruction after the first JSR within the method code
	// it will be used as the target of all RET instructions
	private Label lblAfterFirstJSR = null;

	public JumpAbstractionMV(MethodVisitor mv)
	{
		super(Opcodes.ASM5, mv);
	}

	public void visitJumpInsn(int insnOpcode, Label targetLabel)
	{
		// we replace the JSR instruction by GOTO with the same target label
		if (insnOpcode == Opcodes.JSR)
		{
			// we must also put the target instruction position on the stack
			// here we can use a dummy value (0) because it is not read later
			ASMUtils.generateLoadIntegerConstant(this, 0);

			visitJumpInsn(Opcodes.GOTO, targetLabel);

			// first JSR that we reached
			if (lblAfterFirstJSR == null)
			{
				lblAfterFirstJSR = new Label();

				visitLabel(lblAfterFirstJSR);
			}
		}
		else
		{
			super.visitJumpInsn(insnOpcode, targetLabel);
		}
	}

	public void visitVarInsn(int insnOpcode, int localVar)
	{
		// we replace the RET instruction by GOTO to the stored label (next after the first JSR)
		if (insnOpcode == Opcodes.RET)
		{
			visitJumpInsn(Opcodes.GOTO, lblAfterFirstJSR);
		}
		else
		{
			super.visitVarInsn(insnOpcode, localVar);
		}
	}
}

