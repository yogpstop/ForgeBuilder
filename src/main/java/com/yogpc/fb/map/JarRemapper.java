package com.yogpc.fb.map;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

import com.yogpc.fb.asm.MainTransformer;
import com.yogpc.fb.fix.AsmFixer;

public class JarRemapper {
  private Map<String, ClassNode> nodes;
  private final Map<String, ClassNode> cp = new HashMap<String, ClassNode>();

  public void addCP(final File arg) throws IOException {
    final InputStream is = new FileInputStream(arg);
    MainTransformer.read_jar(is, null, this.cp, null, null);
    is.close();
  }

  public Map<String, ClassNode> process(final Map<String, ClassNode> in, final int mode,
      final JarMapping jm, final Collection<String> depCls, final Collection<String> mask)
      throws Exception {
    final Remapper r = jm.getRemapper(mode, this);
    this.nodes = in;
    if (depCls != null && mask != null)
      for (final String dep : depCls) {
        boolean skip = true;
        for (final String ms : mask)
          if (dep.startsWith(ms)) {
            skip = false;
            break;
          }
        if (skip)
          continue;
        final ClassNode node = this.cp.get(dep);
        if (node != null)
          this.nodes.put(dep, node);
      }
    final Map<String, ClassNode> ret = new HashMap<String, ClassNode>();
    ClassVisitor mapper;
    ClassNode out;
    for (final Map.Entry<String, ClassNode> e : this.nodes.entrySet()) {
      out = new ClassNode();
      mapper = AsmFixer.InitAdapter(out, r);
      e.getValue().accept(mapper);
      ret.put(r.map(e.getKey()), out);
    }
    return ret;
  }

  Collection<String> getParents(final String className) {
    final Collection<String> ret = new ArrayList<String>();
    ClassNode node = this.nodes.get(className);
    if (node != null) {
      if (node.interfaces != null)
        ret.addAll(node.interfaces);
      if (node.superName != null)
        ret.add(node.superName);
      return ret;
    }
    node = this.cp.get(className);
    if (node != null) {
      if (node.interfaces != null)
        ret.addAll(node.interfaces);
      if (node.superName != null)
        ret.add(node.superName);
      return ret;
    }
    return ret;
  }
}
