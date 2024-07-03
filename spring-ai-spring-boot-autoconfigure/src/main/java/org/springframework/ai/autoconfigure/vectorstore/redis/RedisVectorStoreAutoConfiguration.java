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
package org.springframework.ai.autoconfigure.vectorstore.redis;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.RedisVectorStore;
import org.springframework.ai.vectorstore.RedisVectorStore.RedisVectorStoreConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author Christian Tzolov
 * @author Eddú Meléndez
 */
@AutoConfiguration
@ConditionalOnClass({ RedisVectorStore.class, EmbeddingModel.class })
@EnableConfigurationProperties(RedisVectorStoreProperties.class)
public class RedisVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(RedisConnectionDetails.class)
	public PropertiesRedisConnectionDetails redisConnectionDetails(RedisVectorStoreProperties properties) {
		return new PropertiesRedisConnectionDetails(properties);
	}

	@Bean
	@ConditionalOnMissingBean
	public RedisVectorStore vectorStore(EmbeddingModel embeddingModel, RedisVectorStoreProperties properties,
			RedisConnectionDetails redisConnectionDetails) {

		var config = RedisVectorStoreConfig.builder()
			.withURI(redisConnectionDetails.getUri())
			.withIndexName(properties.getIndex())
			.withPrefix(properties.getPrefix())
			.build();

		return new RedisVectorStore(config, embeddingModel, properties.isInitializeSchema());
	}

	static class PropertiesRedisConnectionDetails implements RedisConnectionDetails {

		private final RedisVectorStoreProperties properties;

		public PropertiesRedisConnectionDetails(RedisVectorStoreProperties properties) {
			this.properties = properties;
		}

		@Override
		public String getUri() {
			return this.properties.getUri();
		}

	}

}
