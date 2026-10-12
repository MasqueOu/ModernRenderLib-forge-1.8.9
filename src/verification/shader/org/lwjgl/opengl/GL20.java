package org.lwjgl.opengl;

/** Records shader builder calls without creating an OpenGL context. */
public final class GL20 {
    public static final int GL_CURRENT_PROGRAM = 35725;
    public static final int GL_COMPILE_STATUS = 35713;
    public static final int GL_INFO_LOG_LENGTH = 35716;
    public static final int GL_LINK_STATUS = 35714;
    public static int compiled;
    public static int attached;
    public static int bound;
    public static int linked;
    public static int deleted;
    public static int currentProgram;
    public static int uniforms;

    public static int glCreateProgram() { return 42; }
    public static int glCreateShader(int type) { return type; }
    public static void glShaderSource(int shader, CharSequence source) { }
    public static void glCompileShader(int shader) { compiled++; }
    public static int glGetShaderi(int shader, int parameter) { return 1; }
    public static String glGetShaderInfoLog(int shader, int length) { return ""; }
    public static void glAttachShader(int program, int shader) { attached++; }
    public static void glBindAttribLocation(int program, int index, CharSequence attribute) { bound++; }
    public static void glLinkProgram(int program) { linked++; }
    public static int glGetProgrami(int program, int parameter) { return 1; }
    public static String glGetProgramInfoLog(int program, int length) { return ""; }
    public static void glDeleteShader(int shader) { deleted++; }
    public static void glUseProgram(int program) { currentProgram = program; }
    public static int glGetUniformLocation(int program, CharSequence name) { uniforms++; return 1; }
    public static int glGetAttribLocation(int program, CharSequence name) { return 1; }
    public static void glDeleteProgram(int program) { }
}
