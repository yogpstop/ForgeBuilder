package com.yogpc.fb.fix;

import java.io.File;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.yogpc.fb.sa.Utils;

public class FFFixer extends ClassVisitor {
  private static final class Appender extends MethodVisitor {
    private boolean done = false;

    public Appender(final MethodVisitor mv) {
      super(Opcodes.ASM4, mv);
    }

    @Override
    public void visitVarInsn(final int opcode, final int var) {
      if (this.mv != null) {
        this.mv.visitVarInsn(opcode, var);
        if (!this.done && opcode == Opcodes.ASTORE) {
          final Label a = new Label();
          final Label b = new Label();
          this.mv.visitVarInsn(Opcodes.ALOAD, var);
          this.mv.visitJumpInsn(Opcodes.IFNULL, a);
          this.mv.visitVarInsn(Opcodes.ALOAD, var);
          this.mv.visitFieldInsn(Opcodes.GETFIELD, "aK", "h", "Ljava/util/HashMap;");
          this.mv.visitJumpInsn(Opcodes.IFNULL, a);
          this.mv.visitJumpInsn(Opcodes.GOTO, b);
          this.mv.visitLabel(a);
          this.mv.visitInsn(Opcodes.ACONST_NULL);
          this.mv.visitInsn(Opcodes.ARETURN);
          this.mv.visitLabel(b);
          this.done = true;
        }
      }
    }
  }

  private FFFixer(final ClassVisitor cv) {
    super(Opcodes.ASM4, cv);
  }

  public static void main(final String[] argv) throws Exception {
    if (argv.length != 1)
      return;
    final File arg = new File(argv[0]);
    final ClassReader cr = new ClassReader(Utils.fileToByteArray(arg));
    final ClassWriter cw = new ClassWriter(0);
    final FFFixer fixer = new FFFixer(cw);
    cr.accept(fixer, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    Utils.byteArrayToFile(cw.toByteArray(), arg);
  }

  @Override
  public MethodVisitor visitMethod(final int access, final String name, final String desc,
      final String signature, final String[] exceptions) {
    if (this.cv != null) {
      if ("a".equals(name) && "(LaK;LcX;LaG;)LaJ;".equals(desc))
        return new Appender(this.cv.visitMethod(access, name, desc, signature, exceptions));
      return this.cv.visitMethod(access, name, desc, signature, exceptions);
    }
    return null;
  }
}
