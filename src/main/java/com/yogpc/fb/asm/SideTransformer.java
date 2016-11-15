package com.yogpc.fb.asm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.yogpc.fb.sa.Constants;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class SideTransformer {
  private final TransformRule ops;
  private final AnnotationNode clientNode;
  private final AnnotationNode serverNode;

  SideTransformer(String sidePath, TransformRule m) {
    // TODO old version cpw.mods.fml
    if (sidePath == null)
      sidePath = Constants.DEFAULT_SIDE_PATH;
    this.clientNode = new AnnotationNode("L" + sidePath + "/SideOnly;");
    this.clientNode.values = new ArrayList<Object>();
    this.clientNode.values.add("value");
    this.clientNode.values.add(new String[] {"L" + sidePath + "/Side;", "CLIENT"});
    this.serverNode = new AnnotationNode("L" + sidePath + "/SideOnly;");
    this.serverNode.values = new ArrayList<Object>();
    this.serverNode.values.add("value");
    this.serverNode.values.add(new String[] {"L" + sidePath + "/Side;", "SERVER"});
    this.ops = m;
  }

  private <T> T get_sideonly(final T cls, final boolean cli) {
    if (cls instanceof FieldNode) {
      final FieldNode node = (FieldNode) cls;
      if (node.visibleAnnotations == null)
        node.visibleAnnotations = new ArrayList<AnnotationNode>();
      node.visibleAnnotations.add(cli ? clientNode : serverNode);
    } else if (cls instanceof MethodNode) {
      final MethodNode node = (MethodNode) cls;
      if (node.visibleAnnotations == null)
        node.visibleAnnotations = new ArrayList<AnnotationNode>();
      node.visibleAnnotations.add(cli ? clientNode : serverNode);
    } else if (cls instanceof ClassNode) {
      final ClassNode node = (ClassNode) cls;
      if (this.ops.dont_annotate(node.name, cli))
        return cls;
      if (node.visibleAnnotations == null)
        node.visibleAnnotations = new ArrayList<AnnotationNode>();
      node.visibleAnnotations.add(cli ? clientNode : serverNode);
    }
    return cls;
  }

  private static <T> boolean isSame(final T a, final T b) {
    if (a instanceof FieldNode && b instanceof FieldNode) {
      final FieldNode na = (FieldNode) a, nb = (FieldNode) b;
      return na.name.equals(nb.name);
    } else if (a instanceof MethodNode && b instanceof MethodNode) {
      final MethodNode na = (MethodNode) a, nb = (MethodNode) b;
      return na.name.equals(nb.name) && na.desc.equals(nb.desc);
    }
    return false;
  }

  private static <T> boolean found(final int si, final List<T> fs, final T f) {
    for (int i = si + 1; i < fs.size(); i++)
      if (isSame(f, fs.get(i)))
        return true;
    return false;
  }

  private <T> void process(final List<T> sfs, final List<T> cfs) {
    int i;
    for (i = 0; i < cfs.size() && i < sfs.size(); i++) {
      final T cf = cfs.get(i);
      final T sf = sfs.get(i);
      if (isSame(sf, cf))
        continue;
      if (!found(i, sfs, cf))
        sfs.add(i, get_sideonly(cf, true));
      else if (!found(i, cfs, sf))
        cfs.add(i, get_sideonly(sf, false));
    }
    for (i = sfs.size(); i < cfs.size(); i++)
      sfs.add(i, get_sideonly(cfs.get(i), true));
    for (i = cfs.size(); i < sfs.size(); i++)
      cfs.add(i, get_sideonly(sfs.get(i), false));
  }

  private <T> void put(final int f, final List<T> l, final T n, final boolean cli) {
    boolean both = false;
    for (int i = f; i < l.size(); i++)
      if (isSame(n, l.get(i))) {
        l.remove(i);
        both = true;
        break;
      }
    if (!both)
      get_sideonly(n, cli);
    l.add(f, n);
  }

  private void processMethods(final List<MethodNode> sMethods, final List<MethodNode> cMethods) {
    int cPos = 0;
    int sPos = 0;
    String lcName = "";
    while (cPos < cMethods.size() || sPos < sMethods.size()) {
      while (sPos < sMethods.size()) {
        final MethodNode sM = sMethods.get(sPos);
        final String sName = sM.name;
        if (!sName.equals(lcName) && cPos < cMethods.size())
          break;
        put(cPos++, cMethods, sM, false);
        sPos++;
      }
      while (cPos < cMethods.size()) {
        final MethodNode cM = cMethods.get(cPos);
        final String cName = cM.name;
        final boolean equal = cName.equals(lcName);
        lcName = cName;
        if (!equal && sPos < sMethods.size())
          break;
        put(sPos++, sMethods, cM, true);
        cPos++;
      }
    }
  }

  private ClassNode get_merged_class(final ClassNode srv, final ClassNode cli) {
    process(srv.fields, cli.fields);
    // If not use it to merge methods, patch is failed...
    processMethods(srv.methods, cli.methods);
    return cli;
  }

  Map<String, ClassNode> process(final Collection<String> all, final Map<String, ClassNode> srv,
      final Map<String, ClassNode> cli) {
    final Map<String, ClassNode> ret = new HashMap<String, ClassNode>();
    for (final String name : all) {
      final ClassNode cli_cls = cli.get(name);
      final ClassNode srv_cls = srv.get(name);
      if (srv_cls == null)
        ret.put(name, get_sideonly(cli_cls, true));
      else if (cli_cls == null)
        ret.put(name, get_sideonly(srv_cls, false));
      else
        ret.put(name, get_merged_class(srv_cls, cli_cls));
    }
    return ret;
  }
}
