package com.yogpc.fb.asm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.objectweb.asm.Opcodes;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yogpc.fb.sa.Utils;

public class TransformRule {
  private final Map<String, JsonStruct> json = new HashMap<String, JsonStruct>();
  private final int forgevi;

  TransformRule(final int fvi) {
    this.forgevi = fvi;
  }

  int getAccessMethod(final String cls, final String nam, final String dsc) {
    if (!this.method_acc.containsKey(cls))
      return -1;
    Integer i = this.method_acc.get(cls).get(nam + dsc);
    if (i == null)
      i = this.method_acc.get(cls).get("*()");
    return i != null ? i.intValue() : -1;
  }

  int getAccessField(final String cls, final String nam) {
    if (!this.field_acc.containsKey(cls))
      return -1;
    Integer i = this.field_acc.get(cls).get(nam);
    if (i == null)
      i = this.field_acc.get(cls).get("*");
    return i != null ? i.intValue() : -1;
  }

  int getAccessClass(final String cls) {
    final Integer i = this.class_acc.get(cls);
    return i != null ? i.intValue() : -1;
  }

  String getMarker(final String cls) {
    return this.cls_marker.get(cls);
  }

  List<String> getParams(final String cls, final String nam, final String dsc) {
    if (!this.method_par.containsKey(cls))
      return new ArrayList<String>();
    List<String> tmp = this.method_par.get(cls).get(nam + dsc);
    if (tmp == null)
      tmp = this.method_par_raw.get(cls + nam + dsc);
    return tmp != null ? new ArrayList<String>(tmp) : new ArrayList<String>();
  }

  List<String> getExceptions(final String cls, final String nam, final String dsc) {
    if (!this.method_exc.containsKey(cls))
      return new ArrayList<String>();
    List<String> tmp = this.method_exc.get(cls).get(nam + dsc);
    if (tmp == null)
      tmp = this.method_exc_raw.get(cls + nam + dsc);
    return tmp != null ? new ArrayList<String>(tmp) : new ArrayList<String>();
  }

  JsonStruct getJson(final String cls) {
    return this.json.get(cls);
  }

  void loadJson(final JsonObject o) {
    for (final Map.Entry<String, JsonElement> entry : o.entrySet())
      this.json.put(entry.getKey(), new Gson().fromJson(entry.getValue(), JsonStruct.class));
  }

  void loadMap(final Properties m) {
    for (final Object rk : m.keySet()) {
      String sk = (String) rk;
      final String v = m.getProperty(sk);
      if (sk.endsWith("-Access")) {
        int tv = -1;
        if (v.equals("PUBLIC"))
          tv = Opcodes.ACC_PUBLIC;
        else if (v.equals("PROTECTED"))
          tv = Opcodes.ACC_PROTECTED;
        else if (v.equals("DEFAULT"))
          tv = 0;
        else if (v.equals("PRIVATE"))
          tv = Opcodes.ACC_PRIVATE;
        if (tv == -1)
          continue;
        sk = sk.substring(0, sk.length() - 7);
        final int i = sk.indexOf(".");
        if (i > -1) {
          final String cls = sk.substring(0, i);
          final String nam = sk.substring(i + 1);
          final int j = sk.indexOf("(", i);
          if (j > -1) {
            if (!this.method_acc.containsKey(cls))
              this.method_acc.put(cls, new HashMap<String, Integer>());
            this.method_acc.get(cls).put(nam, new Integer(tv));
          } else {
            if (!this.field_acc.containsKey(cls))
              this.field_acc.put(cls, new HashMap<String, Integer>());
            this.field_acc.get(cls).put(nam, new Integer(tv));
          }
        } else
          this.class_acc.put(sk, new Integer(tv));
      } else if (sk.contains("(")) {
        final String[] two = Utils.split(v, '|');
        final int i = sk.indexOf(".");
        if (i < 0) {
          if (!two[0].equals(""))
            this.method_exc_raw.put(sk, Arrays.asList(Utils.split(two[0], ',')));
          if (two.length >= 2 && !two[1].equals(""))
            this.method_par_raw.put(sk, Arrays.asList(Utils.split(two[1], ',')));
        } else {
          final String cls = sk.substring(0, i);
          final String nam = sk.substring(i + 1);
          if (!two[0].equals("")) {
            if (!this.method_exc.containsKey(cls))
              this.method_exc.put(cls, new HashMap<String, List<String>>());
            this.method_exc.get(cls).put(nam, Arrays.asList(Utils.split(two[0], ',')));
          }
          if (two.length >= 2 && !two[1].equals("")) {
            if (!this.method_par.containsKey(cls))
              this.method_par.put(cls, new HashMap<String, List<String>>());
            this.method_par.get(cls).put(nam, Arrays.asList(Utils.split(two[1], ',')));
          }
        }
      } else if (!sk.contains("."))
        this.cls_marker.put(sk, v);
    }
  }

