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
package org.springframework.ai.openai;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

@SpringBootConfiguration
public class OpenAiTestConfiguration {

	@Bean
	public OpenAiApi openAiApi() {
		return new OpenAiApi(getApiKey());
	}

	@Bean
	public OpenAiImageApi openAiImageApi() {
		return new OpenAiImageApi(getApiKey());
	}

	@Bean
	public OpenAiAudioApi openAiAudioApi() {
		return new OpenAiAudioApi(getApiKey());
	}

	private String getApiKey() {
		String apiKey = System.getenv("OPENAI_API_KEY");
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"You must provide an API key.  Put it in an environment variable under the name OPENAI_API_KEY");
		}
		return apiKey;
	}

	@Bean
	public OpenAiChatModel openAiChatModel(OpenAiApi api) {
		OpenAiChatModel openAiChatModel = new OpenAiChatModel(api);
		return openAiChatModel;
	}

	@Bean
	public OpenAiAudioTranscriptionModel openAiTranscriptionModel(OpenAiAudioApi api) {
		OpenAiAudioTranscriptionModel openAiTranscriptionModel = new OpenAiAudioTranscriptionModel(api);
		return openAiTranscriptionModel;
	}

	@Bean
	public OpenAiAudioSpeechModel openAiAudioSpeechModel(OpenAiAudioApi api) {
		OpenAiAudioSpeechModel openAiAudioSpeechModel = new OpenAiAudioSpeechModel(api);
		return openAiAudioSpeechModel;
	}

	@Bean
	public OpenAiImageModel openAiImageModel(OpenAiImageApi imageApi) {
		OpenAiImageModel openAiImageModel = new OpenAiImageModel(imageApi);
		// openAiImageModel.setModel("foobar");
		return openAiImageModel;
	}

	@Bean
	public EmbeddingModel openAiEmbeddingModel(OpenAiApi api) {
		return new OpenAiEmbeddingModel(api);
	}

}
