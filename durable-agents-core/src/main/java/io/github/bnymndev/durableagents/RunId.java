package io.github.bnymndev.durableagents;

import java.security.SecureRandom;
import java.time.Instant;

/** Identifier of a run: a ULID, lexicographically sortable by creation time. */
public record RunId(String value) implements Comparable<RunId> {

	private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

	private static final SecureRandom RANDOM = new SecureRandom();

	public RunId {
		if (value.isBlank()) {
			throw new IllegalArgumentException("RunId must not be blank");
		}
	}

	public static RunId of(String value) {
		return new RunId(value);
	}

	/** Creates a new ULID-based id. */
	public static RunId next() {
		long time = Instant.now().toEpochMilli();
		char[] chars = new char[26];
		for (int i = 9; i >= 0; i--) {
			chars[i] = ALPHABET[(int) (time & 0x1F)];
			time >>>= 5;
		}
		byte[] random = new byte[10];
		RANDOM.nextBytes(random);
		long hi = 0;
		for (int i = 0; i < 5; i++) {
			hi = (hi << 8) | (random[i] & 0xFF);
		}
		long lo = 0;
		for (int i = 5; i < 10; i++) {
			lo = (lo << 8) | (random[i] & 0xFF);
		}
		for (int i = 17; i >= 10; i--) {
			chars[i] = ALPHABET[(int) (hi & 0x1F)];
			hi >>>= 5;
		}
		for (int i = 25; i >= 18; i--) {
			chars[i] = ALPHABET[(int) (lo & 0x1F)];
			lo >>>= 5;
		}
		return new RunId(new String(chars));
	}

	@Override
	public int compareTo(RunId other) {
		return this.value.compareTo(other.value);
	}

	@Override
	public String toString() {
		return this.value;
	}

}
