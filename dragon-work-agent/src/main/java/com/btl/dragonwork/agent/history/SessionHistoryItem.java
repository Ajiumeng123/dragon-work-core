package com.btl.dragonwork.agent.history;

import lombok.Data;

@Data
public class SessionHistoryItem {

    private String sessionId;
    private String preview;
    private Long updatedAt;
}
