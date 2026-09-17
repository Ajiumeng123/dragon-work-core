package com.btl.dragonwork.agent.permission;

import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import org.springframework.stereotype.Component;

@Component
public class ShellPermissionAsk {

    public PermissionContextState buildShellPermission(){
        return PermissionContextState.builder()
                .mode(PermissionMode.BYPASS)
                .addAskRule(
                        "execute",
                        new PermissionRule(
                        "execute",
                                null,
                                PermissionBehavior.ASK,
                                "projectSettings"
                        )
                )
                .build();
    }
}
