package meldexun.memoryutil;

import java.nio.Buffer;

/** Counts address reads so a failed mapping cannot accidentally reach Unsafe. */
public final class NIOBufferUtil {

	public static final long ADDRESS = 0x1234L;
	public static int addressReads;
	public static RuntimeException addressFailure;

	public static void reset() {
		addressReads = 0;
		addressFailure = null;
	}

	public static long getAddress(Buffer buffer) {
		addressReads++;
		if (buffer == null) {
			throw new AssertionError("A null mapping reached the address accessor");
		}
		if (addressFailure != null) {
			throw addressFailure;
		}
		return ADDRESS;
	}

}
