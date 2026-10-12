package meldexun.renderlib.util;

import java.nio.ByteBuffer;

import meldexun.memoryutil.NIOBufferUtil;

/** Exercises the production GLBuffer source with controlled native-call results. */
public final class GLBufferMappingTest {

	private static final int MAP_WRITE = 0x0002;
	private static final int WRITE_ONLY = 0x88B9;
	private static final int STREAM_DRAW = 0x88E0;
	private static int scenarios;

	public static void main(String[] args) {
		run(GLBufferMappingTest::transientFailureCanBeRetried);
		run(GLBufferMappingTest::persistentNullMappingDeletesBuffer);
		run(GLBufferMappingTest::persistentMappingExceptionDeletesBuffer);
		run(GLBufferMappingTest::cleanupFailurePreservesMappingException);
		run(GLBufferMappingTest::addressFailureDeletesInitialBuffer);
		run(GLBufferMappingTest::transientExceptionLeavesStateUnmapped);
		run(GLBufferMappingTest::successfulPersistentMappingKeepsExistingFlags);
		run(GLBufferMappingTest::persistentRequestFallsBackWithoutOpenGL44);
		System.out.println("GLBuffer mapping tests passed: " + scenarios + " scenarios.");
	}

	private static void run(Runnable scenario) {
		GLUtil.reset();
		NIOBufferUtil.reset();
		scenario.run();
		scenarios++;
	}

	private static void transientFailureCanBeRetried() {
		GLBuffer buffer = new GLBuffer(16, MAP_WRITE, STREAM_DRAW);
		GLException failure = expect(GLException.class, () -> buffer.map(8, MAP_WRITE, WRITE_ONLY));
		check(failure.getMessage().contains("buffer 7"), "Missing buffer ID");
		check(failure.getMessage().contains("size=16, length=8"), "Missing size/length");
		check(failure.getMessage().contains("rangeAccess=0x2, access=0x88b9"), "Missing mapping flags");
		assertUnmapped(buffer);
		check(NIOBufferUtil.addressReads == 0, "Null mapping attempted an address read");
		check(GLUtil.deleteCalls == 0, "A retryable mapping failure deleted the buffer");

		GLUtil.mapResult = ByteBuffer.allocate(8);
		buffer.map(8, MAP_WRITE, WRITE_ONLY);
		check(buffer.isMapped(), "Successful retry was not published");
		check(buffer.getByteBuffer() == GLUtil.mapResult, "Successful mapping lost its ByteBuffer");
		check(buffer.getAddress() == NIOBufferUtil.ADDRESS, "Successful mapping lost its address");
		buffer.map(MAP_WRITE, WRITE_ONLY);
		check(GLUtil.mapCalls == 2, "Already-mapped buffer was mapped again");
		buffer.unmap();
		assertUnmapped(buffer);
		check(GLUtil.unmapCalls == 1, "Unmap did not release the successful mapping");
		buffer.dispose();
		check(GLUtil.deleteCalls == 1, "Dispose did not delete the buffer");
	}

	private static void persistentNullMappingDeletesBuffer() {
		GLException failure = expect(GLException.class,
				() -> new GLBuffer(16, MAP_WRITE, STREAM_DRAW, true, MAP_WRITE));
		check(failure.getMessage().contains("rangeAccess=0x42"), "Missing persistent mapping flag");
		check(NIOBufferUtil.addressReads == 0, "Persistent null mapping attempted an address read");
		assertInitialBufferDeleted();
	}

	private static void persistentMappingExceptionDeletesBuffer() {
		GLUtil.mapFailure = new IllegalStateException("Native mapping failed");
		RuntimeException failure = expect(RuntimeException.class,
				() -> new GLBuffer(16, MAP_WRITE, STREAM_DRAW, true, MAP_WRITE));
		check(failure == GLUtil.mapFailure, "Mapping exception was replaced");
		check(NIOBufferUtil.addressReads == 0, "Thrown mapping failure attempted an address read");
		assertInitialBufferDeleted();
	}

