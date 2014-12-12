package com.yogpc.fb.fg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.yogpc.fb.sa.Utils;

public class FFPatcher {
  static final String MODIFIERS =
      "((?:(?:public|protected|private|static|abstract|final|native|synchronized|transient|volatile|strictfp)\\s+)*)";
  static final String THROWS = "(?:\\s+throws\\s+([\\w$\\.]+(?:\\s*,\\s*[\\w$\\.]+)*))?";
  // 1:indent 2:modifier 3:return 4:name 5:parameters 6:throw 7:name2 8:arg2
  private static final Pattern SYNTHETICS = Pattern
      .compile("(?m)(?:\\s*// \\$FF: (?:synthetic|bridge) method\\n){1,2}" + FmlCleanup.METHOD_REG
          + "\\s*\\{\\s*return\\s+this\\.(.+?)\\((.*)\\);\\s*\\}");
  private static final Pattern ABSTRACT = Pattern.compile("(?m)" + FmlCleanup.METHOD_REG + ";$");
  private static final Pattern TRAILING = Pattern.compile("(?m)[ \\t]+$");
  private static final Pattern VAIN_ZERO = Pattern.compile("([0-9]+\\.[0-9]*[1-9])0+([DdFfEe])");
  private static final Pattern NEWLINES = Pattern.compile("(?m)^\\n{2,}");
  private static final Pattern EMPTY_SUPER = Pattern.compile("(?m)^[ \t]+super\\(\\);\\n");

  public static String processFile(final String _text, final int forgevi, final String mcv) {
    StringBuffer out = new StringBuffer();
    Matcher m = SYNTHETICS.matcher(_text.replaceAll("(\\r\\n|\\r)", "\n"));
    if (forgevi > 534)
      while (m.find()) {
        m.appendReplacement(out, "");
        out.append(synthetic_replacement(m));
      }
    m.appendTail(out);
    String text =
        VAIN_ZERO.matcher(TRAILING.matcher(out.toString()).replaceAll("")).replaceAll("$1$2");
    final List<String> lines = new ArrayList<String>(Arrays.asList(Utils.split(text, '\n')));
    processClass(lines, "", 0, "", forgevi);
    text = Utils.join(lines.toArray(new String[lines.size()]), '\n');
    text = EMPTY_SUPER.matcher(NEWLINES.matcher(text).replaceAll("\n")).replaceAll("");
    if (forgevi >= 1048 && !mcv.equals("1.7.2")) {
      out = new StringBuffer();
      m = ABSTRACT.matcher(text);
      while (m.find()) {
        m.appendReplacement(out, "");
        out.append(abstract_replacement(m));
      }
      m.appendTail(out);
      text = out.toString();
    }
    return text;
  }

  private static int processClass(final List<String> lines, final String indent,
      final int startIndex, final String qualifiedName, final int forgevi) {
    // 1:modifier 2:type 3:name
    final Pattern classPattern =
        Pattern
            .compile(indent
                + MODIFIERS
                + "(enum|class|interface)\\s+([\\w$]+)(?:\\s+(?:extends|implements)\\s+[\\w$\\.]+(?:\\s*,\\s*[\\w$\\.]+)*)*\\s*\\{");

    for (int i = startIndex; i < lines.size(); i++) {
      final String line = lines.get(i);
      if (line == null || line.length() == 0)
        continue;
      else if (line.startsWith("package") || line.startsWith("import"))
        continue;

      final Matcher matcher = classPattern.matcher(line);
      if (matcher.find()) {
        String newIndent;
        String classPath;
        if (qualifiedName == null || qualifiedName.length() == 0) {
          classPath = matcher.group(3);
          newIndent = indent;
        } else {
          classPath = qualifiedName + "." + matcher.group(3);
          newIndent = indent + "   ";
        }
        if (matcher.group(2).equals("enum"))
          processEnum(lines, newIndent, i + 1, classPath, matcher.group(3), forgevi);
        i = processClass(lines, newIndent, i + 1, classPath, forgevi);
      }
      if (line.startsWith(indent + "}"))
        return i;
    }

    return 0;
  }

