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

package org.springframework.ai.vectorstore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.MilvusVectorStore.MilvusVectorStoreConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ilayaperumal Gopinathan
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class MilvusVectorStoreCustomFieldNamesIT {

	@Container
	private static MilvusContainer milvusContainer = new MilvusContainer(MilvusImage.DEFAULT_IMAGE);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	List<Document> documents = List.of(
			new Document(getText("classpath:/test/data/spring.ai.txt"), Map.of("meta1", "meta1")),
			new Document(getText("classpath:/test/data/time.shelter.txt")),
			new Document(getText("classpath:/test/data/great.depression.txt"), Map.of("meta2", "meta2")));

	public static String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void resetCollection(VectorStore vectorStore) {
		((MilvusVectorStore) vectorStore).dropCollection();
		((MilvusVectorStore) vectorStore).createCollection();
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "COSINE" })
	void searchWithCustomFieldNames(String metricType) {

		this.contextRunner
			.withPropertyValues("test.spring.ai.vectorstore.milvus.metricType=" + metricType,
					"test.spring.ai.vectorstore.milvus.idFieldName=document_id",
					"test.spring.ai.vectorstore.milvus.contentFieldName=text",
					"test.spring.ai.vectorstore.milvus.embeddingFieldName=vector",
					"test.spring.ai.vectorstore.milvus.metadataFieldName=meta")
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);

				resetCollection(vectorStore);

				vectorStore.add(this.documents);

				List<Document> fullResult = vectorStore.similaritySearch(SearchRequest.query("Spring"));

				List<Float> distances = fullResult.stream()
					.map(doc -> (Float) doc.getMetadata().get("distance"))
					.toList();

				assertThat(distances).hasSize(3);

				float threshold = (distances.get(0) + distances.get(1)) / 2;

				List<Document> results = vectorStore
					.similaritySearch(SearchRequest.query("Spring").withTopK(5).withSimilarityThreshold(1 - threshold));

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(String.valueOf(resultDoc.getId())).isEqualTo(this.documents.get(0).getId());
				assertThat(resultDoc.getContent()).contains(
						"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
				assertThat(resultDoc.getMetadata()).containsKeys("meta1", "distance");

			});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "COSINE" })
	void searchWithoutMetadataFieldOverride(String metricType) {

		this.contextRunner
			.withPropertyValues("test.spring.ai.vectorstore.milvus.metricType=" + metricType,
					"test.spring.ai.vectorstore.milvus.idFieldName=identity",
					"test.spring.ai.vectorstore.milvus.contentFieldName=text",
					"test.spring.ai.vectorstore.milvus.embeddingFieldName=embed")
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);

				resetCollection(vectorStore);

				vectorStore.add(this.documents);

				List<Document> fullResult = vectorStore.similaritySearch(SearchRequest.query("Spring"));

				List<Float> distances = fullResult.stream()
					.map(doc -> (Float) doc.getMetadata().get("distance"))
					.toList();

				assertThat(distances).hasSize(3);

				float threshold = (distances.get(0) + distances.get(1)) / 2;

				List<Document> results = vectorStore
					.similaritySearch(SearchRequest.query("Spring").withTopK(5).withSimilarityThreshold(1 - threshold));

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				assertThat(String.valueOf(resultDoc.getId())).isEqualTo(this.documents.get(0).getId());
				assertThat(resultDoc.getContent()).contains(
						"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
				assertThat(resultDoc.getMetadata()).containsKeys("meta1", "distance");

			});
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "COSINE" })
	void searchWithAutoIdEnabled(String metricType) {

		this.contextRunner
			.withPropertyValues("test.spring.ai.vectorstore.milvus.metricType=" + metricType,
					"test.spring.ai.vectorstore.milvus.isAutoId=true",
					"test.spring.ai.vectorstore.milvus.idFieldName=identity",
					"test.spring.ai.vectorstore.milvus.contentFieldName=media",
					"test.spring.ai.vectorstore.milvus.metadataFieldName=meta",
					"test.spring.ai.vectorstore.milvus.embeddingFieldName=embed")
			.run(context -> {

				VectorStore vectorStore = context.getBean(VectorStore.class);

				resetCollection(vectorStore);

				vectorStore.add(this.documents);

				List<Document> fullResult = vectorStore.similaritySearch(SearchRequest.query("Spring"));

				List<Float> distances = fullResult.stream()
					.map(doc -> (Float) doc.getMetadata().get("distance"))
					.toList();

				assertThat(distances).hasSize(3);

				float threshold = (distances.get(0) + distances.get(1)) / 2;

				List<Document> results = vectorStore
					.similaritySearch(SearchRequest.query("Spring").withTopK(5).withSimilarityThreshold(1 - threshold));

				assertThat(results).hasSize(1);
				Document resultDoc = results.get(0);
				// Verify that the auto ID is used
				assertThat(String.valueOf(resultDoc.getId())).isNotEqualTo(this.documents.get(0).getId());
				assertThat(resultDoc.getContent()).contains(
						"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
				assertThat(resultDoc.getMetadata()).containsKeys("meta1", "distance");

			});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	static class TestApplication {

		@Value("${test.spring.ai.vectorstore.milvus.metricType}")
		private MetricType metricType;

		@Value("${test.spring.ai.vectorstore.milvus.idFieldName}")
		private String idFieldName;

		@Value("${test.spring.ai.vectorstore.milvus.isAutoId:false}")
		private Boolean isAutoId;

		@Value("${test.spring.ai.vectorstore.milvus.contentFieldName}")
		private String contentFieldName;

		@Value("${test.spring.ai.vectorstore.milvus.embeddingFieldName}")
		private String embeddingFieldName;

		@Value("${test.spring.ai.vectorstore.milvus.metadataFieldName:metadata}")
		private String metadataFieldName;

		@Bean
		VectorStore vectorStore(MilvusServiceClient milvusClient, EmbeddingModel embeddingModel) {
			MilvusVectorStoreConfig config = MilvusVectorStoreConfig.builder()
				.withCollectionName("test_vector_store_custom_fields")
				.withDatabaseName("default")
				.withIndexType(IndexType.IVF_FLAT)
				.withMetricType(this.metricType)
				.withIDFieldName(this.idFieldName)
				.withAutoId(this.isAutoId)
				.withContentFieldName(this.contentFieldName)
				.withEmbeddingFieldName(this.embeddingFieldName)
				.withMetadataFieldName(this.metadataFieldName)
				.build();
			return new MilvusVectorStore(milvusClient, embeddingModel, config, true, new TokenCountBatchingStrategy());
		}

		@Bean
		MilvusServiceClient milvusClient() {
			return new MilvusServiceClient(ConnectParam.newBuilder()
				.withAuthorization("minioadmin", "minioadmin")
				.withUri(milvusContainer.getEndpoint())
				.build());
		}

		@Bean
		EmbeddingModel embeddingModel() {
			return new OpenAiEmbeddingModel(new OpenAiApi(System.getenv("OPENAI_API_KEY")));
		}

	}

}
