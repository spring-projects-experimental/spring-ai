/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.integration.tests.rag.analysis.query.expansion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.integration.tests.TestApplication;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.analysis.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.analysis.query.expansion.QueryExpander;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MultiQueryExpander}.
 *
 * @author Thomas Vitale
 */
@SpringBootTest(classes = TestApplication.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
class MultiQueryExpanderIT {

	@Autowired
	OpenAiChatModel openAiChatModel;

	@Test
	void whenExpanderWithDefaults() {
		Query query = new Query("What is the weather in Rome?");
		QueryExpander queryExpander = MultiQueryExpander.builder()
			.chatClientBuilder(ChatClient.builder(openAiChatModel))
			.build();

		List<Query> queries = queryExpander.apply(query);

		assertThat(queries).isNotNull();
		queries.forEach(System.out::println);
		assertThat(queries).hasSize(3);
	}

	@Test
	void whenExpanderWithCustomQueryNumber() {
		Query query = new Query("What is the weather in Rome?");
		QueryExpander queryExpander = MultiQueryExpander.builder()
			.chatClientBuilder(ChatClient.builder(openAiChatModel))
			.numberOfQueries(4)
			.build();

		List<Query> queries = queryExpander.apply(query);

		assertThat(queries).isNotNull();
		queries.forEach(System.out::println);
		assertThat(queries).hasSize(4);
	}

	@Test
	void whenExpanderWithOriginalQueryIncluded() {
		Query query = new Query("What is the weather in Rome?");
		QueryExpander queryExpander = MultiQueryExpander.builder()
			.chatClientBuilder(ChatClient.builder(openAiChatModel))
			.numberOfQueries(3)
			.includeOriginal(true)
			.build();

		List<Query> queries = queryExpander.apply(query);

		assertThat(queries).isNotNull();
		queries.forEach(System.out::println);
		assertThat(queries).hasSize(4);
	}

}