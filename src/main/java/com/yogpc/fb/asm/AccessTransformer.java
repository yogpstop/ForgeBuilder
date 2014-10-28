package com.yogpc.fb.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class AccessTransformer {
  private final TransformRule ops;

  AccessTransformer(final TransformRule m) {
    this.ops = m;
  }

  static final int CHANGE_FINAL = Opcodes.ACC_STATIC;

  private static boolean greaterThan(final int i, final int j) {
    int im, jm;
    switch (i) {
      case Opcodes.ACC_PUBLIC:
        im = 4;
        break;
      case Opcodes.ACC_PROTECTED:
        im = 3;
        break;
      case Opcodes.ACC_PRIVATE:
        im = 1;
        break;
      default:
        im = 2;
    }
    switch (j) {
      case Opcodes.ACC_PUBLIC:
        jm = 4;
        break;
      case Opcodes.ACC_PROTECTED:
        jm = 3;
        break;
      case Opcodes.ACC_PRIVATE:
        jm = 1;
        break;
      default:
        jm = 2;
    }
    return im > jm;
  }

  static int getAccessFlags(final int a, final int m) {
    int b = a;
    if (greaterThan(m & 7, b & 7)) {
      b &= ~7;
      b |= m & 7;
    }
    if ((m & CHANGE_FINAL) != 0) {
      b &= ~Opcodes.ACC_FINAL;
      b |= m & Opcodes.ACC_FINAL;
    }
    return b;
  }

  void process(final ClassNode cn) {
    int i = this.ops.getAccessClass(cn.name);
    if (i > -1)
      cn.access = getAccessFlags(cn.access, i);
    for (final MethodNode mn : cn.methods) {
      i = this.ops.getAccessMethod(cn.name, mn.name, mn.desc);
      if (i > -1)
        mn.access = getAccessFlags(mn.access, i);
    }
    for (final FieldNode fn : cn.fields) {
      i = this.ops.getAccessField(cn.name, fn.name);
      if (i > -1)
        fn.access = getAccessFlags(fn.access, i);
    }
  }

}
