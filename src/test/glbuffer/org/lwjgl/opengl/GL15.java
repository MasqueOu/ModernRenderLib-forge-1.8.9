package org.lwjgl.opengl;

import meldexun.renderlib.util.GLUtil;

/** Test-only deletion boundary; never included in the mod. */
public final class GL15 {
	public static void glDeleteBuffers(int buffer) {
		GLUtil.deleteBuffer(buffer);
	}
}
