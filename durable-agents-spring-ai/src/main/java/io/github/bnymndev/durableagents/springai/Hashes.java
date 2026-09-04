package io.github.bnymndev.durableagents.springai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 helpers for prompt and tool-argument hashes. */
final class Hashes {

	private Hashes() {
	}

	static String sha256(String text) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	static String shortHash(String text) {
		return sha256(text).substring(0, 12);
	}

}
