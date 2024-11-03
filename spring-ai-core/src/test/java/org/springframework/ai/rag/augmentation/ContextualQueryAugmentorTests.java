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

package org.springframework.ai.rag.augmentation;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ContextualQueryAugmentor}.
 *
 * @author Thomas Vitale
 */
class ContextualQueryAugmentorTests {

	@Test
	void whenQueryIsNullThenThrow() {
		QueryAugmentor augmenter = ContextualQueryAugmentor.builder().build();
		assertThatThrownBy(() -> augmenter.augment(null, List.of())).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("query cannot be null");
	}

	@Test
	void whenDocumentsIsNullThenThrow() {
		QueryAugmentor augmentor = ContextualQueryAugmentor.builder().build();
		Query query = new Query("test query");
		assertThatThrownBy(() -> augmentor.augment(query, null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("documents cannot be null");
	}

	@Test
	void whenDocumentsIsEmptyAndAllowEmptyContextThenReturnOriginalQuery() {
		QueryAugmentor augmentor = ContextualQueryAugmentor.builder().build();
		Query query = new Query("test query");
		Query augmentedQuery = augmentor.augment(query, List.of());
		assertThat(augmentedQuery).isEqualTo(query);
	}

	@Test
	void whenDocumentsIsEmptyAndNotAllowEmptyContextThenReturnAugmentedQueryWithCustomTemplate() {
		PromptTemplate emptyContextPromptTemplate = new PromptTemplate("No context available.");
		QueryAugmentor augmentor = ContextualQueryAugmentor.builder()
			.allowEmptyContext(false)
			.emptyContextPromptTemplate(emptyContextPromptTemplate)
			.build();
		Query query = new Query("test query");
		Query augmentedQuery = augmentor.augment(query, List.of());
		assertThat(augmentedQuery.text()).isEqualTo(emptyContextPromptTemplate.getTemplate());
	}

	@Test
	void whenDocumentsAreProvidedThenReturnAugmentedQueryWithCustomTemplate() {
		PromptTemplate promptTemplate = new PromptTemplate("""
				Context:
				{context}

				Query:
				{query}
				""");
		QueryAugmentor augmentor = ContextualQueryAugmentor.builder().promptTemplate(promptTemplate).build();
		Query query = new Query("test query");
		List<Document> documents = List.of(new Document("content1", Map.of()), new Document("content2", Map.of()));
		Query augmentedQuery = augmentor.augment(query, documents);
		assertThat(augmentedQuery.text()).isEqualTo("""
				Context:
				content1
				content2

				Query:
				test query
				""");
	}

}
