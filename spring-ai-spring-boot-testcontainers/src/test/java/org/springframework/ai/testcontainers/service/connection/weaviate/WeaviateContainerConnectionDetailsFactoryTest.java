/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.testcontainers.service.connection.weaviate;

import org.junit.jupiter.api.Test;
import org.springframework.ai.autoconfigure.vectorstore.weaviate.WeaviateVectorStoreAutoConfiguration;
import org.springframework.ai.autoconfigure.vectorstore.weaviate.WeaviateVectorStoreProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.WeaviateVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.weaviate.WeaviateContainer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig
@Testcontainers
@TestPropertySource(properties = { "spring.ai.vectorstore.weaviate.filter-field.country=TEXT",
		"spring.ai.vectorstore.weaviate.filter-field.year=NUMBER",
		"spring.ai.vectorstore.weaviate.filter-field.active=BOOLEAN",
		"spring.ai.vectorstore.weaviate.filter-field.price=NUMBER" })
class WeaviateContainerConnectionDetailsFactoryTest {

	@Container
	@ServiceConnection
	static WeaviateContainer weaviateContainer = new WeaviateContainer("semitechnologies/weaviate:1.22.4");

	@Autowired
	private WeaviateVectorStoreProperties properties;

	@Autowired
	private VectorStore vectorStore;

	@Test
	public void addAndSearchWithFilters() {
		assertThat(properties.getFilterField()).hasSize(4);

		assertThat(properties.getFilterField().get("country"))
			.isEqualTo(WeaviateVectorStore.WeaviateVectorStoreConfig.MetadataField.Type.TEXT);
		assertThat(properties.getFilterField().get("year"))
			.isEqualTo(WeaviateVectorStore.WeaviateVectorStoreConfig.MetadataField.Type.NUMBER);
		assertThat(properties.getFilterField().get("active"))
			.isEqualTo(WeaviateVectorStore.WeaviateVectorStoreConfig.MetadataField.Type.BOOLEAN);
		assertThat(properties.getFilterField().get("price"))
			.isEqualTo(WeaviateVectorStore.WeaviateVectorStoreConfig.MetadataField.Type.NUMBER);

		var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
				Map.of("country", "Bulgaria", "price", 3.14, "active", true, "year", 2020));
		var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
				Map.of("country", "Netherlands", "price", 1.57, "active", false, "year", 2023));

		vectorStore.add(List.of(bgDocument, nlDocument));

		var request = SearchRequest.query("The World").withTopK(5);

		List<Document> results = vectorStore.similaritySearch(request);
		assertThat(results).hasSize(2);

		results = vectorStore
			.similaritySearch(request.withSimilarityThresholdAll().withFilterExpression("country == 'Bulgaria'"));
		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

		results = vectorStore
			.similaritySearch(request.withSimilarityThresholdAll().withFilterExpression("country == 'Netherlands'"));
		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

		results = vectorStore.similaritySearch(
				request.withSimilarityThresholdAll().withFilterExpression("price > 1.57 && active == true"));
		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

		results = vectorStore
			.similaritySearch(request.withSimilarityThresholdAll().withFilterExpression("year in [2020, 2023]"));
		assertThat(results).hasSize(2);

		results = vectorStore
			.similaritySearch(request.withSimilarityThresholdAll().withFilterExpression("year > 2020 && year <= 2023"));
		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

		// Remove all documents from the store
		vectorStore.delete(List.of(bgDocument, nlDocument).stream().map(doc -> doc.getId()).toList());
	}

	@Configuration(proxyBeanMethods = false)
	@ImportAutoConfiguration(WeaviateVectorStoreAutoConfiguration.class)
	static class Config {

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}