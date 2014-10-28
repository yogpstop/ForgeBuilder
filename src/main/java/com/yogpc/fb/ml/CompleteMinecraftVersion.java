package com.yogpc.fb.ml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.yogpc.fb.sa.Utils;

public class CompleteMinecraftVersion {
  private final Map<Library, Object> libraries = new HashMap<Library, Object>();

  public List<String> getNames() {
    final List<String> ret = new ArrayList<String>();
    for (final Library l : this.libraries.keySet())
      if (l != null && l.name != null)
        ret.add(l.name);
    return ret;
  }

  public boolean contains(final String s) {
    for (final Library l : this.libraries.keySet())
      if (l != null && l.name != null && l.name.startsWith(s))
        return true;
    return false;
  }

  public void add(final String name) {
    if (name != null)
      this.libraries.put(new Library(name), Utils.DUMMY_OBJECT);
  }
}
