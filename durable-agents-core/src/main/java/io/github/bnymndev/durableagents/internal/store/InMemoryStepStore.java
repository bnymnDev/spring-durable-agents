package io.github.bnymndev.durableagents.internal.store;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.github.bnymndev.durableagents.RunId;
import io.github.bnymndev.durableagents.spi.StepRecord;
import io.github.bnymndev.durableagents.spi.StepStore;

/** In-memory {@link StepStore}. */
public final class InMemoryStepStore implements StepStore {

	private final Map<RunId, Map<String, StepRecord>> steps = new ConcurrentHashMap<>();

	@Override
	public void save(StepRecord step) {
		this.steps.computeIfAbsent(step.runId(), (k) -> new ConcurrentHashMap<>()).put(step.stepKey(), step);
	}

	@Override
	public Optional<StepRecord> find(RunId runId, String stepKey) {
		Map<String, StepRecord> byKey = this.steps.get(runId);
		return (byKey != null) ? Optional.ofNullable(byKey.get(stepKey)) : Optional.empty();
	}

	@Override
	public List<StepRecord> findByRun(RunId runId) {
		Map<String, StepRecord> byKey = this.steps.get(runId);
		if (byKey == null) {
			return List.of();
		}
		return byKey.values().stream().sorted(Comparator.comparingInt(StepRecord::sequence)).toList();
	}

	void deleteByRun(RunId runId) {
		this.steps.remove(runId);
	}

	public void clear() {
		this.steps.clear();
	}

}
