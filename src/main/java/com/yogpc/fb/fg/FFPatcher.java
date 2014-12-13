package com.yogpc.fb.fg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.yogpc.fb.sa.Utils;

public class FFPatcher {
  private static final String B = FmlCleanup.B;
  private static final String C = FmlCleanup.C;
  static final String MODIFIERS =
      "((?:(?:public|protected|private|static|abstract|final|native|synchronized|transient|volatile|strictfp) )*)";
  static final String THROWS = "(?: throws (" + B + "(?:, " + B + ")*))?";
  // 1:indent 2:modifier 3:return 4:name 5:parameters 6:throw 7:name2 8:arg2
  private static final Pattern SYNTHETICS = Pattern
      .compile("(?m)(?:\\s*// \\$FF: (?:synthetic|bridge) method\\n){1,2}" + FmlCleanup.METHOD_REG
          + "\\s*\\{\\s*return this\\.(.+?)\\((.*)\\);\\s*\\}");
  private static final Pattern ABSTRACT = Pattern.compile("(?m)" + FmlCleanup.METHOD_REG + ";$");
  private static final Pattern TRAILING = Pattern.compile("(?m)[ \\t]+$");
  private static final Pattern VAIN_ZERO = Pattern.compile("([0-9]+\\.[0-9]*[1-9])0+([DdFfEe])");
  private static final Pattern NEWLINES = Pattern.compile("(?m)^\\n{2,}");
  private static final Pattern EMPTY_SUPER = Pattern.compile("(?m)^[ \t]+super\\(\\);\\n");

  public static String processFile(final String _text, final int forgevi, final String mcv) {
    String text = _text.replaceAll("(\\r\\n|\\r)", "\n");
    StringBuffer out;
    Matcher m;
    if (forgevi > 534) {
      out = new StringBuffer();
      m = SYNTHETICS.matcher(text);
      while (m.find()) {
        m.appendReplacement(out, "");
        out.append(synthetic_replacement(m));
      }
      m.appendTail(out);
      text = out.toString();
    }
    text = VAIN_ZERO.matcher(TRAILING.matcher(text).replaceAll("")).replaceAll("$1$2");
    final List<String> lines = new ArrayList<String>(Arrays.asList(Utils.split(text, '\n')));
    processClass(lines, "", 0, null, forgevi);
    text = Utils.join(lines.toArray(new String[lines.size()]), '\n');
    text = EMPTY_SUPER.matcher(NEWLINES.matcher(text).replaceAll("\n")).replaceAll("");
    if (forgevi >= 1048 && !mcv.equals("1.7.2")) {
      out = new StringBuffer();
      m = ABSTRACT.matcher(text);
      while (m.find()) {
        m.appendReplacement(out, "");
        abstract_replacement(m, out);
      }
      m.appendTail(out);
      text = out.toString();
    }
    return text;
  }

  private static int processClass(final List<String> lines, final String idt1, final int start,
      final String qualifiedName, final int forgevi) {
    final String idt2 = idt1 == null ? "" : idt1 + "   ";
    // 1:modifier 2:type 3:name
    final Pattern classPattern =
        Pattern.compile(idt2 + MODIFIERS + "(enum|class|interface) (" + C
            + ")(?: (?:extend|implement)s " + B + "(?:, " + B + ")*){0,2} \\{");
    for (int i = start; i < lines.size(); i++) {
      final String line = lines.get(i);
      if (line == null || line.length() == 0)
        continue;
      else if (line.startsWith("package") || line.startsWith("import"))
        continue;

      final Matcher matcher = classPattern.matcher(line);
      if (matcher.matches()) {
        final String classPath =
            (qualifiedName == null ? "" : qualifiedName + ".") + matcher.group(3);
        if (matcher.group(2).equals("enum"))
          processEnum(lines, idt2, i + 1, classPath, matcher.group(3), forgevi);
        i = processClass(lines, idt2, i + 1, classPath, forgevi);
      }
      if (idt1 != null && line.length() > idt1.length() && line.startsWith(idt1)
          && line.charAt(idt1.length()) == '}')
        return i;
    }

    return 0;
  }

