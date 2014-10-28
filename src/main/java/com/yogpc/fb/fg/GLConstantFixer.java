package com.yogpc.fb.fg;

import java.io.InputStreamReader;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yogpc.fb.sa.Utils;

public class GLConstantFixer {
  private static final String[] PACKAGES = {"GL11", "GL12", "GL13", "GL14", "GL15", "GL20", "GL21",
      "ARBMultitexture", "ARBOcclusionQuery", "ARBVertexBufferObject", "ARBShaderObjects"};

  private static final String ADD_AFTER = "org.lwjgl.opengl.GL11";
  private static final String CHECK = "org.lwjgl.opengl.";
  private static final String IMPORT_CHECK = "import " + CHECK;
  private static final String IMPORT_REPLACE = "import " + ADD_AFTER + ";";
  private static final Pattern CALL_REGEX = Pattern.compile("(" + Utils.join(PACKAGES, '|')
      + ")\\.([\\w]+)\\(.+\\)");
  private static final Pattern CONSTANT_REGEX = Pattern.compile("(?<![-.\\w])\\d+(?![.\\w])");
  private static final JsonElement json = new JsonParser().parse(new InputStreamReader(
      GLConstantFixer.class.getResourceAsStream("/gl.json")));

  public static String fixOGL(final String _text) {
    String text = _text;
    if (!text.contains(IMPORT_CHECK))
      return text;
    text = annotateConstants(text);
    for (final String pack : PACKAGES)
      if (text.contains(pack + "."))
        text = updateImports(text, CHECK + pack);
    return text;
  }

  private static String annotateConstants(final String text) {
    final Matcher rootMatch = CALL_REGEX.matcher(text);
    String pack, method, fullCall;
    JsonObject listNode;
    final StringBuffer out = new StringBuffer(text.length());
    StringBuffer innerOut;
    while (rootMatch.find()) {
      fullCall = rootMatch.group();
      pack = rootMatch.group(1);
      method = rootMatch.group(2);
      final Matcher constantMatcher = CONSTANT_REGEX.matcher(fullCall);
      innerOut = new StringBuffer(fullCall.length());
      while (constantMatcher.find()) {
        final String constant = constantMatcher.group();
        String answer = null;
        for (final JsonElement group : (JsonArray) GLConstantFixer.json) {
          listNode = (JsonObject) ((JsonArray) group).get(0);
          if (listNode.has(pack) && jsonArrayContains(listNode.getAsJsonArray(pack), method)) {
            listNode = (JsonObject) ((JsonArray) group).get(1);
            for (final Map.Entry<String, JsonElement> entry : listNode.entrySet())
              if (((JsonObject) entry.getValue()).has(constant))
                answer =
                    entry.getKey() + "."
                        + ((JsonObject) entry.getValue()).get(constant).getAsString();
          }
        }
        if (answer != null) {
          constantMatcher.appendReplacement(innerOut, "");
          innerOut.append(answer);
        }
      }
      constantMatcher.appendTail(innerOut);
      rootMatch.appendReplacement(out, "");
      out.append(innerOut.toString());
    }
    rootMatch.appendTail(out);
    return out.toString();
  }

  private static boolean jsonArrayContains(final JsonArray nodes, final String str) {
    for (final JsonElement testMethod : nodes)
      if (testMethod.getAsString().equals(str))
        return true;
    return false;
  }

  private static String updateImports(final String text, final String imp) {
    if (!text.contains("import " + imp + ";"))
      return text.replace(IMPORT_REPLACE, IMPORT_REPLACE + "\nimport " + imp + ";");
    return text;
  }

}
