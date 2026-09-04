package io.github.bnymndev.durableagents.test.app;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import io.github.bnymndev.durableagents.Step;

@Component
public class TriageSteps {

	private final AtomicInteger similarCalls = new AtomicInteger();

	/** A method, not a field: the bean is an AOP proxy and proxy fields are never initialised. */
	public int similarCalls() {
		return this.similarCalls.get();
	}

	@Step
	public List<String> findSimilar(Classification c) {
		this.similarCalls.incrementAndGet();
		return List.of("T-100", "T-101");
	}

}
