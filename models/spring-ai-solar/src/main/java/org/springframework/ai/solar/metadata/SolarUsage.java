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

package org.springframework.ai.solar.metadata;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.solar.api.SolarApi;
import org.springframework.util.Assert;

/**
 * {@link Usage} implementation for {@literal Solar}.
 *
 * @author Seunghyeon Ji
 */
public class SolarUsage implements Usage {

	private final SolarApi.Usage usage;

	protected SolarUsage(SolarApi.Usage usage) {
		Assert.notNull(usage, "Solar Usage must not be null");
		this.usage = usage;
	}

	public static SolarUsage from(SolarApi.Usage usage) {
		return new SolarUsage(usage);
	}

	protected SolarApi.Usage getUsage() {
		return this.usage;
	}

	@Override
	public Long getPromptTokens() {
		return getUsage().promptTokens().longValue();
	}

	@Override
	public Long getGenerationTokens() {
		return 0L;
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
