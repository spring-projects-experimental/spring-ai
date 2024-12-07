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
package org.springframework.ai.bedrock.converse.client;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.Media;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
@SpringBootTest(classes = BedrockNovaChatClientIT.Config.class)
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SESSION_TOKEN", matches = ".*")
public class BedrockNovaChatClientIT {

	private static final Logger logger = LoggerFactory.getLogger(BedrockNovaChatClientIT.class);

	@Autowired
	ChatModel chatModel;

	@Test
	void pdfMultiModalityTest() throws IOException {

		String response = ChatClient.create(chatModel)
			.prompt()
			.user(u -> u.text(
					"You are a very professional document summarization specialist. Please summarize the given document.")
				.media(Media.Format.DOC_PDF, new ClassPathResource("/spring-ai-reference-overview.pdf")))
			.call()
			.content();

		logger.info(response);
		assertThat(response).containsAnyOf("Spring AI", "portable API");
	}

	@Test
	void imageMultiModalityTest() throws IOException {

		String response = ChatClient.create(chatModel)
			.prompt()
			.user(u -> u.text("Explain what do you see on this picture?")
				.media(Media.Format.IMAGE_PNG, new ClassPathResource("/test.png")))
			.call()
			.content();

		logger.info(response);
		assertThat(response).containsAnyOf("banan", "apple", "basket");
	}

	@Test
	void videoMultiModalityTest() throws IOException {

		String response = ChatClient.create(chatModel)
			.prompt()
			.user(u -> u.text("Explain what do you see in this video?")
				.media(Media.Format.VIDEO_MP4, new ClassPathResource("/test.video.mp4")))
			.call()
			.content();

		logger.info(response);
		assertThat(response).containsAnyOf("baby", "small");
		assertThat(response).containsAnyOf("chicks", "chickens");
	}

	public static record WeatherRequest(String location, String unit) {
	}

	public static record WeatherResponse(int temp, String unit) {
	}

	@Test
	void functionCallTest() {

		// @formatter:off
		String response = ChatClient.create(this.chatModel).prompt()
				.user("What's the weather like in San Francisco, Tokyo, and Paris?  Use Celsius.")
				.functions(FunctionCallback.builder()
					.function("getCurrentWeather", (WeatherRequest request) -> {
						if (request.location().contains("Paris")) {
							return new WeatherResponse(15, request.unit());
						}
						else if (request.location().contains("Tokyo")) {
							return new WeatherResponse(10, request.unit());
						}
						else if (request.location().contains("San Francisco")) {
							return new WeatherResponse(30, request.unit());
						}
			
						throw new IllegalArgumentException("Unknown location: " + request.location());
					})
					.inputType(WeatherRequest.class)
					.build())
				.call()
				.content();
		// @formatter:on

		logger.info("Response: {}", response);

		assertThat(response).contains("30", "10", "15");
	}

	@SpringBootConfiguration
	public static class Config {

		@Bean
		public BedrockProxyChatModel bedrockConverseChatModel() {

			String modelId = "amazon.nova-pro-v1:0";

			return BedrockProxyChatModel.builder()
				.withCredentialsProvider(EnvironmentVariableCredentialsProvider.create())
				.withRegion(Region.US_EAST_1)
				.withTimeout(Duration.ofSeconds(120))
				.withDefaultOptions(FunctionCallingOptions.builder().withModel(modelId).build())
				.build();
		}

	}

}
