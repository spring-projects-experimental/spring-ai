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

package org.springframework.ai.deepseek.api;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.springframework.ai.model.ChatModelDescription;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.springframework.ai.deepseek.api.common.DeepSeekConstants.*;

/**
 * Single class implementation of the DeepSeek Chat Completion API:
 * https://platform.deepseek.com/api-docs/api/create-chat-completion
 *
 * @author Geng Rong
 */
public class DeepSeekApi {

	public static final DeepSeekApi.ChatModel DEFAULT_CHAT_MODEL = ChatModel.DEEPSEEK_REASONER;

	private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

	private final String completionsPath;

	private final String betaFeaturePath;

	private final RestClient restClient;

	private final WebClient webClient;

	private DeepSeekStreamFunctionCallingHelper chunkMerger = new DeepSeekStreamFunctionCallingHelper();

	/**
	 * Create a new chat completion api with base URL set to https://api.deepseek.com
	 * @param apiKey DeepSeek apiKey.
	 */
	public DeepSeekApi(String apiKey) {
		this(DEFAULT_BASE_URL, apiKey);
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param apiKey DeepSeek apiKey.
	 */
	public DeepSeekApi(String baseUrl, String apiKey) {
		this(baseUrl, apiKey, RestClient.builder(), WebClient.builder());
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param apiKey DeepSeek apiKey.
	 * @param restClientBuilder RestClient builder.
	 * @param webClientBuilder WebClient builder.
	 */
	public DeepSeekApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder) {
		this(baseUrl, apiKey, restClientBuilder, webClientBuilder, RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param apiKey DeepSeek apiKey.
	 * @param restClientBuilder RestClient builder.
	 * @param webClientBuilder WebClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public DeepSeekApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder,
			WebClient.Builder webClientBuilder, ResponseErrorHandler responseErrorHandler) {
		this(baseUrl, apiKey, DEFAULT_COMPLETIONS_PATH, DEFAULT_BETA_PATH, restClientBuilder, webClientBuilder,
				responseErrorHandler);
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param apiKey DeepSeek apiKey.
	 * @param completionsPath the path to the chat completions endpoint.
	 * @param betaFeaturePath the path to the beta feature endpoint.
	 * @param restClientBuilder RestClient builder.
	 * @param webClientBuilder WebClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public DeepSeekApi(String baseUrl, String apiKey, String completionsPath, String betaFeaturePath,
			RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder,
			ResponseErrorHandler responseErrorHandler) {
		this(baseUrl, apiKey, CollectionUtils.toMultiValueMap(Map.of()), completionsPath, betaFeaturePath,
				restClientBuilder, webClientBuilder, responseErrorHandler);
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param apiKey DeepSeek apiKey.
	 * @param headers the http headers to use.
	 * @param completionsPath the path to the chat completions endpoint.
	 * @param betaFeaturePath the path to the beta feature endpoint.
	 * @param restClientBuilder RestClient builder.
	 * @param webClientBuilder WebClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public DeepSeekApi(String baseUrl, String apiKey, MultiValueMap<String, String> headers, String completionsPath,
			String betaFeaturePath, RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder,
			ResponseErrorHandler responseErrorHandler) {

		Assert.hasText(completionsPath, "Completions Path must not be null");
		Assert.hasText(betaFeaturePath, "Beta feature path must not be null");
		Assert.notNull(headers, "Headers must not be null");

		this.completionsPath = completionsPath;
		this.betaFeaturePath = betaFeaturePath;
		// @formatter:off
		Consumer<HttpHeaders> finalHeaders = h -> {
			h.setBearerAuth(apiKey);
			h.setContentType(MediaType.APPLICATION_JSON);
			h.addAll(headers);
		};
		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(finalHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();

		this.webClient = webClientBuilder
			.baseUrl(baseUrl)
			.defaultHeaders(finalHeaders)
			.build(); // @formatter:on
	}

	/**
	 * Creates a model response for the given chat conversation.
	 * @param chatRequest The chat completion request.
	 * @return Entity response with {@link ChatCompletion} as a body and HTTP status code
	 * and headers.
	 */
	public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest chatRequest) {

		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(!chatRequest.stream(), "Request must set the stream property to false.");

		return this.restClient.post()
			.uri(this.completionsPath)
			.body(chatRequest)
			.retrieve()
			.toEntity(ChatCompletion.class);
	}

	/**
	 * Creates a streaming chat response for the given chat conversation.
	 * @param chatRequest The chat completion request. Must have the stream property set
	 * to true.
	 * @return Returns a {@link Flux} stream from chat completion chunks.
	 */
	public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest chatRequest) {
		return chatCompletionStream(chatRequest, new LinkedMultiValueMap<>());
	}

	/**
	 * Creates a streaming chat response for the given chat conversation.
	 * @param chatRequest The chat completion request. Must have the stream property set
	 * to true.
	 * @param additionalHttpHeader Optional, additional HTTP headers to be added to the
	 * request.
	 * @return Returns a {@link Flux} stream from chat completion chunks.
	 */
	public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest chatRequest,
			MultiValueMap<String, String> additionalHttpHeader) {

		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(chatRequest.stream(), "Request must set the stream property to true.");

		AtomicBoolean isInsideTool = new AtomicBoolean(false);

		return this.webClient.post()
			.uri(this.completionsPath)
			.headers(headers -> headers.addAll(additionalHttpHeader))
			.body(Mono.just(chatRequest), ChatCompletionRequest.class)
			.retrieve()
			.bodyToFlux(String.class)
			// cancels the flux stream after the "[DONE]" is received.
			.takeUntil(SSE_DONE_PREDICATE)
			// filters out the "[DONE]" message.
			.filter(SSE_DONE_PREDICATE.negate())
			.map(content -> ModelOptionsUtils.jsonToObject(content, ChatCompletionChunk.class))
			// Detect is the chunk is part of a streaming function call.
			.map(chunk -> {
				if (this.chunkMerger.isStreamingToolFunctionCall(chunk)) {
					isInsideTool.set(true);
				}
				return chunk;
			})
			// Group all chunks belonging to the same function call.
			// Flux<ChatCompletionChunk> -> Flux<Flux<ChatCompletionChunk>>
			.windowUntil(chunk -> {
				if (isInsideTool.get() && this.chunkMerger.isStreamingToolFunctionCallFinish(chunk)) {
					isInsideTool.set(false);
					return true;
				}
				return !isInsideTool.get();
			})
			// Merging the window chunks into a single chunk.
			// Reduce the inner Flux<ChatCompletionChunk> window into a single
			// Mono<ChatCompletionChunk>,
			// Flux<Flux<ChatCompletionChunk>> -> Flux<Mono<ChatCompletionChunk>>
			.concatMapIterable(window -> {
				Mono<ChatCompletionChunk> monoChunk = window.reduce(
						new ChatCompletionChunk(null, null, null, null, null, null, null, null),
						(previous, current) -> this.chunkMerger.merge(previous, current));
				return List.of(monoChunk);
			})
			// Flux<Mono<ChatCompletionChunk>> -> Flux<ChatCompletionChunk>
			.flatMap(mono -> mono);
	}

	/**
	 * DeepSeek Chat Completion
	 * <a href="https://api-docs.deepseek.com/quick_start/pricing">Models</a>
	 */
	public enum ChatModel implements ChatModelDescription {

		/**
		 * The backend model of deepseek-chat has been updated to DeepSeek-V3, you can
		 * access DeepSeek-V3 without modification to the model name. The open-source
		 * DeepSeek-V3 model supports 128K context window, and DeepSeek-V3 on API/Web
		 * supports 64K context window. Context window: 64k tokens
		 */
		DEEPSEEK_CHAT("deepseek-chat"),

		/**
		 * deepseek-reasoner is a reasoning model developed by DeepSeek. Before delivering
		 * the final answer, the model first generates a Chain of Thought (CoT) to enhance
		 * the accuracy of its responses. Our API provides users with access to the CoT
		 * content generated by deepseek-reasoner, enabling them to view, display, and
		 * distill it.
		 */
		DEEPSEEK_REASONER("deepseek-reasoner");

		public final String value;

		ChatModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

		@Override
		public String getName() {
			return value;
		}

	}

	/**
	 * The reason the model stopped generating tokens.
	 */
	public enum ChatCompletionFinishReason {

		/**
		 * The model hit a natural stop point or a provided stop sequence.
		 */
		@JsonProperty("stop")
		STOP,
		/**
		 * The maximum number of tokens specified in the request was reached.
		 */
		@JsonProperty("length")
		LENGTH,
		/**
		 * The content was omitted due to a flag from our content filters.
		 */
		@JsonProperty("content_filter")
		CONTENT_FILTER,
		/**
		 * The model called a tool.
		 */
		@JsonProperty("tool_calls")
		TOOL_CALLS,
		/**
		 * Only for compatibility with Mistral AI API.
		 */
		@JsonProperty("tool_call")
		TOOL_CALL

	}

	/**
	 * Represents a tool the model may call. Currently, only functions are supported as a
	 * tool.
	 */
	@JsonInclude(Include.NON_NULL)
	public static class FunctionTool {

		/**
		 * The type of the tool. Currently, only 'function' is supported.
		 */
		@JsonProperty("type")
		private Type type = Type.FUNCTION;

		/**
		 * The function definition.
		 */
		@JsonProperty("function")
		private Function function;

		public FunctionTool() {

		}

		/**
		 * Create a tool of type 'function' and the given function definition.
		 * @param type the tool type
		 * @param function function definition
		 */
		public FunctionTool(Type type, Function function) {
			this.type = type;
			this.function = function;
		}

		/**
		 * Create a tool of type 'function' and the given function definition.
		 * @param function function definition.
		 */
		public FunctionTool(Function function) {
			this(Type.FUNCTION, function);
		}

		public Type getType() {
			return this.type;
		}

		public Function getFunction() {
			return this.function;
		}

		public void setType(Type type) {
			this.type = type;
		}

		public void setFunction(Function function) {
			this.function = function;
		}

		/**
		 * Create a tool of type 'function' and the given function definition.
		 */
		public enum Type {

			/**
			 * Function tool type.
			 */
			@JsonProperty("function")
			FUNCTION

		}

		/**
		 * Function definition.
		 */
		@JsonInclude(Include.NON_NULL)
		public static class Function {

			@JsonProperty("description")
			private String description;

			@JsonProperty("name")
			private String name;

			@JsonProperty("parameters")
			private Map<String, Object> parameters;

			@JsonProperty("strict")
			Boolean strict;

			@JsonIgnore
			private String jsonSchema;

			/**
			 * NOTE: Required by Jackson, JSON deserialization!
			 */
			@SuppressWarnings("unused")
			private Function() {
			}

			/**
			 * Create tool function definition.
			 * @param description A description of what the function does, used by the
			 * model to choose when and how to call the function.
			 * @param name The name of the function to be called. Must be a-z, A-Z, 0-9,
			 * or contain underscores and dashes, with a maximum length of 64.
			 * @param parameters The parameters the functions accepts, described as a JSON
			 * Schema object. To describe a function that accepts no parameters, provide
			 * the value {"type": "object", "properties": {}}.
			 * @param strict Whether to enable strict schema adherence when generating the
			 * function call. If set to true, the model will follow the exact schema
			 * defined in the parameters field. Only a subset of JSON Schema is supported
			 * when strict is true.
			 */
			public Function(String description, String name, Map<String, Object> parameters, Boolean strict) {
				this.description = description;
				this.name = name;
				this.parameters = parameters;
				this.strict = strict;
			}

			/**
			 * Create tool function definition.
			 * @param description tool function description.
			 * @param name tool function name.
			 * @param jsonSchema tool function schema as json.
			 */
			public Function(String description, String name, String jsonSchema) {
				this(description, name, ModelOptionsUtils.jsonToMap(jsonSchema), null);
			}

			public String getDescription() {
				return this.description;
			}

			public String getName() {
				return this.name;
			}

			public Map<String, Object> getParameters() {
				return this.parameters;
			}

			public void setDescription(String description) {
				this.description = description;
			}

			public void setName(String name) {
				this.name = name;
			}

			public void setParameters(Map<String, Object> parameters) {
				this.parameters = parameters;
			}

			public Boolean getStrict() {
				return this.strict;
			}

			public void setStrict(Boolean strict) {
				this.strict = strict;
			}

			public String getJsonSchema() {
				return this.jsonSchema;
			}

			public void setJsonSchema(String jsonSchema) {
				this.jsonSchema = jsonSchema;
				if (jsonSchema != null) {
					this.parameters = ModelOptionsUtils.jsonToMap(jsonSchema);
				}
			}

		}

	}

	/**
	 * Creates a model response for the given chat conversation.
	 *
	 * @param messages A list of messages comprising the conversation so far.
	 * @param model ID of the model to use.
	 * @param frequencyPenalty Number between -2.0 and 2.0. Positive values penalize new
	 * tokens based on their existing frequency in the text so far, decreasing the model's
	 * likelihood to repeat the same line verbatim.
	 * @param maxTokens The maximum number of tokens that can be generated in the chat
	 * completion. This value can be used to control costs for text generated via API.
	 * This value is now deprecated in favor of max_completion_tokens, and is not
	 * compatible with o1 series models.
	 * @param presencePenalty Number between -2.0 and 2.0. Positive values penalize new
	 * tokens based on whether they appear in the text so far, increasing the model's
	 * likelihood to talk about new topics.
	 * @param responseFormat An object specifying the format that the model must output.
	 * Setting to { "type": "json_object" } enables JSON mode, which guarantees the
	 * message the model generates is valid JSON.
	 * @param stop A string or a list containing up to 4 strings, upon encountering these
	 * words, the API will cease generating more tokens.
	 * @param stream If set, partial message deltas will be sent.Tokens will be sent as
	 * data-only server-sent events as they become available, with the stream terminated
	 * by a data: [DONE] message.
	 * @param temperature What sampling temperature to use, between 0 and 2. Higher values
	 * like 0.8 will make the output more random, while lower values like 0.2 will make it
	 * more focused and deterministic. We generally recommend altering this or top_p but
	 * not both.
	 * @param topP An alternative to sampling with temperature, called nucleus sampling,
	 * where the model considers the results of the tokens with top_p probability mass. So
	 * 0.1 means only the tokens comprising the top 10% probability mass are considered.
	 * We generally recommend altering this or temperature but not both.
	 * @param logprobs Whether to return log probabilities of the output tokens or not. If
	 * true, returns the log probabilities of each output token returned in the content of
	 * message.
	 * @param topLogprobs An integer between 0 and 20 specifying the number of most likely
	 * tokens to return at each token position, each with an associated log probability.
	 * logprobs must be set to true if this parameter is used.
	 * @param tools A list of tools the model may call. Currently, only functions are
	 * supported as a tool. Use this to provide a list of functions the model may generate
	 * JSON inputs for.
	 * @param toolChoice Controls which (if any) function is called by the model. none
	 * means the model will not call a function and instead generates a message. auto
	 * means the model can pick between generating a message or calling a function.
	 * Specifying a particular function via {"type: "function", "function": {"name":
	 * "my_function"}} forces the model to call that function. none is the default when no
	 * functions are present. auto is the default if functions are present. Use the
	 * {@link ToolChoiceBuilder} to create the tool choice value.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionRequest(// @formatter:off
			@JsonProperty("messages") List<ChatCompletionMessage> messages,
			@JsonProperty("model") String model,
			@JsonProperty("frequency_penalty") Double frequencyPenalty,
			@JsonProperty("max_tokens") Integer maxTokens, // Use maxCompletionTokens instead
			@JsonProperty("presence_penalty") Double presencePenalty,
			@JsonProperty("response_format") ResponseFormat responseFormat,
			@JsonProperty("stop") List<String> stop,
			@JsonProperty("stream") Boolean stream,
			@JsonProperty("temperature") Double temperature,
			@JsonProperty("top_p") Double topP,
			@JsonProperty("logprobs") Boolean logprobs,
            @JsonProperty("top_logprobs") Integer topLogprobs,
			@JsonProperty("tools") List<FunctionTool> tools,
			@JsonProperty("tool_choice") Object toolChoice)
	{


		/**
		 * Shortcut constructor for a chat completion request with the given messages for streaming.
		 *
		 * @param messages A list of messages comprising the conversation so far.
		 * @param stream If set, partial message deltas will be sent.Tokens will be sent as data-only server-sent events
		 * as they become available, with the stream terminated by a data: [DONE] message.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, Boolean stream) {
            this(messages, null, null, null, null, null,
                    null, stream, null, null, null, null, null, null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages, model and temperature.
		 *
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use.
		 * @param temperature What sampling temperature to use, between 0 and 1.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Double temperature) {
            this(messages, model, null,
                    null, null, null, null,  false,  temperature, null,
                    null, null, null,null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages, model, temperature and control for streaming.
		 *
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use.
		 * @param temperature What sampling temperature to use, between 0 and 1.
		 * @param stream If set, partial message deltas will be sent.Tokens will be sent as data-only server-sent events
		 * as they become available, with the stream terminated by a data: [DONE] message.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Double temperature, boolean stream) {
            this(messages, model, null,
                    null, null, null, null,  stream,  temperature, null,
                    null, null, null,null);
		}

		/**
		 * Shortcut constructor for a chat completion request with the given messages, model, tools and tool choice.
		 * Streaming is set to false, temperature to 0.8 and all other parameters are null.
		 *
		 * @param messages A list of messages comprising the conversation so far.
		 * @param model ID of the model to use.
		 * @param tools A list of tools the model may call. Currently, only functions are supported as a tool.
		 * @param toolChoice Controls which (if any) function is called by the model.
		 */
		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model,
				List<FunctionTool> tools, Object toolChoice) {
            this(messages, model, null,
                    null, null, null, null,  false,  0.8, null,
                    null, null, tools, toolChoice );
		}

		/**
		 * Helper factory that creates a tool_choice of type 'none', 'auto' or selected function by name.
		 */
		public static class ToolChoiceBuilder {
			/**
			 * Model can pick between generating a message or calling a function.
			 */
			public static final String AUTO = "auto";
			/**
			 * Model will not call a function and instead generates a message
			 */
			public static final String NONE = "none";

			/**
			 * Specifying a particular function forces the model to call that function.
			 */
			public static Object FUNCTION(String functionName) {
				return Map.of("type", "function", "function", Map.of("name", functionName));
			}
		}

		/**
		 * Parameters for audio output. Required when audio output is requested with outputModalities: ["audio"].
		 * @param voice Specifies the voice type.
		 * @param format Specifies the output audio format.
		 */
		@JsonInclude(Include.NON_NULL)
		public record AudioParameters(
				@JsonProperty("voice") Voice voice,
				@JsonProperty("format") AudioResponseFormat format) {

			/**
			 * Specifies the voice type.
			 */
			public enum Voice {
				/** Alloy voice */
				@JsonProperty("alloy") ALLOY,
				/** Echo voice */
				@JsonProperty("echo") ECHO,
				/** Fable voice */
				@JsonProperty("fable") FABLE,
				/** Onyx voice */
				@JsonProperty("onyx") ONYX,
				/** Nova voice */
				@JsonProperty("nova") NOVA,
				/** Shimmer voice */
				@JsonProperty("shimmer") SHIMMER
			}

			/**
			 * Specifies the output audio format.
			 */
			public enum AudioResponseFormat {
				/** MP3 format */
				@JsonProperty("mp3") MP3,
				/** FLAC format */
				@JsonProperty("flac") FLAC,
				/** OPUS format */
				@JsonProperty("opus") OPUS,
				/** PCM16 format */
				@JsonProperty("pcm16") PCM16,
				/** WAV format */
				@JsonProperty("wav") WAV
			}
		}

		/**
		 * @param includeUsage If set, an additional chunk will be streamed
		 * before the data: [DONE] message. The usage field on this chunk
		 * shows the token usage statistics for the entire request, and
		 * the choices field will always be an empty array. All other chunks
		 * will also include a usage field, but with a null value.
		 */
		@JsonInclude(Include.NON_NULL)
		public record StreamOptions(
				@JsonProperty("include_usage") Boolean includeUsage) {

			public static StreamOptions INCLUDE_USAGE = new StreamOptions(true);
		}
	} // @formatter:on

