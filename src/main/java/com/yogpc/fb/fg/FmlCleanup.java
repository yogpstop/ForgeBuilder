package com.yogpc.fb.fg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.yogpc.fb.sa.Utils;

public class FmlCleanup {
  // 1:indent 2:modifier 3:return 4:name 5:parameters 6:throw
  static final String A = "[\\w$\\.\\[\\]]+";
  private static final String AD = "[\\w$\\[\\]]+";// ...
  static final String B = "[\\w$\\.]+";
  static final String C = "[\\w$]+";
  private static final String F = "(?:final )?";
  private static final String I = "[ \\t\\f\\v]*";
  static final Pattern V = Pattern.compile("var\\d+(?:x)*");
  private static final Pattern VCALL = Pattern.compile("(" + AD + ") (" + V + ")");
  static final String PARAMS = "\\(((?:" + F + A + "(?: \\.\\.\\.)? " + C + "(?:, " + F + A
      + "(?: \\.\\.\\.)? " + C + ")*)?)\\)";
  public static final Pattern METHOD_REG = Pattern.compile("^(" + I + ")" + FFPatcher.MODIFIERS
      + "(?:(" + A + ") )?(" + C + ")" + PARAMS + FFPatcher.THROWS);
  private static final Pattern CATCH_REG = Pattern.compile("catch \\(" + F + "(" + B + ") (" + C
      + ")\\)");

  static final ComparatorImpl COMPARATOR = new ComparatorImpl();

  static final class ComparatorImpl implements Comparator<String> {
    private final static int compare_in(final String str1, final String str2) {
      if (str1.length() != str2.length())
        return str1.length() - str2.length();
      return str1.compareTo(str2);
    }

    private boolean inv = true;

    Comparator<String> setinv(final boolean b) {
      this.inv = b;
      return this;
    }

    @Override
    public int compare(final String str1, final String str2) {
      return compare_in(str1, str2) * (this.inv ? -1 : 1);
    }
  }

  public static String renameClass(final String text, final int forgevi) {
    final String[] lines = text.split("(\r\n|\r|\n)");
    final List<String> output = new ArrayList<String>(lines.length);
    MethodInfo method = null;

    for (final String line : lines) {
      Matcher matcher = METHOD_REG.matcher(line);
      if (matcher.find()
          && (forgevi < 967 ? !line.contains("=") : line.charAt(line.length() - 1) != ';'
              && line.charAt(line.length() - 1) != ',')) {
        method = new MethodInfo(method, matcher.group(1));
        method.lines.add(line);

        final String args = matcher.group(5);
        if (args != null && args.length() > 0)
          for (final String str : args.split(", ")) {
            final int p = str.lastIndexOf(' ');
            method.vars.put(str.substring(p + 1),
                str.substring(str.startsWith("final ") ? 6 : 0, p));
          }

        if (line.endsWith(";") || line.endsWith("}")) {
          if (method.parent == null)
            for (final String l : Utils.split(method.rename(null, forgevi), '\n'))
              output.add(l);
          method = method.parent;
        }
      } else if (method != null && method.ENDING.equals(line)) {
        method.lines.add(line);

        if (method.parent == null)
          for (final String l : Utils.split(method.rename(null, forgevi), '\n'))
            output.add(l);

        method = method.parent;
      } else if (method != null) {
        method.lines.add(line);
        matcher = CATCH_REG.matcher(line);
        if (matcher.find())
          method.vars.put(matcher.group(2), matcher.group(1));
        else {
          matcher = VCALL.matcher(line);
          while (matcher.find())
            if (!matcher.group(1).equals("return") && !matcher.group(1).equals("throw"))
              method.vars.put(matcher.group(2), matcher.group(1));
        }
      } else
        output.add(line);
    }
    final StringBuilder sb = new StringBuilder();
    for (final String out : output)
      sb.append(out).append('\n');
    // Cut last LF => error
    return sb.toString();
  }

  private static class MethodInfo {
    MethodInfo parent = null;
    List<Object> lines = new ArrayList<Object>();
    final Map<String, String> vars = new LinkedHashMap<String, String>();
    final String ENDING;

    MethodInfo(final MethodInfo parent, final String indent) {
      this.parent = parent;
      this.ENDING = indent + "}";
      if (parent != null)
        parent.lines.add(this);
    }

