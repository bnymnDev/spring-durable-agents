package io.github.bnymndev.durableagents.spi;

import java.util.List;
import java.util.Optional;

import io.github.bnymndev.durableagents.RunId;

/** Store for steps. */
public interface StepStore {

	/** Inserts or replaces the record identified by {@code (runId, stepKey)}. */
	void save(StepRecord step);

	Optional<StepRecord> find(RunId runId, String stepKey);

	/** All steps of a run ordered by sequence. */
	List<StepRecord> findByRun(RunId runId);

}