  private static void processEnum(final List<String> lines, final String indent,
      final int startIndex, final String qualifiedName, final String simpleName, final int forgevi) {
    final String newIndent = indent + "   ";
    // 1:name 2:body 3:end
    final Pattern enumEntry =
        Pattern
            .compile("^"
                + newIndent
                + "([\\w$]+)\\s*\\(\\s*\"(?:[\\w$]+)\"\\s*,\\s*[0-9]+(?:\\s*,\\s*(.*?))?\\)(\\s*(?:;|,|\\{)$)");
    // 1:modifier 2:paramaters 3:end 4:throw
    final Pattern constructor =
        Pattern.compile("^" + newIndent + MODIFIERS + simpleName + "\\((.*?)\\)(" + THROWS
            + " *(?:\\{\\}| \\{))");
    // 1:name 2:body
    final Pattern constructorCall =
        Pattern.compile("^" + newIndent + "   (this|super)\\s*\\(\\s*(.*?)\\s*\\)\\s*;");
    final Pattern valueField =
        Pattern.compile("^" + newIndent + "private static final " + qualifiedName
            + "\\[\\] [$\\w\\d]+ = new " + qualifiedName + "\\[\\]\\{.*?\\};");
    String newLine;
    boolean prevSynthetic = false;

    for (int i = startIndex; i < lines.size(); i++) {
      newLine = null;
      final String line = lines.get(i);
      Matcher matcher = enumEntry.matcher(line);
      if (matcher.find()) {
        String body = matcher.group(2);

        newLine = newIndent + matcher.group(1);

        if (body != null && body.length() != 0) {
          String[] args = body.split(", ");

          if (line.endsWith("{"))
            if (args[args.length - 1].equals("null"))
              args = Arrays.copyOf(args, args.length - 1);
          body = Utils.join(args, ", ");
        }

        if (body == null || body.length() == 0)
          newLine += matcher.group(3);
        else
          newLine += "(" + body + ")" + matcher.group(3);
      }
      matcher = constructor.matcher(line);
      if (matcher.find()) {
        final StringBuilder tmp = new StringBuilder();
        tmp.append(newIndent).append(matcher.group(1)).append(simpleName).append("(");

        final String[] args = matcher.group(2).split(", ");
        for (int x = 2; x < args.length; x++)
          tmp.append(args[x]).append(x < args.length - 1 ? ", " : "");
        tmp.append(")");

        tmp.append(matcher.group(3));
        newLine = tmp.toString();

        if (args.length <= 2 && newLine.endsWith("}"))
          newLine = "";
      }
      if (forgevi >= 967) {
        matcher = constructorCall.matcher(line);
        if (matcher.find()) {
          String body = matcher.group(2);

          if (body != null && body.length() != 0) {
            String[] args = body.split(", ");
            args = Arrays.copyOfRange(args, 2, args.length);
            body = Utils.join(args, ", ");
          }

          newLine = newIndent + "   " + matcher.group(1) + "(" + body + ");";
        }
      }

      if (prevSynthetic) {
        matcher = valueField.matcher(line);
        if (matcher.find())
          newLine = "";
      }

      if (line.contains("// $FF: synthetic field")) {
        newLine = "";
        prevSynthetic = true;
      } else
        prevSynthetic = false;

      if (newLine != null)
        lines.set(i, newLine);
      if (line.startsWith(indent + "}"))
        break;
    }
  }

  private static String synthetic_replacement(final Matcher match) {
    if (!match.group(4).equals(match.group(7)))
      return match.group();
    String arg1 = match.group(5);
    final String arg2 = match.group(8);
    if (arg1.equals(arg2) && arg1.equals(""))
      return "";

    final String[] args = match.group(5).split(", ");
    for (int x = 0; x < args.length; x++)
      args[x] = args[x].split(" ")[1];

    final StringBuilder b = new StringBuilder();
    b.append(args[0]);
    for (int x = 1; x < args.length; x++)
      b.append(", ").append(args[x]);
    arg1 = b.toString();

    if (arg1.equals(arg2))
      return "";

    return match.group();
  }

  private static String abstract_replacement(final Matcher match) {
    final String orig = match.group(5);
    String number = match.group(4);
    if (!number.startsWith("func_") || orig == null || orig.length() == 0)
      return match.group();
    number = number.substring(5);
    final int i = number.indexOf('_');
    if (i > 0)
      number = number.substring(0, i);
    final String[] args = orig.split(", ");
    final StringBuilder fixed = new StringBuilder();
    for (int x = 0; x < args.length; x++) {
      final String[] p = args[x].split(" ");
      if (p.length == 3) {
        p[0] = p[0] + " " + p[1];
        p[1] = p[2];
      }
      fixed.append(p[0]).append(" p_").append(number).append('_').append(p[1].substring(3))
          .append('_');
      if (x != args.length - 1)
        fixed.append(", ");
    }
    return match.group().replace(orig, fixed.toString());
  }
}