  private static void processEnum(final List<String> lines, final String idt1, final int start,
      final String name, final String simpleName, final int forgevi) {
    final String idt2 = idt1 + "   ";
    // 1:name 2:body 3:end
    final Pattern enumEntry =
        Pattern.compile(idt2 + "(" + C + ")\\(\"" + C + "\", [0-9]+((?:, .+?)*)\\)(;|,| \\{)");
    // 1:modifier 2:paramaters 3:end 4:throw
    final Pattern constructor =
        Pattern.compile(idt2 + MODIFIERS + simpleName + FmlCleanup.PARAMS + "(" + THROWS
            + " (?:\\{\\}|\\{))");
    // 1:name 2:body
    final Pattern constructorCall = Pattern.compile(idt2 + "   (this|super)\\((.*?)\\);");
    final Pattern valueField =
        Pattern.compile(idt2 + "private static final " + name + "\\[\\] " + C + " = new " + name
            + "\\[\\]\\{.*?\\};");
    boolean prevSynthetic = false;
    for (int i = start; i < lines.size(); i++) {
      final String line = lines.get(i);
      Matcher matcher = enumEntry.matcher(line);
      if (matcher.matches()) {
        final StringBuilder sb = new StringBuilder();
        sb.append(idt2).append(matcher.group(1));
        final String[] args = Utils.split(matcher.group(2), ',');
        int lastidx = args.length - 1;
        if (line.endsWith("{") && args[lastidx].equals(" null"))
          lastidx--;
        if (lastidx > 0) {
          sb.append('(');
          args[1] = args[1].substring(1);
          for (int x = 1; x <= lastidx; x++)
            sb.append(args[x]).append(x < lastidx ? "," : "");
          sb.append(')');
        }
        lines.set(i, sb.append(matcher.group(3)).toString());
      }
      matcher = constructor.matcher(line);
      if (matcher.matches()) {
        final StringBuilder sb = new StringBuilder();
        sb.append(idt2).append(matcher.group(1)).append(simpleName).append('(');
        final String[] args = Utils.split(matcher.group(2), ',');
        if (args.length > 2) {
          args[2] = args[2].substring(1);
          for (int x = 2; x < args.length; x++)
            sb.append(args[x]).append(x < args.length - 1 ? "," : "");
        }
        lines.set(i, sb.append(')').append(matcher.group(3)).toString());
      }
      if (forgevi >= 967) {
        matcher = constructorCall.matcher(line);
        if (matcher.matches()) {
          final StringBuilder sb = new StringBuilder();
          sb.append(idt2).append("   ").append(matcher.group(1)).append('(');
          final String[] args = Utils.split(matcher.group(2), ',');
          if (args.length > 2) {
            args[2] = args[2].substring(1);
            for (int x = 2; x < args.length; x++)
              sb.append(args[x]).append(x < args.length - 1 ? "," : "");
          }
          lines.set(i, sb.append(");").toString());
        }
      }
      if (prevSynthetic) {
        matcher = valueField.matcher(line);
        if (matcher.matches())
          lines.set(i, "");
      }
      if (line.contains("// $FF: synthetic field")) {
        lines.set(i, "");
        prevSynthetic = true;
      } else
        prevSynthetic = false;
      if (line.length() > idt1.length() && line.startsWith(idt1)
          && line.charAt(idt1.length()) == '}')
        break;
    }
  }

  private static String synthetic_replacement(final Matcher match) {
    if (!match.group(4).equals(match.group(7)))
      return match.group();
    String arg1 = match.group(5);
    final String arg2 = match.group(8);
    if (arg1.equals("") && arg2.equals(""))
      return "";
    final String[] args = arg1.split(", ");
    for (int x = 0; x < args.length; x++)
      args[x] = Utils.split(args[x], ' ')[1];
    final StringBuilder sb = new StringBuilder();
    sb.append(args[0]);
    for (int x = 1; x < args.length; x++)
      sb.append(", ").append(args[x]);
    arg1 = sb.toString();
    if (arg1.equals(arg2))
      return "";
    return match.group();
  }

  private static void abstract_replacement(final Matcher match, final StringBuffer sb) {
    final int from = match.start(5) - match.start();
    final int to = match.end(5) - match.start();
    String number = match.group(4);
    final String g0 = match.group();
    if (!number.startsWith("func_") || from == to) {
      sb.append(g0);
      return;
    }
    number = number.substring(5);
    final int i = number.indexOf('_');
    if (i > 0)
      number = number.substring(0, i);
    sb.append(g0.substring(0, from));
    final String[] args = g0.substring(from, to).split(", ");
    for (int x = 0; x < args.length; x++) {
      final String[] p = Utils.split(args[x], ' ');
      sb.append(p[0]);
      sb.append(' ');
      if (p.length > 2) {
        sb.append(p[1]).append(' ');
        p[1] = p[2];
      }
      if (p[1].startsWith("var"))
        sb.append("p_").append(number).append('_').append(p[1].substring(3)).append('_');
      else
        sb.append(p[1]);
      if (x < args.length - 1)
        sb.append(", ");
    }
    sb.append(g0.substring(to));
  }
}
