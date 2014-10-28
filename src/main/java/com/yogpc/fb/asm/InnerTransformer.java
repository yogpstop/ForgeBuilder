package com.yogpc.fb.asm;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;

public class InnerTransformer {
  private final TransformRule ops;

  InnerTransformer(final TransformRule m) {
    this.ops = m;
  }

  void process(final ClassNode cn) {
    final JsonStruct json = this.ops.getJson(cn.name);
    if (json == null)
      return;
    final JsonStruct.EnclosingMethod enc = json.enclosingMethod;
    if (cn.outerClass == null && enc != null) {
      cn.outerClass = enc.owner;
      cn.outerMethod = enc.name;
      cn.outerMethodDesc = enc.desc;
    }
    final List<String> icns = new ArrayList<String>();
    for (final InnerClassNode icn : cn.innerClasses)
      icns.add(icn.name);
    if (json.innerClasses != null)
      for (final JsonStruct.InnerClass i : json.innerClasses) {
        if (icns.contains(i.inner_class))
          continue;
        int acc = i.getAccess();
        final int accm = this.ops.getAccessClass(i.inner_class);
        if (accm > -1)
          acc = AccessTransformer.getAccessFlags(acc, accm);
        cn.innerClasses.add(new InnerClassNode(i.inner_class, i.outer_class, i.inner_name, acc));
      }
  }
}
