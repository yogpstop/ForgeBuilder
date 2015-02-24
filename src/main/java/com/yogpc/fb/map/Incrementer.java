package com.yogpc.fb.map;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.commons.Remapper;

import com.yogpc.fb.asm.MainTransformer;
import com.yogpc.fb.map.JarMapping.ObfEntry;
import com.yogpc.fb.map.JarMapping.SrgEntry;
import com.yogpc.fb.sa.Utils;

public class Incrementer extends Remapper {
  private static final Incrementer INSTANCE = new Incrementer();

  public static final Comparator<String> CMP = new Comparator<String>() {
    @Override
    public int compare(final String o1, final String o2) {
      if (o1.length() != o2.length())
        return o1.length() - o2.length();
      return o1.compareTo(o2);
    }
  };

  private static List<String> load(final String mcv) throws Exception {
    final MainTransformer trans = new MainTransformer(9999, null);
    final List<String> ret = new ArrayList<String>(trans.process_jar(mcv, null).keySet());
    Collections.sort(ret, CMP);
    return ret;
  }

  private static Map<String, String> map = new HashMap<String, String>();

  public static void main(final String[] args) throws Exception {
    // Initialize
    final JarMapping jm = new JarMapping();
    List<String> lold = null, lnew = null;
    final List<String> onold = new ArrayList<String>(), onnew = new ArrayList<String>();
    String out = null;
    boolean loaded = false, oldload = false, newload = false;
    // Parse args
    for (final String arg : args)
      if (arg.startsWith("-"))
        onold.add(arg.substring(1));
      else if (arg.startsWith("+"))
        onnew.add(arg.substring(1));
      else if (!loaded) {
        MappingBuilder.loadNew(new File(arg), jm);
        loaded = true;
      } else if (!oldload) {
        lold = load(arg);
        oldload = true;
      } else if (!newload) {
        lnew = load(arg);
        newload = true;
      } else
        out = arg;
    // Check args
    if (lold == null || lnew == null || out == null)
      return;
    // Create mapping
    Collections.sort(onold, CMP);
    Collections.sort(onnew, CMP);
    final Iterator<String> oitr = lold.iterator();
    final Iterator<String> nitr = lnew.iterator();
    final Iterator<String> oolditr = onold.iterator();
    final Iterator<String> onewitr = onnew.iterator();
    String skipold = oolditr.hasNext() ? oolditr.next() : null;
    String skipnew = onewitr.hasNext() ? onewitr.next() : null;
    while (oitr.hasNext() && nitr.hasNext()) {
      String olds = oitr.next();
      String news = nitr.next();
      if (skipold != null && CMP.compare(olds, skipold) >= 0) {
        if (!oitr.hasNext())
          break;
        olds = oitr.next();
        skipold = oolditr.hasNext() ? oolditr.next() : null;
      }
      if (skipnew != null && CMP.compare(news, skipnew) >= 0) {
        if (!nitr.hasNext())
          break;
        news = nitr.next();
        skipnew = onewitr.hasNext() ? onewitr.next() : null;
      }
      map.put(olds, news);
    }
    // Apply mapping
    final Map<String, String> ocls = new HashMap<String, String>(jm.cls_obf_raw);
    jm.cls_obf_raw.clear();
    for (final Map.Entry<String, String> e : ocls.entrySet())
      jm.cls_obf_raw.put(INSTANCE.map(e.getKey()), e.getValue());
    for (final Map.Entry<String, SrgEntry> e : jm.srg.entrySet()) {
      final Map<ObfEntry, String> nobf = e.getValue().obfs;
      final Map<ObfEntry, String> oobf = new HashMap<ObfEntry, String>(nobf);
      nobf.clear();
      for (final Map.Entry<ObfEntry, String> o : oobf.entrySet())
        nobf.put(
            new ObfEntry(INSTANCE.map(o.getKey().owner), INSTANCE.mapMethodDesc(o.getKey().desc)),
            o.getValue());
    }
    // Writing
    final OutputStream os = new FileOutputStream(new File(out));
    final Writer w = new OutputStreamWriter(os, Utils.UTF_8);
    MappingBuilder.writeSrg(w, jm);
    w.close();
    os.close();
  }

  @Override
  public String map(final String s) {
    final String r = map.get(s);
    return r == null ? s : r;
  }

  @Override
  public String mapMethodDesc(final String s) {
    if (s.equals(""))
      return s;
    return super.mapMethodDesc(s);
  }

  // TODO unused
  public static int strToId(final String s) {
    int r = 0, i = 0;
    do {
      r += s.charAt(i) - 'a';
      if (++i >= s.length())
        break;
      r = (r + 1) * 26;
    } while (true);
    return r;
  }

  // TODO unused
  public static String idToStr(final int _id) {
    int size = 1;
    int tmp = 26;
    while (true) {
      if (_id < tmp)
        break;
      tmp = tmp * 26 + 26;
      size++;
    }
    final char[] r = new char[size];
    int id = _id;
    for (int i = size - 1; i >= 0; i--) {
      r[i] = (char) (id % 26 + 'a');
      id = (id - 26) / 26;
    }
    return new String(r);
  }
}
