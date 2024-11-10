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

package org.springframework.ai.rag.analysis.query.expansion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.util.PromptAssert;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Expander that implements semantic query expansion for retrieval-augmented generation
 * flows. It uses a large language model to generate multiple semantically diverse
 * variations of an input query to capture different perspectives and improve document
 * retrieval coverage.
 *
 * <p>
 * Example usage: <pre>{@code
 * MultiQueryExpander expander = MultiQueryExpander.builder()
 *    .chatClientBuilder(chatClientBuilder)
 *    .numberOfQueries(3)
 *    .build();
 * List<Query> queries = expander.expand(new Query("How to run a Spring Boot app?"));
 * }</pre>
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public final class MultiQueryExpander implements QueryExpander {

	private static final Logger logger = LoggerFactory.getLogger(MultiQueryExpander.class);

	private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = new PromptTemplate("""
			You are an expert at information retrieval and search optimization.
			Your task is to generate {number} different versions of the given query.

			Each variant must cover different perspectives or aspects of the topic,
			while maintaining the core intent of the original query. The goal is to
			expand the search space and improve the chances of finding relevant information.

			Do not explain your choices or add any other text.
			Provide the query variants separated by newlines.

			Original query: {query}

			Query variants:
			""");

	private static final Boolean DEFAULT_INCLUDE_ORIGINAL = false;

	private static final Integer DEFAULT_NUMBER_OF_QUERIES = 3;

	private final ChatClient chatClient;

	private final PromptTemplate promptTemplate;

	private final boolean includeOriginal;

	private final int numberOfQueries;

	public MultiQueryExpander(ChatClient.Builder chatClientBuilder, @Nullable PromptTemplate promptTemplate,
			@Nullable Boolean includeOriginal, @Nullable Integer numberOfQueries) {
		Assert.notNull(chatClientBuilder, "chatClientBuilder cannot be null");

		this.chatClient = chatClientBuilder.build();
		this.promptTemplate = promptTemplate != null ? promptTemplate : DEFAULT_PROMPT_TEMPLATE;
		this.includeOriginal = includeOriginal != null ? includeOriginal : DEFAULT_INCLUDE_ORIGINAL;
		this.numberOfQueries = numberOfQueries != null ? numberOfQueries : DEFAULT_NUMBER_OF_QUERIES;

		PromptAssert.templateHasRequiredPlaceholders(this.promptTemplate, "number", "query");
	}

	@Override
	public List<Query> expand(Query query) {
		Assert.notNull(query, "query cannot be null");

		logger.debug("Generating {} query variants", numberOfQueries);

		var response = chatClient.prompt()
			.user(user -> user.text(promptTemplate.getTemplate())
				.param("number", numberOfQueries)
				.param("query", query.text()))
			.call()
			.content();

		if (response == null) {
			logger.warn("Query expansion result is null. Returning the input query unchanged.");
			return List.of(query);
		}

		var queryVariants = Arrays.asList(response.split("\n"));

		if (CollectionUtils.isEmpty(queryVariants) || numberOfQueries != queryVariants.size()) {
			logger.warn(
					"Query expansion result does not contain the requested {} variants. Returning the input query unchanged.",
					numberOfQueries);
			return List.of(query);
		}

		var queries = queryVariants.stream().filter(StringUtils::hasText).map(Query::new).collect(Collectors.toList());

		if (includeOriginal) {
			logger.debug("Including the original query in the result");
			queries.add(0, query);
		}

		return queries;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private ChatClient.Builder chatClientBuilder;

		private PromptTemplate promptTemplate;

		private Boolean includeOriginal;

		private Integer numberOfQueries;

		private Builder() {
		}

		public Builder chatClientBuilder(ChatClient.Builder chatClientBuilder) {
			this.chatClientBuilder = chatClientBuilder;
			return this;
		}

		public Builder promptTemplate(PromptTemplate promptTemplate) {
			this.promptTemplate = promptTemplate;
			return this;
		}

		public Builder includeOriginal(Boolean includeOriginal) {
			this.includeOriginal = includeOriginal;
			return this;
		}

		public Builder numberOfQueries(Integer numberOfQueries) {
			this.numberOfQueries = numberOfQueries;
			return this;
		}

		public MultiQueryExpander build() {
			return new MultiQueryExpander(chatClientBuilder, promptTemplate, includeOriginal, numberOfQueries);
		}

	}

}