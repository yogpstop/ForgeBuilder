package com.yogpc.fb.map;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.TreeMap;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import com.yogpc.fb.asm.MainTransformer;
import com.yogpc.fb.sa.Utils;

public class ListObf {
  public static void main(final String[] args) throws Exception {
    final MainTransformer trans = new MainTransformer(9999, null);
    final Map<String, ClassNode> tmp = trans.process_jar(args[0], null);
    int max_int = 0, max_strlen = 0;
    final Map<String, String> size = new TreeMap<String, String>(Incrementer.CMP);
    for (final Map.Entry<String, ClassNode> f : tmp.entrySet()) {
      final ClassWriter cw = new ClassWriter(0);
      f.getValue().accept(cw);
      final String len = Integer.toString(cw.toByteArray().length);
      size.put(f.getKey(), len);
      max_strlen = Math.max(max_strlen, f.getKey().length());
      max_int = Math.max(max_int, len.length());
    }
    final String fmt =
        String.format("%%%ds %%-%ds\n", Integer.valueOf(max_int), Integer.valueOf(max_strlen));
    final OutputStream os = new FileOutputStream(new File(args[1]));
    for (final Map.Entry<String, String> f : size.entrySet())
      os.write(String.format(fmt, f.getValue(), f.getKey()).getBytes(Utils.UTF_8));
    os.close();
  }
}
