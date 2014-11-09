package com.yogpc.fb;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.yogpc.fb.sa.Constants;
import com.yogpc.fb.sa.MavenWrapper;

public class Eclipse {
  private static String getPath(final String group, final String artifact, final String version,
      final String sub) {
    final StringBuilder sb = new StringBuilder();
    sb.append(group.replace(".", "/")).append("/");
    sb.append(artifact).append("/");
    sb.append(version).append("/");
    sb.append(artifact).append('-').append(version);
    if (sub != null)
      sb.append('-').append(sub);
    sb.append(".jar");
    return sb.toString();
  }

  private static String get(final String path) {
    if (path == null)
      return null;
    if (new File(Constants.MINECRAFT_LIBRARIES, path.replace('/', File.separatorChar)).exists())
      return "lib/" + path;
    if (new File(Constants.DATA_DIR, ("cache/" + path).replace('/', File.separatorChar)).exists())
      return "mcp/cache/" + path;
    return null;
  }

  private static Element getCPE(final Document d, final String kind, final String path,
      final String sp, final boolean exported) {
    final Element ret = d.createElement("classpathentry");
    ret.setAttribute("kind", kind);
    ret.setAttribute("path", path);
    if (sp != null)
      ret.setAttribute("sourcepath", sp);
    if (exported)
      ret.setAttribute("exported", "true");
    return ret;
  }

  private static Element getLibrary(final Document d, final String l) {
    final Matcher m = MavenWrapper.lib_nam.matcher(l);
    String lib = null, source = null;
    if (!l.startsWith("http://") && !l.startsWith("https://") && m.matches()) {
      lib = getPath(m.group(1), m.group(2), m.group(3), m.group(4));
      source = getPath(m.group(1), m.group(2), m.group(3), "sources");
    } else {
      lib = l.replace(":", "%3A").replace("//", "/");
      if (!lib.endsWith("/"))
        lib += "/";
      lib += "file.jar";
    }
    if ((lib = get(lib)) == null)
      return null;
    return getCPE(d, "lib", lib, get(source), true);
  }

  private static void addSources(final Document doc, final Element root, final ProjectConfig pc,
      final ProjectConfig.ForgeVersion fv) {
    if (fv.src_base == null)
      fv.src_base = "src/" + (fv.parent == null ? "main" : fv.name);
    final List<String> from = new LinkedList<String>();
    if (fv.parent == null) {
      if (pc.java == null)
        from.add("java");
      else
        from.addAll(pc.java);
      if (pc.resources == null)
        from.add("resources");
      else
        from.addAll(pc.resources);
    } else
      from.add("java");
    for (final String s : from)
      root.appendChild(getCPE(doc, "src", fv.src_base + "/" + s, null, false));
  }

  private static void createClasspath(final File base, final ForgeData fd, final ProjectConfig pc,
      final ProjectConfig.ForgeVersion fv) throws Exception {
    final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    final DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
    final Document doc = docBuilder.newDocument();
    final Element root = doc.createElement("classpath");
    doc.appendChild(root);
    root.appendChild(getCPE(
        doc,
        "con",
        "org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.6",
        null, false));
    addSources(doc, root, pc, fv);
    root.appendChild(getCPE(doc, "output", "bin", null, false));
    root.appendChild(getCPE(doc, "lib", "mcp/" + fv.forgev + "-dev.jar", "mcp/" + fv.forgev
        + "-sources.jar", true));
    Element lib;
    if (fd.config.depends != null)
      for (final Object s : fd.config.depends)
        if ((lib = getLibrary(doc, (String) s)) != null)
          root.appendChild(lib);
    if (fv.depends != null)
      for (final Object s : fv.depends)
        if ((lib = getLibrary(doc, (String) s)) != null)
          root.appendChild(lib);
    final TransformerFactory transformerFactory = TransformerFactory.newInstance();
    final Transformer transformer = transformerFactory.newTransformer();
    final DOMSource source = new DOMSource(doc);
    final StreamResult result = new StreamResult(new File(base, ".classpath"));
    transformer.transform(source, result);
  }

  private static Element createLink(final Document d, final File f, final String n)
      throws DOMException, IOException {
    final Element link = d.createElement("link");
    final Element name = d.createElement("name");
    name.appendChild(d.createTextNode(n));
    link.appendChild(name);
    final Element type = d.createElement("type");
    type.appendChild(d.createTextNode("2"));
    link.appendChild(type);
    final Element uri = d.createElement("location");
    uri.appendChild(d.createTextNode(f.getCanonicalPath().replace(File.separatorChar, '/')));
    link.appendChild(uri);
    return link;
  }

  private static Element createLinkedResources(final Document d) throws DOMException, IOException {
    final Element ret = d.createElement("linkedResources");
    ret.appendChild(createLink(d, Constants.MINECRAFT_LIBRARIES, "lib"));
    ret.appendChild(createLink(d, Constants.DATA_DIR, "mcp"));
    return ret;
  }

  private static void addJavaProject(final Document doc, final Element root) {
    final Element bs = doc.createElement("buildSpec");
    final Element bc = doc.createElement("buildCommand");
    final Element name = doc.createElement("name");
    name.appendChild(doc.createTextNode("org.eclipse.jdt.core.javabuilder"));
    bc.appendChild(name);
    final Element arguments = doc.createElement("arguments");
    arguments.appendChild(doc.createTextNode(""));
    bc.appendChild(arguments);
    bs.appendChild(bc);
    root.appendChild(bs);
    final Element nt = doc.createElement("natures");
    final Element nature = doc.createElement("nature");
    nature.appendChild(doc.createTextNode("org.eclipse.jdt.core.javanature"));
    nt.appendChild(nature);
    root.appendChild(nt);
  }

  private static void createProject(final File base, final String n) throws Exception {
    final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    final DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
    final Document doc = docBuilder.newDocument();
    final Element root = doc.createElement("projectDescription");
    doc.appendChild(root);
    final Element buf = doc.createElement("name");
    buf.appendChild(doc.createTextNode(n));
    root.appendChild(buf);
    addJavaProject(doc, root);
    root.appendChild(createLinkedResources(doc));
    final TransformerFactory transformerFactory = TransformerFactory.newInstance();
    final Transformer transformer = transformerFactory.newTransformer();
    final DOMSource source = new DOMSource(doc);
    final StreamResult result = new StreamResult(new File(base, ".project"));
    transformer.transform(source, result);
  }

  static void createEclipse(final File base, final ForgeData fd, final ProjectConfig pc,
      final ProjectConfig.ForgeVersion fv) throws Exception {
    System.out.println("> Create eclipse project");
    createProject(base, pc.artifactId);
    createClasspath(base, fd, pc, fv);
  }
}
