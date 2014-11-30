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
  public static final Pattern METHOD_REG = Pattern.compile("^(?<indent>\\s+)" + FFPatcher.MODIFIERS
      + "(?<return>[\\w\\[\\]\\.$]+) +(?<name>[\\w$]+)\\((?<parameters>.*?)\\)(?<end>"
      + FFPatcher.THROWS + ")");
  private static final Pattern CATCH_REG = Pattern.compile("catch \\((.*)\\)$");
  private static final Pattern METHOD_DEC_END = Pattern.compile("(}|\\);|throws .+?;)$");
  private static final Pattern CAPS_START = Pattern.compile("^[A-Z]");
  private static final Pattern ARRAY = Pattern.compile("(\\[|\\.\\.\\.)");
  private static final Pattern VAR_CALL = Pattern
      .compile("(?i)[a-z_][a-z0-9_\\[\\]]+ var\\d+(?:x)*");
  static final Pattern VAR = Pattern.compile("var\\d+(?:x)*");

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
      final boolean found = matcher.find();
      if ((forgevi < 967 ? !line.contains("=") : !line.endsWith(";") && !line.endsWith(","))
          && found) {
        method = new MethodInfo(method, matcher.group("indent"));
        method.lines.add(line);

        boolean invalid = false;
        final String args = matcher.group("parameters");
        if (args != null)
          for (final String str : Utils.csplit(args, ',')) {
            if (str.indexOf(' ') == -1) {
              invalid = true;
              break;
            }
            method.addVar(str);
          }

        if (invalid || METHOD_DEC_END.matcher(line).find()) {
          if (method.parent != null)
            method.parent.children.remove(method);
          else
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
          method.addVar(matcher.group(1));
        else {
          matcher = VAR_CALL.matcher(line);
          while (matcher.find()) {
            final String match = matcher.group();
            if (!match.startsWith("return") && !match.startsWith("throw"))
              method.addVar(match);
          }
        }
      } else
        output.add(line);
    }
    final String ret = Utils.join(output.toArray(new String[output.size()]), '\n');
    return new StringBuilder().append(ret).append('\n').toString();
  }

  private static class MethodInfo {
    MethodInfo parent = null;
    List<Object> lines = new ArrayList<Object>();
    private final List<String> vars = new ArrayList<String>();
    List<MethodInfo> children = new ArrayList<MethodInfo>();
    final String ENDING;

    MethodInfo(final MethodInfo parent, final String indent) {
      this.parent = parent;
      this.ENDING = indent + "}";
      if (parent != null) {
        parent.children.add(this);
        parent.lines.add(this);
      }
    }

    void addVar(final String info) {
      this.vars.add(info);
    }

    String rename(final FmlCleanup _namer, final int forgevi) {
      final FmlCleanup namer = _namer == null ? new FmlCleanup() : new FmlCleanup(_namer);

      final Map<String, String> renames = new HashMap<String, String>();
      final Map<String, String> unnamed = new LinkedHashMap<String, String>();

      for (final String var : this.vars) {
        final String[] split = var.split(" ");

        if (!split[1].startsWith("var"))
          renames.put(split[1], namer.getName(split[0]));
        else
          unnamed.put(split[1], split[0]);
      }

      if (unnamed.size() > 0) {
        final List<String> sorted = new ArrayList<String>(unnamed.keySet());
        if (forgevi >= 967)
          Collections.sort(sorted, COMPARATOR.setinv(false));
        for (final String s : sorted)
          renames.put(s, namer.getName(unnamed.get(s)));
      }

      final StringBuilder buf = new StringBuilder();
      for (final Object line : this.lines)
        if (line instanceof MethodInfo)
          buf.append(((MethodInfo) line).rename(namer, forgevi)).append("\n");
        else
          buf.append((String) line).append("\n");

      String body = buf.toString();

      if (renames.size() > 0) {
        final List<String> sortedKeys = new ArrayList<String>(renames.keySet());
        Collections.sort(sortedKeys, COMPARATOR.setinv(true));
        for (final String key : sortedKeys)
          if (VAR.matcher(key).matches())
            body = body.replace(key, renames.get(key));
      }

      return body.substring(0, body.length() - "\n".length());
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
    String type = _type;
    String index = null;
    String findtype = type;
    while (findtype.contains("[][]"))
      findtype = findtype.replaceAll("\\[\\]\\[\\]", "[]");
    if (this.last.containsKey(findtype))
      index = findtype;
    else if (this.last.containsKey(findtype.toLowerCase()))
      index = findtype.toLowerCase();
    else if (this.remap.containsKey(type))
      index = this.remap.get(type);
    if ((index == null || index.length() == 0)
        && (CAPS_START.matcher(type).find() || ARRAY.matcher(type).find())) {
      type = type.replace("...", "[]");
      while (type.contains("[][]"))
        type = type.replaceAll("\\[\\]\\[\\]", "[]");
      String name = type.toLowerCase();
      name = name.replace(".", "");
      if (Pattern.compile("\\[").matcher(type).find()) {
        name = "a" + name;
        name = name.replace("[]", "").replace("...", "");
      }
      this.last.put(type.toLowerCase(), new Holder(0, true, name));
      index = type.toLowerCase();
    }
    if (index == null || index.length() == 0)
      return type.toLowerCase();
    final Holder holder = this.last.get(index);
    final int id = holder.id;
    final List<String> names = holder.names;
    final int ammount = names.size();
    String name;
    if (ammount == 1)
      name = names.get(0) + (id == 0 && holder.skip_zero ? "" : Integer.toString(id));
    else {
      final int num = id / ammount;
      name =
          names.get(id % ammount) + (id < ammount && holder.skip_zero ? "" : Integer.toString(num));
    }
    holder.id++;
    return name;
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
