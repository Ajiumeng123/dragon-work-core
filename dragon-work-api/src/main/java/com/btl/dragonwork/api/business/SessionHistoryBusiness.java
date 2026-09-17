package com.btl.dragonwork.api.business;

import com.btl.dragonwork.agent.history.SessionHistory;
import com.btl.dragonwork.agent.history.SessionHistoryItem;
import com.btl.dragonwork.agent.history.SessionHistoryService;
import com.btl.dragonwork.common.result.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/agent-business")
public class SessionHistoryBusiness {

    @Resource
    private SessionHistoryService sessionHistoryService;

    @GetMapping("/sessions")
    public Result<List<SessionHistoryItem>> sessions(
            @RequestParam("userId") String userId
    ) {
        return Result.ok(sessionHistoryService.list(userId));
    }

    @GetMapping("/session-history")
    public Result<SessionHistory> sessionHistory(
            @RequestParam("userId") String userId,
            @RequestParam("sessionId") String sessionId
    ) {
        return Result.ok(sessionHistoryService.detail(userId, sessionId));
    }
}
