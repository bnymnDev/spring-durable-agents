package io.github.bnymndev.durableagents;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RunIdTests {

	@Test
	void generatesSortableUniqueIds() throws InterruptedException {
		Set<String> seen = new HashSet<>();
		RunId previous = RunId.next();
		for (int i = 0; i < 1000; i++) {
			RunId id = RunId.next();
			assertThat(id.value()).hasSize(26).matches("[0-9A-HJKMNP-TV-Z]{26}");
			assertThat(seen.add(id.value())).isTrue();
			previous = id;
		}
		Thread.sleep(2);
		assertThat(RunId.next().compareTo(previous)).isGreaterThan(0);
	}

}
