package com.btl.dragonwork.agent.history;

import lombok.Data;

@Data
public class SessionHistoryMessage {

    private String id;
    private String role;
    private String content;
    private String toolCallId;
    private String timestamp;
}
