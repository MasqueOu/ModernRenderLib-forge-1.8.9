package meldexun.renderlib.validation;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.function.Supplier;
import java.util.function.Consumer;

/** Exercises production bytecode when another coremod provides fastutil first. */
public final class ShaderBuilderCompatibilityTest {
    public static void main(String[] args) throws Exception {
        // No parent project classpath: the selected provider must really win resolution.
        URL[] urls = {new File(args[2]).toURI().toURL(), new File(args[1]).toURI().toURL(),
                new File(args[0]).toURI().toURL()};
        try (URLClassLoader loader = new URLClassLoader(urls, null)) {
            Class<?> provider = loader.loadClass("it.unimi.dsi.fastutil.ints.IntArrayList");
            System.out.println("Effective fastutil: " + provider.getProtectionDomain().getCodeSource().getLocation());
            Class<?> builderType = loader.loadClass("meldexun.renderlib.util.GLShader$Builder");
            Object builder = builderType.getConstructor().newInstance();
            builderType.getMethod("addShader", int.class, Supplier.class).invoke(builder, 35633,
                    (Supplier<String>) () -> "vertex fixture");
            builderType.getMethod("addShader", int.class, Supplier.class).invoke(builder, 35632,
                    (Supplier<String>) () -> "fragment fixture");
            builderType.getMethod("bindAttribute", String.class, int.class).invoke(builder, "a_Pos", 0);
            builderType.getMethod("bindAttribute", String.class, int.class).invoke(builder, "a_Color", 1);
            Object shader;
            final int[] callbacks = {0};
            Consumer<Object> initialize = program -> {
                callbacks[0]++;
                try {
                    if (!program.getClass().getMethod("getUniform", String.class).invoke(program, "u_BlockTex").equals(1)) {
                        throw new AssertionError("Uniform lookup failed");
                    }
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError("Shader initialization callback failed", failure);
                }
            };
            try {
                shader = builderType.getMethod("build", Consumer.class).invoke(builder, initialize);
            } catch (InvocationTargetException failure) {
                throw new AssertionError("Production GLShader.Builder failed with "
                        + provider.getProtectionDomain().getCodeSource().getLocation(), failure.getCause());
            }
            if (!shader.getClass().getMethod("getProgram").invoke(shader).equals(42)) {
                throw new AssertionError("Unexpected shader program");
            }
            Class<?> gl = loader.loadClass("org.lwjgl.opengl.GL20");
            for (String counter : new String[] {"compiled", "attached", "bound", "deleted"}) {
                if (gl.getField(counter).getInt(null) != 2) throw new AssertionError(counter);
            }
            if (gl.getField("linked").getInt(null) != 1) throw new AssertionError("linked");
            if (callbacks[0] != 1 || gl.getField("uniforms").getInt(null) != 1
                    || gl.getField("currentProgram").getInt(null) != 0) throw new AssertionError("Shader callback/program stack");
            System.out.println("Production shader builder: compile, attach, attributes, link, deletion and initialization callback passed.");
        }
    }
}
