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
package org.springframework.ai.autoconfigure.vectorstore.couchbase;

import com.couchbase.client.java.Cluster;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.CouchbaseVectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.couchbase.CouchbaseAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * @author Laurent Doguin
 * @since 1.0.0
 */
@AutoConfiguration(after = CouchbaseAutoConfiguration.class)
@ConditionalOnClass({ CouchbaseVectorStore.class, EmbeddingModel.class, Cluster.class })
@EnableConfigurationProperties(CouchbaseVectorStoreProperties.class)
public class CouchbaseVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public CouchbaseVectorStore vectorStore(CouchbaseVectorStoreProperties properties, Cluster cluster,
			EmbeddingModel embeddingModel) {
		var builder = CouchbaseVectorStore.CouchbaseVectorStoreConfig.builder();

		if (StringUtils.hasText(properties.getIndexName())) {
			builder.withVectorIndexName(properties.getIndexName());
		}
		if (StringUtils.hasText(properties.getBucketName())) {
			builder.withBucketName(properties.getBucketName());
		}
		if (StringUtils.hasText(properties.getScopeName())) {
			builder.withScopeName(properties.getScopeName());
		}
		if (StringUtils.hasText(properties.getCollectionName())) {
			builder.withCollectionName(properties.getCollectionName());
		}
		if (properties.getDimensions() != null) {
			builder.withDimensions(properties.getDimensions());
		}
		if (properties.getSimilarity() != null) {
			builder.withSimilarityFunction(properties.getSimilarity());
		}
		if (properties.getOptimization() != null) {
			builder.withIndexOptimization(properties.getOptimization());
		}
		return new CouchbaseVectorStore(cluster, embeddingModel, builder.build(), properties.isInitializeSchema());
	}

}
