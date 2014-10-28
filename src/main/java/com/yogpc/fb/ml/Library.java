package com.yogpc.fb.ml;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Library {
  final String name;

  Library(final String n) {
    this.name = n;
  }

  private static final Pattern p = Pattern.compile("([^:]+:[^:]+):.*");

  @Override
  public boolean equals(final Object o) {
    if (!(o instanceof Library))
      return false;
    if (this.name == null)
      return ((Library) o).name == null;
    if (this.name.equals(((Library) o).name))
      return true;
    final Matcher m1 = p.matcher(this.name);
    final Matcher m2 = p.matcher(((Library) o).name);
    if (m1.matches() && m2.matches())
      return m1.group(1).equals(m2.group(1));
    return false;
  }

  @Override
  public int hashCode() {
    if (this.name == null)
      return super.hashCode();
    final Matcher m1 = p.matcher(this.name);
    if (m1.matches())
      return m1.group(1).hashCode();
    return this.name.hashCode();
  }
}
