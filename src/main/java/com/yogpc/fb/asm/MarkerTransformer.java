package com.yogpc.fb.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

public class MarkerTransformer {
  private final TransformRule ops;

  MarkerTransformer(final TransformRule m) {
    this.ops = m;
  }

  void process(final ClassNode cn) {
    if ((cn.access & Opcodes.ACC_INTERFACE) != 0)
      return;
    final String m = this.ops.getMarker(cn.name);
    if (m == null)
      return;
    cn.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
        "__OBFID", "Ljava/lang/String;", null, m));
  }
}