  private final Map<String, Map<String, List<String>>> method_par =
      new HashMap<String, Map<String, List<String>>>();
  private final Map<String, Map<String, List<String>>> method_exc =
      new HashMap<String, Map<String, List<String>>>();
  private final Map<String, List<String>> method_par_raw = new HashMap<String, List<String>>();
  private final Map<String, List<String>> method_exc_raw = new HashMap<String, List<String>>();
  private final Map<String, String> cls_marker = new HashMap<String, String>();

  private final Map<String, Map<String, Integer>> field_acc =
      new HashMap<String, Map<String, Integer>>();
  private final Map<String, Map<String, Integer>> method_acc =
      new HashMap<String, Map<String, Integer>>();
  private final Map<String, Integer> class_acc = new HashMap<String, Integer>();

  void loadAT(final byte[] at) {
    final String[] lines = new String(at, Utils.UTF_8).split("(\r\n|\r|\n)");
    for (String line : lines) {
      if (line.indexOf('#') != -1)
        line = line.substring(0, line.indexOf('#'));
      line = line.trim().replace('.', '/');
      if (line.isEmpty())
        continue;
      final String[] columns = line.split(" +");
      // Calculate access flags
      int acc = 0;
      final String acc_raw = columns[0];
      if (acc_raw.startsWith("public"))
        acc |= Opcodes.ACC_PUBLIC;
      else if (acc_raw.startsWith("protected"))
        acc |= Opcodes.ACC_PROTECTED;
      else if (acc_raw.startsWith("private"))
        acc |= Opcodes.ACC_PRIVATE;
      if (acc_raw.endsWith("+f"))
        acc |= AccessTransformer.CHANGE_FINAL | Opcodes.ACC_FINAL;
      else if (acc_raw.endsWith("-f"))
        acc |= AccessTransformer.CHANGE_FINAL;
      // Generate names
      String owner_o = null;
      String owner = null;
      String name = null;
      if (columns.length == 3) {
        owner = columns[1];
        name = columns[2];
      } else if (columns.length == 2) {
        int i;
        if (!columns[1].contains("(")) {
          owner_o = columns[1];
          i = columns[1].lastIndexOf("/");
        } else
          i = columns[1].lastIndexOf("/", columns[1].indexOf("("));
        if (i > -1) {
          owner = columns[1].substring(0, i);
          name = columns[1].substring(i + 1);
        }
      }
      // Put
      if (name != null)
        if (name.contains("(")) {
          if (!this.method_acc.containsKey(owner))
            this.method_acc.put(owner, new HashMap<String, Integer>());
          this.method_acc.get(owner).put(name, new Integer(acc));
        } else {
          if (!this.field_acc.containsKey(owner))
            this.field_acc.put(owner, new HashMap<String, Integer>());
          this.field_acc.get(owner).put(name, new Integer(acc));
        }
      if (owner_o != null && !owner_o.contains("("))
        this.class_acc.put(owner_o, new Integer(acc));
    }
  }

  private final Map<String, Object> dontAnnotate = new HashMap<String, Object>();
  final Map<String, Object> dontProcess = new HashMap<String, Object>();
  private final Map<String, Object> copyToClient = new HashMap<String, Object>();
  private final Map<String, Object> copyToServer = new HashMap<String, Object>();

  boolean dont_annotate(final String cls, final boolean cli) {
    if (this.dontAnnotate.containsKey(cls))
      return true;
    if (this.forgevi < 219 && cli && !this.copyToServer.containsKey(cls))
      return true;
    if (this.forgevi < 219 && !cli && !this.copyToClient.containsKey(cls))
      return true;
    return false;
  }

  void loadMerge(final String[] merge) {
    for (String line : merge) {
      if (line.indexOf('#') != -1)
        line = line.substring(0, line.indexOf('#'));
      line = line.trim();
      switch (line.charAt(0)) {
        case '!':
          this.dontAnnotate.put(line.substring(1), Utils.DUMMY_OBJECT);
          break;
        case '^':
          this.dontProcess.put(line.substring(1), Utils.DUMMY_OBJECT);
          break;
        case '<':
          this.copyToClient.put(line.substring(1), Utils.DUMMY_OBJECT);
          break;
        case '>':
          this.copyToServer.put(line.substring(1), Utils.DUMMY_OBJECT);
          break;
      }
    }
  }
}
