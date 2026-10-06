package com.app.accounting.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopilotChatRequest {
    private List<ChatMessageDto> history;

    @NotBlank(message = "El mensaje o pregunta no puede estar vacío")
    private String prompt;
}
