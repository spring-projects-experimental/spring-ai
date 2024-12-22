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

package org.springframework.ai.zhipuai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * The ZhiPuAiEmbeddingOptions class represents the options for ZhiPuAI embedding.
 *
 * @author Geng Rong
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @since 1.0.0 M1
 */
@JsonInclude(Include.NON_NULL)
public class ZhiPuAiEmbeddingOptions implements EmbeddingOptions {

	// @formatter:off
	/**
	 * ID of the model to use.
	 */
	private @JsonProperty("model") String model;
	// @formatter:on

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	@JsonIgnore
	public Integer getDimensions() {
		return null;
	}

	public static class Builder {

		protected ZhiPuAiEmbeddingOptions options;

		public Builder() {
			this.options = new ZhiPuAiEmbeddingOptions();
		}

		public Builder model(String model) {
			this.options.setModel(model);
			return this;
		}

		/**
		 * @deprecated use {@link #model(String)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		public Builder withModel(String model) {
			this.options.setModel(model);
			return this;
		}

		public ZhiPuAiEmbeddingOptions build() {
			return this.options;
		}

	}

}
