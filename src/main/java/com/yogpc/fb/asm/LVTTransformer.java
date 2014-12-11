package com.yogpc.fb.asm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

public class LVTTransformer {
  private final TransformRule ops;
  private final boolean gradle;
  private int ii;

  LVTTransformer(final TransformRule m, final boolean g) {
    this.ops = m;
    this.gradle = g;
    final String tmp = this.ops.getMarker("max_constructor_index");
    if (tmp != null)
      this.ii = Integer.parseInt(tmp);
    else
      this.ii = 1000;
  }

  private void generate(final String cls, final String nam, final String pdsc,
      final List<String> params, final List<Type> types) {
    if (types.size() <= params.size())
      return;
    String fid = null;
    if (nam.equals("<init>")) {
      final List<String> old = this.ops.getParams(cls, nam, pdsc);
      if (old.size() > 0)
        fid = old.get(0);
      if (fid == null || !fid.matches("p_i\\d+_\\d+_"))
        fid = Integer.toString(this.ii++);
      else
        fid = fid.substring(3, fid.indexOf('_', 3));
    } else if (nam.matches("func_\\d+_.+"))
      fid = nam.substring(5, nam.indexOf('_', 5));
    else
      fid = nam;
    int i = params.size(), j = i;
    while (i < types.size()) {
      params.add("p_" + fid + "_" + Integer.toString(j) + "_");
      final String d = types.get(i++).getDescriptor();
      j += d.equals("J") || d.equals("D") ? 2 : 1;
    }
  }

  private void processLVT(final String cls, final MethodNode mn) {
    final List<String> params = this.ops.getParams(cls, mn.name, mn.desc);
    final List<Type> types = new ArrayList<Type>();
    if ((mn.access & Opcodes.ACC_STATIC) == 0) {
      types.add(Type.getType("L" + cls + ";"));
      params.add(0, "this");
    }
    types.addAll(Arrays.asList(Type.getArgumentTypes(mn.desc)));
    if (this.gradle)
      generate(cls, mn.name, mn.desc, params, types);
    if (types.size() != params.size())
      return;
    if (mn.instructions.size() == 0)
      mn.instructions.add(new LabelNode());
    else {
      if (!(mn.instructions.getFirst() instanceof LabelNode))
        mn.instructions.insert(new LabelNode());
      if (!(mn.instructions.getLast() instanceof LabelNode))
        mn.instructions.add(new LabelNode());
    }
    final LabelNode start = (LabelNode) mn.instructions.getFirst();
    final LabelNode end = (LabelNode) mn.instructions.getLast();
    mn.localVariables = new ArrayList<LocalVariableNode>();
    int x = 0, y = x;
    while (x < params.size()) {
      final String arg = params.get(x);
      final String dsc = types.get(x++).getDescriptor();
      if (!arg.equals(""))
        mn.localVariables.add(new LocalVariableNode(arg, dsc, null, start, end, y));
      y += dsc.equals("J") || dsc.equals("D") ? 2 : 1;
    }
  }

  void process(final ClassNode cn) {
    for (final MethodNode mn : cn.methods) {
      if (mn.name.equals("<clinit>") || (mn.access & Opcodes.ACC_ABSTRACT) != 0
          || (mn.access & Opcodes.ACC_NATIVE) != 0)
        continue;
      processLVT(cn.name, mn);
    }
  }
}
