package dev.langchain4j.mcp.client;

import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.List;

public interface McpToolPostInterceptor {
    /**
     * 在某个MCP客户端的全部工具添加完成后执行
     * @param client 当前MCP客户端实例
     * @param tools 已添加的工具列表
     */
    void afterToolsAdded(McpClient client, List<ToolSpecification> tools);
}
