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
package org.springframework.ai.chat.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Abstract base class for tool call support. Provides functionality for handling function
 * callbacks and executing functions.
 *
 * @author Christian Tzolov
 * @author Grogdunn
 * @author Thomas Vitale
 * @since 1.0.0
 */
public abstract class AbstractToolCallSupport {

	protected final static boolean IS_RUNTIME_CALL = true;

	/**
	 * The function callback register is used to resolve the function callbacks by name.
	 */
	protected final Map<String, FunctionCallback> functionCallbackRegister = new ConcurrentHashMap<>();

	/**
	 * The function callback context is used to resolve the function callbacks by name
	 * from the Spring context. It is optional and usually used with Spring
	 * auto-configuration.
	 */
	protected final FunctionCallbackContext functionCallbackContext;

	protected AbstractToolCallSupport(FunctionCallbackContext functionCallbackContext) {
		this(functionCallbackContext, FunctionCallingOptions.builder().build(), List.of());
	}

	protected AbstractToolCallSupport(FunctionCallbackContext functionCallbackContext,
			FunctionCallingOptions functionCallingOptions, List<FunctionCallback> toolFunctionCallbacks) {

		this.functionCallbackContext = functionCallbackContext;

		List<FunctionCallback> defaultFunctionCallbacks = merge(functionCallingOptions, toolFunctionCallbacks);

		if (!CollectionUtils.isEmpty(defaultFunctionCallbacks)) {
			this.functionCallbackRegister.putAll(defaultFunctionCallbacks.stream()
				.collect(ConcurrentHashMap::new, (m, v) -> m.put(v.getName(), v), ConcurrentHashMap::putAll));
		}
	}

	private static List<FunctionCallback> merge(FunctionCallingOptions funcitonOptions,
			List<FunctionCallback> toolFunctionCallbacks) {
		List<FunctionCallback> toolFunctionCallbacksCopy = new ArrayList<>();
		if (!CollectionUtils.isEmpty(toolFunctionCallbacks)) {
			toolFunctionCallbacksCopy.addAll(toolFunctionCallbacks);
		}

		if (!CollectionUtils.isEmpty(funcitonOptions.getFunctionCallbacks())) {
			toolFunctionCallbacksCopy.addAll(funcitonOptions.getFunctionCallbacks());
			// Make sure that that function callbacks are are registered directly to the
			// functionCallbackRegister and not passed in the default options.
			funcitonOptions.setFunctionCallbacks(List.of());
		}
		return toolFunctionCallbacksCopy;
	}

	public Map<String, FunctionCallback> getFunctionCallbackRegister() {
		return this.functionCallbackRegister;
	}

	/**
	 * Handle the runtime function callback configurations. Register the function
	 * callbacks
	 * @param runtimeFunctionOptions FunctionCallingOptions to handle.
	 * @return Set of function names to call.
	 */
	protected Set<String> runtimeFunctionCallbackConfigurations(FunctionCallingOptions runtimeFunctionOptions) {

		Set<String> enabledFunctionsToCall = new HashSet<>();

		if (runtimeFunctionOptions != null) {
			// Add the explicitly enabled functions.
			if (!CollectionUtils.isEmpty(runtimeFunctionOptions.getFunctions())) {
				enabledFunctionsToCall.addAll(runtimeFunctionOptions.getFunctions());
			}

			// Add the function callbacks to the register and automatically enable them.
			if (!CollectionUtils.isEmpty(runtimeFunctionOptions.getFunctionCallbacks())) {
				runtimeFunctionOptions.getFunctionCallbacks().stream().forEach(functionCallback -> {

					// Register the tool callback.
					this.functionCallbackRegister.put(functionCallback.getName(), functionCallback);

					// Automatically enable the function, usually from prompt callback.
					enabledFunctionsToCall.add(functionCallback.getName());
				});
			}
		}

		return enabledFunctionsToCall;
	}

	protected List<Message> handleToolCalls(Prompt prompt, ChatResponse response) {
		Optional<Generation> toolCallGeneration = response.getResults()
			.stream()
			.filter(g -> !CollectionUtils.isEmpty(g.getOutput().getToolCalls()))
			.findFirst();
		if (toolCallGeneration.isEmpty()) {
			throw new IllegalStateException("No tool call generation found in the response!");
		}
		AssistantMessage assistantMessage = toolCallGeneration.get().getOutput();
		ToolResponseMessage toolMessageResponse = this.executeFunctions(assistantMessage);
		return this.buildToolCallConversation(prompt.getInstructions(), assistantMessage, toolMessageResponse);
	}

	protected List<Message> buildToolCallConversation(List<Message> previousMessages, AssistantMessage assistantMessage,
			ToolResponseMessage toolResponseMessage) {
		List<Message> messages = new ArrayList<>(previousMessages);
		messages.add(assistantMessage);
		messages.add(toolResponseMessage);
		return messages;
	}

	/**
	 * Resolve the function callbacks by name. Retrieve them from the registry or try to
	 * resolve them from the Application Context.
	 * @param functionNames Name of function callbacks to retrieve.
	 * @return list of resolved FunctionCallbacks.
	 */
	protected List<FunctionCallback> resolveFunctionCallbacks(Set<String> functionNames) {

		List<FunctionCallback> retrievedFunctionCallbacks = new ArrayList<>();

		for (String functionName : functionNames) {
			if (!this.functionCallbackRegister.containsKey(functionName)) {

				if (this.functionCallbackContext != null) {
					FunctionCallback functionCallback = this.functionCallbackContext.getFunctionCallback(functionName,
							null);
					if (functionCallback != null) {
						this.functionCallbackRegister.put(functionName, functionCallback);
					}
					else {
						throw new IllegalStateException(
								"No function callback [" + functionName + "] fund in tht FunctionCallbackContext");
					}
				}
				else {
					throw new IllegalStateException("No function callback found for name: " + functionName);
				}
			}
			FunctionCallback functionCallback = this.functionCallbackRegister.get(functionName);

			retrievedFunctionCallbacks.add(functionCallback);
		}

		return retrievedFunctionCallbacks;
	}

	protected ToolResponseMessage executeFunctions(AssistantMessage assistantMessage) {

		List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();

		for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {

			var functionName = toolCall.name();
			String functionArguments = toolCall.arguments();

			if (!this.functionCallbackRegister.containsKey(functionName)) {
				throw new IllegalStateException("No function callback found for function name: " + functionName);
			}

			String functionResponse = this.functionCallbackRegister.get(functionName).call(functionArguments);

			toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), functionName, functionResponse));
		}

		return new ToolResponseMessage(toolResponses, Map.of());
	}

	protected boolean isToolCall(ChatResponse chatResponse, Set<String> toolCallFinishReasons) {
		Assert.isTrue(!CollectionUtils.isEmpty(toolCallFinishReasons), "Tool call finish reasons cannot be empty!");

		if (chatResponse == null) {
			return false;
		}

		var generations = chatResponse.getResults();
		if (CollectionUtils.isEmpty(generations)) {
			return false;
		}

		return generations.stream().anyMatch(g -> isToolCall(g, toolCallFinishReasons));
	}

	protected boolean isToolCall(Generation generation, Set<String> toolCallFinishReasons) {
		var finishReason = (generation.getMetadata().getFinishReason() != null)
				? generation.getMetadata().getFinishReason() : "";
		return !CollectionUtils.isEmpty(generation.getOutput().getToolCalls()) && toolCallFinishReasons.stream()
			.map(s -> s.toLowerCase())
			.toList()
			.contains(finishReason.toLowerCase());
	}

}
