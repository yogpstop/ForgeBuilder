package com.yogpc.fb.map;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import com.yogpc.fb.sa.CSVParser;
import com.yogpc.fb.sa.Utils;

public class MappingBuilder {
  public static final void loadNew(final String s, final JarMapping jm, final boolean fm) {
    for (final String line : s.split("\n")) {
      final String[] es = Utils.split(line, ',');
      if (es[0].equals("CLS")) {
        jm.cls_obf_raw.put(es[1], es[2]);
        jm.cls_raw_obf.put(es[2], es[1]);
      } else if (es[0].equals("PKG")) {
        jm.pkg_obf_raw.put(es[1], es[2]);
        jm.pkg_raw_obf.put(es[2], es[1]);
      } else if (fm) {
        final JarMapping.SrgEntry e = new JarMapping.SrgEntry();
        e.raw = es[1];
        for (int i = 2; i < es.length; i++) {
          final String[] oe = Utils.split(es[i], '|');
          e.obfs.put(new JarMapping.ObfEntry(oe[0], oe[1]), oe[2]);
        }
        jm.srg.put(es[0], e);
      }
    }
  }

  public static final void writeSrg(final Writer w, final JarMapping jm) throws IOException {
    for (final Map.Entry<String, JarMapping.SrgEntry> m : jm.srg.entrySet()) {
      final JarMapping.SrgEntry e = m.getValue();
      w.write(m.getKey());// 0
      w.write(',');
      w.write(e.raw);// 1
      for (final Map.Entry<JarMapping.ObfEntry, String> oe : e.obfs.entrySet()) {
        final JarMapping.ObfEntry o = oe.getKey();
        w.write(',');
        w.write(o.owner);// 2...[0]
        w.write('|');
        w.write(o.desc);// [1]
        w.write('|');
        w.write(oe.getValue());// [2]
      }
      w.write('\n');
    }
    for (final Map.Entry<String, String> e : jm.cls_obf_raw.entrySet()) {
      w.write("CLS,");// 0
      w.write(e.getKey());// 1
      w.write(',');
      w.write(e.getValue());// 2
      w.write('\n');
    }
    for (final Map.Entry<String, String> e : jm.pkg_obf_raw.entrySet()) {
      w.write("PKG,");// 0
      w.write(e.getKey());// 1
      w.write(',');
      w.write(e.getValue());// 2
      w.write('\n');
    }
  }

  private static final void putObf(final String srg, final String own, final String obf,
      final String dsc, final JarMapping jm) {
    if (srg.equals(obf))
      return;
    JarMapping.SrgEntry e = jm.srg.get(srg);
    if (e == null)
      jm.srg.put(srg, e = new JarMapping.SrgEntry());
    e.obfs.put(new JarMapping.ObfEntry(own, dsc), obf);
  }

  public static final void loadSrg(final String all, final JarMapping jm) {
    for (String line : all.split("(\r\n|\r|\n)")) {
      final int ci = line.indexOf('#');
      if (ci != -1)
        line = line.substring(0, ci);
      if (line.isEmpty())
        continue;
      final String[] tokens = line.split(" +");
      if (tokens[0].equals("CL:")) {
        if (tokens[1].equals(tokens[2]))
          continue;
        if (tokens[1].endsWith("/*") && tokens[2].endsWith("/*")) {
          final String obf = tokens[1].substring(0, tokens[1].length() - 1);
          final String raw = tokens[2].substring(0, tokens[2].length() - 1);
          jm.pkg_obf_raw.put(obf, raw);
        } else
          jm.cls_obf_raw.put(tokens[1], tokens[2]);
      } else if (tokens[0].equals("PK:")) {
        if (!tokens[1].equals(".") && tokens[1].charAt(tokens[1].length() - 1) != '/')
          tokens[1] += "/";
        if (!tokens[2].equals(".") && tokens[2].charAt(tokens[2].length() - 1) != '/')
          tokens[2] += "/";
        if (tokens[1].equals(tokens[2]))
          continue;
        jm.pkg_obf_raw.put(tokens[1], tokens[2]);
      } else if (tokens[0].equals("FD:")) {
        final int del1 = tokens[1].lastIndexOf('/');
        putObf(tokens[2].substring(tokens[2].lastIndexOf('/') + 1), tokens[1].substring(0, del1),
            tokens[1].substring(del1 + 1), "", jm);
      } else if (tokens[0].equals("MD:")) {
        final int del1 = tokens[1].lastIndexOf('/');
        putObf(tokens[3].substring(tokens[3].lastIndexOf('/') + 1), tokens[1].substring(0, del1),
            tokens[1].substring(del1 + 1), tokens[2], jm);
      }
    }
  }

  public static final void loadCsv(final String csv, final JarMapping jm, final boolean hasJD,
      final boolean pack) {
    String[] l;
    for (final String rl : csv.split("(\r\n|\r|\n)")) {
      l = CSVParser.I.parseLine(rl);
      if (l.length < (hasJD ? 4 : 2))
        continue;
      if (hasJD)
        jm.srg_javadoc.put(l[0], l[3]);
      if (l[1].equals(pack ? "net/minecraft/src" : l[0]))
        continue;
      if (pack)
        jm.jnd_pkd.put("net/minecraft/src/" + l[0], l[1] + "/" + l[0]);
      else {
        JarMapping.SrgEntry a = jm.srg.get(l[0]);
        if (a == null)
          jm.srg.put(l[0], a = new JarMapping.SrgEntry());
        a.raw = l[1];
      }
    }
  }

  public static final void fix(final JarMapping jm) {
    final Map<String, String> jnd = new HashMap<String, String>(jm.cls_obf_raw);
    jm.cls_obf_raw.clear();
    for (final Map.Entry<String, String> e : jnd.entrySet()) {
      final String raw = jm.jnd_pkd.get(e.getValue());
      if (raw != null) {
        jm.cls_obf_raw.put(e.getKey(), raw);
        jm.cls_raw_obf.put(raw, e.getKey());
      } else {
        jm.cls_obf_raw.put(e.getKey(), e.getValue());
        jm.cls_raw_obf.put(e.getValue(), e.getKey());
      }
    }
    for (final Map.Entry<String, String> e : jm.pkg_obf_raw.entrySet())
      jm.pkg_raw_obf.put(e.getValue(), e.getKey());
  }
}
