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
package org.springframework.ai.bedrock.titan;

import java.util.List;

import reactor.core.publisher.Flux;

import org.springframework.ai.bedrock.MessageToPromptConverter;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi.TitanChatRequest;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi.TitanChatResponse;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi.TitanChatResponseChunk;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.util.Assert;

/**
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class BedrockTitanChatModel implements ChatModel, StreamingChatModel {

	private final TitanChatBedrockApi chatApi;

	private final BedrockTitanChatOptions defaultOptions;

	public BedrockTitanChatModel(TitanChatBedrockApi chatApi) {
		this(chatApi, BedrockTitanChatOptions.builder().withTemperature(0.8f).build());
	}

	public BedrockTitanChatModel(TitanChatBedrockApi chatApi, BedrockTitanChatOptions defaultOptions) {
		Assert.notNull(chatApi, "ChatApi must not be null");
		Assert.notNull(defaultOptions, "DefaultOptions must not be null");
		this.chatApi = chatApi;
		this.defaultOptions = defaultOptions;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		TitanChatResponse response = this.chatApi.chatCompletion(this.createRequest(prompt));
		List<Generation> generations = response.results().stream().map(result -> {
			return new Generation(result.outputText());
		}).toList();

		return new ChatResponse(generations);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		return this.chatApi.chatCompletionStream(this.createRequest(prompt)).map(chunk -> {

			Generation generation = new Generation(chunk.outputText());

			if (chunk.amazonBedrockInvocationMetrics() != null) {
				String completionReason = chunk.completionReason().name();
				generation = generation.withGenerationMetadata(
						ChatGenerationMetadata.from(completionReason, chunk.amazonBedrockInvocationMetrics()));
			}
			else if (chunk.inputTextTokenCount() != null && chunk.totalOutputTextTokenCount() != null) {
				String completionReason = chunk.completionReason().name();
				generation = generation
					.withGenerationMetadata(ChatGenerationMetadata.from(completionReason, extractUsage(chunk)));

			}
			return new ChatResponse(List.of(generation));
		});
	}

	/**
	 * Test access.
	 */
	TitanChatRequest createRequest(Prompt prompt) {
		final String promptValue = MessageToPromptConverter.create().toPrompt(prompt.getInstructions());

		var requestBuilder = TitanChatRequest.builder(promptValue);

		if (this.defaultOptions != null) {
			requestBuilder = update(requestBuilder, this.defaultOptions);
		}

		if (prompt.getOptions() != null) {
			BedrockTitanChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(),
					ChatOptions.class, BedrockTitanChatOptions.class);

			requestBuilder = update(requestBuilder, updatedRuntimeOptions);
		}

		return requestBuilder.build();
	}

	private TitanChatRequest.Builder update(TitanChatRequest.Builder builder, BedrockTitanChatOptions options) {
		if (options.getTemperature() != null) {
			builder.withTemperature(options.getTemperature());
		}
		if (options.getTopP() != null) {
			builder.withTopP(options.getTopP());
		}
		if (options.getMaxTokenCount() != null) {
			builder.withMaxTokenCount(options.getMaxTokenCount());
		}
		if (options.getStopSequences() != null) {
			builder.withStopSequences(options.getStopSequences());
		}
		return builder;
	}

	private Usage extractUsage(TitanChatResponseChunk response) {
		return new Usage() {

			@Override
			public Long getPromptTokens() {
				return response.inputTextTokenCount().longValue();
			}

			@Override
			public Long getGenerationTokens() {
				return response.totalOutputTextTokenCount().longValue();
			}
		};
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return BedrockTitanChatOptions.fromOptions(this.defaultOptions);
	}

}
