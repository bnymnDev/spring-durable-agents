package io.github.bnymndev.durableagents.internal;

import java.lang.reflect.Type;

import io.github.bnymndev.durableagents.Agent;

/** A registered {@code @DurableAgent} bean. */
public record AgentDefinition(String name, int version, Agent<Object, Object> agent, Class<?> agentClass, Type inputType) {
}
