package io.github.bnymndev.durableagents.internal;

import java.net.InetAddress;
import java.time.Duration;

/**
 * Engine settings, filled from {@code durable-agents.*} by the starter.
 *
 * @param instanceId identifies this instance as lease owner
 * @param leaseDuration how long a lease is valid without a heartbeat
 * @param strictReplay whether to fail a resume when the step sequence diverged from the history
 * @param defaultApprovalTimeout timeout used by {@code steps.approval(role)} without an explicit one
 */
public record EngineSettings(String instanceId, Duration leaseDuration, boolean strictReplay,
		Duration defaultApprovalTimeout) {

	public EngineSettings(String instanceId, Duration leaseDuration, boolean strictReplay) {
		this(instanceId, leaseDuration, strictReplay, Duration.ofDays(1));
	}

	public static EngineSettings defaults() {
		return new EngineSettings(defaultInstanceId(), Duration.ofSeconds(30), false, Duration.ofDays(1));
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
