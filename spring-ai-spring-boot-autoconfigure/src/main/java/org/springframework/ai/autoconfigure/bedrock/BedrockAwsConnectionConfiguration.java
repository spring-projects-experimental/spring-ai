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
package org.springframework.ai.autoconfigure.bedrock;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * @author Christian Tzolov
 * @author Wei Jiang
 */
@Configuration
@EnableConfigurationProperties({ BedrockAwsConnectionProperties.class })
public class BedrockAwsConnectionConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public AwsCredentialsProvider credentialsProvider(BedrockAwsConnectionProperties properties) {

		if (StringUtils.hasText(properties.getAccessKey()) && StringUtils.hasText(properties.getSecretKey())) {
			return StaticCredentialsProvider
				.create(AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));
		}

		return DefaultCredentialsProvider.create();
	}

	@Bean
	@ConditionalOnMissingBean
	public AwsRegionProvider regionProvider(BedrockAwsConnectionProperties properties) {

		if (StringUtils.hasText(properties.getRegion())) {
			return new StaticRegionProvider(properties.getRegion());
		}

		return DefaultAwsRegionProviderChain.builder().build();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean({ AwsCredentialsProvider.class, AwsRegionProvider.class })
	public BedrockRuntimeClient bedrockRuntimeClient(AwsCredentialsProvider credentialsProvider,
			AwsRegionProvider regionProvider, BedrockAwsConnectionProperties properties) {

		return BedrockRuntimeClient.builder()
			.region(regionProvider.getRegion())
			.credentialsProvider(credentialsProvider)
			.overrideConfiguration(c -> c.apiCallTimeout(properties.getTimeout()))
			.build();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean({ AwsCredentialsProvider.class, AwsRegionProvider.class })
	public BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient(AwsCredentialsProvider credentialsProvider,
			AwsRegionProvider regionProvider, BedrockAwsConnectionProperties properties) {

		return BedrockRuntimeAsyncClient.builder()
			.region(regionProvider.getRegion())
			.credentialsProvider(credentialsProvider)
			.overrideConfiguration(c -> c.apiCallTimeout(properties.getTimeout()))
			.build();
	}

	/**
	 * @author Wei Jiang
	 */
	static class StaticRegionProvider implements AwsRegionProvider {

		private final Region region;

		public StaticRegionProvider(String region) {
			try {
				this.region = Region.of(region);
			}
			catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("The region '" + region + "' is not a valid region!", e);
			}
		}

		@Override
		public Region getRegion() {
			return this.region;
		}

	}

}
