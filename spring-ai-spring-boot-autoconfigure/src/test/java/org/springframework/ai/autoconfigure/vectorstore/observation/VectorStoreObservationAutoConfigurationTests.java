/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.vectorstore.observation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.observation.VectorStoreAddRequestContentObservationFilter;
import org.springframework.ai.vectorstore.observation.VectorStoreDeleteRequestContentObservationFilter;
import org.springframework.ai.vectorstore.observation.VectorStoreQueryResponseObservationFilter;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Unit tests for {@link VectorStoreObservationAutoConfiguration}.
 *
 * @author Christian Tzolov
 */
class VectorStoreObservationAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(VectorStoreObservationAutoConfiguration.class));

	@Test
	void queryResponseFilterDefault() {
		contextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(VectorStoreQueryResponseObservationFilter.class);
		});
	}

	@Test
	void queryResponseFilterFilterEnabled() {
		contextRunner.withPropertyValues("spring.ai.vectorstore.observations.include-query-response=true")
			.run(context -> {
				assertThat(context).hasSingleBean(VectorStoreQueryResponseObservationFilter.class);
			});
	}

	@Test
	void deleteRequestFilterDefault() {
		contextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(VectorStoreDeleteRequestContentObservationFilter.class);
		});
	}

	@Test
	void deleteRequestFilterEnabled() {
		contextRunner.withPropertyValues("spring.ai.vectorstore.observations.include-delete-request=true")
			.run(context -> {
				assertThat(context).hasSingleBean(VectorStoreDeleteRequestContentObservationFilter.class);
			});
	}

	@Test
	void addRequestFilterDefault() {
		contextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(VectorStoreAddRequestContentObservationFilter.class);
		});
	}

	@Test
	void addRequestFilterEnabled() {
		contextRunner.withPropertyValues("spring.ai.vectorstore.observations.include-add-request=true").run(context -> {
			assertThat(context).hasSingleBean(VectorStoreAddRequestContentObservationFilter.class);
		});
	}

}
