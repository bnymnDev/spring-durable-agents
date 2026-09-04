package io.github.bnymndev.durableagents;

import java.util.List;

/** A page of results. */
public record Page<T>(List<T> content, int page, int size, long total) {

	public boolean hasNext() {
		return (long) (this.page + 1) * this.size < this.total;
	}

}
