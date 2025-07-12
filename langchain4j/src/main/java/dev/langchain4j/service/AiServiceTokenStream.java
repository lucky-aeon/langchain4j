package dev.langchain4j.service;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.emptyList;

@Internal
public class AiServiceTokenStream implements TokenStream {

    private final List<ChatMessage> messages;
    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, ToolExecutor> toolExecutors;
    private final List<Content> retrievedContents;
    private final AiServiceContext context;
    private final Object memoryId;

    private Consumer<String> partialResponseHandler;
    private Consumer<List<Content>> contentsHandler;
    private Consumer<ToolExecution> toolExecutionHandler;
    private Consumer<ChatResponse> completeResponseHandler;
    private Consumer<Throwable> errorHandler;
    
    // Reasoning-related handlers
    private Consumer<String> partialReasoningHandler;
    private Consumer<String> completeReasoningHandler;
    private BiFunction<String, Object, Boolean> reasoningDetector;
    private String reasoningJsonPath;

    private int onPartialResponseInvoked;
    private int onCompleteResponseInvoked;
    private int onRetrievedInvoked;
    private int onToolExecutedInvoked;
    private int onErrorInvoked;
    private int ignoreErrorsInvoked;

    /**
     * Creates a new instance of {@link AiServiceTokenStream} with the given parameters.
     *
     * @param parameters the parameters for creating the token stream
     */
    public AiServiceTokenStream(AiServiceTokenStreamParameters parameters) {
        this.messages = copy(ensureNotEmpty(parameters.messages(), "messages"));
        this.toolSpecifications = copy(parameters.toolSpecifications());
        this.toolExecutors = copy(parameters.toolExecutors());
        this.retrievedContents = copy(parameters.gretrievedContents());
        this.context = ensureNotNull(parameters.context(), "context");
        ensureNotNull(this.context.streamingChatModel, "streamingChatModel");
        this.memoryId = ensureNotNull(parameters.memoryId(), "memoryId");
    }

    @Override
    public TokenStream onPartialResponse(Consumer<String> partialResponseHandler) {
        this.partialResponseHandler = partialResponseHandler;
        this.onPartialResponseInvoked++;
        return this;
    }

    @Override
    public TokenStream onRetrieved(Consumer<List<Content>> contentsHandler) {
        this.contentsHandler = contentsHandler;
        this.onRetrievedInvoked++;
        return this;
    }

    @Override
    public TokenStream onToolExecuted(Consumer<ToolExecution> toolExecutionHandler) {
        this.toolExecutionHandler = toolExecutionHandler;
        this.onToolExecutedInvoked++;
        return this;
    }

    @Override
    public TokenStream onCompleteResponse(Consumer<ChatResponse> completionHandler) {
        this.completeResponseHandler = completionHandler;
        this.onCompleteResponseInvoked++;
        return this;
    }

    @Override
    public TokenStream onError(Consumer<Throwable> errorHandler) {
        this.errorHandler = errorHandler;
        this.onErrorInvoked++;
        return this;
    }

    @Override
    public TokenStream ignoreErrors() {
        this.errorHandler = null;
        this.ignoreErrorsInvoked++;
        return this;
    }

    @Override
    public TokenStream onPartialReasoning(Consumer<String> partialReasoningHandler) {
        this.partialReasoningHandler = partialReasoningHandler;
        return this;
    }

    @Override
    public TokenStream onCompleteReasoning(Consumer<String> completeReasoningHandler) {
        this.completeReasoningHandler = completeReasoningHandler;
        return this;
    }

    @Override
    public TokenStream onReasoningDetected(BiFunction<String, Object, Boolean> reasoningDetector, String jsonPath) {
        this.reasoningDetector = reasoningDetector;
        this.reasoningJsonPath = jsonPath;
        return this;
    }

    @Override
    public void processRawData(Object rawData) {
        if (reasoningDetector != null && reasoningJsonPath != null) {
            try {
                boolean isReasoning = reasoningDetector.apply(reasoningJsonPath, rawData);
                if (isReasoning) {
                    // Extract reasoning content from raw data
                    String reasoningContent = extractReasoningContent(rawData);
                    if (reasoningContent != null && !reasoningContent.isEmpty()) {
                        if (partialReasoningHandler != null) {
                            partialReasoningHandler.accept(reasoningContent);
                        }
                    }
                } else {
                    // Extract regular response content from raw data
                    String responseContent = extractResponseContent(rawData);
                    if (responseContent != null && !responseContent.isEmpty()) {
                        if (partialResponseHandler != null) {
                            partialResponseHandler.accept(responseContent);
                        }
                    }
                }
            } catch (Exception e) {
                // Log error but don't fail the stream
                if (errorHandler != null) {
                    errorHandler.accept(e);
                }
            }
        }
    }

    /**
     * Extract reasoning content from raw data. Override this method to customize extraction logic.
     */
    protected String extractReasoningContent(Object rawData) {
        // This is a basic implementation - users should override this
        if (rawData != null) {
            String dataStr = rawData.toString();
            // Try to extract reasoning content using simple string matching
            if (dataStr.contains("reasoning_content")) {
                // Simple extraction logic - users should implement proper JSON parsing
                return dataStr;
            }
        }
        return null;
    }

    /**
     * Extract regular response content from raw data. Override this method to customize extraction logic.
     */
    protected String extractResponseContent(Object rawData) {
        // This is a basic implementation - users should override this
        if (rawData != null) {
            String dataStr = rawData.toString();
            // Try to extract regular content
            return dataStr;
        }
        return null;
    }

    @Override
    public void start() {
        validateConfiguration();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(toolSpecifications)
                .build();

        StreamingChatResponseHandler handler = new AiServiceStreamingResponseHandler(
                context,
                memoryId,
                partialResponseHandler,
                toolExecutionHandler,
                completeResponseHandler,
                errorHandler,
                initTemporaryMemory(context, messages),
                new TokenUsage(),
                toolSpecifications,
                toolExecutors,
                partialReasoningHandler,
                completeReasoningHandler,
                reasoningDetector,
                reasoningJsonPath);

        if (contentsHandler != null && retrievedContents != null) {
            contentsHandler.accept(retrievedContents);
        }

        context.streamingChatModel.chat(chatRequest, handler);
    }

    private void validateConfiguration() {
        if (onPartialResponseInvoked != 1) {
            throw new IllegalConfigurationException("onPartialResponse must be invoked on TokenStream exactly 1 time");
        }
        if (onCompleteResponseInvoked > 1) {
            throw new IllegalConfigurationException("onCompleteResponse can be invoked on TokenStream at most 1 time");
        }
        if (onRetrievedInvoked > 1) {
            throw new IllegalConfigurationException("onRetrieved can be invoked on TokenStream at most 1 time");
        }
        if (onToolExecutedInvoked > 1) {
            throw new IllegalConfigurationException("onToolExecuted can be invoked on TokenStream at most 1 time");
        }
        if (onErrorInvoked + ignoreErrorsInvoked != 1) {
            throw new IllegalConfigurationException(
                    "One of [onError, ignoreErrors] " + "must be invoked on TokenStream exactly 1 time");
        }
    }

    private List<ChatMessage> initTemporaryMemory(AiServiceContext context, List<ChatMessage> messagesToSend) {
        if (context.hasChatMemory()) {
            return emptyList();
        } else {
            return new ArrayList<>(messagesToSend);
        }
    }
}
