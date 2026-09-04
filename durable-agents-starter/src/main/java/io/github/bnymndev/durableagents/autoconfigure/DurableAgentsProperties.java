package io.github.bnymndev.durableagents.autoconfigure;

import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code durable-agents.*} configuration. */
@ConfigurationProperties(prefix = "durable-agents")
public class DurableAgentsProperties {

	/** Whether the durable agents engine is active. */
	private boolean enabled = true;

	/** Store used for runs, steps and approvals. */
	private Store store = Store.JDBC;

	/**
	 * Identifies this instance as lease owner. Defaults to {@code hostname:pid}; set it to something
	 * stable (the pod name) when you want to recognise instances in the store.
	 */
	private @Nullable String instanceId;

	/** How long a run's lease is valid without a heartbeat. A crashed instance is detected after this. */
	private Duration leaseDuration = Duration.ofSeconds(30);

	/** How often the reaper looks for runs with expired leases. */
	private Duration reaperInterval = Duration.ofSeconds(10);

	/** How many expired runs the reaper resumes per pass. */
	private int reaperBatchSize = 50;

	/** How long finished runs are kept. {@code 0} keeps them forever. */
	private Duration retention = Duration.ofDays(30);

	/** How often finished runs past the retention are deleted. */
	private Duration retentionInterval = Duration.ofHours(1);

	/**
	 * Fail a resume when the sequence of step names produced by the code differs from the recorded
	 * one, instead of silently continuing. Recommended in tests and staging.
	 */
	private boolean strictReplay = false;

	private final Scheduling scheduling = new Scheduling();

	private final Jdbc jdbc = new Jdbc();

	private final Approval approval = new Approval();

	private final Observability observability = new Observability();

	public enum Store {

		/** PostgreSQL or H2 through JDBC, migrations included. */
		JDBC,

		/** In memory; runs are lost on restart. For tests and demos. */
		MEMORY

	}

	public static class Scheduling {

		/** Whether the reaper, retention cleanup and approval expiry run on a schedule. */
		private boolean enabled = true;

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

	}

	public static class Jdbc {

		/**
		 * Apply the bundled Flyway migrations at startup, tracked in a separate history table
		 * {@code durable_agents_schema_history}. Turn off to manage the schema yourself.
		 */
		private boolean migrate = true;

		public boolean isMigrate() {
			return this.migrate;
		}

		public void setMigrate(boolean migrate) {
			this.migrate = migrate;
		}

	}

	public static class Approval {

		/** Base path of the approval REST endpoints. */
		private String basePath = "/agents/approvals";

		/** Timeout used by {@code steps.approval(role)} without an explicit timeout. */
		private Duration defaultTimeout = Duration.ofDays(1);

		/** How often expired approvals are detected and their runs resumed with the timeout policy. */
		private Duration expiryInterval = Duration.ofMinutes(1);

		/**
		 * Require the caller of the approval endpoints to hold the approval's role (via
		 * {@code HttpServletRequest#isUserInRole}). Turn off only when another layer authorizes.
		 */
		private boolean enforceRoles = true;

		/**
		 * Register a Spring Security filter chain for the approval endpoints (HTTP Basic,
		 * authenticated) when the application defines no {@code SecurityFilterChain} of its own.
		 */
		private boolean securityAutoConfigured = true;

		public String getBasePath() {
			return this.basePath;
		}

		public void setBasePath(String basePath) {
			this.basePath = basePath;
		}

		public Duration getDefaultTimeout() {
			return this.defaultTimeout;
		}

		public void setDefaultTimeout(Duration defaultTimeout) {
			this.defaultTimeout = defaultTimeout;
		}

		public Duration getExpiryInterval() {
			return this.expiryInterval;
		}

		public void setExpiryInterval(Duration expiryInterval) {
			this.expiryInterval = expiryInterval;
		}

		public boolean isEnforceRoles() {
			return this.enforceRoles;
		}

		public void setEnforceRoles(boolean enforceRoles) {
			this.enforceRoles = enforceRoles;
		}

		public boolean isSecurityAutoConfigured() {
			return this.securityAutoConfigured;
		}

		public void setSecurityAutoConfigured(boolean securityAutoConfigured) {
			this.securityAutoConfigured = securityAutoConfigured;
		}

	}

	public static class Observability {

		/**
		 * Store the full prompt text with each LLM step instead of its SHA-256 hash. Off by default:
		 * prompts tend to contain personal data.
		 */
		private boolean recordPrompts = false;

		/** Create Micrometer observations (and so OpenTelemetry spans) per run and step. */
		private boolean observations = true;

		/** Register Micrometer metrics (agent.step.duration, agent.llm.tokens, ...). */
		private boolean metrics = true;

		/** Put runId, agent and stepKey into the SLF4J MDC while a run executes. */
		private boolean mdc = true;

		public boolean isRecordPrompts() {
			return this.recordPrompts;
		}

		public void setRecordPrompts(boolean recordPrompts) {
			this.recordPrompts = recordPrompts;
		}

		public boolean isObservations() {
			return this.observations;
		}

		public void setObservations(boolean observations) {
			this.observations = observations;
		}

		public boolean isMetrics() {
			return this.metrics;
		}

		public void setMetrics(boolean metrics) {
			this.metrics = metrics;
		}

		public boolean isMdc() {
			return this.mdc;
		}

		public void setMdc(boolean mdc) {
			this.mdc = mdc;
		}

	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Store getStore() {
		return this.store;
	}

	public void setStore(Store store) {
		this.store = store;
	}

	public @Nullable String getInstanceId() {
		return this.instanceId;
	}

	public void setInstanceId(@Nullable String instanceId) {
		this.instanceId = instanceId;
	}

	public Duration getLeaseDuration() {
		return this.leaseDuration;
	}

	public void setLeaseDuration(Duration leaseDuration) {
		this.leaseDuration = leaseDuration;
	}

	public Duration getReaperInterval() {
		return this.reaperInterval;
	}

	public void setReaperInterval(Duration reaperInterval) {
		this.reaperInterval = reaperInterval;
	}

	public int getReaperBatchSize() {
		return this.reaperBatchSize;
	}

	public void setReaperBatchSize(int reaperBatchSize) {
		this.reaperBatchSize = reaperBatchSize;
	}

	public Duration getRetention() {
		return this.retention;
	}

	public void setRetention(Duration retention) {
		this.retention = retention;
	}

	public Duration getRetentionInterval() {
		return this.retentionInterval;
	}

	public void setRetentionInterval(Duration retentionInterval) {
		this.retentionInterval = retentionInterval;
	}

	public boolean isStrictReplay() {
		return this.strictReplay;
	}

	public void setStrictReplay(boolean strictReplay) {
		this.strictReplay = strictReplay;
	}

	public Scheduling getScheduling() {
		return this.scheduling;
	}

	public Jdbc getJdbc() {
		return this.jdbc;
	}

	public Approval getApproval() {
		return this.approval;
	}

	public Observability getObservability() {
		return this.observability;
	}

}