	/**
	 * Message comprising the conversation.
	 *
	 * @param rawContent The contents of the message. Can be either a {@link MediaContent}
	 * or a {@link String}. The response message content is always a {@link String}.
	 * @param role The role of the messages author. Could be one of the {@link Role}
	 * types.
	 * @param name An optional name for the participant. Provides the model information to
	 * differentiate between participants of the same role. In case of Function calling,
	 * the name is the function name that the message is responding to.
	 * @param toolCallId Tool call that this message is responding to. Only applicable for
	 * the {@link Role#TOOL} role and null otherwise.
	 * @param toolCalls The tool calls generated by the model, such as function calls.
	 * Applicable only for {@link Role#ASSISTANT} role and null otherwise.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionMessage(// @formatter:off
			@JsonProperty("content") Object rawContent,
			@JsonProperty("role") Role role,
			@JsonProperty("name") String name,
			@JsonProperty("tool_call_id") String toolCallId,
			@JsonProperty("tool_calls")
			@JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<ToolCall> toolCalls
        ) { // @formatter:on

		/**
		 * Create a chat completion message with the given content and role. All other
		 * fields are null.
		 * @param content The contents of the message.
		 * @param role The role of the author of this message.
		 */
		public ChatCompletionMessage(Object content, Role role) {
			this(content, role, null, null, null);

		}

