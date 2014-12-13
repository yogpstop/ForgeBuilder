package com.yogpc.fb.sa;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UnifiedDiff {
  private final List<PFile> files = new LinkedList<PFile>();

  private static enum LineType {
    ADD('+'), REMOVE('-'), KEEP(' ');
    final char key;

    private LineType(final char c) {
      this.key = c;
    }

    @Override
    public String toString() {
      return new String(new char[] {this.key});
    }
  }

  private static final class Line {
    final LineType type;
    final String data;

    Line(final LineType t, final String raw) {
      this.type = t;
      this.data = raw;
    }

    @Override
    public String toString() {
      return new StringBuilder().append(this.type.toString()).append(this.data).toString();
    }
  }

  private static final class Hunk {
    private static final Pattern p = Pattern
        .compile("@@\\s+-([0-9]+)(,([0-9]+))?\\s+\\+([0-9]+)(,([0-9]+))?\\s+@@.*");
    private final List<Line> lines = new LinkedList<Line>();
    final int from_pos, from_len, to_pos, to_len, fuzz_top, fuzz_last;

    Hunk(final String at, final BufferedReader br) throws IOException {
      final Matcher m = p.matcher(at);
      m.matches();
      String buf;
      this.from_pos = Integer.parseInt(m.group(1));
      buf = m.group(3);
      if (buf != null && buf.length() > 0)
        this.from_len = Integer.parseInt(buf);
      else
        this.from_len = 1;
      this.to_pos = Integer.parseInt(m.group(4));
      buf = m.group(6);
      if (buf != null && buf.length() > 0)
        this.to_len = Integer.parseInt(buf);
      else
        this.to_len = 1;
      int from = 0, to = 0;
      while (from < this.from_len || to < this.to_len) {
        buf = br.readLine();
        switch (buf.charAt(0)) {
          case ' ':
            this.lines.add(new Line(LineType.KEEP, buf.substring(1)));
            to++;
            from++;
            break;
          case '+':
            this.lines.add(new Line(LineType.ADD, buf.substring(1)));
            to++;
            break;
          case '-':
            this.lines.add(new Line(LineType.REMOVE, buf.substring(1)));
            from++;
            break;
        }
      }
      for (from = 0; from < this.lines.size(); from++)
        if (this.lines.get(from).type != LineType.KEEP)
          break;
      this.fuzz_top = from;
      for (from = this.lines.size() - 1; from >= 0; from--)
        if (this.lines.get(from).type != LineType.KEEP)
          break;
      this.fuzz_last = this.lines.size() - 1 - from;
    }

    private final boolean tpatch(final List<String> l, final int o, final int f, final boolean dry) {
      final int ft = Math.min(this.fuzz_top, f), fl = Math.min(this.fuzz_last, f);
      int i = o;
      if (o < 0 || o + this.from_len - ft - fl > l.size())
        return false;
      for (int n = ft; n < this.lines.size() - fl; n++) {
        final Line e = this.lines.get(n);
        if (e.type != LineType.ADD && !l.get(i++).equals(e.data))
          return false;
      }
      if (dry)
        return true;
      i = o;
      for (int n = ft; n < this.lines.size() - fl; n++) {
        final Line e = this.lines.get(n);
        switch (e.type) {
          case ADD:
            l.add(i, e.data);
            //$FALL-THROUGH$
          case KEEP:
            i++;
            break;
          case REMOVE:
            l.remove(i);
            break;
        }
      }
      return true;
    }

    final Integer patch(final List<String> l, final int d, final boolean dry) {
      for (int f = 0; f < 3; f++)
        for (int i = 0; i < l.size(); i++) {
          if (tpatch(l, this.from_pos + d - i, f, dry))
            return new Integer(this.to_len - this.from_len);
          if (tpatch(l, this.from_pos + d + i, f, dry))
            return new Integer(this.to_len - this.from_len);
        }
      return null;
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder();
      sb.append("@@ -").append(Integer.toString(this.from_pos));
      if (this.from_len != 1)
        sb.append(',').append(Integer.toString(this.from_len));
      sb.append(" +");
      sb.append(Integer.toString(this.to_pos));
      if (this.to_len != 1)
        sb.append(',').append(Integer.toString(this.to_len));
      sb.append(" @@\n");
      for (final Line l : this.lines)
        sb.append(l.toString()).append('\n');
      return sb.toString();
    }
  }

  private static final class PFile {
    private static final Pattern p1 = Pattern.compile("---\\s+(\"[^\"]+\"|\\S+).*");
    private static final Pattern p2 = Pattern.compile(".*(net/minecraft/.*\\.java).*?");
    private final String filename;
    final List<Hunk> hunks = new LinkedList<Hunk>();

    private PFile(final String fn) {
      this.filename = fn;
    }

    static PFile init(final String l, final BufferedReader br, final int p) throws IOException {
      br.readLine();// skip +++ line
      Matcher m = p1.matcher(l);
      if (!m.matches())
        return null;
      String c = m.group(1).replace('\\', '/');
      if (c.charAt(0) == '"')
        c = c.substring(1, c.length() - 1);
      if (p < 0) {
        m = p2.matcher(c);
        if (!m.matches())
          return null;
        return new PFile(m.group(1));
      }
      int s = 0;
      for (int i = 0; i < p; i++)
        s = c.indexOf('/', s);
      return new PFile(c.substring(s + 1));
    }

    final boolean patch(final Map<String, String> target, final boolean dry,
        final Map<String, String> rejs) {
      final String bak = target.get(this.filename);
      final List<Hunk> failed = new ArrayList<Hunk>();
      if (bak != null) {
        final List<String> l = new ArrayList<String>(Arrays.asList(bak.split("(\r\n|\r|\n)")));
        int i = 0;
        for (final Hunk h : this.hunks) {
          final Integer oi = h.patch(l, i, dry);
          if (oi != null)
            i += oi.intValue();
          else
            failed.add(h);
        }

        final StringBuilder o = new StringBuilder();
        for (final String e : l)
          o.append(e).append("\n");
        target.put(this.filename, o.toString());
      } else
        failed.addAll(this.hunks);
      if (failed.size() > 0) {
        final StringBuilder sb = new StringBuilder();
        sb.append("--- a/").append(this.filename).append('\n');
        sb.append("+++ b/").append(this.filename).append('\n');
        for (final Hunk h : failed)
          sb.append(h.toString());
        rejs.put(this.filename, sb.toString());
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder();
      sb.append("--- a/").append(this.filename).append('\n');
      sb.append("+++ b/").append(this.filename).append('\n');
      for (final Hunk h : this.hunks)
        sb.append(h.toString());
      return sb.toString();
    }
  }

  public final void add(final BufferedReader br, final int p) throws IOException {
    String l;
    PFile f = null;
    while ((l = br.readLine()) != null)
      if (l.startsWith("---")) {
        if (f != null)
          this.files.add(f);
        f = PFile.init(l, br, p);
      } else if (l.startsWith("@@"))
        if (f != null)
          f.hunks.add(new Hunk(l, br));
    if (f != null)
      this.files.add(f);
  }

  private static void writeDebug(final Map<String, String> in, final File base) throws IOException {
    File out;
    for (final Map.Entry<String, String> e : in.entrySet()) {
      out = new File(base, e.getKey().replace('/', File.separatorChar));
      out.getParentFile().mkdirs();
      Utils.stringToFile(e.getValue(), out, Utils.ISO_8859_1);
    }
  }

  private final boolean _patch(final Map<String, String> target, final boolean dry,
      final Map<String, String> rejs) {
    boolean failed = false;
    for (final PFile f : this.files)
      failed |= !f.patch(target, dry, rejs);
    return failed;
  }

  public final boolean patch(final Map<String, String> target, final boolean dry, final File in,
      final File out, final File rej, final boolean force) throws IOException {
    final Map<String, String> bak = new HashMap<String, String>(target);
    final Map<String, String> rejs = new HashMap<String, String>();
    final boolean failed = _patch(target, dry, rejs);
    if (failed) {
      writeDebug(bak, in);
      writeDebug(target, out);
      writeDebug(rejs, rej);
      return false;
    } else if (force) {
      writeDebug(bak, in);
      writeDebug(target, out);
    }
    return true;
  }

  public static final boolean patch(final Map<String, String> target, final File in,
      final File out, final File rej, final boolean force, final List<String> diffs)
      throws IOException {
    final Map<String, String> bak = new HashMap<String, String>(target);
    final Map<String, String> rejs = new HashMap<String, String>();
    boolean failed = true;
    for (final String diff : diffs) {
      final UnifiedDiff ud = new UnifiedDiff();
      final Reader r = new StringReader(diff);
      final BufferedReader br = new BufferedReader(r);
      ud.add(br, -1);
      br.close();
      r.close();
      if (ud._patch(target, true, rejs))
        continue;
      failed = ud._patch(target, false, rejs);
      break;
    }
    if (failed) {
      writeDebug(bak, in);
      writeDebug(target, out);
      writeDebug(rejs, rej);
      return false;
    } else if (force) {
      writeDebug(bak, in);
      writeDebug(target, out);
    }
    return true;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    for (final PFile f : this.files)
      sb.append(f.toString());
    return sb.toString();
  }
}
