package dev.langchain4j.service;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Handles response from a language model for AI Service that is streamed token-by-token.
 * Handles both regular (text) responses and responses with the request to execute one or multiple tools.
 */
@Internal
class AiServiceStreamingResponseHandler implements StreamingChatResponseHandler {

    private final Logger log = LoggerFactory.getLogger(AiServiceStreamingResponseHandler.class);

    private final AiServiceContext context;
    private final Object memoryId;

    private final Consumer<String> partialResponseHandler;
    private final Consumer<ToolExecution> toolExecutionHandler;
    private final Consumer<ChatResponse> completeResponseHandler;

    private final Consumer<Throwable> errorHandler;
    
    // Reasoning-related handlers
    private final Consumer<String> partialReasoningHandler;
    private final Consumer<String> completeReasoningHandler;
    private final BiFunction<String, Object, Boolean> reasoningDetector;
    private final String reasoningJsonPath;

    private final List<ChatMessage> temporaryMemory;
    private final TokenUsage tokenUsage;

    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, ToolExecutor> toolExecutors;

    AiServiceStreamingResponseHandler(AiServiceContext context,
                                      Object memoryId,
                                      Consumer<String> partialResponseHandler,
                                      Consumer<ToolExecution> toolExecutionHandler,
                                      Consumer<ChatResponse> completeResponseHandler,
                                      Consumer<Throwable> errorHandler,
                                      List<ChatMessage> temporaryMemory,
                                      TokenUsage tokenUsage,
                                      List<ToolSpecification> toolSpecifications,
                                      Map<String, ToolExecutor> toolExecutors) {
        this(context, memoryId, partialResponseHandler, toolExecutionHandler, completeResponseHandler,
             errorHandler, temporaryMemory, tokenUsage, toolSpecifications, toolExecutors,
             null, null, null, null);
    }

    AiServiceStreamingResponseHandler(AiServiceContext context,
                                      Object memoryId,
                                      Consumer<String> partialResponseHandler,
                                      Consumer<ToolExecution> toolExecutionHandler,
                                      Consumer<ChatResponse> completeResponseHandler,
                                      Consumer<Throwable> errorHandler,
                                      List<ChatMessage> temporaryMemory,
                                      TokenUsage tokenUsage,
                                      List<ToolSpecification> toolSpecifications,
                                      Map<String, ToolExecutor> toolExecutors,
                                      Consumer<String> partialReasoningHandler,
                                      Consumer<String> completeReasoningHandler,
                                      BiFunction<String, Object, Boolean> reasoningDetector,
                                      String reasoningJsonPath) {
        this.context = ensureNotNull(context, "context");
        this.memoryId = ensureNotNull(memoryId, "memoryId");

        this.partialResponseHandler = ensureNotNull(partialResponseHandler, "partialResponseHandler");
        this.completeResponseHandler = completeResponseHandler;
        this.toolExecutionHandler = toolExecutionHandler;
        this.errorHandler = errorHandler;

        this.partialReasoningHandler = partialReasoningHandler;
        this.completeReasoningHandler = completeReasoningHandler;
        this.reasoningDetector = reasoningDetector;
        this.reasoningJsonPath = reasoningJsonPath;

        this.temporaryMemory = new ArrayList<>(temporaryMemory);
        this.tokenUsage = ensureNotNull(tokenUsage, "tokenUsage");

        this.toolSpecifications = copy(toolSpecifications);
        this.toolExecutors = copy(toolExecutors);
    }

    @Override
    public void onPartialResponse(String partialResponse) {
        // This will only be called for non-AiService handlers
        // AiService handlers use onRawData instead
        partialResponseHandler.accept(partialResponse);
    }

