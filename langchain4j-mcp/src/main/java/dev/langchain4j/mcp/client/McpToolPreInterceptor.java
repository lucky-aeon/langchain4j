package dev.langchain4j.mcp.client;

import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.List;

public interface McpToolPreInterceptor {
    /**
     * 在工具被添加到提供者之前执行
     * @param client 当前MCP客户端实例
     * @param toolSpec 待添加的工具规格
     */
    void beforeAddTool(McpClient client, List<ToolSpecification> toolSpec);
}
