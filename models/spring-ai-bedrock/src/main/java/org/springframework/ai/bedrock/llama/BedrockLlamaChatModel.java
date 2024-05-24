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
package org.springframework.ai.bedrock.llama;

import java.util.List;

import reactor.core.publisher.Flux;

import org.springframework.ai.bedrock.MessageToPromptConverter;
import org.springframework.ai.bedrock.llama.api.LlamaChatBedrockApi;
import org.springframework.ai.bedrock.llama.api.LlamaChatBedrockApi.LlamaChatRequest;
import org.springframework.ai.bedrock.llama.api.LlamaChatBedrockApi.LlamaChatResponse;
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
 * Java {@link ChatModel} and {@link StreamingChatModel} for the Bedrock Llama chat
 * generative.
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 * @since 0.8.0
 */
public class BedrockLlamaChatModel implements ChatModel, StreamingChatModel {

	private final LlamaChatBedrockApi chatApi;

	private final BedrockLlamaChatOptions defaultOptions;

	public BedrockLlamaChatModel(LlamaChatBedrockApi chatApi) {
		this(chatApi,
				BedrockLlamaChatOptions.builder().withTemperature(0.8f).withTopP(0.9f).withMaxGenLen(100).build());
	}

	public BedrockLlamaChatModel(LlamaChatBedrockApi chatApi, BedrockLlamaChatOptions options) {
		Assert.notNull(chatApi, "LlamaChatBedrockApi must not be null");
		Assert.notNull(options, "BedrockLlamaChatOptions must not be null");

		this.chatApi = chatApi;
		this.defaultOptions = options;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		var request = createRequest(prompt);

		LlamaChatResponse response = this.chatApi.chatCompletion(request);

		return new ChatResponse(List.of(new Generation(response.generation()).withGenerationMetadata(
				ChatGenerationMetadata.from(response.stopReason().name(), extractUsage(response)))));
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {

		var request = createRequest(prompt);

		Flux<LlamaChatResponse> fluxResponse = this.chatApi.chatCompletionStream(request);

		return fluxResponse.map(response -> {
			String stopReason = response.stopReason() != null ? response.stopReason().name() : null;
			return new ChatResponse(List.of(new Generation(response.generation())
				.withGenerationMetadata(ChatGenerationMetadata.from(stopReason, extractUsage(response)))));
		});
	}

	private Usage extractUsage(LlamaChatResponse response) {
		return new Usage() {

			@Override
			public Long getPromptTokens() {
				return response.promptTokenCount().longValue();
			}

			@Override
			public Long getGenerationTokens() {
				return response.generationTokenCount().longValue();
			}
		};
	}

	/**
	 * Accessible for testing.
	 */
	LlamaChatRequest createRequest(Prompt prompt) {

		final String promptValue = MessageToPromptConverter.create().toPrompt(prompt.getInstructions());

		LlamaChatRequest request = LlamaChatRequest.builder(promptValue).build();

		if (this.defaultOptions != null) {
			request = ModelOptionsUtils.merge(request, this.defaultOptions, LlamaChatRequest.class);
		}

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ChatOptions runtimeOptions) {
				BedrockLlamaChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(runtimeOptions,
						ChatOptions.class, BedrockLlamaChatOptions.class);

				request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, LlamaChatRequest.class);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type ChatOptions: "
						+ prompt.getOptions().getClass().getSimpleName());
			}
		}

		return request;
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return BedrockLlamaChatOptions.fromOptions(this.defaultOptions);
	}

}
