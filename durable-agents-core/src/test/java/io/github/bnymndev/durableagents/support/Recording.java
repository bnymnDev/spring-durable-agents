package io.github.bnymndev.durableagents.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

/** Records real executions so tests can prove that replay did not re-run them. */
@Component
public class Recording {

	private final List<String> executions = new CopyOnWriteArrayList<>();

	public void record(String what) {
		this.executions.add(what);
	}

	public List<String> executions() {
		return this.executions;
	}

	public long count(String what) {
		return this.executions.stream().filter(what::equals).count();
	}

	public void clear() {
		this.executions.clear();
	}

}
