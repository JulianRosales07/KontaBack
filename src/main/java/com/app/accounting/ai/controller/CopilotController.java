package com.app.accounting.ai.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.app.accounting.ai.dto.CopilotChatRequest;
import com.app.accounting.ai.dto.CopilotChatResponse;
import com.app.accounting.ai.service.CopilotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "Konta AI Copilot", description = "Endpoints seguros de Inteligencia Artificial para consultas contables y operativas")
@PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
public class CopilotController {

    private final CopilotService copilotService;

    @PostMapping("/chat")
    @Operation(summary = "Consultar al Asistente Contable y Tributario Konta Copilot con Groq Cloud")
    public ResponseEntity<CopilotChatResponse> chat(@Valid @RequestBody CopilotChatRequest request) {
        return ResponseEntity.ok(copilotService.askCopilot(request));
    }
}
