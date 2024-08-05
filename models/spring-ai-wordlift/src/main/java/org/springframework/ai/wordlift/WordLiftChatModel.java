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
package org.springframework.ai.wordlift;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.observation.*;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.observation.AiOperationMetadata;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.wordlift.api.WordLiftApi;
import org.springframework.ai.wordlift.api.WordLiftApi.ChatCompletion;
import org.springframework.ai.wordlift.api.WordLiftApi.ChatCompletion.Choice;
import org.springframework.ai.wordlift.api.WordLiftApi.ChatCompletionMessage;
import org.springframework.ai.wordlift.api.WordLiftApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.wordlift.api.WordLiftApi.ChatCompletionMessage.MediaContent;
import org.springframework.ai.wordlift.api.WordLiftApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.wordlift.api.WordLiftApi.ChatCompletionRequest;
import org.springframework.ai.wordlift.metadata.WordLiftUsage;
import org.springframework.ai.wordlift.metadata.support.WordLiftResponseHeaderExtractor;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * {@link ChatModel} and {@link StreamingChatModel} implementation for {@link WordLiftApi}.
 * <p>
 * This code is an adaptation of the OpenAIChatModel code to which WordLift abides with slight differences.
 *
 * @author David Riccitelli
 * @see ChatModel
 * @see StreamingChatModel
 * @see WordLiftApi
 */
