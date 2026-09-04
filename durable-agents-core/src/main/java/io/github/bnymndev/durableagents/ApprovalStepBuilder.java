package io.github.bnymndev.durableagents;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/** Configuration of an approval step; obtained from {@link Steps#approval(String, java.time.Duration)}. */
public interface ApprovalStepBuilder {

	/** What happens when nobody decides before the timeout. Default: {@link ApprovalTimeoutPolicy#FAIL}. */
	ApprovalStepBuilder onTimeout(ApprovalTimeoutPolicy policy);

	/** A short description shown to approvers. */
	ApprovalStepBuilder description(String description);

	<T extends @Nullable Object> T run(String name, Supplier<T> supplier);

	<T extends @Nullable Object> T run(String name, Class<T> type, Supplier<T> supplier);

	<T extends @Nullable Object> T run(String name, TypeRef<T> type, Supplier<T> supplier);

	void run(String name, Runnable action);

}
