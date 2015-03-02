package com.yogpc.fb.map;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import com.yogpc.fb.asm.MainTransformer;
import com.yogpc.fb.sa.Utils;

public class ListMF {
  public static final Comparator<String[]> CMP = new Comparator<String[]>() {
    @Override
    public int compare(final String[] o1, final String[] o2) {
      return Incrementer.CMP.compare(o1[0], o2[0]);
    }
  };

  public static void main(final String[] args) throws Exception {
    final Map<String, Map<String, Set<String[]>>> methods =
        new TreeMap<String, Map<String, Set<String[]>>>(Incrementer.CMP);
    final Map<String, Map<String, Set<String>>> fields =
        new TreeMap<String, Map<String, Set<String>>>(Incrementer.CMP);
    final MainTransformer trans = new MainTransformer(9999, null);
    Map<String, ClassNode> tmp = trans.process_jar(args[0], null);
    final JarMapping jm = new JarMapping();
    final JarRemapper jr = new JarRemapper();
    MappingBuilder.loadNew(Utils.fileToString(new File(args[1]), Utils.UTF_8), jm, false);
    tmp = jr.process(tmp, JarMapping.OBF_SRG, jm, null, null);
    for (final ClassNode cn : tmp.values()) {
      final Map<String, Set<String[]>> cm = new TreeMap<String, Set<String[]>>(Incrementer.CMP);
      methods.put(cn.name, cm);
      for (final MethodNode mn : cn.methods) {
        final int i = mn.desc.indexOf(')') + 1;
        final String arg = mn.desc.substring(0, i);
        Set<String[]> cdm = cm.get(arg);
        if (cdm == null)
          cm.put(arg, cdm = new TreeSet<String[]>(CMP));
        cdm.add(new String[] {mn.name, mn.desc.substring(i)});
      }
      final Map<String, Set<String>> cf = new TreeMap<String, Set<String>>(Incrementer.CMP);
      fields.put(cn.name, cf);
      for (final FieldNode fn : cn.fields) {
        Set<String> cdf = cf.get(fn.desc);
        if (cdf == null)
          cf.put(fn.desc, cdf = new TreeSet<String>(Incrementer.CMP));
        cdf.add(fn.name);
      }
    }
    final OutputStream os = new FileOutputStream(new File(args[2]));
    for (final Map.Entry<String, Map<String, Set<String[]>>> e1 : methods.entrySet())
      for (final Map.Entry<String, Set<String[]>> e2 : e1.getValue().entrySet())
        for (final String[] e3 : e2.getValue())
          os.write((e1.getKey() + '.' + e3[0] + e2.getKey() + e3[1] + '\n').getBytes(Utils.UTF_8));
    for (final Map.Entry<String, Map<String, Set<String>>> e1 : fields.entrySet())
      for (final Map.Entry<String, Set<String>> e2 : e1.getValue().entrySet())
        for (final String e3 : e2.getValue())
          os.write((e1.getKey() + '.' + e3 + '=' + e2.getKey() + '\n').getBytes(Utils.UTF_8));
    os.close();
  }
}
