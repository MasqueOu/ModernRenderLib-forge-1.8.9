package meldexun.memoryutil;

/** Only the address accessor is needed to exercise GLBuffer's mapping lifecycle. */
public interface MemoryAccess {
	interface SingleRegister {
		long getAddress();
	}
}
