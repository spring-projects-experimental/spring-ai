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
package org.springframework.ai.vectorstore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.PineconeVectorStore.PineconeVectorStoreConfig;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

/**
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "PINECONE_API_KEY", matches = ".+")
public class PineconeVectorStoreIT {

	// Replace the PINECONE_ENVIRONMENT, PINECONE_PROJECT_ID, PINECONE_INDEX_NAME and
	// PINECONE_API_KEY with your pinecone credentials.
	private static final String PINECONE_ENVIRONMENT = "gcp-starter";

	private static final String PINECONE_PROJECT_ID = "814621f";

	private static final String PINECONE_INDEX_NAME = "spring-ai-test-index";

	// NOTE: Leave it empty as for free tier as later doesn't support namespaces.
	private static final String PINECONE_NAMESPACE = "";

	List<Document> documents = List.of(
			new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
			new Document("3", getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	public static String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	@BeforeAll
	public static void beforeAll() {
		Awaitility.setDefaultPollInterval(2, TimeUnit.SECONDS);
		Awaitility.setDefaultPollDelay(Duration.ZERO);
		Awaitility.setDefaultTimeout(Duration.ONE_MINUTE);
	}

	@Test
	public void addAndSearchTest() {

		contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			vectorStore.add(documents);

			Awaitility.await().until(() -> {
				return vectorStore.similaritySearch(SearchRequest.query("Great Depression").withTopK(1));
			}, hasSize(1));

			List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Great Depression").withTopK(1));

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(documents.get(2).getId());
			assertThat(resultDoc.getContent()).contains("The Great Depression (1929–1939) was an economic shock");
			assertThat(resultDoc.getMetadata()).hasSize(2);
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey("distance");

			// Remove all documents from the store
			vectorStore.delete(documents.stream().map(doc -> doc.getId()).toList());

			Awaitility.await().until(() -> {
				return vectorStore.similaritySearch(SearchRequest.query("Hello").withTopK(1));
			}, hasSize(0));
		});
	}

	@Test
	public void addAndSearchWithFilters() {

		// Pinecone metadata filtering syntax:
		// https://docs.pinecone.io/docs/metadata-filtering

		contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "Bulgaria"));
			var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "Netherlands"));

			vectorStore.add(List.of(bgDocument, nlDocument));

			SearchRequest searchRequest = SearchRequest.query("The World");

			Awaitility.await().until(() -> {
				return vectorStore.similaritySearch(searchRequest.withTopK(1));
			}, hasSize(1));

			List<Document> results = vectorStore.similaritySearch(searchRequest.withTopK(5));
			assertThat(results).hasSize(2);

			results = vectorStore.similaritySearch(searchRequest.withTopK(5)
				.withSimilarityThresholdAll()
				.withFilterExpression("country == 'Bulgaria'"));
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			results = vectorStore.similaritySearch(searchRequest.withTopK(5)
				.withSimilarityThresholdAll()
				.withFilterExpression("country == 'Netherlands'"));
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			results = vectorStore.similaritySearch(searchRequest.withTopK(5)
				.withSimilarityThresholdAll()
				.withFilterExpression("NOT(country == 'Netherlands')"));

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			// Remove all documents from the store
			vectorStore.delete(List.of(bgDocument, nlDocument).stream().map(doc -> doc.getId()).toList());

			Awaitility.await().until(() -> {
				return vectorStore.similaritySearch(searchRequest.withTopK(1));
			}, hasSize(0));
		});
	}

	@Test
	public void documentUpdateTest() {

		// Note ,using OpenAI to calculate embeddings
		contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
					Collections.singletonMap("meta1", "meta1"));

			vectorStore.add(List.of(document));

			SearchRequest springSearchRequest = SearchRequest.query("Spring").withTopK(5);

			Awaitility.await().until(() -> {
				return vectorStore.similaritySearch(springSearchRequest);
			}, hasSize(1));

			List<Document> results = vectorStore.similaritySearch(springSearchRequest);

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getContent()).isEqualTo("Spring AI rocks!!");
			assertThat(resultDoc.getMetadata()).containsKey("meta1");
			assertThat(resultDoc.getMetadata()).containsKey("distance");

			Document sameIdDocument = new Document(document.getId(),
					"The World is Big and Salvation Lurks Around the Corner",
					Collections.singletonMap("meta2", "meta2"));

			vectorStore.add(List.of(sameIdDocument));

			SearchRequest fooBarSearchRequest = SearchRequest.query("FooBar").withTopK(5);

			Awaitility.await().until(() -> {
				return vectorStore.similaritySearch(fooBarSearchRequest).get(0).getContent();
			}, equalTo("The World is Big and Salvation Lurks Around the Corner"));

			results = vectorStore.similaritySearch(fooBarSearchRequest);

			assertThat(results).hasSize(1);
			resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getContent()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey("distance");

			// Remove all documents from the store
			vectorStore.delete(List.of(document.getId()));
			Awaitility.await().until(() -> {
				return vectorStore.similaritySearch(fooBarSearchRequest);
			}, hasSize(0));

		});
	}

	@Test
	public void searchThresholdTest() {

		contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			vectorStore.add(documents);

			Awaitility.await().until(() -> {
				return vectorStore
					.similaritySearch(SearchRequest.query("Depression").withTopK(50).withSimilarityThresholdAll());
			}, hasSize(3));

			List<Document> fullResult = vectorStore
				.similaritySearch(SearchRequest.query("Depression").withTopK(5).withSimilarityThresholdAll());

			List<Float> distances = fullResult.stream().map(doc -> (Float) doc.getMetadata().get("distance")).toList();

			assertThat(distances).hasSize(3);

			float threshold = (distances.get(0) + distances.get(1)) / 2;

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.query("Depression").withTopK(5).withSimilarityThreshold(1 - threshold));

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(documents.get(2).getId());
			assertThat(resultDoc.getContent()).contains("The Great Depression (1929–1939) was an economic shock");
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey("distance");

			// Remove all documents from the store
			vectorStore.delete(documents.stream().map(doc -> doc.getId()).toList());
			Awaitility.await().until(() -> {
				return vectorStore.similaritySearch(SearchRequest.query("Hello").withTopK(1));
			}, hasSize(0));
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	public static class TestApplication {

		@Bean
		public PineconeVectorStoreConfig pineconeVectorStoreConfig() {

			return PineconeVectorStoreConfig.builder()
				.withApiKey(System.getenv("PINECONE_API_KEY"))
				.withEnvironment(PINECONE_ENVIRONMENT)
				.withProjectId(PINECONE_PROJECT_ID)
				.withIndexName(PINECONE_INDEX_NAME)
				.withNamespace(PINECONE_NAMESPACE)
				.build();
		}

		@Bean
		public VectorStore vectorStore(PineconeVectorStoreConfig config, EmbeddingModel embeddingModel) {
			return new PineconeVectorStore(config, embeddingModel);
		}

		@Bean
		public TransformersEmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}