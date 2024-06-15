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
package org.springframework.ai.autoconfigure.bedrock.cohere;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.autoconfigure.bedrock.BedrockAwsConnectionConfiguration;
import org.springframework.ai.autoconfigure.bedrock.BedrockAwsConnectionProperties;
import org.springframework.ai.bedrock.cohere.BedrockCohereChatModel;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;

/**
 * {@link AutoConfiguration Auto-configuration} for Bedrock Cohere Chat Client.
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 * @since 0.8.0
 */
@AutoConfiguration
@ConditionalOnClass(CohereChatBedrockApi.class)
@EnableConfigurationProperties({ BedrockCohereChatProperties.class, BedrockAwsConnectionProperties.class })
@ConditionalOnProperty(prefix = BedrockCohereChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true")
@Import(BedrockAwsConnectionConfiguration.class)
public class BedrockCohereChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean({ AwsCredentialsProvider.class, AwsRegionProvider.class })
	public CohereChatBedrockApi cohereChatApi(AwsCredentialsProvider credentialsProvider,
			AwsRegionProvider regionProvider, BedrockCohereChatProperties properties,
			BedrockAwsConnectionProperties awsProperties) {
		return new CohereChatBedrockApi(properties.getModel(), credentialsProvider, regionProvider.getRegion(),
				new ObjectMapper(), awsProperties.getTimeout());
	}

	@Bean
	@ConditionalOnBean(CohereChatBedrockApi.class)
	public BedrockCohereChatModel cohereChatModel(CohereChatBedrockApi cohereChatApi,
			BedrockCohereChatProperties properties) {

		return new BedrockCohereChatModel(cohereChatApi, properties.getOptions());
	}

}
