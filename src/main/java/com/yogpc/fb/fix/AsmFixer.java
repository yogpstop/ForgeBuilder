package com.yogpc.fb.fix;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.RemappingClassAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class AsmFixer extends ClassLoader {
  private static final class MyRemapper extends Remapper {
    public MyRemapper() {}

    @Override
    public String map(final String typeName) {
      if ("org/objectweb/asm/commons/RemappingClassAdapter".equals(typeName))
        return "com/yogpc/fb/fix/RemappingClassAdapter";
      if ("org/objectweb/asm/commons/RemappingMethodAdapter".equals(typeName))
        return "com/yogpc/fb/fix/RemappingMethodAdapter";
      if ("org/objectweb/asm/commons/LocalVariablesSorter".equals(typeName))
        return "org/objectweb/asm/MethodVisitor";
      return typeName;
    }
  }

  private static final void fixConstructor(final ClassNode cn) {
    for (final MethodNode mn : cn.methods)
      if ("<init>".equals(mn.name)
          && "(IILjava/lang/String;Lorg/objectweb/asm/MethodVisitor;Lorg/objectweb/asm/commons/Remapper;)V"
              .equals(mn.desc)) {
        AbstractInsnNode in = mn.instructions.getFirst(), tmp;
        while (in != null) {
          if (in.getOpcode() == Opcodes.ILOAD && ((VarInsnNode) in).var == 2
              || in.getOpcode() == Opcodes.ALOAD && ((VarInsnNode) in).var == 3) {
            tmp = in.getNext();
            mn.instructions.remove(in);
            in = tmp;
            continue;
          }
          if (in.getOpcode() == Opcodes.INVOKESPECIAL) {
            final MethodInsnNode min = (MethodInsnNode) in;
            if (min.owner.equals("org/objectweb/asm/MethodVisitor") && min.name.equals("<init>")
                && min.desc.equals("(IILjava/lang/String;Lorg/objectweb/asm/MethodVisitor;)V"))
              min.desc = "(ILorg/objectweb/asm/MethodVisitor;)V";
          }
          in = in.getNext();
        }
        break;
      }
  }

  private static final Remapper rmp = new MyRemapper();

  private static final ClassNode get(final String name) throws IOException {
    final InputStream is = ClassVisitor.class.getClassLoader().getResource(name).openStream();
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final byte[] byteChunk = new byte[4096];
    int n;
    while ((n = is.read(byteChunk)) >= 0)
      baos.write(byteChunk, 0, n);
    is.close();
    final ClassReader cr = new ClassReader(baos.toByteArray());
    final ClassNode cn = new ClassNode();
    final RemappingClassAdapter rca = new RemappingClassAdapter(cn, rmp);
    cr.accept(rca, ClassReader.EXPAND_FRAMES);
    return cn;
  }

  @SuppressWarnings("unchecked")
  private final <T> Class<? extends T> write(final ClassNode cn) {
    final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    cn.accept(cw);
    final byte[] ba = cw.toByteArray();
    return (Class<? extends T>) defineClass(cn.name.replace('/', '.'), ba, 0, ba.length, null);
  }

  private final Class<? extends ClassVisitor> classAdapter;

  public AsmFixer() {
    super();
    ClassNode rca = null, rma = null;
    try {
      rca = get("org/objectweb/asm/commons/RemappingClassAdapter.class");
      rma = get("org/objectweb/asm/commons/RemappingMethodAdapter.class");
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    fixConstructor(rma);
    write(rma);
    this.classAdapter = write(rca);
  }

  public ClassVisitor InitAdapter(final ClassVisitor cv, final Remapper rem) throws Exception {
    return this.classAdapter.getConstructor(ClassVisitor.class, Remapper.class)
        .newInstance(cv, rem);
  }
}
