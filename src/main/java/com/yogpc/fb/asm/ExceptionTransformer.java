package com.yogpc.fb.asm;

import java.util.List;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class ExceptionTransformer {
  private final TransformRule ops;

  ExceptionTransformer(final TransformRule m) {
    this.ops = m;
  }

  private void processException(final String cls, final MethodNode mn) {
    final List<String> map = this.ops.getExceptions(cls, mn.name, mn.desc);
    for (final String s : mn.exceptions)
      if (!map.contains(s))
        map.add(s);
    if (map.size() > 0)
      mn.exceptions = map;
  }

  void process(final ClassNode cn) {
    for (final MethodNode mn : cn.methods) {
      if (mn.name.equals("<clinit>"))
        continue;
      processException(cn.name, mn);
    }
  }
}
