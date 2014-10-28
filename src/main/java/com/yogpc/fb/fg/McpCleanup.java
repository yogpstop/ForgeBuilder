package com.yogpc.fb.fg;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class McpCleanup {
  public static final Pattern COMMENTS_TRAILING = Pattern.compile("(?m)[ \\t]+$");
  public static final Pattern COMMENTS_NEWLINES = Pattern.compile("(?m)^(?:\\r\\n|\\r|\\n){2,}");

  public static String stripComments(final String text) throws IOException {
    final StringReader in = new StringReader(text);
    final StringWriter out = new StringWriter(text.length());
    boolean inComment = false;
    boolean inString = false;
    char c;
    int ci;
    while ((ci = in.read()) != -1) {
      c = (char) ci;
      switch (c) {
        case '\\': {
          out.write(c);
          out.write(in.read());// Skip escaped chars
          break;
        }
        case '\"': {
          if (!inComment) {
            out.write(c);
            inString = !inString;
          }
          break;
        }
        case '\'': {
          if (!inComment) {
            out.write(c);
            out.write(in.read());
            out.write(in.read());
          }
          break;
        }
        case '*': {
          final char c2 = (char) in.read();
          if (inComment && c2 == '/') {
            inComment = false;
            out.write(' ');
          } else {
            out.write(c);
            out.write(c2);
          }
          break;
        }
        case '/': {
          if (!inString) {
            final char c2 = (char) in.read();
            switch (c2) {
              case '/':
                char c3 = 0;
                while (c3 != '\n' && c3 != '\r')
                  c3 = (char) in.read();
                out.write(c3);
                break;
              case '*':
                inComment = true;
                break;
              default:
                out.write(c);
                out.write(c2);
                break;
            }
          } else
            out.write(c);
          break;
        }
        default: {
          if (!inComment)
            out.write(c);
          break;
        }
      }
    }
    out.close();
    return COMMENTS_NEWLINES.matcher(COMMENTS_TRAILING.matcher(out.toString()).replaceAll(""))
        .replaceAll("\n");
  }

  public static final Pattern CLEANUP_header = Pattern.compile("^\\s+");
  public static final Pattern CLEANUP_footer = Pattern.compile("\\s+$");
  public static final Pattern CLEANUP_trailing = Pattern.compile("(?m)[ \\t]+$");
  public static final Pattern CLEANUP_package = Pattern.compile("(?m)^package ([\\w.]+);$");
  public static final Pattern CLEANUP_import = Pattern
      .compile("(?m)^import (?:([\\w.]*?)\\.)?(?:[\\w]+);(?:\\r\\n|\\r|\\n)");
  public static final Pattern CLEANUP_newlines = Pattern.compile("(?m)^\\s*(?:\\r\\n|\\r|\\n){2,}");
  public static final Pattern CLEANUP_ifstarts = Pattern
      .compile("(?m)(^(?![\\s{}]*$).+(?:\\r\\n|\\r|\\n))((?:[ \\t]+)if.*)");
  public static final Pattern CLEANUP_blockstarts = Pattern
      .compile("(?m)(?<=\\{)\\s+(?=(?:\\r\\n|\\r|\\n)[ \\t]*\\S)");
  public static final Pattern CLEANUP_blockends = Pattern
      .compile("(?m)(?<=[;}])\\s+(?=(?:\\r\\n|\\r|\\n)\\s*})");
  public static final Pattern CLEANUP_gl = Pattern.compile("\\s*\\/\\*\\s*GL_[^*]+\\*\\/\\s*");
  public static final Pattern CLEANUP_unicode = Pattern.compile("'\\\\u([0-9a-fA-F]{4})'");
  public static final Pattern CLEANUP_charval = Pattern.compile("Character\\.valueOf\\(('.')\\)");
  public static final Pattern CLEANUP_maxD = Pattern.compile("1\\.7976[0-9]*[Ee]\\+308[Dd]");
  public static final Pattern CLEANUP_piD = Pattern.compile("3\\.1415[0-9]*[Dd]");
  public static final Pattern CLEANUP_piF = Pattern.compile("3\\.1415[0-9]*[Ff]");
  public static final Pattern CLEANUP_2piD = Pattern.compile("6\\.2831[0-9]*[Dd]");
  public static final Pattern CLEANUP_2piF = Pattern.compile("6\\.2831[0-9]*[Ff]");
  public static final Pattern CLEANUP_pi2D = Pattern.compile("1\\.5707[0-9]*[Dd]");
  public static final Pattern CLEANUP_pi2F = Pattern.compile("1\\.5707[0-9]*[Ff]");
  public static final Pattern CLEANUP_3pi2D = Pattern.compile("4\\.7123[0-9]*[Dd]");
  public static final Pattern CLEANUP_3pi2F = Pattern.compile("4\\.7123[0-9]*[Ff]");
  public static final Pattern CLEANUP_pi4D = Pattern.compile("0\\.7853[0-9]*[Dd]");
  public static final Pattern CLEANUP_pi4F = Pattern.compile("0\\.7853[0-9]*[Ff]");
  public static final Pattern CLEANUP_pi5D = Pattern.compile("0\\.6283[0-9]*[Dd]");
  public static final Pattern CLEANUP_pi5F = Pattern.compile("0\\.6283[0-9]*[Ff]");
  public static final Pattern CLEANUP_180piD = Pattern.compile("57\\.295[0-9]*[Dd]");
  public static final Pattern CLEANUP_180piF = Pattern.compile("57\\.295[0-9]*[Ff]");
  public static final Pattern CLEANUP_2pi9D = Pattern.compile("0\\.6981[0-9]*[Dd]");
  public static final Pattern CLEANUP_2pi9F = Pattern.compile("0\\.6981[0-9]*[Ff]");
  public static final Pattern CLEANUP_pi10D = Pattern.compile("0\\.3141[0-9]*[Dd]");
  public static final Pattern CLEANUP_pi10F = Pattern.compile("0\\.3141[0-9]*[Ff]");
  public static final Pattern CLEANUP_2pi5D = Pattern.compile("1\\.2566[0-9]*[Dd]");
  public static final Pattern CLEANUP_2pi5F = Pattern.compile("1\\.2566[0-9]*[Ff]");
  public static final Pattern CLEANUP_7pi100D = Pattern.compile("0\\.21991[0-9]*[Dd]");
  public static final Pattern CLEANUP_7pi100F = Pattern.compile("0\\.21991[0-9]*[Ff]");
  public static final Pattern CLEANUP_185pi100D = Pattern.compile("5\\.8119[0-9]*[Dd]");
  public static final Pattern CLEANUP_185pi100F = Pattern.compile("0\\.8119[0-9]*[Ff]");

  public static String cleanup(final String _text) {
    String text = CLEANUP_header.matcher(_text).replaceAll("");
    text = CLEANUP_footer.matcher(text).replaceAll("");
    text = CLEANUP_trailing.matcher(text).replaceAll("");
    text = CLEANUP_newlines.matcher(text).replaceAll("\n");
    text = CLEANUP_ifstarts.matcher(text).replaceAll("$1\n$2");
    text = CLEANUP_blockstarts.matcher(text).replaceAll("");
    text = CLEANUP_blockends.matcher(text).replaceAll("");
    text = CLEANUP_gl.matcher(text).replaceAll("");
    text = CLEANUP_maxD.matcher(text).replaceAll("Double.MAX_VALUE");

    {
      final Matcher matcher = CLEANUP_unicode.matcher(text);
      int val;
      final StringBuffer buffer = new StringBuffer(text.length());

      while (matcher.find()) {
        val = Integer.parseInt(matcher.group(1), 16);
        if (val > 255) {
          matcher.appendReplacement(buffer, "");
          buffer.append(Integer.toString(val));
        }
      }
      matcher.appendTail(buffer);
      text = buffer.toString();
    }

    text = CLEANUP_charval.matcher(text).replaceAll("$1");
    text = CLEANUP_piD.matcher(text).replaceAll("Math.PI");
    text = CLEANUP_piF.matcher(text).replaceAll("(float)Math.PI");
    text = CLEANUP_2piD.matcher(text).replaceAll("(Math.PI * 2D)");
    text = CLEANUP_2piF.matcher(text).replaceAll("((float)Math.PI * 2F)");
    text = CLEANUP_pi2D.matcher(text).replaceAll("(Math.PI / 2D)");
    text = CLEANUP_pi2F.matcher(text).replaceAll("((float)Math.PI / 2F)");
    text = CLEANUP_3pi2D.matcher(text).replaceAll("(Math.PI * 3D / 2D)");
    text = CLEANUP_3pi2F.matcher(text).replaceAll("((float)Math.PI * 3F / 2F)");
    text = CLEANUP_pi4D.matcher(text).replaceAll("(Math.PI / 4D)");
    text = CLEANUP_pi4F.matcher(text).replaceAll("((float)Math.PI / 4F)");
    text = CLEANUP_pi5D.matcher(text).replaceAll("(Math.PI / 5D)");
    text = CLEANUP_pi5F.matcher(text).replaceAll("((float)Math.PI / 5F)");
    text = CLEANUP_180piD.matcher(text).replaceAll("(180D / Math.PI)");
    text = CLEANUP_180piF.matcher(text).replaceAll("(180F / (float)Math.PI)");
    text = CLEANUP_2pi9D.matcher(text).replaceAll("(Math.PI * 2D / 9D)");
    text = CLEANUP_2pi9F.matcher(text).replaceAll("((float)Math.PI * 2F / 9F)");
    text = CLEANUP_pi10D.matcher(text).replaceAll("(Math.PI / 10D)");
    text = CLEANUP_pi10F.matcher(text).replaceAll("((float)Math.PI / 10F)");
    text = CLEANUP_2pi5D.matcher(text).replaceAll("(Math.PI * 2D / 5D)");
    text = CLEANUP_2pi5F.matcher(text).replaceAll("((float)Math.PI * 2F / 5F)");
    text = CLEANUP_7pi100D.matcher(text).replaceAll("(Math.PI * 7D / 100D)");
    text = CLEANUP_7pi100F.matcher(text).replaceAll("((float)Math.PI * 7F / 100F)");
    text = CLEANUP_185pi100D.matcher(text).replaceAll("(Math.PI * 185D / 100D)");
    text = CLEANUP_185pi100F.matcher(text).replaceAll("((float)Math.PI * 185F / 100F)");

    return text;
  }

  public static String fixImports(final String _text) {
    String text = _text;
    final Matcher match = CLEANUP_package.matcher(text);
    if (match.find()) {
      final String pack = match.group(1);

      final Matcher match2 = CLEANUP_import.matcher(text);
      while (match2.find())
        if (match2.group(1).equals(pack))
          text = text.replace(match2.group(), "");
    }

    return text;
  }
}
