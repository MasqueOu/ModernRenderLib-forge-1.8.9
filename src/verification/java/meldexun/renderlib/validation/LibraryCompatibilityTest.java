package meldexun.renderlib.validation;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;

/** Static member resolution plus CPU-only calls through the real consumer classes. */
public final class LibraryCompatibilityTest {
    private final Map<String, ClassNode> classes = new HashMap<>();
    private final ClassLoader loader;
    private int references;

    private LibraryCompatibilityTest(ClassLoader loader) { this.loader = loader; }

    public static void main(String[] args) throws Exception {
        List<URL> urls = new ArrayList<>();
        // An optional candidate archive takes precedence without changing the mod under test.
        if (args.length > 3) urls.add(new java.io.File(args[3]).toURI().toURL());
        urls.add(new java.io.File(args[0]).toURI().toURL());
        urls.add(new java.io.File(args[1]).toURI().toURL());
        for (String path : args[2].split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            urls.add(new java.io.File(path).toURI().toURL());
        }
        try (URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), null)) {
            ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(loader);
            try {
            LibraryCompatibilityTest test = new LibraryCompatibilityTest(loader);
            test.verifyReferences(args[0], "meldexun/renderlib/");
            test.verifyReferences(args[1], "meldexun/nothirium/");
            test.verifySectors();
            test.verifyUniformCache();
            test.verifyAsmLogging();
            System.out.println("Java 8 consumer compatibility passed: " + test.references
                    + " member references, Nothirium RB/AVL sector allocation, uniform cache and ASMUtil beta9 logging.");
            } finally {
                Class<?> logManager = loader.loadClass("org.apache.logging.log4j.LogManager");
                Object context = logManager.getMethod("getContext", boolean.class).invoke(null, false);
                context.getClass().getMethod("stop").invoke(context);
                Thread.currentThread().setContextClassLoader(previousLoader);
            }
        }
    }

    private static boolean libraryOwner(String owner) {
        return owner.startsWith("it/unimi/dsi/fastutil/") || owner.startsWith("meldexun/asmutil2/")
                || owner.startsWith("meldexun/memoryutil/") || owner.startsWith("meldexun/matrixutil/")
                || owner.startsWith("meldexun/reflectionutil/") || owner.startsWith("meldexun/randomutil/")
                || owner.startsWith("meldexun/renderlib/") && !owner.startsWith("meldexun/renderlib/mixin/");
    }

    private ClassNode readClass(String name) throws Exception {
        if (classes.containsKey(name)) return classes.get(name);
        try (InputStream input = loader.getResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError("Missing class " + name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            classes.put(name, node);
            return node;
        }
    }

    private boolean hasMember(String owner, String name, String desc, boolean field, Set<String> visited) throws Exception {
        if (owner == null || !visited.add(owner)) return false;
        ClassNode node = readClass(owner);
        if (field) {
            for (Object member : node.fields) {
                FieldNode value = (FieldNode) member;
                if (value.name.equals(name) && value.desc.equals(desc)) return true;
            }
        } else {
            for (Object member : node.methods) {
                MethodNode value = (MethodNode) member;
                if (value.name.equals(name) && value.desc.equals(desc)) return true;
            }
            if (name.equals("<init>")) return false;
        }
        if (hasMember(node.superName, name, desc, field, visited)) return true;
        for (Object parent : node.interfaces) {
            if (hasMember((String) parent, name, desc, field, visited)) return true;
        }
        return false;
    }

    private void verifyReferences(String jarPath, String callerPrefix) throws Exception {
        try (JarFile jar = new JarFile(jarPath)) {
            java.util.Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().startsWith(callerPrefix) || !entry.getName().endsWith(".class")) continue;
                ClassNode node = new ClassNode();
                try (InputStream input = jar.getInputStream(entry)) {
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
                for (Object item : node.methods) {
                    MethodNode method = (MethodNode) item;
                    for (AbstractInsnNode instruction : method.instructions.toArray()) {
                        String owner, name, desc;
                        boolean field;
                        if (instruction instanceof MethodInsnNode) {
                            MethodInsnNode call = (MethodInsnNode) instruction;
                            owner = call.owner; name = call.name; desc = call.desc; field = false;
                        } else if (instruction instanceof FieldInsnNode) {
                            FieldInsnNode call = (FieldInsnNode) instruction;
                            owner = call.owner; name = call.name; desc = call.desc; field = true;
                        } else if (instruction instanceof InvokeDynamicInsnNode) {
                            for (Object argument : ((InvokeDynamicInsnNode) instruction).bsmArgs) {
                                if (argument instanceof Handle) {
                                    Handle handle = (Handle) argument;
                                    if (libraryOwner(handle.getOwner())) {
                                        boolean handleField = handle.getTag() <= Opcodes.H_PUTSTATIC;
                                        if (!hasMember(handle.getOwner(), handle.getName(), handle.getDesc(), handleField, new HashSet<>())) {
                                            throw new AssertionError("Unresolved consumer method handle: " + handle.getOwner() + "." + handle.getName() + handle.getDesc());
                                        }
                                        references++;
                                    }
                                }
                            }
                            continue;
                        } else continue;
                        if (!libraryOwner(owner)) continue;
                        if (!hasMember(owner, name, desc, field, new HashSet<>())) {
                            throw new AssertionError("Unresolved consumer member: " + node.name + " -> " + owner + "." + name + desc);
                        }
                        references++;
                    }
                }
            }
        }
    }

    private void verifySectors() throws Exception {
        Class<?> listType = loader.loadClass("meldexun.nothirium.util.SectorizedList");
        Object list = listType.getConstructor(int.class).newInstance(32);
        Method claim = listType.getMethod("claim", int.class);
        Object a = claim.invoke(list, 8), b = claim.invoke(list, 5), c = claim.invoke(list, 10);
        Class<?> sectorType = a.getClass();
        Method first = sectorType.getMethod("getFirstSector");
        Method free = listType.getMethod("free", sectorType);
        int releasedStart = (Integer) first.invoke(b);
        free.invoke(list, b);
        Object reused = claim.invoke(list, 4);
        if (((Integer) first.invoke(reused)) != releasedStart) throw new AssertionError("Nothirium best-fit sector reuse changed");
        free.invoke(list, a); free.invoke(list, c); free.invoke(list, reused);
        Object merged = claim.invoke(list, 32);
        if (((Integer) first.invoke(merged)) != 0) throw new AssertionError("Nothirium free-sector merging failed");
        claim.invoke(list, 96);

        Class<?> treeType = loader.loadClass("it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap");
        Class<?> avlType = loader.loadClass("meldexun.nothirium.util.FreeSectorManager$AVL");
        Object avl = avlType.getConstructor(treeType).newInstance(treeType.getConstructor().newInstance());
        Constructor<?> sectorConstructor = sectorType.getDeclaredConstructor(boolean.class, int.class, int.class);
        sectorConstructor.setAccessible(true);
        Object avlLarge = sectorConstructor.newInstance(false, 0, 8);
        Object avlSmall = sectorConstructor.newInstance(false, 8, 5);
        avlType.getMethod("add", sectorType).invoke(avl, avlLarge);
        avlType.getMethod("add", sectorType).invoke(avl, avlSmall);
        if (avlType.getMethod("get", int.class).invoke(avl, 6) != avlLarge) throw new AssertionError("Nothirium AVL reflective traversal failed");
    }

    private void verifyUniformCache() throws Exception {
        Class<?> type = loader.loadClass("meldexun.renderlib.util.FunctionalObject2IntMap");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object cache = constructor.newInstance();
        Method compute = type.getDeclaredMethod("computeIfAbsent", Object.class, ToIntFunction.class);
        compute.setAccessible(true);
        final int[] calls = {0};
        ToIntFunction<Object> function = key -> { calls[0]++; return -17; };
        compute.invoke(cache, "uniform", function);
        if (!compute.invoke(cache, "uniform", function).equals(-17) || calls[0] != 1) throw new AssertionError("Uniform cache recomputed an existing location");
        for (int i = 0; i < 1024; i++) compute.invoke(cache, "key" + i, function);
        compute.invoke(cache, null, function);
        int previousCalls = calls[0];
        compute.invoke(cache, null, function);
        if (calls[0] != previousCalls) throw new AssertionError("Null uniform-cache key recomputed");
    }

    private void verifyAsmLogging() throws Exception {
        Class<?> nodeType = loader.loadClass("org.objectweb.asm.tree.ClassNode");
        Object node = nodeType.getConstructor().newInstance();
        nodeType.getField("name").set(node, "CompatibilityFixture");
        Class<?> transformerType = loader.loadClass("meldexun.asmutil2.ClassNodeTransformer");
        Consumer<Object> consumer = value -> { };
        Object transformer = transformerType.getMethod("create", int.class, Consumer.class).invoke(null, 0, consumer);
        if (!Boolean.TRUE.equals(transformerType.getMethod("transform", nodeType).invoke(transformer, node))) {
            throw new AssertionError("ASMUtil transform/logging failed");
        }
    }
}
