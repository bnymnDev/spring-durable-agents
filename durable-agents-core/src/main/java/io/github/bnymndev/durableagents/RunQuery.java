package io.github.bnymndev.durableagents;

import org.jspecify.annotations.Nullable;

/** Filter for {@link AgentRuns#find(RunQuery)}. All filters are optional. */
public record RunQuery(@Nullable String agentName, @Nullable RunStatus status, @Nullable String correlationId, int page,
		int size) {

	public RunQuery {
		if (page < 0) {
			throw new IllegalArgumentException("page must be >= 0");
		}
		if (size < 1 || size > 1000) {
			throw new IllegalArgumentException("size must be between 1 and 1000");
		}
	}

	public static RunQuery all() {
		return new RunQuery(null, null, null, 0, 50);
	}

	public static RunQuery forAgent(String agentName) {
		return new RunQuery(agentName, null, null, 0, 50);
	}

	public static RunQuery withStatus(RunStatus status) {
		return new RunQuery(null, status, null, 0, 50);
	}

	public RunQuery agent(String name) {
		return new RunQuery(name, this.status, this.correlationId, this.page, this.size);
	}

	public RunQuery status(RunStatus s) {
		return new RunQuery(this.agentName, s, this.correlationId, this.page, this.size);
	}

	public RunQuery correlation(String id) {
		return new RunQuery(this.agentName, this.status, id, this.page, this.size);
	}

	public RunQuery page(int p, int s) {
		return new RunQuery(this.agentName, this.status, this.correlationId, p, s);
	}

}
