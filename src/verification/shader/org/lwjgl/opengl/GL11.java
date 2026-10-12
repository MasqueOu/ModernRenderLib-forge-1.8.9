package org.lwjgl.opengl;

/** CPU-only linkage fixture; never included in the mod JAR. */
public final class GL11 {
    public static final int GL_TRUE = 1;

    public static int glGetInteger(int name) {
        return 0;
    }
}