    String rename(final FmlCleanup _namer, final int forgevi) {
      final FmlCleanup namer = _namer == null ? new FmlCleanup() : new FmlCleanup(_namer);
      final Map<String, String> renames = new HashMap<String, String>();
      final Map<String, String> unnamed = new LinkedHashMap<String, String>();

      for (final Map.Entry<String, String> e : this.vars.entrySet())
        if (V.matcher(e.getKey()).matches())
          unnamed.put(e.getKey(), e.getValue());
        else
          namer.getName(e.getValue());

      if (unnamed.size() > 0) {
        final List<String> sorted = new ArrayList<String>(unnamed.keySet());
        if (forgevi >= 967)
          Collections.sort(sorted, COMPARATOR.setinv(false));
        for (final String key : sorted)
          renames.put(key, namer.getName(unnamed.get(key)));
      }

      final StringBuilder buf = new StringBuilder();
      for (final Object line : this.lines)
        if (line instanceof MethodInfo)
          buf.append(((MethodInfo) line).rename(namer, forgevi)).append('\n');
        else
          buf.append((String) line).append('\n');
      String body = buf.toString();

      if (renames.size() > 0) {
        final List<String> sortedKeys = new ArrayList<String>(renames.keySet());
        Collections.sort(sortedKeys, COMPARATOR.setinv(true));
        for (final String key : sortedKeys)
          body = body.replace(key, renames.get(key));
      }

      return body.substring(0, body.length() - 1);
    }
  }

  HashMap<String, Holder> last;
  HashMap<String, String> remap;

  FmlCleanup() {
    this.last = new HashMap<String, Holder>();
    this.last.put("byte", new Holder(0, false, "b"));
    this.last.put("char", new Holder(0, false, "c"));
    this.last.put("short", new Holder(1, false, "short"));
    this.last.put("int", new Holder(0, true, "i", "j", "k", "l"));
    this.last.put("boolean", new Holder(0, true, "flag"));
    this.last.put("double", new Holder(0, false, "d"));
    this.last.put("float", new Holder(0, true, "f"));
    this.last.put("File", new Holder(1, true, "file"));
    this.last.put("String", new Holder(0, true, "s"));
    this.last.put("Class", new Holder(0, true, "oclass"));
    this.last.put("Long", new Holder(0, true, "olong"));
    this.last.put("Byte", new Holder(0, true, "obyte"));
    this.last.put("Short", new Holder(0, true, "oshort"));
    this.last.put("Boolean", new Holder(0, true, "obool"));
    this.last.put("Package", new Holder(0, true, "opackage"));
    this.last.put("Enum", new Holder(0, true, "oenum"));

    this.remap = new HashMap<String, String>();
    this.remap.put("long", "int");
  }

  FmlCleanup(final FmlCleanup parent) {
    this.last = new HashMap<String, Holder>();
    for (final Entry<String, Holder> e : parent.last.entrySet())
      this.last.put(e.getKey(), new Holder(e.getValue()));

    this.remap = new HashMap<String, String>();
    for (final Entry<String, String> e : parent.remap.entrySet())
      this.remap.put(e.getKey(), e.getValue());
  }

  String getName(final String _type) {
    int i;
    String key = null;
    String type = _type.replace("...", "[]");
    final StringBuilder sb = new StringBuilder(type.length());
    int p = 0;
    for (i = 0; i < type.length(); i++) {
      final char c = type.charAt(i);
      if (c == '.' || c == ' ')
        continue;
      if (c == '[' && p == 0)
        p++;
      else if (c == ']' && p == 1)
        p++;
      else if (c == '[' && p == 2) {
        p++;
        continue;
      } else if (c == ']' && p == 3) {
        p--;
        continue;
      } else
        p = 0;
      sb.append(c);
    }
    type = sb.toString();
    if (this.last.containsKey(type))
      key = type;
    else if (this.last.containsKey(type.toLowerCase()))
      key = type.toLowerCase();
    else if (this.remap.containsKey(type))
      key = this.remap.get(type);
    else if (Character.isUpperCase(type.charAt(0)) || type.indexOf("[]") > -1)
      this.last.put(key = type.toLowerCase(), new Holder(0, true, key.indexOf("[]") > -1 ? "a"
          + key.replace("[]", "") : key));
    else
      return type.toLowerCase();
    final Holder holder = this.last.get(key);
    final int id = holder.id++;
    final List<String> names = holder.names;
    final int amount = names.size();
    if (amount == 1)
      return names.get(0) + (id == 0 && holder.skip_zero ? "" : Integer.toString(id));
    return names.get(id % amount)
        + (id < amount && holder.skip_zero ? "" : Integer.toString(id / amount));
  }

  private class Holder {
    public int id;
    public boolean skip_zero;
    public final List<String> names = new ArrayList<String>();

    public Holder(final int t1, final boolean skip_zero, final String... names) {
      this.id = t1;
      this.skip_zero = skip_zero;
      Collections.addAll(this.names, names);
    }

    public Holder(final Holder p) {
      this.id = p.id;
      this.skip_zero = p.skip_zero;
      this.names.addAll(p.names);
    }
  }
}
