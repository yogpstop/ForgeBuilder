package com.yogpc.fb.dep;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
    MainTransformer.read_jar(arg, null, this.cp, null, null);
  }

  public Collection<String> process(final File arg) throws Exception {
    final Collection<String> done = new ArrayList<String>();
    final Collection<String> todo = new ArrayList<String>();
    MainTransformer.read_jar(arg, todo, this.cp, null, null);
    do {
      for (final String s : todo) {
        done.add(s);
        if (!this.cp.containsKey(s))
          continue;
        this.cp.get(s).accept(AsmFixer.InitAdapter(new ClassNode(), this));
      }
      todo.clear();
      todo.addAll(this.names.keySet());
      this.names.clear();
      todo.removeAll(done);
    } while (todo.size() > 0);
    return done;
  }
}
