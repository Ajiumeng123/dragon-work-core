package com.btl.dragonwork.agent.history;

import lombok.Data;

import java.util.List;

@Data
public class SessionHistory {

    private String userId;
    private String sessionId;
    private List<SessionHistoryMessage> messages;
}
