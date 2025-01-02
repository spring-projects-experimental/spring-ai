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

package org.springframework.ai.vectorstore.redis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.redis.testcontainers.RedisStackContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.JedisPooled;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Julien Ruaux
 * @author Eddú Meléndez
 * @author Thomas Vitale
 */
@Testcontainers
class RedisVectorStoreIT {

	@Container
	static RedisStackContainer redisContainer = new RedisStackContainer(
			RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(RedisStackContainer.DEFAULT_TAG));

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
		.withUserConfiguration(TestApplication.class)
		.withPropertyValues("spring.data.redis.url=" + redisContainer.getRedisURI());

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

	@BeforeEach
	void cleanDatabase() {
		this.contextRunner.run(context -> context.getBean(RedisVectorStore.class).getJedis().flushAll());
	}

	@Test
	void ensureIndexGetsCreated() {
		this.contextRunner.run(context -> assertThat(context.getBean(RedisVectorStore.class).getJedis().ftList())
			.contains(RedisVectorStore.DEFAULT_INDEX_NAME));
	}

	@Test
	void addAndSearch() {

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			vectorStore.add(this.documents);

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(0).getId());
			assertThat(resultDoc.getContent()).contains(
					"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
			assertThat(resultDoc.getMetadata()).hasSize(3);
			assertThat(resultDoc.getMetadata()).containsKeys("meta1", RedisVectorStore.DISTANCE_FIELD_NAME,
					DocumentMetadata.DISTANCE.value());

			// Remove all documents from the store
			vectorStore.delete(this.documents.stream().map(doc -> doc.getId()).toList());

			results = vectorStore.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());
			assertThat(results).isEmpty();
		});
	}

	@Test
	void searchWithFilters() throws InterruptedException {

		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);

			var bgDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2020));
			var nlDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "NL"));
			var bgDocument2 = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("country", "BG", "year", 2023));

			vectorStore.add(List.of(bgDocument, nlDocument, bgDocument2));

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("The World").topK(5).build());
			assertThat(results).hasSize(3);

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country == 'NL'")
				.build());
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(nlDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country == 'BG'")
				.build());

			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(bgDocument.getId(), bgDocument2.getId());
			assertThat(results.get(1).getId()).isIn(bgDocument.getId(), bgDocument2.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("country == 'BG' && year == 2020")
				.build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(bgDocument.getId());

			results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("NOT(country == 'BG' && year == 2020)")
				.build());

			assertThat(results).hasSize(2);
			assertThat(results.get(0).getId()).isIn(nlDocument.getId(), bgDocument2.getId());
			assertThat(results.get(1).getId()).isIn(nlDocument.getId(), bgDocument2.getId());

		});
	}

	@Test
	void documentUpdate() {

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			Document document = new Document(UUID.randomUUID().toString(), "Spring AI rocks!!",
					Collections.singletonMap("meta1", "meta1"));

			vectorStore.add(List.of(document));

			List<Document> results = vectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").topK(5).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getContent()).isEqualTo("Spring AI rocks!!");
			assertThat(resultDoc.getMetadata()).containsKey("meta1");
			assertThat(resultDoc.getMetadata()).containsKey(RedisVectorStore.DISTANCE_FIELD_NAME);
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

			Document sameIdDocument = new Document(document.getId(),
					"The World is Big and Salvation Lurks Around the Corner",
					Collections.singletonMap("meta2", "meta2"));

			vectorStore.add(List.of(sameIdDocument));

			results = vectorStore.similaritySearch(SearchRequest.builder().query("FooBar").topK(5).build());

			assertThat(results).hasSize(1);
			resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(document.getId());
			assertThat(resultDoc.getContent()).isEqualTo("The World is Big and Salvation Lurks Around the Corner");
			assertThat(resultDoc.getMetadata()).containsKey("meta2");
			assertThat(resultDoc.getMetadata()).containsKey(RedisVectorStore.DISTANCE_FIELD_NAME);
			assertThat(resultDoc.getMetadata()).containsKey(DocumentMetadata.DISTANCE.value());

			vectorStore.delete(List.of(document.getId()));

		});
	}

	@Test
	void searchWithThreshold() {

		this.contextRunner.run(context -> {

			VectorStore vectorStore = context.getBean(VectorStore.class);

			vectorStore.add(this.documents);

			List<Document> fullResult = vectorStore
				.similaritySearch(SearchRequest.builder().query("Spring").topK(5).similarityThresholdAll().build());

			List<Double> scores = fullResult.stream().map(Document::getScore).toList();

			assertThat(scores).hasSize(3);

			double similarityThreshold = (scores.get(0) + scores.get(1)) / 2;

			List<Document> results = vectorStore.similaritySearch(
					SearchRequest.builder().query("Spring").topK(5).similarityThreshold(similarityThreshold).build());

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(this.documents.get(0).getId());
			assertThat(resultDoc.getContent()).contains(
					"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
			assertThat(resultDoc.getMetadata()).containsKeys("meta1", RedisVectorStore.DISTANCE_FIELD_NAME,
					DocumentMetadata.DISTANCE.value());
			assertThat(resultDoc.getScore()).isGreaterThanOrEqualTo(similarityThreshold);
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Bean
		public RedisVectorStore vectorStore(EmbeddingModel embeddingModel,
				JedisConnectionFactory jedisConnectionFactory) {
			return RedisVectorStore
				.builder(new JedisPooled(jedisConnectionFactory.getHostName(), jedisConnectionFactory.getPort()),
						embeddingModel)
				.metadataFields(MetadataField.tag("meta1"), MetadataField.tag("meta2"), MetadataField.tag("country"),
						MetadataField.numeric("year"))
				.initializeSchema(true)
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}