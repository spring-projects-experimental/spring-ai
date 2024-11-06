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

package org.springframework.ai.azure.openai;

import java.util.List;
import java.util.stream.Collectors;

import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.OpenAIServiceVersion;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.http.policy.HttpLogOptions;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Soby Chacko
 */
@SpringBootTest(classes = AzureOpenAiChatModelObservationIT.TestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_ENDPOINT", matches = ".+")
class AzureOpenAiChatModelObservationIT {

	@Autowired
	TestObservationRegistry observationRegistry;

	@Autowired
	private AzureOpenAiChatModel chatModel;

	@BeforeEach
	void beforeEach() {
		this.observationRegistry.clear();
	}

	@Test
	void observationForImperativeChatOperation() {

		var options = AzureOpenAiChatOptions.builder()
			.withFrequencyPenalty(0.0)
			.withMaxTokens(2048)
			.withPresencePenalty(0.0)
			.withStop(List.of("this-is-the-end"))
			.withTemperature(0.7)
			.withTopP(1.0)
			.build();

		Prompt prompt = new Prompt("Why does a raven look like a desk?", options);

		ChatResponse chatResponse = this.chatModel.call(prompt);
		assertThat(chatResponse.getResult().getOutput().getContent()).isNotEmpty();

		ChatResponseMetadata responseMetadata = chatResponse.getMetadata();
		assertThat(responseMetadata).isNotNull();

		validate(responseMetadata, true);
	}

	@Test
	void observationForStreamingChatOperation() {

		var options = AzureOpenAiChatOptions.builder()
			.withFrequencyPenalty(0.0)
			.withDeploymentName("gpt-4o")
			.withMaxTokens(2048)
			.withPresencePenalty(0.0)
			.withStop(List.of("this-is-the-end"))
			.withTemperature(0.7)
			.withTopP(1.0)
			.build();

		Prompt prompt = new Prompt("Why does a raven look like a desk?", options);

		Flux<ChatResponse> chatResponseFlux = this.chatModel.stream(prompt);
		List<ChatResponse> responses = chatResponseFlux.collectList().block();
		assertThat(responses).isNotEmpty();
		assertThat(responses).hasSizeGreaterThan(10);

		String aggregatedResponse = responses.subList(0, responses.size() - 1)
			.stream()
			.map(r -> r.getResult().getOutput().getContent())
			.collect(Collectors.joining());
		assertThat(aggregatedResponse).isNotEmpty();

		ChatResponse lastChatResponse = responses.get(responses.size() - 1);

		ChatResponseMetadata responseMetadata = lastChatResponse.getMetadata();
		assertThat(responseMetadata).isNotNull();

		validate(responseMetadata, false);
	}

	private void validate(ChatResponseMetadata responseMetadata, boolean checkModel) {

		TestObservationRegistryAssert.That that = TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultChatModelObservationConvention.DEFAULT_NAME);

		// TODO - Investigate why streaming does not contain model in the response.
		if (checkModel) {
			that.that()
				.hasLowCardinalityKeyValue(
						ChatModelObservationDocumentation.LowCardinalityKeyNames.RESPONSE_MODEL.asString(),
						responseMetadata.getModel());
		}

		that.that()
			.hasLowCardinalityKeyValue(
					ChatModelObservationDocumentation.LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.CHAT.value())
			.hasLowCardinalityKeyValue(ChatModelObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER.asString(),
					AiProvider.AZURE_OPENAI.value())
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_FREQUENCY_PENALTY.asString(),
					"0.0")
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_MAX_TOKENS.asString(), "2048")
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_PRESENCE_PENALTY.asString(),
					"0.0")
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_STOP_SEQUENCES.asString(),
					"[\"this-is-the-end\"]")
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TEMPERATURE.asString(), "0.7")
			.doesNotHaveHighCardinalityKeyValueWithKey(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TOP_K.asString())
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TOP_P.asString(), "1.0")
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.RESPONSE_ID.asString(),
					responseMetadata.getId())
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.RESPONSE_FINISH_REASONS.asString(),
					"[\"stop\"]")
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.USAGE_INPUT_TOKENS.asString(),
					String.valueOf(responseMetadata.getUsage().getPromptTokens()))
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.USAGE_OUTPUT_TOKENS.asString(),
					String.valueOf(responseMetadata.getUsage().getGenerationTokens()))
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.USAGE_TOTAL_TOKENS.asString(),
					String.valueOf(responseMetadata.getUsage().getTotalTokens()))
			.hasBeenStarted()
			.hasBeenStopped();
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		public OpenAIClientBuilder openAIClient() {
			return new OpenAIClientBuilder().credential(new AzureKeyCredential(System.getenv("AZURE_OPENAI_API_KEY")))
				.endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
				.serviceVersion(OpenAIServiceVersion.V2024_02_15_PREVIEW)
				.httpLogOptions(new HttpLogOptions()
					.setLogLevel(com.azure.core.http.policy.HttpLogDetailLevel.BODY_AND_HEADERS));
		}

		@Bean
		public AzureOpenAiChatModel azureOpenAiChatModel(OpenAIClientBuilder openAIClientBuilder,
				TestObservationRegistry observationRegistry) {
			return new AzureOpenAiChatModel(openAIClientBuilder,
					AzureOpenAiChatOptions.builder().withDeploymentName("gpt-4o").withMaxTokens(1000).build(), null,
					List.of(), observationRegistry);
		}

	}

}
