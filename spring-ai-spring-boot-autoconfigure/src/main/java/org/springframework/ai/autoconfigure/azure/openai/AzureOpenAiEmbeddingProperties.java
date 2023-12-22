/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.autoconfigure.azure.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(AzureOpenAiEmbeddingProperties.CONFIG_PREFIX)
public class AzureOpenAiEmbeddingProperties {

	public static final String CONFIG_PREFIX = "spring.ai.azure.openai.embedding";

	/**
	 * The text embedding model to use for the embedding client.
	 */
	private String model = "text-embedding-ada-002";

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

}
