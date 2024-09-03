/*
 * Copyright 2024 - 2024 the original author or authors.
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
package org.springframework.ai.zhipuai.metadata;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.util.Assert;

/**
 * {@link Usage} implementation for {@literal ZhiPuAI}.
 *
 * @author Geng Rong
 * @since 1.0.0 M1
 */
public class ZhiPuAiUsage implements Usage {

	public static ZhiPuAiUsage from(ZhiPuAiApi.Usage usage) {
		return new ZhiPuAiUsage(usage);
	}

	private final ZhiPuAiApi.Usage usage;

	protected ZhiPuAiUsage(ZhiPuAiApi.Usage usage) {
		Assert.notNull(usage, "ZhiPuAI Usage must not be null");
		this.usage = usage;
	}

	protected ZhiPuAiApi.Usage getUsage() {
		return this.usage;
	}

	@Override
	public Long getPromptTokens() {
		return getUsage().promptTokens().longValue();
	}

	@Override
	public Long getGenerationTokens() {
		return getUsage().completionTokens().longValue();
	}

	@Override
	public Long getTotalTokens() {
		return getUsage().totalTokens().longValue();
	}

	@Override
	public String toString() {
		return getUsage().toString();
	}

}
