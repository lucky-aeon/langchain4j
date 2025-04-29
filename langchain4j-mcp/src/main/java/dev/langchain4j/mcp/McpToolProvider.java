package dev.langchain4j.mcp;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpToolPostInterceptor;
import dev.langchain4j.mcp.client.McpToolPreInterceptor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A tool provider backed by one or more MCP clients.
 */
public class McpToolProvider implements ToolProvider {

    private final List<McpClient> mcpClients;
    private final boolean failIfOneServerFails;

    private final McpToolPreInterceptor preInterceptor;

    private final McpToolPostInterceptor postInterceptor;
    private static final Logger log = LoggerFactory.getLogger(McpToolProvider.class);

    private McpToolProvider(Builder builder) {
        this.mcpClients = new ArrayList<>(builder.mcpClients);
        this.failIfOneServerFails = Utils.getOrDefault(builder.failIfOneServerFails, false);
        this.preInterceptor = builder.preInterceptor;
        this.postInterceptor = builder().postInterceptor;
    }

    @Override
    public ToolProviderResult provideTools(final ToolProviderRequest request) {
        ToolProviderResult.Builder builder = ToolProviderResult.builder();
        for (McpClient mcpClient : mcpClients) {
            try {
                List<ToolSpecification> toolSpecifications = mcpClient.listTools();
                if (preInterceptor != null) {
                    preInterceptor.beforeAddTool(mcpClient, toolSpecifications);
                }
                for (ToolSpecification toolSpecification : toolSpecifications) {
                    builder.add(
                            toolSpecification, (executionRequest, memoryId) -> mcpClient.executeTool(executionRequest));
                }
                if (postInterceptor != null) {
                    postInterceptor.afterToolsAdded(mcpClient, toolSpecifications);
                }
            } catch (Exception e) {
                if (failIfOneServerFails) {
                    throw new RuntimeException("Failed to retrieve tools from MCP server", e);
                } else {
                    log.warn("Failed to retrieve tools from MCP server", e);
                }
            }
        }
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<McpClient> mcpClients;
        private Boolean failIfOneServerFails;
        private McpToolPreInterceptor preInterceptor;

        private McpToolPostInterceptor postInterceptor;

        /**
         * aad a preInterceptor befor sending request to MCP server
         */
        public Builder addPreInterceptor(McpToolPreInterceptor preInterceptor) {
            this.preInterceptor = preInterceptor;
            return this;
        }

        /**
         * aad a postInterceptor after receiving response from MCP server
         */
        public Builder addPosInterceptor(McpToolPostInterceptor postInterceptor) {
            this.postInterceptor = postInterceptor;
            return this;
        }

        /**
         * The list of MCP clients to use for retrieving tools.
         */
        public McpToolProvider.Builder mcpClients(List<McpClient> mcpClients) {
            this.mcpClients = mcpClients;
            return this;
        }

        /**
         * The list of MCP clients to use for retrieving tools.
         */
        public McpToolProvider.Builder mcpClients(McpClient... mcpClients) {
            this.mcpClients = Arrays.asList(mcpClients);
            return this;
        }

        /**
         * If this is true, then the tool provider will throw an exception if it fails to list tools from any of the servers.
         * If this is false (default), then the tool provider will ignore the error and continue with the next server.
         */
        public McpToolProvider.Builder failIfOneServerFails(boolean failIfOneServerFails) {
            this.failIfOneServerFails = failIfOneServerFails;
            return this;
        }

        public McpToolProvider build() {
            return new McpToolProvider(this);
        }
    }
}
