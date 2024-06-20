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
package org.springframework.ai.autoconfigure.bedrock.titan;

import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import org.springframework.ai.autoconfigure.bedrock.BedrockAwsConnectionConfiguration;
import org.springframework.ai.autoconfigure.bedrock.BedrockAwsConnectionProperties;
import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.bedrock.titan.BedrockTitanEmbeddingModel;
import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.retry.support.RetryTemplate;

/**
 * {@link AutoConfiguration Auto-configuration} for Bedrock Titan Embedding Model.
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 * @since 0.8.0
 */
@AutoConfiguration(after = SpringAiRetryAutoConfiguration.class)
@ConditionalOnClass(TitanEmbeddingBedrockApi.class)
@EnableConfigurationProperties({ BedrockTitanEmbeddingProperties.class, BedrockAwsConnectionProperties.class })
@ConditionalOnProperty(prefix = BedrockTitanEmbeddingProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true")
@Import(BedrockAwsConnectionConfiguration.class)
public class BedrockTitanEmbeddingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean({ BedrockRuntimeClient.class, BedrockRuntimeAsyncClient.class })
	public TitanEmbeddingBedrockApi titanEmbeddingBedrockApi(BedrockTitanEmbeddingProperties properties,
			BedrockRuntimeClient bedrockRuntimeClient, BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient) {
		return new TitanEmbeddingBedrockApi(properties.getModel(), bedrockRuntimeClient, bedrockRuntimeAsyncClient,
				new ObjectMapper());
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(TitanEmbeddingBedrockApi.class)
	public BedrockTitanEmbeddingModel titanEmbeddingModel(TitanEmbeddingBedrockApi titanEmbeddingApi,
			BedrockTitanEmbeddingProperties properties, RetryTemplate retryTemplate) {

		return new BedrockTitanEmbeddingModel(titanEmbeddingApi, retryTemplate)
			.withInputType(properties.getInputType());
	}

}
