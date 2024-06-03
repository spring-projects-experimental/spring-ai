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

import org.springframework.ai.bedrock.cohere.BedrockCohereChatOptions;
import org.springframework.ai.bedrock.cohere.BedrockCohereChatModel.CohereChatModel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.Assert;

/**
 * Bedrock Cohere Chat autoconfiguration properties.
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 * @since 0.8.0
 */
@ConfigurationProperties(BedrockCohereChatProperties.CONFIG_PREFIX)
public class BedrockCohereChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.bedrock.cohere.chat";

	/**
	 * Enable Bedrock Cohere Chat Client. False by default.
	 */
	private boolean enabled = false;

	/**
	 * Bedrock Cohere Chat generative name. Defaults to 'cohere-command-v14'.
	 */
	private String model = CohereChatModel.COHERE_COMMAND_V14.id();

	@NestedConfigurationProperty
	private BedrockCohereChatOptions options = BedrockCohereChatOptions.builder().build();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public BedrockCohereChatOptions getOptions() {
		return this.options;
	}

	public void setOptions(BedrockCohereChatOptions options) {
		Assert.notNull(options, "BedrockCohereChatOptions must not be null");

		this.options = options;
	}

}
