package com.yogpc.fb.sa;

import java.util.ArrayList;
import java.util.List;

public class CSVParser {
  public static final CSVParser I = new CSVParser(',', '"', '\\');
  private final char sep;
  private final char quot;
  private final char esc;

  private CSVParser(final char separator, final char quotechar, final char escape) {
    this.sep = separator;
    this.quot = quotechar;
    this.esc = escape;
  }

  public String[] parseLine(final String l) {
    if (l == null)
      return null;
    final List<String> ret = new ArrayList<String>();
    final StringBuilder sb = new StringBuilder(l.length());
    boolean inQ = false;
    for (int i = 0; i < l.length(); i++) {
      final char p = i > 0 ? l.charAt(i - 1) : 0;
      final char c = l.charAt(i);
      final char n = l.length() > i + 1 ? l.charAt(i + 1) : 0;
      if (c == this.esc && inQ) {
        if (n == this.quot || n == this.esc)
          sb.append(l.charAt(1 + i++));
        else
          sb.append(c);
      } else if (c == this.sep && !inQ) {
        ret.add(sb.toString());
        sb.setLength(0);
      } else if (c == this.quot) {
        if (inQ && n == this.quot)
          sb.append(l.charAt(1 + i++));
        else {
          if (p != this.sep && p != 0 && n != this.sep && n != 0)
            if (sb.length() > 0 && isAllWhiteSpace(sb))
              sb.setLength(0);
            else
              sb.append(c);
          inQ = !inQ;
        }
      } else
        sb.append(c);
    }
    ret.add(sb.toString());
    return ret.toArray(new String[ret.size()]);

  }

  private static boolean isAllWhiteSpace(final CharSequence seq) {
    for (final char c : seq.toString().toCharArray())
      if (!Character.isWhitespace(c))
        return false;
    return true;
  }
}
