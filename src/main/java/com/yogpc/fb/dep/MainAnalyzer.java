package com.yogpc.fb.dep;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

import com.yogpc.fb.asm.MainTransformer;
import com.yogpc.fb.fix.AsmFixer;

public class MainAnalyzer extends Remapper {
  private final Map<String, Object> names = new HashMap<String, Object>();
  private final Map<String, ClassNode> cp = new HashMap<String, ClassNode>();

  @Override
  public String map(final String name) {
    this.names.put(name, name);
    return super.map(name);
  }

  public void addCP(final File arg) throws IOException {
    final InputStream is = new FileInputStream(arg);
    MainTransformer.read_jar(is, null, this.cp, null, null);
    is.close();
  }

  public List<String> process(final File arg) throws Exception {
    final List<String> done = new ArrayList<String>();
    final Collection<String> todo = new ArrayList<String>();
    final InputStream is = new FileInputStream(arg);
    MainTransformer.read_jar(is, todo, this.cp, null, null);
    is.close();
    do {
      for (final String s : todo) {
        done.add(s);
        if (!this.cp.containsKey(s)) {
          if (!s.startsWith("java/") && !s.startsWith("javax/"))
            ;// TODO no stdlib missing class?
          continue;
        }
        this.cp.get(s).accept(AsmFixer.InitAdapter(new ClassNode(), this));
      }
      todo.clear();
      todo.addAll(this.names.keySet());
      this.names.clear();
      for (final String s : done)
        todo.remove(s);
    } while (todo.size() > 0);
    return done;
  }
}
