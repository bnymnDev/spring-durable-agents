package io.github.bnymndev.durableagents.test;

import java.io.IOException;
import java.util.Set;

import org.springframework.boot.test.context.filter.annotation.StandardAnnotationCustomizableTypeExcludeFilter;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

import io.github.bnymndev.durableagents.DurableAgent;
import io.github.bnymndev.durableagents.Step;

/** Includes {@code @DurableAgent} classes and classes that declare {@code @Step} methods. */
public final class DurableAgentTypeExcludeFilter extends StandardAnnotationCustomizableTypeExcludeFilter<DurableAgentTest> {

	private static final Set<Class<?>> DEFAULT_INCLUDES = Set.of(DurableAgent.class);

	DurableAgentTypeExcludeFilter(Class<?> testClass) {
		super(testClass);
	}

	@Override
	protected Set<Class<?>> getKnownIncludes() {
		return DEFAULT_INCLUDES;
	}

	@Override
	protected boolean defaultInclude(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory)
			throws IOException {
		if (super.defaultInclude(metadataReader, metadataReaderFactory)) {
			return true;
		}
		return metadataReader.getAnnotationMetadata().hasAnnotatedMethods(Step.class.getName());
	}

}
