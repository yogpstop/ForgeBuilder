package com.yogpc.fb.lc;

import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.PUTSTATIC;

import java.io.File;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class DataDirInjector implements IClassTransformer {
  @Override
  public byte[] transform(final String name, final byte[] bytes) {
    if (bytes == null)
      return null;
    if (!"net.minecraft.client.Minecraft".equals(name))
      return bytes;

    final ClassNode classNode = new ClassNode();
    final ClassReader classReader = new ClassReader(bytes);
    classReader.accept(classNode, ClassReader.EXPAND_FRAMES);

    MethodNode main = null;
    for (final MethodNode methodNode : classNode.methods)
      if ("main".equals(methodNode.name)) {
        main = methodNode;
        break;
      }
    if (main == null)
      return bytes;

    FieldNode workDirNode = null;
    for (final FieldNode fieldNode : classNode.fields) {
      final String fileTypeDescriptor = Type.getDescriptor(File.class);
      if (fileTypeDescriptor.equals(fieldNode.desc)
          && (fieldNode.access & ACC_STATIC) == ACC_STATIC) {
        workDirNode = fieldNode;
        break;
      }
    }
    if (workDirNode == null)
      return bytes;

    main.instructions.insert(new FieldInsnNode(PUTSTATIC, "net/minecraft/client/Minecraft",
        workDirNode.name, "Ljava/io/File;"));
    main.instructions.insert(new FieldInsnNode(GETSTATIC, "com/yogpc/fb/lc/LaunchClassLoader",
        "minecraftHome", "Ljava/io/File;"));
    final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    classNode.accept(cw);
    return cw.toByteArray();
  }
}