public class WordLiftChatModel
  extends AbstractToolCallSupport
  implements ChatModel {

  private static final Logger logger = LoggerFactory.getLogger(
    WordLiftChatModel.class
  );

  private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION =
    new DefaultChatModelObservationConvention();

  /**
   * The default options used for the chat completion requests.
   */
  private final WordLiftChatOptions defaultOptions;

  /**
   * The retry template used to retry the WordLift API calls.
   */
  private final RetryTemplate retryTemplate;

  /**
   * Low-level access to the WordLift API.
   */
  private final WordLiftApi wordliftApi;

  /**
   * Observation registry used for instrumentation.
   */
  private final ObservationRegistry observationRegistry;

  /**
   * Conventions to use for generating observations.
   */
  private ChatModelObservationConvention observationConvention =
    DEFAULT_OBSERVATION_CONVENTION;

  /**
   * Creates an instance of the OpenAiChatModel.
   *
   * @param wordliftApi The WordLiftApi instance to be used for interacting with the OpenAI
   *                    Chat API.
   * @throws IllegalArgumentException if openAiApi is null
   */
  public WordLiftChatModel(WordLiftApi wordliftApi) {
    this(
      wordliftApi,
      WordLiftChatOptions
        .builder()
        .withModel(WordLiftApi.DEFAULT_CHAT_MODEL)
        .withTemperature(0.7f)
        .build()
    );
  }

  /**
   * Initializes an instance of the OpenAiChatModel.
   *
   * @param wordliftApi The WordLiftApi instance to be used for interacting with the OpenAI
   *                    Chat API.
   * @param options     The OpenAiChatOptions to configure the chat model.
   */
  public WordLiftChatModel(
    WordLiftApi wordliftApi,
    WordLiftChatOptions options
  ) {
    this(wordliftApi, options, null, RetryUtils.DEFAULT_RETRY_TEMPLATE);
  }

  /**
   * Initializes a new instance of the OpenAiChatModel.
   *
   * @param wordliftApi             The WordLiftApi instance to be used for interacting with the OpenAI
   *                                Chat API.
   * @param options                 The OpenAiChatOptions to configure the chat model.
   * @param functionCallbackContext The function callback context.
   * @param retryTemplate           The retry template.
   */
  public WordLiftChatModel(
    WordLiftApi wordliftApi,
    WordLiftChatOptions options,
    FunctionCallbackContext functionCallbackContext,
    RetryTemplate retryTemplate
  ) {
    this(
      wordliftApi,
      options,
      functionCallbackContext,
      List.of(),
      retryTemplate
    );
  }

  /**
   * Initializes a new instance of the OpenAiChatModel.
   *
   * @param wordliftApi             The WordLiftApi instance to be used for interacting with the OpenAI
   *                                Chat API.
   * @param options                 The OpenAiChatOptions to configure the chat model.
   * @param functionCallbackContext The function callback context.
   * @param toolFunctionCallbacks   The tool function callbacks.
   * @param retryTemplate           The retry template.
   */
  public WordLiftChatModel(
    WordLiftApi wordliftApi,
    WordLiftChatOptions options,
    FunctionCallbackContext functionCallbackContext,
    List<FunctionCallback> toolFunctionCallbacks,
    RetryTemplate retryTemplate
  ) {
    this(
      wordliftApi,
      options,
      functionCallbackContext,
      toolFunctionCallbacks,
      retryTemplate,
      ObservationRegistry.NOOP
    );
  }

  /**
   * Initializes a new instance of the OpenAiChatModel.
   *
   * @param wordliftApi             The WordLiftApi instance to be used for interacting with the OpenAI
   *                                Chat API.
   * @param options                 The OpenAiChatOptions to configure the chat model.
   * @param functionCallbackContext The function callback context.
   * @param toolFunctionCallbacks   The tool function callbacks.
   * @param retryTemplate           The retry template.
   * @param observationRegistry     The ObservationRegistry used for instrumentation.
   */
  public WordLiftChatModel(
    WordLiftApi wordliftApi,
    WordLiftChatOptions options,
    FunctionCallbackContext functionCallbackContext,
    List<FunctionCallback> toolFunctionCallbacks,
    RetryTemplate retryTemplate,
    ObservationRegistry observationRegistry
  ) {
    super(functionCallbackContext, options, toolFunctionCallbacks);
    Assert.notNull(wordliftApi, "WordLiftApi must not be null");
    Assert.notNull(options, "Options must not be null");
    Assert.notNull(retryTemplate, "RetryTemplate must not be null");
    Assert.isTrue(
      CollectionUtils.isEmpty(options.getFunctionCallbacks()),
      "The default function callbacks must be set via the toolFunctionCallbacks constructor parameter"
    );
    Assert.notNull(observationRegistry, "ObservationRegistry must not be null");

    this.wordliftApi = wordliftApi;
    this.defaultOptions = options;
    this.retryTemplate = retryTemplate;
    this.observationRegistry = observationRegistry;
  }

  @Override
  public ChatResponse call(Prompt prompt) {
    ChatCompletionRequest request = createRequest(prompt, false);

    ChatModelObservationContext observationContext = ChatModelObservationContext
      .builder()
      .prompt(prompt)
      .operationMetadata(buildOperationMetadata())
      .requestOptions(buildRequestOptions(request))
      .build();

    ChatResponse response =
      ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
        .observation(
          this.observationConvention,
          DEFAULT_OBSERVATION_CONVENTION,
          () -> observationContext,
          this.observationRegistry
        )
        .observe(() -> {
          ResponseEntity<ChatCompletion> completionEntity =
            this.retryTemplate.execute(ctx ->
                this.wordliftApi.chatCompletionEntity(
                    request,
                    getAdditionalHttpHeaders(prompt)
                  )
              );

          var chatCompletion = completionEntity.getBody();

          if (chatCompletion == null) {
            logger.warn("No chat completion returned for prompt: {}", prompt);
            return new ChatResponse(List.of());
          }

          List<Choice> choices = chatCompletion.choices();
          if (choices == null) {
            logger.warn("No choices returned for prompt: {}", prompt);
            return new ChatResponse(List.of());
          }

          List<Generation> generations = choices
            .stream()
            .map(choice -> {
              // @formatter:off
              Map<String, Object> metadata = Map.of(
								"id", chatCompletion.id() != null ? chatCompletion.id() : "",
								"role", choice.message().role() != null ? choice.message().role().name() : "",
								"index", choice.index(),
								"finishReason", choice.finishReason() != null ? choice.finishReason().name() : "");
              // @formatter:on
              return buildGeneration(choice, metadata);
            })
            .toList();

          // Non function calling.
          RateLimit rateLimit =
            WordLiftResponseHeaderExtractor.extractAiResponseHeaders(
              completionEntity
            );

          ChatResponse chatResponse = new ChatResponse(
            generations,
            from(completionEntity.getBody(), rateLimit)
          );

          observationContext.setResponse(chatResponse);

          return chatResponse;
        });

    if (
      response != null &&
      isToolCall(
        response,
        Set.of(
          WordLiftApi.ChatCompletionFinishReason.TOOL_CALLS.name(),
          WordLiftApi.ChatCompletionFinishReason.STOP.name()
        )
      )
    ) {
      var toolCallConversation = handleToolCalls(prompt, response);
      // Recursively call the call method with the tool call message
      // conversation that contains the call responses.
      return this.call(new Prompt(toolCallConversation, prompt.getOptions()));
    }

    return response;
  }

  @Override
  public Flux<ChatResponse> stream(Prompt prompt) {
    ChatCompletionRequest request = createRequest(prompt, true);

    Flux<WordLiftApi.ChatCompletionChunk> completionChunks =
      this.retryTemplate.execute(ctx ->
          this.wordliftApi.chatCompletionStream(
              request,
              getAdditionalHttpHeaders(prompt)
            )
        );

    // For chunked responses, only the first chunk contains the choice role.
    // The rest of the chunks with same ID share the same role.
    ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();

    // Convert the ChatCompletionChunk into a ChatCompletion to be able to reuse
    // the function call handling logic.
    Flux<ChatResponse> chatResponse = completionChunks
      .map(this::chunkToChatCompletion)
      .switchMap(chatCompletion ->
        Mono
          .just(chatCompletion)
          .map(chatCompletion2 -> {
            try {
              @SuppressWarnings("null")
              String id = chatCompletion2.id();

              // @formatter:off
					List<Generation> generations = chatCompletion2.choices().stream().map(choice -> {
						if (choice.message().role() != null) {
							roleMap.putIfAbsent(id, choice.message().role().name());
						}
						Map<String, Object> metadata = Map.of(
								"id", chatCompletion2.id(),
								"role", roleMap.getOrDefault(id, ""),
								"index", choice.index(),
								"finishReason", choice.finishReason() != null ? choice.finishReason().name() : "");
								return buildGeneration(choice, metadata);
						}).toList();
              // @formatter:on

              if (chatCompletion2.usage() != null) {
                return new ChatResponse(
                  generations,
                  from(chatCompletion2, null)
                );
              } else {
                return new ChatResponse(generations);
              }
            } catch (Exception e) {
              logger.error("Error processing chat completion", e);
              return new ChatResponse(List.of());
            }
          })
      );

    return chatResponse.flatMap(response -> {
      if (
        isToolCall(
          response,
          Set.of(
            WordLiftApi.ChatCompletionFinishReason.TOOL_CALLS.name(),
            WordLiftApi.ChatCompletionFinishReason.STOP.name()
          )
        )
      ) {
        var toolCallConversation = handleToolCalls(prompt, response);
        // Recursively call the stream method with the tool call message
        // conversation that contains the call responses.
        return this.stream(
            new Prompt(toolCallConversation, prompt.getOptions())
          );
      } else {
        return Flux.just(response);
      }
    });
  }

  private MultiValueMap<String, String> getAdditionalHttpHeaders(
    Prompt prompt
  ) {
    Map<String, String> headers = new HashMap<>(
      this.defaultOptions.getHttpHeaders()
    );
    if (
      prompt.getOptions() != null &&
      prompt.getOptions() instanceof WordLiftChatOptions chatOptions
    ) {
      headers.putAll(chatOptions.getHttpHeaders());
    }
    return CollectionUtils.toMultiValueMap(
      headers
        .entrySet()
        .stream()
        .collect(Collectors.toMap(e -> e.getKey(), e -> List.of(e.getValue())))
    );
  }

  private Generation buildGeneration(
    Choice choice,
    Map<String, Object> metadata
  ) {
    List<AssistantMessage.ToolCall> toolCalls = choice.message().toolCalls() ==
      null
      ? List.of()
      : choice
        .message()
        .toolCalls()
        .stream()
        .map(toolCall ->
          new AssistantMessage.ToolCall(
            toolCall.id(),
            "function",
            toolCall.function().name(),
            toolCall.function().arguments()
          )
        )
        .toList();

    var assistantMessage = new AssistantMessage(
      choice.message().content(),
      metadata,
      toolCalls
    );
    String finishReason =
      (choice.finishReason() != null ? choice.finishReason().name() : "");
    var generationMetadata = ChatGenerationMetadata.from(finishReason, null);
    return new Generation(assistantMessage, generationMetadata);
  }

  private ChatResponseMetadata from(
    ChatCompletion result,
    RateLimit rateLimit
  ) {
    Assert.notNull(result, "WordLift ChatCompletionResult must not be null");
    var builder = ChatResponseMetadata
      .builder()
      .withId(result.id() != null ? result.id() : "")
      .withUsage(
        result.usage() != null
          ? WordLiftUsage.from(result.usage())
          : new EmptyUsage()
      )
      .withModel(result.model() != null ? result.model() : "")
      .withRateLimit(rateLimit)
      .withKeyValue("created", result.created() != null ? result.created() : 0L)
      .withKeyValue(
        "system-fingerprint",
        result.systemFingerprint() != null ? result.systemFingerprint() : ""
      );
    if (rateLimit != null) {
      builder.withRateLimit(rateLimit);
    }
    return builder.build();
  }

  /**
   * Convert the ChatCompletionChunk into a ChatCompletion. The Usage is set to null.
   *
   * @param chunk the ChatCompletionChunk to convert
   * @return the ChatCompletion
   */
  private ChatCompletion chunkToChatCompletion(
    WordLiftApi.ChatCompletionChunk chunk
  ) {
    List<Choice> choices = chunk
      .choices()
      .stream()
      .map(chunkChoice ->
        new Choice(
          chunkChoice.finishReason(),
          chunkChoice.index(),
          chunkChoice.delta(),
          chunkChoice.logprobs()
        )
      )
      .toList();

    return new ChatCompletion(
      chunk.id(),
      choices,
      chunk.created(),
      chunk.model(),
      chunk.systemFingerprint(),
      "chat.completion",
      chunk.usage()
    );
  }

  /**
   * Accessible for testing.
   */
  ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {
    List<ChatCompletionMessage> chatCompletionMessages = prompt
      .getInstructions()
      .stream()
      .map(message -> {
        if (
          message.getMessageType() == MessageType.USER ||
          message.getMessageType() == MessageType.SYSTEM
        ) {
          Object content = message.getContent();
          if (message instanceof UserMessage userMessage) {
            if (!CollectionUtils.isEmpty(userMessage.getMedia())) {
              List<MediaContent> contentList = new ArrayList<>(
                List.of(new MediaContent(message.getContent()))
              );

              contentList.addAll(
                userMessage
                  .getMedia()
                  .stream()
                  .map(media ->
                    new MediaContent(
                      new MediaContent.ImageUrl(
                        this.fromMediaData(media.getMimeType(), media.getData())
                      )
                    )
                  )
                  .toList()
              );

              content = contentList;
            }
          }

          return List.of(
            new ChatCompletionMessage(
              content,
              ChatCompletionMessage.Role.valueOf(
                message.getMessageType().name()
              )
            )
          );
        } else if (message.getMessageType() == MessageType.ASSISTANT) {
          var assistantMessage = (AssistantMessage) message;
          List<ToolCall> toolCalls = null;
          if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
            toolCalls =
              assistantMessage
                .getToolCalls()
                .stream()
                .map(toolCall -> {
                  var function = new ChatCompletionFunction(
                    toolCall.name(),
                    toolCall.arguments()
                  );
                  return new ToolCall(toolCall.id(), toolCall.type(), function);
                })
                .toList();
          }
          return List.of(
            new ChatCompletionMessage(
              assistantMessage.getContent(),
              ChatCompletionMessage.Role.ASSISTANT,
              null,
              null,
              toolCalls
            )
          );
        } else if (message.getMessageType() == MessageType.TOOL) {
          ToolResponseMessage toolMessage = (ToolResponseMessage) message;

          toolMessage
            .getResponses()
            .forEach(response -> {
              Assert.isTrue(
                response.id() != null,
                "ToolResponseMessage must have an id"
              );
              Assert.isTrue(
                response.name() != null,
                "ToolResponseMessage must have a name"
              );
            });

          return toolMessage
            .getResponses()
            .stream()
            .map(tr ->
              new ChatCompletionMessage(
                tr.responseData(),
                ChatCompletionMessage.Role.TOOL,
                tr.name(),
                tr.id(),
                null
              )
            )
            .toList();
        } else {
          throw new IllegalArgumentException(
            "Unsupported message type: " + message.getMessageType()
          );
        }
      })
      .flatMap(List::stream)
      .toList();

    ChatCompletionRequest request = new ChatCompletionRequest(
      chatCompletionMessages,
      stream
    );

    Set<String> enabledToolsToUse = new HashSet<>();

    if (prompt.getOptions() != null) {
      WordLiftChatOptions updatedRuntimeOptions =
        ModelOptionsUtils.copyToTarget(
          prompt.getOptions(),
          ChatOptions.class,
          WordLiftChatOptions.class
        );

      enabledToolsToUse.addAll(
        this.runtimeFunctionCallbackConfigurations(updatedRuntimeOptions)
      );

      request =
        ModelOptionsUtils.merge(
          updatedRuntimeOptions,
          request,
          ChatCompletionRequest.class
        );
    }

    if (!CollectionUtils.isEmpty(this.defaultOptions.getFunctions())) {
      enabledToolsToUse.addAll(this.defaultOptions.getFunctions());
    }

    request =
      ModelOptionsUtils.merge(
        request,
        this.defaultOptions,
        ChatCompletionRequest.class
      );

    // Add the enabled functions definitions to the request's tools parameter.
    if (!CollectionUtils.isEmpty(enabledToolsToUse)) {
      request =
        ModelOptionsUtils.merge(
          WordLiftChatOptions
            .builder()
            .withTools(this.getFunctionTools(enabledToolsToUse))
            .build(),
          request,
          ChatCompletionRequest.class
        );
    }

    // Remove `streamOptions` from the request if it is not a streaming request
    if (request.streamOptions() != null && !stream) {
      logger.warn(
        "Removing streamOptions from the request as it is not a streaming request!"
      );
      request = request.withStreamOptions(null);
    }

    return request;
  }

  private String fromMediaData(MimeType mimeType, Object mediaContentData) {
    if (mediaContentData instanceof byte[] bytes) {
      // Assume the bytes are an image. So, convert the bytes to a base64 encoded
      // following the prefix pattern.
      return String.format(
        "data:%s;base64,%s",
        mimeType.toString(),
        Base64.getEncoder().encodeToString(bytes)
      );
    } else if (mediaContentData instanceof String text) {
      // Assume the text is a URLs or a base64 encoded image prefixed by the user.
      return text;
    } else {
      throw new IllegalArgumentException(
        "Unsupported media data type: " +
        mediaContentData.getClass().getSimpleName()
      );
    }
  }

  private List<WordLiftApi.FunctionTool> getFunctionTools(
    Set<String> functionNames
  ) {
    return this.resolveFunctionCallbacks(functionNames)
      .stream()
      .map(functionCallback -> {
        var function = new WordLiftApi.FunctionTool.Function(
          functionCallback.getDescription(),
          functionCallback.getName(),
          functionCallback.getInputTypeSchema()
        );
        return new WordLiftApi.FunctionTool(function);
      })
      .toList();
  }

  private AiOperationMetadata buildOperationMetadata() {
    return AiOperationMetadata
      .builder()
      .operationType(AiOperationType.CHAT.value())
      .provider(AiProvider.OPENAI.value())
      .build();
  }

  private ChatModelRequestOptions buildRequestOptions(
    ChatCompletionRequest request
  ) {
    return ChatModelRequestOptions
      .builder()
      .model(StringUtils.hasText(request.model()) ? request.model() : "unknown")
      .frequencyPenalty(request.frequencyPenalty())
      .maxTokens(request.maxTokens())
      .presencePenalty(request.presencePenalty())
      .stopSequences(request.stop())
      .temperature(request.temperature())
      .topP(request.topP())
      .build();
  }

  @Override
  public ChatOptions getDefaultOptions() {
    return WordLiftChatOptions.fromOptions(this.defaultOptions);
  }

  @Override
  public String toString() {
    return "OpenAiChatModel [defaultOptions=" + defaultOptions + "]";
  }

  /**
   * Use the provided convention for reporting observation data
   *
   * @param observationConvention The provided convention
   */
  public void setObservationConvention(
    ChatModelObservationConvention observationConvention
  ) {
    Assert.notNull(
      observationConvention,
      "observationConvention cannot be null"
    );
    this.observationConvention = observationConvention;
  }
}
