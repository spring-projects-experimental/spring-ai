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
package org.springframework.ai.wordlift.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.wordlift.WordLiftChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "WORDLIFT_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WORDLIFT_CHAT_URL", matches = ".+")
public class ClientIT {

	@Autowired
	protected WordLiftChatModel wordliftChatModel;

	@Test
	void helloWorldCompletion() {
		String mistral7bInstruct = """
				[INST] You are a helpful code assistant. Your task is to generate a valid JSON object based on the given information:
				name: John
				lastname: Smith
				address: #1 Samuel St.
				Just generate the JSON object without explanations:
				[/INST]
				 """;
		Prompt prompt = new Prompt(mistral7bInstruct);
		ChatResponse chatResponse = wordliftChatModel.call(prompt);
		assertThat(chatResponse.getResult().getOutput().getContent()).isNotEmpty();
		String expectedResponse = """
				{
				  "name": "John",
				  "lastname": "Smith",
				  "address": "#1 Samuel St."
				}""";
		assertThat(chatResponse.getResult().getOutput().getContent()).isEqualTo(expectedResponse);
		assertThat(chatResponse.getResult().getOutput().getMetadata()).containsKey("generated_tokens");
		assertThat(chatResponse.getResult().getOutput().getMetadata()).containsEntry("generated_tokens", 32);

	}

}
