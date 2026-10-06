package com.app.accounting.ai.service;

import com.app.accounting.ai.dto.CopilotChatRequest;
import com.app.accounting.ai.dto.CopilotChatResponse;

public interface CopilotService {
    CopilotChatResponse askCopilot(CopilotChatRequest request);
}
