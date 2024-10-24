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
package org.springframework.ai.bedrock.converse.experiements;

import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.RetryUtils;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */

public class BedrockConverseChatModelTest {

	public static void main(String[] args) {

		// String modelId = "anthropic.claude-3-5-sonnet-20240620-v1:0";
		String modelId = "ai21.jamba-1-5-large-v1:0";
		var prompt = new Prompt("Tell me a joke?", ChatOptionsBuilder.builder().withModel(modelId).build());

		var chatModel = BedrockProxyChatModel.builder()
			.withCredentialsProvider(EnvironmentVariableCredentialsProvider.create())
			.withRegion(Region.US_EAST_1)
			.build();

		var chatResponse = chatModel.call(prompt);
		System.out.println(chatResponse);
	}

}
