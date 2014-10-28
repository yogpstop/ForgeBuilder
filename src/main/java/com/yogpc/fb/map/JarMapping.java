package com.yogpc.fb.map;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.objectweb.asm.commons.Remapper;

public class JarMapping {
  static final class ObfEntry {
    final String owner;
    final String desc;

    ObfEntry(final String o, final String d) {
      this.owner = o;
      this.desc = d;
    }

    @Override
    public final boolean equals(final Object o) {
      if (!(o instanceof ObfEntry))
        return false;
      final ObfEntry e = (ObfEntry) o;
      return this.owner.equals(e.owner) && this.desc.equals(e.desc);
    }

    @Override
    public final int hashCode() {
      return this.owner.hashCode() ^ this.desc.hashCode();
    }
  }

  static final class SrgEntry {
    String raw = "";
    final Map<ObfEntry, String> obfs = new HashMap<ObfEntry, String>();
  }

  private final class SrgRemapper extends Remapper {
    final Map<String, String> nam = new HashMap<String, String>();
    private final int cls;
    private final boolean all;

    SrgRemapper(final int i, final boolean a) {
      this.cls = i;
      this.all = a;
    }

    @Override
    public String mapMethodDesc(final String s) {
      if (s.equals(""))
        return s;
      return super.mapMethodDesc(s);
    }

    @Override
    public String mapFieldName(final String owner, final String name, final String desc) {
      return mapMethodName(owner, name, "");
    }

    @Override
    public String map(final String n) {
      if (this.cls == SRG_RAW)
        return n;
      final String r =
          (this.cls == SRG_OBF ? JarMapping.this.cls_raw_obf : JarMapping.this.cls_obf_raw).get(n);
      if (r != null)
        return r;
      final int index = n.lastIndexOf('$');
      if (index > -1)
        return map(n.substring(0, index)) + n.substring(index);
      final Map<String, String> pkg =
          this.cls == SRG_OBF ? JarMapping.this.pkg_raw_obf : JarMapping.this.pkg_obf_raw;
      final Iterator<String> iter = pkg.keySet().iterator();
      while (iter.hasNext()) {
        final String p = iter.next();
        if (p.equals(".") ? n.indexOf('/') == -1 : n.startsWith(p)) {
          final String newPackage = pkg.get(p);
          final String simple = p.equals(".") ? n : n.substring(p.length());
          return newPackage.equals(".") ? simple : newPackage + simple;
        }
      }
      return n;
    }

    @Override
    public String mapMethodName(final String owner, final String name, final String desc) {
      String r = this.nam.get(this.all ? owner + "/" + name + desc : name);
      if (r != null)
        return r;
      if (!this.all)
        return name;
      for (final String p : JarMapping.this.jr.getParents(owner))
        if (!(r = mapMethodName(p, name, desc)).equals(name))
          return r;
      return name;
    }

  }

  public static final int SRG_RAW = 0;
  public static final int SRG_OBF = 1;
  public static final int OBF_SRG = 2;
  public static final int OBF_RAW = 3;
  public static final int RAW_SRG = 4;
  public static final int RAW_OBF = 5;
  private final SrgRemapper[] rmps = new SrgRemapper[] {new SrgRemapper(SRG_RAW, false),
      new SrgRemapper(SRG_OBF, true), new SrgRemapper(OBF_SRG, true),
      new SrgRemapper(OBF_SRG, true), new SrgRemapper(SRG_RAW, true),
      new SrgRemapper(SRG_OBF, true)};
  JarRemapper jr;

  public final Remapper getRemapper(final int m, final JarRemapper j) {
    this.jr = j;
    final SrgRemapper r = this.rmps[m];
    final Remapper os = this.rmps[OBF_SRG];
    if (r.nam.size() > 0)
      return r;
    String ssrg;
    switch (m) {
      case SRG_RAW:
        for (final Map.Entry<String, SrgEntry> e : this.srg.entrySet())
          if (!e.getValue().raw.equals(""))
            r.nam.put(e.getKey(), e.getValue().raw);
        break;
      case SRG_OBF:
        for (final Map.Entry<String, SrgEntry> e : this.srg.entrySet()) {
          ssrg = e.getKey();
          for (final Map.Entry<ObfEntry, String> o : e.getValue().obfs.entrySet())
            r.nam.put(os.map(o.getKey().owner) + "/" + ssrg + os.mapMethodDesc(o.getKey().desc),
                o.getValue());
        }
        break;
      case OBF_SRG:
        for (final Map.Entry<String, SrgEntry> e : this.srg.entrySet()) {
          ssrg = e.getKey();
          for (final Map.Entry<ObfEntry, String> o : e.getValue().obfs.entrySet())
            r.nam.put(o.getKey().owner + "/" + o.getValue() + o.getKey().desc, ssrg);
        }
        break;
      case OBF_RAW:
        for (final Map.Entry<String, SrgEntry> e : this.srg.entrySet()) {
          ssrg = e.getValue().raw.equals("") ? e.getKey() : e.getValue().raw;
          for (final Map.Entry<ObfEntry, String> o : e.getValue().obfs.entrySet())
            r.nam.put(o.getKey().owner + "/" + o.getValue() + o.getKey().desc, ssrg);
        }
        break;
      case RAW_SRG:
        for (final Map.Entry<String, SrgEntry> e : this.srg.entrySet()) {
          if (e.getValue().raw.equals(""))
            continue;
          ssrg = e.getKey();
          for (final Map.Entry<ObfEntry, String> o : e.getValue().obfs.entrySet())
            r.nam.put(
                os.map(o.getKey().owner) + "/" + e.getValue().raw
                    + os.mapMethodDesc(o.getKey().desc), ssrg);
        }
        break;
      case RAW_OBF:
        for (final Map.Entry<String, SrgEntry> e : this.srg.entrySet()) {
          ssrg = e.getValue().raw.equals("") ? e.getKey() : e.getValue().raw;
          for (final Map.Entry<ObfEntry, String> o : e.getValue().obfs.entrySet())
            r.nam.put(os.map(o.getKey().owner) + "/" + ssrg + os.mapMethodDesc(o.getKey().desc),
                o.getValue());
        }
        break;
    }
    return r;
  }

  final Map<String, SrgEntry> srg = new HashMap<String, SrgEntry>();
  final Map<String, String> cls_obf_raw = new HashMap<String, String>();
  final Map<String, String> pkg_obf_raw = new HashMap<String, String>();
  final Map<String, String> cls_raw_obf = new HashMap<String, String>();
  final Map<String, String> pkg_raw_obf = new HashMap<String, String>();
  final Map<String, String> srg_javadoc = new HashMap<String, String>();
  final Map<String, String> jnd_pkd = new HashMap<String, String>();

  public final String toPkd(final String k) {
    return this.jnd_pkd.get(k);
  }

  public final String getJavadoc(final String k) {
    return this.srg_javadoc.get(k);
  }
}