    /**
     * Handle raw data from the streaming response for reasoning detection.
     * This method is called with the original raw data before any processing.
     */
    public void onRawData(Object rawData) {
        System.out.println("DEBUG: onRawData called with data: " + (rawData != null ? rawData.getClass().getSimpleName() : "null"));
        
        // If reasoning detection is configured, automatically route the response
        if (reasoningDetector != null && reasoningJsonPath != null) {
            System.out.println("DEBUG: Reasoning detector configured, calling detection function");
            try {
                boolean isReasoning = reasoningDetector.apply(reasoningJsonPath, rawData);
                System.out.println("DEBUG: Detection result: " + isReasoning);
                if (isReasoning) {
                    // Extract reasoning content from raw data and send to reasoning handler
                    String reasoningContent = extractReasoningFromRawData(rawData);
                    if (reasoningContent != null && !reasoningContent.isEmpty() && partialReasoningHandler != null) {
                        partialReasoningHandler.accept(reasoningContent);
                    }
                    // Don't call normal handler for reasoning content
                    return;
                }
            } catch (Exception e) {
                log.warn("Error in reasoning detection, processing as normal response", e);
            }
        } else {
            System.out.println("DEBUG: No reasoning detector configured");
        }
        
        // If not reasoning content, extract regular response and process normally
        String responseContent = extractResponseFromRawData(rawData);
        if (responseContent != null && !responseContent.isEmpty()) {
            partialResponseHandler.accept(responseContent);
        }
    }

    /**
     * Extract reasoning content from raw data. Override this in model implementations.
     */
    protected String extractReasoningFromRawData(Object rawData) {
        // Default implementation - models should override this
        if (rawData != null) {
            return rawData.toString();
        }
        return null;
    }

    /**
     * Extract regular response content from raw data. Override this in model implementations.
     */
    protected String extractResponseFromRawData(Object rawData) {
        // Default implementation - models should override this
        if (rawData != null) {
            return rawData.toString();
        }
        return null;
    }

    @Override
    public void onPartialReasoning(String partialReasoning) {
        if (partialReasoningHandler != null) {
            partialReasoningHandler.accept(partialReasoning);
        }
    }

    @Override
    public void onCompleteReasoning(String completeReasoning) {
        if (completeReasoningHandler != null) {
            completeReasoningHandler.accept(completeReasoning);
        }
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {

        AiMessage aiMessage = completeResponse.aiMessage();
        addToMemory(aiMessage);

        if (aiMessage.hasToolExecutionRequests()) {
            for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                String toolName = toolExecutionRequest.name();
                ToolExecutor toolExecutor = toolExecutors.get(toolName);
                String toolExecutionResult = toolExecutor.execute(toolExecutionRequest, memoryId);
                ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(
                        toolExecutionRequest,
                        toolExecutionResult
                );
                addToMemory(toolExecutionResultMessage);

                if (toolExecutionHandler != null) {
                    ToolExecution toolExecution = ToolExecution.builder()
                            .request(toolExecutionRequest)
                            .result(toolExecutionResult)
                            .build();
                    toolExecutionHandler.accept(toolExecution);
                }
            }

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(messagesToSend(memoryId))
                    .toolSpecifications(toolSpecifications)
                    .build();

            StreamingChatResponseHandler handler = new AiServiceStreamingResponseHandler(
                    context,
                    memoryId,
                    partialResponseHandler,
                    toolExecutionHandler,
                    completeResponseHandler,
                    errorHandler,
                    temporaryMemory,
                    TokenUsage.sum(tokenUsage, completeResponse.metadata().tokenUsage()),
                    toolSpecifications,
                    toolExecutors
            );

            context.streamingChatModel.chat(chatRequest, handler);
        } else {
            if (completeResponseHandler != null) {
                ChatResponse finalChatResponse = ChatResponse.builder()
                        .aiMessage(aiMessage)
                        .metadata(completeResponse.metadata().toBuilder()
                                .tokenUsage(tokenUsage.add(completeResponse.metadata().tokenUsage()))
                                .build())
                        .build();
                completeResponseHandler.accept(finalChatResponse);
            }
        }
    }

    private void addToMemory(ChatMessage chatMessage) {
        if (context.hasChatMemory()) {
            context.chatMemoryService.getOrCreateChatMemory(memoryId).add(chatMessage);
        } else {
            temporaryMemory.add(chatMessage);
        }
    }

    private List<ChatMessage> messagesToSend(Object memoryId) {
        return context.hasChatMemory()
                ? context.chatMemoryService.getOrCreateChatMemory(memoryId).messages()
                : temporaryMemory;
    }

    @Override
    public void onError(Throwable error) {
        if (errorHandler != null) {
            try {
                errorHandler.accept(error);
            } catch (Exception e) {
                log.error("While handling the following error...", error);
                log.error("...the following error happened", e);
            }
        } else {
            log.warn("Ignored error", error);
        }
    }
}
