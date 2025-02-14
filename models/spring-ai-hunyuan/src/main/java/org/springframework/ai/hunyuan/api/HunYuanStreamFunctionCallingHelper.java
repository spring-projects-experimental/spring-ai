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

package org.springframework.ai.hunyuan.api;

import java.util.ArrayList;
import java.util.List;

import org.springframework.util.CollectionUtils;
import org.springframework.ai.hunyuan.api.HunYuanApi.*;
import org.springframework.ai.hunyuan.api.HunYuanApi.ChatCompletionChunk.*;
import org.springframework.ai.hunyuan.api.HunYuanApi.ChatCompletionMessage.*;
import org.springframework.util.StringUtils;

/**
 * Helper class to support Streaming function calling. It can merge the streamed
 * ChatCompletionChunk in case of function calling message.
 *
 * @author Guo Junyu
 */
public class HunYuanStreamFunctionCallingHelper {

	public ChatCompletionChunk merge(ChatCompletionChunk previous, ChatCompletionChunk current) {

		if (previous == null) {
			return current;
		}

		String id = (current.id() != null ? current.id() : previous.id());
		Long created = (current.created() != null ? current.created() : previous.created());
		String note = (current.note() != null ? current.note() : previous.note());

		ChatCompletion.Choice previousChoice = (CollectionUtils.isEmpty(previous.choices()) ? null
				: previous.choices().get(0));
		ChatCompletion.Choice currentChoice = (CollectionUtils.isEmpty(current.choices()) ? null
				: current.choices().get(0));
		Usage usage = (current.usage() != null ? current.usage() : previous.usage());
		ChatCompletion.Choice choice = mergeChoice(previousChoice, currentChoice);
		List<ChatCompletion.Choice> chunkChoices = choice == null ? List.of() : List.of(choice);
		return new ChatCompletionChunk(id, null, created, note, chunkChoices, usage, null, null, null, null, null);
	}

	private ChatCompletion.Choice mergeChoice(ChatCompletion.Choice previous, ChatCompletion.Choice current) {
		if (previous == null) {
			return current;
		}

		String finishReason = (current.finishReason() != null ? current.finishReason() : previous.finishReason());
		Integer index = (current.index() != null ? current.index() : previous.index());

		ChatCompletion.ChatCompletionDelta delta = merge(previous.delta(), current.delta());
		return new ChatCompletion.Choice(index, null, finishReason, delta);
	}

	private ChatCompletion.ChatCompletionDelta merge(ChatCompletion.ChatCompletionDelta previous,
			ChatCompletion.ChatCompletionDelta current) {
		String content = (current.content() != null ? current.content()
				: "" + ((previous.content() != null) ? previous.content() : ""));
		Role role = (current.role() != null ? current.role() : previous.role());
		role = (role != null ? role : Role.assistant); // default to ASSISTANT (if null
		// String name = (current.name() != null ? current.name() : previous.name());
		List<ToolCall> toolCalls = new ArrayList<>();
		ToolCall lastPreviousTooCall = null;
		if (previous.toolCalls() != null) {
			lastPreviousTooCall = previous.toolCalls().get(previous.toolCalls().size() - 1);
			if (previous.toolCalls().size() > 1) {
				toolCalls.addAll(previous.toolCalls().subList(0, previous.toolCalls().size() - 1));
			}
		}
		if (current.toolCalls() != null) {
			if (current.toolCalls().size() > 1) {
				throw new IllegalStateException("Currently only one tool call is supported per message!");
			}
			var currentToolCall = current.toolCalls().iterator().next();
			if (!currentToolCall.id().equals(lastPreviousTooCall.id())) {
				if (lastPreviousTooCall != null) {
					toolCalls.add(lastPreviousTooCall);
				}
				toolCalls.add(currentToolCall);
			}
			else {
				toolCalls.add(merge(lastPreviousTooCall, currentToolCall));
			}
		}
		else {
			if (lastPreviousTooCall != null) {
				toolCalls.add(lastPreviousTooCall);
			}
		}
		return new ChatCompletion.ChatCompletionDelta(role, content, toolCalls);
	}

	private ToolCall merge(ToolCall previous, ToolCall current) {
		if (previous == null) {
			return current;
		}
		String id = (current.id() != null ? current.id() : previous.id());
		String type = (current.type() != null ? current.type() : previous.type());
		Integer index = (current.index() != null ? current.index() : previous.index());
		ChatCompletionFunction function = merge(previous.function(), current.function());
		return new ToolCall(id, type, index, function);
	}

	private ChatCompletionFunction merge(ChatCompletionFunction previous, ChatCompletionFunction current) {
		if (previous == null) {
			return current;
		}
		String name = (StringUtils.hasText(current.name()) ? current.name() : previous.name());
		StringBuilder arguments = new StringBuilder();
		if (StringUtils.hasText(previous.arguments())) {
			arguments.append(previous.arguments());
		}
		if (StringUtils.hasText(current.arguments())) {
			arguments.append(current.arguments());
		}
		return new ChatCompletionFunction(name, arguments.toString());
	}

	/**
	 * @param chatCompletion the ChatCompletionChunk to check
	 * @return true if the ChatCompletionChunk is a streaming tool function call.
	 */
	public boolean isStreamingToolFunctionCall(ChatCompletionChunk chatCompletion) {

		if (chatCompletion == null || CollectionUtils.isEmpty(chatCompletion.choices())) {
			return false;
		}

		var choice = chatCompletion.choices().get(0);
		if (choice == null || choice.delta() == null) {
			return false;
		}
		return !CollectionUtils.isEmpty(choice.delta().toolCalls());
	}

	/**
	 * @param chatCompletion the ChatCompletionChunk to check
	 * @return true if the ChatCompletionChunk is a streaming tool function call and it is
	 * the last one.
	 */
	public boolean isStreamingToolFunctionCallFinish(ChatCompletionChunk chatCompletion) {

		if (chatCompletion == null || CollectionUtils.isEmpty(chatCompletion.choices())) {
			return false;
		}

		var choice = chatCompletion.choices().get(0);
		if (choice == null || choice.delta() == null) {
			return false;
		}
		return choice.finishReason() == ChatCompletionFinishReason.TOOL_CALLS.getJsonValue();
	}

}
