package com.yogpc.fb.ml;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CompleteMinecraftVersion {
  private final Set<Library> libraries = new HashSet<Library>();

  public List<String> getNames() {
    final List<String> ret = new ArrayList<String>();
    for (final Library l : this.libraries)
      if (l != null && l.name != null)
        ret.add(l.name);
    return ret;
  }

  public boolean contains(final String s) {
    for (final Library l : this.libraries)
      if (l != null && l.name != null && l.name.startsWith(s))
        return true;
    return false;
  }

  public void add(final String name) {
    if (name != null)
      this.libraries.add(new Library(name));
  }
}
