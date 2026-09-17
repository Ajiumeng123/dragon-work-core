package com.btl.dragonwork.api.business;

import com.btl.dragonwork.agent.config.mcp.McpPingResult;
import com.btl.dragonwork.agent.config.mcp.McpPingService;
import com.btl.dragonwork.common.result.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/mcp")
public class McpBusiness {

    @Resource
    private McpPingService mcpPingService;

    @GetMapping("/ping")
    public Result<List<McpPingResult>> ping(
            @RequestParam(value = "name", required = false) String name
    ) {
        return Result.ok(mcpPingService.ping(name));
    }
}
