package meldexun.renderlib.util;

import java.nio.ByteBuffer;

/** Test-only OpenGL boundary, compiled separately from the mod. */
public final class GLUtil {

	public static final Capabilities CAPS = new Capabilities();
	public static final int BUFFER_ID = 7;
	public static ByteBuffer mapResult;
	public static RuntimeException mapFailure;
	public static RuntimeException deleteFailure;
	public static int storageFlags;
	public static int mapFlags;
	public static int mapCalls;
	public static int unmapCalls;
	public static int deleteCalls;
	public static int deletedBuffer;

	public static void reset() {
		CAPS.OpenGL44 = true;
		mapResult = null;
		mapFailure = null;
		deleteFailure = null;
		storageFlags = mapFlags = mapCalls = unmapCalls = deleteCalls = deletedBuffer = 0;
	}

	public static int createBuffer(long size, int flags, int usage) {
		storageFlags = flags;
		return BUFFER_ID;
	}

	public static ByteBuffer map(int buffer, long length, int rangeAccess, int access, ByteBuffer oldBuffer) {
		mapCalls++;
		mapFlags = rangeAccess;
		if (mapFailure != null) {
			throw mapFailure;
		}
		return mapResult;
	}

	public static void unmap(int buffer) {
		unmapCalls++;
	}

	public static void deleteBuffer(int buffer) {
		deleteCalls++;
		deletedBuffer = buffer;
		if (deleteFailure != null) {
			throw deleteFailure;
		}
	}

	public static final class Capabilities {
		public boolean OpenGL44;
	}

}
