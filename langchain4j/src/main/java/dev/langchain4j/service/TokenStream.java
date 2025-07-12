package dev.langchain4j.service;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.tool.ToolExecution;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Represents a token stream from the model to which you can subscribe and receive updates
 * when a new partial response (usually a single token) is available,
 *  when the model finishes streaming, or when an error occurs during streaming.
 * It is intended to be used as a return type in AI Service.
 */
public interface TokenStream {

    /**
     * The provided consumer will be invoked every time a new partial response (usually a single token)
     * from a language model is available.
     *
     * @param partialResponseHandler lambda that will be invoked when a model generates a new partial response
     * @return token stream instance used to configure or start stream processing
     */
    TokenStream onPartialResponse(Consumer<String> partialResponseHandler);

    /**
     * The provided consumer will be invoked if any {@link Content}s are retrieved using {@link RetrievalAugmentor}.
     * <p>
     * The invocation happens before any call is made to the language model.
     *
     * @param contentHandler lambda that consumes all retrieved contents
     * @return token stream instance used to configure or start stream processing
     */
    TokenStream onRetrieved(Consumer<List<Content>> contentHandler);

    /**
     * The provided consumer will be invoked if any tool is executed.
     * <p>
     * The invocation happens after the tool method has finished and before any other tool is executed.
     *
     * @param toolExecuteHandler lambda that consumes {@link ToolExecution}
     * @return token stream instance used to configure or start stream processing
     */
    TokenStream onToolExecuted(Consumer<ToolExecution> toolExecuteHandler);

    /**
     * The provided handler will be invoked when a language model finishes streaming a response.
     *
     * @param completeResponseHandler lambda that will be invoked when language model finishes streaming
     * @return token stream instance used to configure or start stream processing
     */
    TokenStream onCompleteResponse(Consumer<ChatResponse> completeResponseHandler);

    /**
     * The provided consumer will be invoked every time a new partial reasoning/thinking content
     * from a language model is available (for models that support reasoning chains like OpenAI o1 series).
     *
     * @param partialReasoningHandler lambda that will be invoked when a model generates new partial reasoning content
     * @return token stream instance used to configure or start stream processing
     */
    TokenStream onPartialReasoning(Consumer<String> partialReasoningHandler);

    /**
     * The provided handler will be invoked when a language model finishes streaming reasoning content.
     * This contains the complete reasoning/thinking process for models that support it.
     *
     * @param completeReasoningHandler lambda that will be invoked when language model finishes reasoning
     * @return token stream instance used to configure or start stream processing
     */
    TokenStream onCompleteReasoning(Consumer<String> completeReasoningHandler);

    /**
     * Sets a custom function to determine if the current data chunk represents reasoning content.
     * This allows you to define your own logic based on JSONPath expressions and raw model data.
     *
     * @param reasoningDetector A BiFunction where:
     *                         - First parameter: JSONPath expression to extract data (e.g., "$.reasoning_content")
     *                         - Second parameter: The complete response data from the model
     *                         - Returns: true if this chunk represents reasoning content, false otherwise
     * @param jsonPath The JSONPath expression used to extract reasoning content
     * @return token stream instance used to configure or start stream processing
     */
    TokenStream onReasoningDetected(BiFunction<String, Object, Boolean> reasoningDetector, String jsonPath);

    /**
     * The provided consumer will be invoked when an error occurs during streaming.
     *
     * @param errorHandler lambda that will be invoked when an error occurs
     * @return token stream instance used to configure or start stream processing
     */
    TokenStream onError(Consumer<Throwable> errorHandler);

    /**
     * All errors during streaming will be ignored (but will be logged with a WARN log level).
     *
     * @return token stream instance used to configure or start stream processing
     */
    TokenStream ignoreErrors();

    /**
     * Completes the current token stream building and starts processing.
     * <p>
     * Will send a request to LLM and start response streaming.
     */
    void start();
}
