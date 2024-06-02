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
package org.springframework.ai.autoconfigure.bedrock.mistral;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.autoconfigure.bedrock.BedrockAwsConnectionConfiguration;
import org.springframework.ai.autoconfigure.bedrock.BedrockAwsConnectionProperties;
import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.bedrock.mistral.BedrockMistralChatModel;
import org.springframework.ai.bedrock.mistral.api.MistralChatBedrockApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.retry.support.RetryTemplate;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;

/**
 * {@link AutoConfiguration Auto-configuration} for Bedrock Mistral Chat Client.
 *
 * @author Wei Jiang
 * @since 1.0.0
 */
@AutoConfiguration(after = { SpringAiRetryAutoConfiguration.class })
@ConditionalOnClass(MistralChatBedrockApi.class)
@EnableConfigurationProperties({ BedrockMistralChatProperties.class, BedrockAwsConnectionProperties.class })
@ConditionalOnProperty(prefix = BedrockMistralChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true")
@Import(BedrockAwsConnectionConfiguration.class)
public class BedrockMistralChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean({ AwsCredentialsProvider.class, AwsRegionProvider.class })
	public MistralChatBedrockApi mistralChatApi(AwsCredentialsProvider credentialsProvider,
			AwsRegionProvider regionProvider, BedrockMistralChatProperties properties,
			BedrockAwsConnectionProperties awsProperties) {
		return new MistralChatBedrockApi(properties.getModel(), credentialsProvider, regionProvider.getRegion(),
				new ObjectMapper(), awsProperties.getTimeout());
	}

	@Bean
	@ConditionalOnBean(MistralChatBedrockApi.class)
	public BedrockMistralChatModel mistralChatModel(MistralChatBedrockApi mistralChatApi,
			BedrockMistralChatProperties properties, RetryTemplate retryTemplate) {

		return new BedrockMistralChatModel(mistralChatApi, properties.getOptions(), retryTemplate);
	}

}