	private static void cleanupFailurePreservesMappingException() {
		GLUtil.deleteFailure = new IllegalStateException("Native deletion failed");
		GLException failure = expect(GLException.class,
				() -> new GLBuffer(16, MAP_WRITE, STREAM_DRAW, true, MAP_WRITE));
		check(failure.getSuppressed().length == 1, "Cleanup failure was lost");
		check(failure.getSuppressed()[0] == GLUtil.deleteFailure, "Wrong suppressed cleanup failure");
		assertInitialBufferDeleted();
	}

	private static void addressFailureDeletesInitialBuffer() {
		GLUtil.mapResult = ByteBuffer.allocate(16);
		NIOBufferUtil.addressFailure = new IllegalStateException("Address accessor failed");
		RuntimeException failure = expect(RuntimeException.class,
				() -> new GLBuffer(16, MAP_WRITE, STREAM_DRAW, true, MAP_WRITE));
		check(failure == NIOBufferUtil.addressFailure, "Address failure was replaced");
		assertInitialBufferDeleted();
	}

	private static void transientExceptionLeavesStateUnmapped() {
		GLBuffer buffer = new GLBuffer(16, MAP_WRITE, STREAM_DRAW);
		GLUtil.mapFailure = new IllegalStateException("Native mapping threw");
		RuntimeException failure = expect(RuntimeException.class, () -> buffer.map(MAP_WRITE, WRITE_ONLY));
		check(failure == GLUtil.mapFailure, "Transient mapping exception was replaced");
		assertUnmapped(buffer);
		check(NIOBufferUtil.addressReads == 0, "Thrown failure attempted an address read");
		check(GLUtil.deleteCalls == 0, "Transient failure deleted an existing buffer");
		buffer.dispose();
	}

	private static void successfulPersistentMappingKeepsExistingFlags() {
		GLUtil.mapResult = ByteBuffer.allocate(16);
		GLBuffer buffer = new GLBuffer(16, MAP_WRITE, STREAM_DRAW, true, MAP_WRITE);
		check(GLUtil.storageFlags == 0x42 && GLUtil.mapFlags == 0x42, "Persistent flags changed");
		check(buffer.isMapped() && buffer.getAddress() == NIOBufferUtil.ADDRESS, "Persistent mapping invalid");
		buffer.unmap();
		check(buffer.isMapped() && GLUtil.unmapCalls == 0, "Persistent unmap behavior changed");
		buffer.dispose();
		assertUnmapped(buffer);
		check(GLUtil.unmapCalls == 1 && GLUtil.deleteCalls == 1, "Persistent dispose failed");
	}

	private static void persistentRequestFallsBackWithoutOpenGL44() {
		GLUtil.CAPS.OpenGL44 = false;
		GLBuffer buffer = new GLBuffer(16, MAP_WRITE, STREAM_DRAW, true, MAP_WRITE);
		check(GLUtil.mapCalls == 0 && GLUtil.storageFlags == MAP_WRITE, "Fallback constructor changed");
		expect(GLException.class, () -> buffer.map(MAP_WRITE, WRITE_ONLY));
		assertUnmapped(buffer);
		check(NIOBufferUtil.addressReads == 0, "Fallback null mapping attempted an address read");
		buffer.dispose();
		check(GLUtil.deleteCalls == 1, "Fallback buffer was not disposable after failure");
	}

	private static void assertInitialBufferDeleted() {
		check(GLUtil.deleteCalls == 1 && GLUtil.deletedBuffer == GLUtil.BUFFER_ID,
				"Failed initialization did not delete exactly its allocated buffer");
	}

	private static void assertUnmapped(GLBuffer buffer) {
		check(!buffer.isMapped(), "Failed/released buffer claims to be mapped");
		check(buffer.getByteBuffer() == null, "Failed/released buffer exposes a ByteBuffer");
		check(buffer.getAddress() == 0L, "Failed/released buffer exposes an address");
	}

	private static <T extends Throwable> T expect(Class<T> type, Runnable action) {
		try {
			action.run();
		} catch (Throwable failure) {
			if (type.isInstance(failure)) {
				return type.cast(failure);
			}
			throw new AssertionError("Expected " + type.getName() + ", received " + failure, failure);
		}
		throw new AssertionError("Expected " + type.getName());
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

}
