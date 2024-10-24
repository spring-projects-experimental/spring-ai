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

package org.springframework.ai.autoconfigure.bedrock.converse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import reactor.core.publisher.Flux;
import software.amazon.awssdk.regions.Region;

@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SESSION_TOKEN", matches = ".*")
public class BedrockConverseProxyChatAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(BedrockConverseProxyChatAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.bedrock.aws.access-key=" + System.getenv("AWS_ACCESS_KEY_ID"),
				"spring.ai.bedrock.aws.secret-key=" + System.getenv("AWS_SECRET_ACCESS_KEY"),
				"spring.ai.bedrock.aws.session-token=" + System.getenv("AWS_SESSION_TOKEN"),
				"spring.ai.bedrock.aws.region=" + Region.US_EAST_1.id(),
				"spring.ai.bedrock.converse.chat.options.model=" + "anthropic.claude-3-5-sonnet-20240620-v1:0",
				"spring.ai.bedrock.converse.chat.options.temperature=0.5")
		.withConfiguration(AutoConfigurations.of(BedrockConverseProxyChatAutoConfiguration.class));

	@Test
	void call() {
		this.contextRunner.run(context -> {
			BedrockProxyChatModel chatModel = context.getBean(BedrockProxyChatModel.class);
			String response = chatModel.call("Hello");
			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

	@Test
	void stream() {
		this.contextRunner.run(context -> {
			BedrockProxyChatModel chatModel = context.getBean(BedrockProxyChatModel.class);
			Flux<ChatResponse> responseFlux = chatModel.stream(new Prompt(new UserMessage("Hello")));

			String response = responseFlux.collectList()
				.block()
				.stream()
				.map(ChatResponse::getResults)
				.flatMap(List::stream)
				.map(Generation::getOutput)
				.map(AssistantMessage::getContent)
				.collect(Collectors.joining());

			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

}
