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

package org.springframework.ai.minimax.api;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.ai.minimax.MiniMaxChatOptions;
import org.springframework.ai.minimax.MiniMaxEmbeddingModel;
import org.springframework.ai.minimax.MiniMaxEmbeddingOptions;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletion;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionChunk;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionFinishReason;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionMessage;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionMessage.Role;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionRequest;
import org.springframework.ai.minimax.api.MiniMaxApi.EmbeddingList;
import org.springframework.ai.minimax.api.MiniMaxApi.EmbeddingRequest;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;

/**
 * @author Geng Rong
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
public class MiniMaxRetryTests {

	private TestRetryListener retryListener;

	private RetryTemplate retryTemplate;

	private @Mock MiniMaxApi miniMaxApi;

	private MiniMaxChatModel chatModel;

	private MiniMaxEmbeddingModel embeddingModel;

	@BeforeEach
	public void beforeEach() {
		this.retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;
		this.retryListener = new TestRetryListener();
		this.retryTemplate.registerListener(this.retryListener);

		this.chatModel = new MiniMaxChatModel(this.miniMaxApi, MiniMaxChatOptions.builder().build(), null,
				this.retryTemplate);
		this.embeddingModel = new MiniMaxEmbeddingModel(this.miniMaxApi, MetadataMode.EMBED,
				MiniMaxEmbeddingOptions.builder().build(), this.retryTemplate);
	}

	@Test
	public void miniMaxChatTransientError() {

		var choice = new ChatCompletion.Choice(ChatCompletionFinishReason.STOP, 0,
				new ChatCompletionMessage("Response", Role.ASSISTANT), null, null);
		ChatCompletion expectedChatCompletion = new ChatCompletion("id", List.of(choice), 666l, "model", null, null,
				null, new MiniMaxApi.Usage(10, 10, 10));

		when(this.miniMaxApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(ResponseEntity.of(Optional.of(expectedChatCompletion)));

		var result = this.chatModel.call(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getContent()).isSameAs("Response");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void miniMaxChatNonTransientError() {
		when(this.miniMaxApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
			.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> this.chatModel.call(new Prompt("text")));
	}

	@Test
	public void miniMaxChatStreamTransientError() {

		var choice = new ChatCompletionChunk.ChunkChoice(ChatCompletionFinishReason.STOP, 0,
				new ChatCompletionMessage("Response", Role.ASSISTANT), null);
		ChatCompletionChunk expectedChatCompletion = new ChatCompletionChunk("id", List.of(choice), 666l, "model", null,
				null);

		when(this.miniMaxApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(Flux.just(expectedChatCompletion));

		var result = this.chatModel.stream(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.collectList().block().get(0).getResult().getOutput().getContent()).isSameAs("Response");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void miniMaxChatStreamNonTransientError() {
		when(this.miniMaxApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
			.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> this.chatModel.stream(new Prompt("text")).collectList().block());
	}

	@Test
	public void miniMaxEmbeddingTransientError() {

		EmbeddingList expectedEmbeddings = new EmbeddingList(List.of(new float[] { 9.9f, 8.8f }), "model", 10);

		when(this.miniMaxApi.embeddings(isA(EmbeddingRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(ResponseEntity.of(Optional.of(expectedEmbeddings)));

		var result = this.embeddingModel
			.call(new org.springframework.ai.embedding.EmbeddingRequest(List.of("text1", "text2"), null));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput()).isEqualTo(new float[] { 9.9f, 8.8f });
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void miniMaxEmbeddingNonTransientError() {
		when(this.miniMaxApi.embeddings(isA(EmbeddingRequest.class)))
			.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> this.embeddingModel
			.call(new org.springframework.ai.embedding.EmbeddingRequest(List.of("text1", "text2"), null)));
	}

	private class TestRetryListener implements RetryListener {

		int onErrorRetryCount = 0;

		int onSuccessRetryCount = 0;

		@Override
		public <T, E extends Throwable> void onSuccess(RetryContext context, RetryCallback<T, E> callback, T result) {
			this.onSuccessRetryCount = context.getRetryCount();
		}

		@Override
		public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
				Throwable throwable) {
			this.onErrorRetryCount = context.getRetryCount();
		}

	}

}
