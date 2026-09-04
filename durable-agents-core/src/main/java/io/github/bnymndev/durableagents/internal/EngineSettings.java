package io.github.bnymndev.durableagents.internal;

import java.net.InetAddress;
import java.time.Duration;

/**
 * Engine settings, filled from {@code durable-agents.*} by the starter.
 *
 * @param instanceId identifies this instance as lease owner
 * @param leaseDuration how long a lease is valid without a heartbeat
 * @param strictReplay whether to fail a resume when the step sequence diverged from the history
 */
public record EngineSettings(String instanceId, Duration leaseDuration, boolean strictReplay) {

	public static EngineSettings defaults() {
		return new EngineSettings(defaultInstanceId(), Duration.ofSeconds(30), false);
	}

	public static String defaultInstanceId() {
		String host;
		try {
			host = InetAddress.getLocalHost().getHostName();
		}
		catch (Exception ex) {
			host = "localhost";
		}
		return host + ":" + ProcessHandle.current().pid();
	}

}
