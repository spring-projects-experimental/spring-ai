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
package org.springframework.ai.openai.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.tool.MockWeatherService;
import org.springframework.ai.openai.api.tool.MockWeatherService.Request;
import org.springframework.ai.openai.api.tool.MockWeatherService.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpenAiChatModelFunctionCallingIT.Config.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiChatModelFunctionCallingIT {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiChatModelFunctionCallingIT.class);

	@Autowired
	ChatModel chatModel;

	@Test
	void functionCallTest() {
		functionCallTest(OpenAiChatOptions.builder()
			.withModel(OpenAiApi.ChatModel.GPT_4_O.getValue())
			.withFunctionCallbacks(List.of(FunctionCallbackWrapper.builder(new MockWeatherService())
				.withName("getCurrentWeather")
				.withDescription("Get the weather in location")
				.withResponseConverter((response) -> "" + response.temp() + response.unit())
				.build()))
			.build());
	}

	@Test
	void functionCallWithToolContextTest() {

		var biFunction = new BiFunction<MockWeatherService.Request, ToolContext, MockWeatherService.Response>() {

			@Override
			public Response apply(Request request, ToolContext toolContext) {

				assertThat(toolContext.context).containsEntry("sessionId", "123");

				double temperature = 0;
				if (request.location().contains("Paris")) {
					temperature = 15;
				}
				else if (request.location().contains("Tokyo")) {
					temperature = 10;
				}
				else if (request.location().contains("San Francisco")) {
					temperature = 30;
				}

				return new MockWeatherService.Response(temperature, 15, 20, 2, 53, 45, MockWeatherService.Unit.C);
			}

		};

		functionCallTest(OpenAiChatOptions.builder()
			.withModel(OpenAiApi.ChatModel.GPT_4_O.getValue())
			.withFunctionCallbacks(List.of(FunctionCallbackWrapper.builder(biFunction)
				.withName("getCurrentWeather")
				.withDescription("Get the weather in location")
				.withResponseConverter((response) -> "" + response.temp() + response.unit())
				.build()))
			.withToolContext(Map.of("sessionId", "123"))
			.build());
	}

	void functionCallTest(OpenAiChatOptions promptOptions) {

		UserMessage userMessage = new UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		ChatResponse response = chatModel.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);

		assertThat(response.getResult().getOutput().getContent()).contains("30", "10", "15");
	}

	@Test
	void streamFunctionCallTest() {

		streamFunctionCallTest(OpenAiChatOptions.builder()
			.withFunctionCallbacks(List.of((FunctionCallbackWrapper.builder(new MockWeatherService())
				.withName("getCurrentWeather")
				.withDescription("Get the weather in location")
				.withResponseConverter((response) -> "" + response.temp() + response.unit())
				.build())))
			.build());
	}

	@Test
	void streamFunctionCallWithToolContextTest() {

		var biFunction = new BiFunction<MockWeatherService.Request, ToolContext, MockWeatherService.Response>() {

			@Override
			public Response apply(Request request, ToolContext toolContext) {

				assertThat(toolContext.getContext()).containsEntry("sessionId", "123");

				double temperature = 0;
				if (request.location().contains("Paris")) {
					temperature = 15;
				}
				else if (request.location().contains("Tokyo")) {
					temperature = 10;
				}
				else if (request.location().contains("San Francisco")) {
					temperature = 30;
				}

				return new MockWeatherService.Response(temperature, 15, 20, 2, 53, 45, MockWeatherService.Unit.C);
			}

		};

		OpenAiChatOptions promptOptions = OpenAiChatOptions.builder()
			.withFunctionCallbacks(List.of((FunctionCallbackWrapper.builder(biFunction)
				.withName("getCurrentWeather")
				.withDescription("Get the weather in location")
				.withResponseConverter((response) -> "" + response.temp() + response.unit())
				.build())))
			.withToolContext(Map.of("sessionId", "123"))
			.build();

		streamFunctionCallTest(promptOptions);
	}

	void streamFunctionCallTest(OpenAiChatOptions promptOptions) {

		UserMessage userMessage = new UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		Flux<ChatResponse> response = chatModel.stream(new Prompt(messages, promptOptions));

		String content = response.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());
		logger.info("Response: {}", content);

		assertThat(content).contains("30", "10", "15");
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiApi chatCompletionApi() {
			return new OpenAiApi(System.getenv("OPENAI_API_KEY"));
		}

		@Bean
		public OpenAiChatModel openAiClient(OpenAiApi openAiApi) {
			return new OpenAiChatModel(openAiApi);
		}

	}

}