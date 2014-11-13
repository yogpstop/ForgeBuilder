package com.yogpc.fb.sa;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Patcher {
  private static enum RegexpType {
    SIMPLE {
      @Override
      public void put(final LinkedHashMap<Pattern, String> m, final String[] l) {
        m.put(Pattern.compile(l[1], Pattern.MULTILINE), l[2]);
      }
    },
    METHOD {
      @Override
      public void put(final LinkedHashMap<Pattern, String> m, final String[] l) {
        final StringBuilder rep1 = new StringBuilder(), rep2 = new StringBuilder(), rep3 =
            new StringBuilder(), rep4 = new StringBuilder();
        rep1.append(l[1]).append("\\s+");
        rep2.append(l[2]).append(" ");
        rep1.append(l[3]).append("\\s*\\(\\s*");
        rep3.append(l[3]).append("\\s*\\(\\s*");
        rep2.append(l[4]).append("(");
        rep4.append(l[4]).append("(");
        boolean J1 = false, J2 = false;
        int j = 0;
        for (int i = 5; i + 1 < l.length; i += 2) {
          if (J1) {
            rep1.append(",\\s*");
            rep3.append(",\\s*");
          }
          if (J2 && !l[i + 1].equals("@SKIP@")) {
            J2 = false;
            rep2.append(", ");
            rep4.append(", ");
          }
          J1 = true;
          j++;
          rep1.append("(final\\s*)?").append(l[i]);
          rep1.append("\\s+([^,\\(\\)]+)");
          rep3.append("([^,\\(\\)]+)");
          if (!l[i + 1].equals("@SKIP@")) {
            J2 = true;
            rep2.append("$").append(j * 2 - 1).append(l[i + 1]);
            rep2.append(" $").append(j * 2);
            rep4.append("$").append(j);
          }
        }
        rep1.append("\\)");
        rep2.append(")");
        rep3.append("\\)");
        rep4.append(")");
        m.put(Pattern.compile(rep1.toString()), rep2.toString());
        m.put(Pattern.compile(rep3.toString()), rep4.toString());
      }
    };
    public abstract void put(LinkedHashMap<Pattern, String> m, String[] l);
  }

  private static void applyRegexpCsv(final String csv, final Map<String, String> target) {
    if (csv == null)
      return;
    final LinkedHashMap<Pattern, String> ret = new LinkedHashMap<Pattern, String>();
    String[] l;
    for (final String rl : csv.split("(\r\n|\r|\n)")) {
      l = CSVParser.I.parseLine(rl.replace("\\n", "\n"));
      RegexpType.valueOf(l[0]).put(ret, l);
    }
    final List<String> names = new ArrayList<String>(target.keySet());
    for (final String name : names) {
      if (!name.toLowerCase().endsWith(".java"))
        continue;
      String data = target.get(name);
      for (final Map.Entry<Pattern, String> e : ret.entrySet())
        data = e.getKey().matcher(data).replaceAll(e.getValue());
      target.put(name, data);
    }
  }

  private static enum FileProcType {
    ADD {
      @Override
      public void process(final Map<String, String> patch, final Map<String, String> target,
          final String[] l) {
        for (final String e : new ArrayList<String>(patch.keySet()))
          if (e.startsWith(l[1]))
            target.put(l[2] + e.substring(l[1].length()), patch.get(e));
      }
    },
    MV {
      @Override
      public void process(final Map<String, String> patch, final Map<String, String> target,
          final String[] l) {
        for (final String e : new ArrayList<String>(target.keySet()))
          if (e.startsWith(l[1]))
            target.put(l[2] + e.substring(l[1].length()), target.remove(e));
      }
    },
    RM {
      @Override
      public void process(final Map<String, String> patch, final Map<String, String> target,
          final String[] l) {
        for (final String e : new ArrayList<String>(target.keySet()))
          if (e.startsWith(l[1]))
            target.remove(e);
      }
    };
    public abstract void process(Map<String, String> patch, Map<String, String> target, String[] l);
  }

  private static void applyFileCsv(final String csv, final Map<String, String> patch,
      final Map<String, String> target) {
    if (csv == null)
      return;
    String[] l;
    for (final String rl : csv.split("(\r\n|\r|\n)")) {
      l = CSVParser.I.parseLine(rl);
      FileProcType.valueOf(l[0]).process(patch, target, l);
    }
  }

  private static final class ImportDiff {
    private static final Pattern PKGPT = Pattern.compile("\\s*package\\s+([^;]+)\\s*;");
    private static final Pattern IMPPT = Pattern.compile("\\s*import\\s+([^;]+)\\s*;");

    static boolean cont(final String e, final Map<String, Object> r, final Map<String, Object> f) {
      for (final String p : r.keySet())
        if (p.charAt(p.length() - 1) == '.') {
          if (e.startsWith(p))
            return true;
        } else if (e.equals(p))
          return true;
      for (final String p : f.keySet())
        if (p.charAt(p.length() - 1) == '.') {
          if (e.startsWith(p))
            return true;
        } else if (e.equals(p))
          return true;
      return false;
    }

    final Map<String, Object> a = new HashMap<String, Object>();
    final Map<String, Object> r = new HashMap<String, Object>();
    private final String d;

    ImportDiff(final String t) {
      this.d = t;
    }

    String process(final Map<String, Object> f) {
      final Map<String, Object> imps = new HashMap<String, Object>();
      final StringBuffer sb = new StringBuffer();
      Matcher m = IMPPT.matcher(this.d);
      boolean loop = true;
      if (!m.find()) {
        loop = false;
        m = PKGPT.matcher(this.d);
        if (!m.find())
          return this.d;
      }
      m.appendReplacement(sb, "");
      if (!loop)
        sb.append(m.group());
      for (final String s : this.a.keySet()) {
        if (imps.put(s, Utils.DUMMY_OBJECT) != null)
          continue;
        sb.append("\nimport ").append(s).append(";");
      }
      if (loop)
        do {
          final String e = m.group(1).replaceAll("\\s", "");
          if (!cont(e, this.r, f) && imps.put(e, Utils.DUMMY_OBJECT) == null)
            sb.append(m.group());
          if (!m.find())
            break;
          m.appendReplacement(sb, "");
        } while (true);
      m.appendTail(sb);
      return sb.toString();
    }
  }

  private static void applyImportCsv(final String csv, final Map<String, String> target) {
    final Map<String, ImportDiff> w = new HashMap<String, ImportDiff>();
    final Map<String, Object> f = new HashMap<String, Object>();
    for (final Map.Entry<String, String> e : target.entrySet())
      if (e.getKey().toLowerCase().endsWith(".java"))
        w.put(e.getKey(), new ImportDiff(e.getValue()));
    if (csv != null) {
      String[] l;
      final Map<ImportDiff, Object> t = new HashMap<ImportDiff, Object>();
      final Map<String, Object> a = new HashMap<String, Object>();
      for (final String rl : csv.split("(\r\n|\r|\n)")) {
        t.clear();
        a.clear();
        l = CSVParser.I.parseLine(rl);
        for (int i = 1; i < l.length; i++)
          if (l[i].contains("/")) {
            final String java = l[i].replaceAll("\\s", "") + ".java";
            final ImportDiff id = w.get(java);
            if (id == null) {
              System.err.println("Can't patch import csv to " + java);
              continue;
            }
            t.put(id, Utils.DUMMY_OBJECT);
          } else if (l[i].contains("."))
            a.put(l[i].replaceAll("\\s", ""), Utils.DUMMY_OBJECT);
        if (t.size() == 0)
          f.putAll(a);
        else if (l[0].equals("A"))
          for (final ImportDiff id : t.keySet())
            id.a.putAll(a);
        else if (l[0].equals("R"))
          for (final ImportDiff id : t.keySet())
            id.r.putAll(a);
      }
    }
    for (final Map.Entry<String, ImportDiff> e : w.entrySet())
      target.put(e.getKey(), e.getValue().process(f));
  }

  private static boolean applyDiff(final String diff, final Map<String, String> target,
      final File in, final File out, final File rej, final boolean debug) throws IOException {
    if (diff == null)
      return true;
    final UnifiedDiff ud = new UnifiedDiff();
    final Reader sr = new StringReader(diff);
    final BufferedReader br = new BufferedReader(sr);
    ud.add(br, 1);
    br.close();
    sr.close();
    return ud.patch(target, false, in, out, rej, debug);
  }

  public static boolean applyPatch(final Map<String, String> patch,
      final ProjectConfig.ForgeVersion v, final boolean debug, final File base) throws IOException {
    applyFileCsv(patch.get(v.name + "-file.csv"), patch, v.srces);
    applyImportCsv(patch.get(v.name + "-import.csv"), v.srces);
    applyRegexpCsv(patch.get(v.name + "-regexp.csv"), v.srces);
    return applyDiff(patch.get(v.name + ".patch"), v.srces, new File(base, v.parent + "-" + v.name
        + File.separatorChar + "java"), new File(base, v.name + File.separatorChar + "java"),
        new File(base, v.name + File.separatorChar + "rej"), debug);
  }
}
