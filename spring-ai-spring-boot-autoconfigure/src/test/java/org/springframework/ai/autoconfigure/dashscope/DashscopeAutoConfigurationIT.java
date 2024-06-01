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
package org.springframework.ai.autoconfigure.dashscope;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.dashscope.DashscopeChatModel;
import org.springframework.ai.dashscope.DashscopeEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".*")
public class DashscopeAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(DashscopeAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.dashscope.apiKey=" + System.getenv("DASHSCOPE_API_KEY"))
		.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
				RestClientAutoConfiguration.class, DashscopeAutoConfiguration.class));

	@Test
	void generate() {
		contextRunner.run(context -> {
			DashscopeChatModel client = context.getBean(DashscopeChatModel.class);
			String response = client.call("Hello");
			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

	@Test
	void generateStreaming() {
		contextRunner.run(context -> {
			DashscopeChatModel client = context.getBean(DashscopeChatModel.class);
			Flux<ChatResponse> responseFlux = client.stream(new Prompt(new UserMessage("Hello")));
			String response = responseFlux.collectList().block().stream().map(chatResponse -> {
				return chatResponse.getResults().get(0).getOutput().getContent();
			}).collect(Collectors.joining());

			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

	@Test
	void embedding() {
		contextRunner.run(context -> {
			DashscopeEmbeddingModel embeddingClient = context.getBean(DashscopeEmbeddingModel.class);

			EmbeddingResponse embeddingResponse = embeddingClient
				.embedForResponse(List.of("Hello World", "World is big and salvation is near"));
			assertThat(embeddingResponse.getResults()).hasSize(2);
			assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
			assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
			assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
			assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);

			assertThat(embeddingClient.dimensions()).isEqualTo(1536);
		});
	}

}