		/**
		 * Get message content as String.
		 */
		public String content() {
			if (this.rawContent == null) {
				return null;
			}
			if (this.rawContent instanceof String text) {
				return text;
			}
			throw new IllegalStateException("The content is not a string!");
		}

		/**
		 * The role of the author of this message.
		 */
		public enum Role {

			/**
			 * System message.
			 */
			@JsonProperty("system")
			SYSTEM,
			/**
			 * User message.
			 */
			@JsonProperty("user")
			USER,
			/**
			 * Assistant message.
			 */
			@JsonProperty("assistant")
			ASSISTANT,
			/**
			 * Tool message.
			 */
			@JsonProperty("tool")
			TOOL

		}

		/**
		 * An array of content parts with a defined type. Each MediaContent can be of
		 * either "text", "image_url", or "input_audio" type. Only one option allowed.
		 *
		 * @param type Content type, each can be of type text or image_url.
		 * @param text The text content of the message.
		 * @param imageUrl The image content of the message. You can pass multiple images
		 * by adding multiple image_url content parts. Image input is only supported when
		 * using the gpt-4-visual-preview model.
		 * @param inputAudio Audio content part.
		 */
		@JsonInclude(Include.NON_NULL)
		public record MediaContent(// @formatter:off
			@JsonProperty("type") String type,
			@JsonProperty("text") String text,
			@JsonProperty("image_url") ImageUrl imageUrl,
			@JsonProperty("input_audio") InputAudio inputAudio) { // @formatter:on

			/**
			 * Shortcut constructor for a text content.
			 * @param text The text content of the message.
			 */
			public MediaContent(String text) {
				this("text", text, null, null);
			}

			/**
			 * Shortcut constructor for an image content.
			 * @param imageUrl The image content of the message.
			 */
			public MediaContent(ImageUrl imageUrl) {
				this("image_url", null, imageUrl, null);
			}

			/**
			 * Shortcut constructor for an audio content.
			 * @param inputAudio The audio content of the message.
			 */
			public MediaContent(InputAudio inputAudio) {
				this("input_audio", null, null, inputAudio);
			}

			/**
			 * @param data Base64 encoded audio data.
			 * @param format The format of the encoded audio data. Currently supports
			 * "wav" and "mp3".
			 */
			@JsonInclude(Include.NON_NULL)
			public record InputAudio(// @formatter:off
				@JsonProperty("data") String data,
				@JsonProperty("format") Format format) {

				public enum Format {
					/** MP3 audio format */
					@JsonProperty("mp3") MP3,
					/** WAV audio format */
					@JsonProperty("wav") WAV
				} // @formatter:on
			}

			/**
			 * Shortcut constructor for an image content.
			 *
			 * @param url Either a URL of the image or the base64 encoded image data. The
			 * base64 encoded image data must have a special prefix in the following
			 * format: "data:{mimetype};base64,{base64-encoded-image-data}".
			 * @param detail Specifies the detail level of the image.
			 */
			@JsonInclude(Include.NON_NULL)
			public record ImageUrl(@JsonProperty("url") String url, @JsonProperty("detail") String detail) {

				public ImageUrl(String url) {
					this(url, null);
				}

			}

		}

