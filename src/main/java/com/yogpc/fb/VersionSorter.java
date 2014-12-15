package com.yogpc.fb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.yogpc.fb.sa.ProjectConfig.ForgeVersion;

public class VersionSorter {
  private final ArrayList<String> gskip, gdebug, gonly, gpatch;

  @SuppressWarnings("unchecked")
  public VersionSorter(final ArrayList<String> skip, final ArrayList<String> debug,
      final ArrayList<String> only, final ArrayList<String> patch) {
    this.gskip = skip == null ? null : (ArrayList<String>) skip.clone();
    this.gdebug = debug == null ? null : (ArrayList<String>) debug.clone();
    this.gonly = only == null ? null : (ArrayList<String>) only.clone();
    this.gpatch = patch == null ? null : (ArrayList<String>) patch.clone();
  }

  public List<ForgeVersion> sort(final List<ForgeVersion> from, final List<ForgeVersion> ponly,
      final List<ForgeVersion> ddebug) {
    final Map<String, ForgeVersion> map = new HashMap<String, ForgeVersion>();
    final Map<String, String> parent = new HashMap<String, String>();
    final List<String> compile = new ArrayList<String>();
    final List<String> patch = new ArrayList<String>();
    final List<String> debug = new ArrayList<String>();
    for (final ForgeVersion v : from) {
      if (v.name == null)
        v.name = v.forgev;
      map.put(v.name, v);
      if (v.parent != null)
        parent.put(v.name, v.parent);
      if (this.gskip != null && this.gskip.contains(v.name))
        continue;
      if (this.gdebug != null && (this.gdebug.size() == 0 || this.gdebug.contains(v.name)))
        debug.add(v.name);
      if (this.gpatch != null && (this.gpatch.size() == 0 || this.gpatch.contains(v.name))) {
        patch.add(v.name);
        continue;
      }
      if (this.gonly != null && !this.gonly.contains(v.name))
        continue;
      patch.add(v.name);
      compile.add(v.name);
    }
    for (int i = 0; i < patch.size(); i++) {
      final String sp = parent.get(patch.get(i));
      if (sp == null)
        continue;
      final int ip = patch.indexOf(sp);
      if (ip < 0)
        patch.add(i--, sp);
      else if (i < ip)
        patch.add(i--, patch.remove(ip));
    }
    final List<ForgeVersion> ret = new ArrayList<ForgeVersion>();
    for (final String s : patch) {
      final ForgeVersion v = map.get(s);
      if (v == null) {
        System.err.print(":::WARNING::: null ForgeVersion detected ");
        System.err.println(s);
        continue;
      }
      if (debug.contains(s))
        ddebug.add(v);
      if (!compile.contains(s))
        ponly.add(v);
      ret.add(v);
    }
    return ret;
  }
}
