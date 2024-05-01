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
package org.springframework.ai.bedrock.cohere;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.ai.bedrock.BedrockConverseChatGenerationMetadata;
import org.springframework.ai.bedrock.api.BedrockConverseApi;
import org.springframework.ai.bedrock.api.BedrockConverseApiUtils;
import org.springframework.ai.bedrock.api.BedrockConverseApi.BedrockConverseRequest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.AbstractFunctionCallSupport;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import reactor.core.publisher.Flux;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultStatus;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock.Type;

/**
 * Java {@link ChatModel} and {@link StreamingChatModel} for the Bedrock Command R chat
 * generative model.
 *
 * @author Wei Jiang
 * @since 1.0.0
 */
public class BedrockCohereCommandRChatModel
		extends AbstractFunctionCallSupport<Message, BedrockConverseRequest, ChatResponse>
		implements ChatModel, StreamingChatModel {

	private final String modelId;

	private final BedrockConverseApi converseApi;

	private final BedrockCohereCommandRChatOptions defaultOptions;

	public BedrockCohereCommandRChatModel(BedrockConverseApi converseApi) {
		this(converseApi, BedrockCohereCommandRChatOptions.builder().build());
	}

	public BedrockCohereCommandRChatModel(BedrockConverseApi converseApi, BedrockCohereCommandRChatOptions options) {
		this(CohereCommandRChatModel.COHERE_COMMAND_R_PLUS_V1.id(), converseApi, options);
	}

	public BedrockCohereCommandRChatModel(String modelId, BedrockConverseApi converseApi,
			BedrockCohereCommandRChatOptions options) {
		this(modelId, converseApi, options, null);
	}

	public BedrockCohereCommandRChatModel(String modelId, BedrockConverseApi converseApi,
			BedrockCohereCommandRChatOptions options, FunctionCallbackContext functionCallbackContext) {
		super(functionCallbackContext);

		Assert.notNull(modelId, "modelId must not be null.");
		Assert.notNull(converseApi, "BedrockConverseApi must not be null.");
		Assert.notNull(options, "BedrockCohereCommandRChatOptions must not be null.");

		this.modelId = modelId;
		this.converseApi = converseApi;
		this.defaultOptions = options;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		Assert.notNull(prompt, "Prompt must not be null.");

		var request = createBedrockConverseRequest(prompt);

		return this.callWithFunctionSupport(request);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		Assert.notNull(prompt, "Prompt must not be null.");

		var request = createBedrockConverseRequest(prompt);

		return converseApi.converseStream(request);
	}

	private BedrockConverseRequest createBedrockConverseRequest(Prompt prompt) {
		var request = BedrockConverseApiUtils.createBedrockConverseRequest(modelId, prompt, defaultOptions);

		ToolConfiguration toolConfiguration = createToolConfiguration(prompt);

		return BedrockConverseRequest.from(request).withToolConfiguration(toolConfiguration).build();
	}

	private ToolConfiguration createToolConfiguration(Prompt prompt) {
		Set<String> functionsForThisRequest = new HashSet<>();

		if (this.defaultOptions != null) {
			Set<String> promptEnabledFunctions = this.handleFunctionCallbackConfigurations(this.defaultOptions,
					!IS_RUNTIME_CALL);
			functionsForThisRequest.addAll(promptEnabledFunctions);
		}

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ChatOptions runtimeOptions) {
				BedrockCohereCommandRChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(runtimeOptions,
						ChatOptions.class, BedrockCohereCommandRChatOptions.class);

				Set<String> defaultEnabledFunctions = this.handleFunctionCallbackConfigurations(updatedRuntimeOptions,
						IS_RUNTIME_CALL);
				functionsForThisRequest.addAll(defaultEnabledFunctions);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type ChatOptions: "
						+ prompt.getOptions().getClass().getSimpleName());
			}
		}

		if (!CollectionUtils.isEmpty(functionsForThisRequest)) {
			return ToolConfiguration.builder().tools(getFunctionTools(functionsForThisRequest)).build();
		}

		return null;
	}

	private List<Tool> getFunctionTools(Set<String> functionNames) {
		return this.resolveFunctionCallbacks(functionNames).stream().map(functionCallback -> {
			var description = functionCallback.getDescription();
			var name = functionCallback.getName();
			String inputSchema = functionCallback.getInputTypeSchema();

			return Tool.builder()
				.toolSpec(ToolSpecification.builder()
					.name(name)
					.description(description)
					.inputSchema(ToolInputSchema.builder()
						.json(BedrockConverseApiUtils.convertObjectToDocument(ModelOptionsUtils.jsonToMap(inputSchema)))
						.build())
					.build())
				.build();
		}).toList();
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return BedrockCohereCommandRChatOptions.fromOptions(defaultOptions);
	}

	@Override
	protected BedrockConverseRequest doCreateToolResponseRequest(BedrockConverseRequest previousRequest,
			Message responseMessage, List<Message> conversationHistory) {
		List<ToolUseBlock> toolToUseList = responseMessage.content()
			.stream()
			.filter(content -> content.type() == Type.TOOL_USE)
			.map(content -> content.toolUse())
			.toList();

		List<ToolResultBlock> toolResults = new ArrayList<>();

		for (ToolUseBlock toolToUse : toolToUseList) {
			var functionCallId = toolToUse.toolUseId();
			var functionName = toolToUse.name();
			var functionArguments = toolToUse.input().unwrap();

			if (!this.functionCallbackRegister.containsKey(functionName)) {
				throw new IllegalStateException("No function callback found for function name: " + functionName);
			}

			String functionResponse = this.functionCallbackRegister.get(functionName)
				.call(ModelOptionsUtils.toJsonString(functionArguments));

			toolResults.add(ToolResultBlock.builder()
				.toolUseId(functionCallId)
				.status(ToolResultStatus.SUCCESS)
				.content(ToolResultContentBlock.builder().text(functionResponse).build())
				.build());
		}

		// Add the function response to the conversation.
		Message toolResultMessage = Message.builder()
			.content(toolResults.stream().map(toolResult -> ContentBlock.fromToolResult(toolResult)).toList())
			.role(ConversationRole.USER)
			.build();
		conversationHistory.add(toolResultMessage);

		// Recursively call chatCompletionWithTools until the model doesn't call a
		// functions anymore.
		return BedrockConverseRequest.from(previousRequest).withMessages(conversationHistory).build();
	}

	@Override
	protected List<Message> doGetUserMessages(BedrockConverseRequest request) {
		return request.messages();
	}

	@Override
	protected Message doGetToolResponseMessage(ChatResponse response) {
		Generation result = response.getResult();

		var metadata = (BedrockConverseChatGenerationMetadata) result.getMetadata();

		return metadata.getMessage();
	}

	@Override
	protected ChatResponse doChatCompletion(BedrockConverseRequest request) {
		return converseApi.converse(request);
	}

	@Override
	protected Flux<ChatResponse> doChatCompletionStream(BedrockConverseRequest request) {
		throw new UnsupportedOperationException("Streaming function calling is not supported.");
	}

	@Override
	protected boolean isToolFunctionCall(ChatResponse response) {
		Generation result = response.getResult();
		if (result == null) {
			return false;
		}

		return StopReason.fromValue(result.getMetadata().getFinishReason()) == StopReason.TOOL_USE;
	}

	/**
	 * Cohere command R models version.
	 */
	public enum CohereCommandRChatModel {

		/**
		 * cohere.command-r-v1:0
		 */
		COHERE_COMMAND_R_V1("cohere.command-r-v1:0"),

		/**
		 * cohere.command-r-plus-v1:0
		 */
		COHERE_COMMAND_R_PLUS_V1("cohere.command-r-plus-v1:0");

		private final String id;

		/**
		 * @return The model id.
		 */
		public String id() {
			return id;
		}

		CohereCommandRChatModel(String value) {
			this.id = value;
		}

	}

}