		/**
		 * The relevant tool call.
		 *
		 * @param index The index of the tool call in the list of tool calls. Required in
		 * case of streaming.
		 * @param id The ID of the tool call. This ID must be referenced when you submit
		 * the tool outputs in using the Submit tool outputs to run endpoint.
		 * @param type The type of tool call the output is required for. For now, this is
		 * always function.
		 * @param function The function definition.
		 */
		@JsonInclude(Include.NON_NULL)
		public record ToolCall(// @formatter:off
				@JsonProperty("index") Integer index,
				@JsonProperty("id") String id,
				@JsonProperty("type") String type,
				@JsonProperty("function") ChatCompletionFunction function) { // @formatter:on

			public ToolCall(String id, String type, ChatCompletionFunction function) {
				this(null, id, type, function);
			}

		}

		/**
		 * The function definition.
		 *
		 * @param name The name of the function.
		 * @param arguments The arguments that the model expects you to pass to the
		 * function.
		 */
		@JsonInclude(Include.NON_NULL)
		public record ChatCompletionFunction(// @formatter:off
				@JsonProperty("name") String name,
				@JsonProperty("arguments") String arguments) { // @formatter:on
		}
	}

	/**
	 * Represents a chat completion response returned by model, based on the provided
	 * input.
	 *
	 * @param id A unique identifier for the chat completion.
	 * @param choices A list of chat completion choices. Can be more than one if n is
	 * greater than 1.
	 * @param created The Unix timestamp (in seconds) of when the chat completion was
	 * created.
	 * @param model The model used for the chat completion.
	 * @param systemFingerprint This fingerprint represents the backend configuration that
	 * the model runs with. Can be used in conjunction with the seed request parameter to
	 * understand when backend changes have been made that might impact determinism.
	 * @param object The object type, which is always chat.completion.
	 * @param usage Usage statistics for the completion request.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletion(// @formatter:off
			@JsonProperty("id") String id,
			@JsonProperty("choices") List<Choice> choices,
			@JsonProperty("created") Long created,
			@JsonProperty("model") String model,
			@JsonProperty("system_fingerprint") String systemFingerprint,
			@JsonProperty("object") String object,
			@JsonProperty("usage") Usage usage
	) { // @formatter:on

		/**
		 * Chat completion choice.
		 *
		 * @param finishReason The reason the model stopped generating tokens.
		 * @param index The index of the choice in the list of choices.
		 * @param message A chat completion message generated by the model.
		 * @param logprobs Log probability information for the choice.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Choice(// @formatter:off
				@JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
				@JsonProperty("index") Integer index,
				@JsonProperty("message") ChatCompletionMessage message,
				@JsonProperty("logprobs") LogProbs logprobs) { // @formatter:on
		}

	}

	/**
	 * Log probability information for the choice.
	 *
	 * @param content A list of message content tokens with log probability information.
	 * @param refusal A list of message refusal tokens with log probability information.
	 */
	@JsonInclude(Include.NON_NULL)
	public record LogProbs(@JsonProperty("content") List<Content> content,
			@JsonProperty("refusal") List<Content> refusal) {

		/**
		 * Message content tokens with log probability information.
		 *
		 * @param token The token.
		 * @param logprob The log probability of the token.
		 * @param probBytes A list of integers representing the UTF-8 bytes representation
		 * of the token. Useful in instances where characters are represented by multiple
		 * tokens and their byte representations must be combined to generate the correct
		 * text representation. Can be null if there is no bytes representation for the
		 * token.
		 * @param topLogprobs List of the most likely tokens and their log probability, at
		 * this token position. In rare cases, there may be fewer than the number of
		 * requested top_logprobs returned.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Content(// @formatter:off
				@JsonProperty("token") String token,
				@JsonProperty("logprob") Float logprob,
				@JsonProperty("bytes") List<Integer> probBytes,
				@JsonProperty("top_logprobs") List<TopLogProbs> topLogprobs) { // @formatter:on

			/**
			 * The most likely tokens and their log probability, at this token position.
			 *
			 * @param token The token.
			 * @param logprob The log probability of the token.
			 * @param probBytes A list of integers representing the UTF-8 bytes
			 * representation of the token. Useful in instances where characters are
			 * represented by multiple tokens and their byte representations must be
			 * combined to generate the correct text representation. Can be null if there
			 * is no bytes representation for the token.
			 */
			@JsonInclude(Include.NON_NULL)
			public record TopLogProbs(// @formatter:off
					@JsonProperty("token") String token,
					@JsonProperty("logprob") Float logprob,
					@JsonProperty("bytes") List<Integer> probBytes) { // @formatter:on
			}

		}

	}

	// Embeddings API

	/**
	 * Usage statistics for the completion request.
	 *
	 * @param completionTokens Number of tokens in the generated completion. Only
	 * applicable for completion requests.
	 * @param promptTokens Number of tokens in the prompt.
	 * @param totalTokens Total number of tokens used in the request (prompt +
	 * completion).
	 * @param promptTokensDetails Breakdown of tokens used in the prompt.
	 * @param completionTokenDetails Breakdown of tokens used in a completion.
	 */
	@JsonInclude(Include.NON_NULL)
	public record Usage(// @formatter:off
		@JsonProperty("completion_tokens") Integer completionTokens,
		@JsonProperty("prompt_tokens") Integer promptTokens,
		@JsonProperty("total_tokens") Integer totalTokens,
		@JsonProperty("prompt_tokens_details") PromptTokensDetails promptTokensDetails,
		@JsonProperty("completion_tokens_details") CompletionTokenDetails completionTokenDetails) { // @formatter:on

		public Usage(Integer completionTokens, Integer promptTokens, Integer totalTokens) {
			this(completionTokens, promptTokens, totalTokens, null, null);
		}

		/**
		 * Breakdown of tokens used in the prompt
		 *
		 * @param audioTokens Audio input tokens present in the prompt.
		 * @param cachedTokens Cached tokens present in the prompt.
		 */
		@JsonInclude(Include.NON_NULL)
		public record PromptTokensDetails(// @formatter:off
			@JsonProperty("audio_tokens") Integer audioTokens,
			@JsonProperty("cached_tokens") Integer cachedTokens) { // @formatter:on
		}

		/**
		 * Breakdown of tokens used in a completion.
		 *
		 * @param reasoningTokens Number of tokens generated by the model for reasoning.
		 * @param acceptedPredictionTokens Number of tokens generated by the model for
		 * accepted predictions.
		 * @param audioTokens Number of tokens generated by the model for audio.
		 * @param rejectedPredictionTokens Number of tokens generated by the model for
		 * rejected predictions.
		 */
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record CompletionTokenDetails(// @formatter:off
			@JsonProperty("reasoning_tokens") Integer reasoningTokens,
			@JsonProperty("accepted_prediction_tokens") Integer acceptedPredictionTokens,
			@JsonProperty("audio_tokens") Integer audioTokens,
			@JsonProperty("rejected_prediction_tokens") Integer rejectedPredictionTokens) { // @formatter:on
		}
	}

	/**
	 * Represents a streamed chunk of a chat completion response returned by model, based
	 * on the provided input.
	 *
	 * @param id A unique identifier for the chat completion. Each chunk has the same ID.
	 * @param choices A list of chat completion choices. Can be more than one if n is
	 * greater than 1.
	 * @param created The Unix timestamp (in seconds) of when the chat completion was
	 * created. Each chunk has the same timestamp.
	 * @param model The model used for the chat completion.
	 * @param serviceTier The service tier used for processing the request. This field is
	 * only included if the service_tier parameter is specified in the request.
	 * @param systemFingerprint This fingerprint represents the backend configuration that
	 * the model runs with. Can be used in conjunction with the seed request parameter to
	 * understand when backend changes have been made that might impact determinism.
	 * @param object The object type, which is always 'chat.completion.chunk'.
	 * @param usage Usage statistics for the completion request. Present in the last chunk
	 * only if the StreamOptions.includeUsage is set to true.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionChunk(// @formatter:off
			@JsonProperty("id") String id,
			@JsonProperty("choices") List<ChunkChoice> choices,
			@JsonProperty("created") Long created,
			@JsonProperty("model") String model,
			@JsonProperty("service_tier") String serviceTier,
			@JsonProperty("system_fingerprint") String systemFingerprint,
			@JsonProperty("object") String object,
			@JsonProperty("usage") Usage usage) { // @formatter:on

		/**
		 * Chat completion choice.
		 *
		 * @param finishReason The reason the model stopped generating tokens.
		 * @param index The index of the choice in the list of choices.
		 * @param delta A chat completion delta generated by streamed model responses.
		 * @param logprobs Log probability information for the choice.
		 */
		@JsonInclude(Include.NON_NULL)
		public record ChunkChoice(// @formatter:off
				@JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
				@JsonProperty("index") Integer index,
				@JsonProperty("delta") ChatCompletionMessage delta,
				@JsonProperty("logprobs") LogProbs logprobs) { // @formatter:on

		}

	}

}
