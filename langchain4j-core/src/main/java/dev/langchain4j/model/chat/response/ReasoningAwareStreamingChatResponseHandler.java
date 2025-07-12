package dev.langchain4j.model.chat.response;

import java.util.function.BiFunction;

/**
 * An extended streaming chat response handler that supports custom reasoning detection logic.
 * This allows users to define their own logic to determine whether a particular chunk of data
 * represents reasoning/thinking content or regular response content.
 */
public interface ReasoningAwareStreamingChatResponseHandler extends StreamingChatResponseHandler {

    /**
     * Sets a custom function to determine if the current data chunk represents reasoning content.
     * 
     * @param reasoningDetector A BiFunction where:
     *                         - First parameter: JSONPath expression to extract data (e.g., "$.reasoning_content")
     *                         - Second parameter: The complete response data from the model
     *                         - Returns: true if this chunk represents reasoning content, false otherwise
     */
    void setReasoningDetector(BiFunction<String, Object, Boolean> reasoningDetector);

    /**
     * Sets the JSONPath expression used to extract reasoning content.
     * 
     * @param jsonPath The JSONPath expression (e.g., "$.reasoning_content", "$.choices[0].reasoning_content")
     */
    void setReasoningJsonPath(String jsonPath);

    /**
     * Processes a raw data chunk from the model using the custom reasoning detection logic.
     * This method should be called by the model implementation to handle each incoming data chunk.
     * 
     * @param rawData The raw response data from the model (typically a JSON object)
     */
    void processRawChunk(Object rawData);

    /**
     * Default implementation that provides a convenient way to create a reasoning-aware handler
     * with lambda expressions.
     */
    static ReasoningAwareStreamingChatResponseHandler create(
            StreamingChatResponseHandler delegate,
            BiFunction<String, Object, Boolean> reasoningDetector,
            String jsonPath) {
        
        return new ReasoningAwareStreamingChatResponseHandler() {
            private BiFunction<String, Object, Boolean> detector = reasoningDetector;
            private String reasoningPath = jsonPath;

            @Override
            public void setReasoningDetector(BiFunction<String, Object, Boolean> reasoningDetector) {
                this.detector = reasoningDetector;
            }

            @Override
            public void setReasoningJsonPath(String jsonPath) {
                this.reasoningPath = jsonPath;
            }

            @Override
            public void processRawChunk(Object rawData) {
                if (detector != null && reasoningPath != null) {
                    boolean isReasoning = detector.apply(reasoningPath, rawData);
                    if (isReasoning) {
                        // Extract reasoning content and call onPartialReasoning
                        String reasoningContent = extractReasoningContent(rawData);
                        if (reasoningContent != null && !reasoningContent.isEmpty()) {
                            onPartialReasoning(reasoningContent);
                        }
                    } else {
                        // Extract regular content and call onPartialResponse
                        String responseContent = extractResponseContent(rawData);
                        if (responseContent != null && !responseContent.isEmpty()) {
                            onPartialResponse(responseContent);
                        }
                    }
                }
            }

            @Override
            public void onPartialResponse(String partialResponse) {
                delegate.onPartialResponse(partialResponse);
            }

            @Override
            public void onPartialReasoning(String partialReasoning) {
                delegate.onPartialReasoning(partialReasoning);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                delegate.onCompleteResponse(completeResponse);
            }

            @Override
            public void onCompleteReasoning(String completeReasoning) {
                delegate.onCompleteReasoning(completeReasoning);
            }

            @Override
            public void onError(Throwable error) {
                delegate.onError(error);
            }

            /**
             * Extract reasoning content from raw data.
             * Override this method to customize content extraction logic.
             */
            protected String extractReasoningContent(Object rawData) {
                // Default implementation - users should override this
                return null;
            }

            /**
             * Extract regular response content from raw data.
             * Override this method to customize content extraction logic.
             */
            protected String extractResponseContent(Object rawData) {
                // Default implementation - users should override this
                return null;
            }
        };
    }
}