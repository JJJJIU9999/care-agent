package com.jiu.careagent.conversation;

import com.jiu.careagent.common.ApiException;
import com.jiu.careagent.common.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {
    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);
    private final ConversationService conversations;
    private final PythonAgentClient python;

    public ConversationController(ConversationService conversations, PythonAgentClient python) {
        this.conversations = conversations;
        this.python = python;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse create(@AuthenticationPrincipal Jwt jwt) {
        return new ConversationResponse(conversations.create(UUID.fromString(jwt.getSubject())));
    }

    @PostMapping(value = "/{conversationId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter message(@PathVariable UUID conversationId,
                              @Valid @RequestBody MessageRequest body,
                              @AuthenticationPrincipal Jwt jwt,
                              HttpServletRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        validateContext(body.context());
        conversations.startMessage(conversationId, userId, body.message());
        UUID requestId = RequestIds.current(request);
        SseEmitter emitter = new SseEmitter(300_000L);
        List<PythonAgentClient.ContextMessage> context = body.context().stream()
                .map(item -> new PythonAgentClient.ContextMessage(item.role(), item.content()))
                .toList();
        python.forward(
                new PythonAgentClient.AgentRequest(userId, conversationId, body.message().trim(), context, requestId),
                emitter,
                terminal -> {
                    conversations.finishMessage(conversationId, terminal.durationMs(), terminal.status());
                    log.info("request_id={} user_id={} conversation_id={} stage=proxy duration_ms={} status={} error_code={}",
                            requestId, userId, conversationId, terminal.durationMs(), terminal.status(), terminal.errorCode());
                });
        return emitter;
    }

    private void validateContext(List<ContextMessage> context) {
        if (context.size() == 2
                && "user".equals(context.get(0).role())
                && "assistant".equals(context.get(1).role())) {
            return;
        }
        if (context.isEmpty()) {
            return;
        }
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_CONTEXT",
                "context 必须为空或按 USER、ASSISTANT 顺序提供一组问答");
    }

    public record ConversationResponse(UUID conversationId) {
    }

    public record MessageRequest(
            @NotBlank(message = "message 不能为空") @Size(max = 1000, message = "message 不能超过 1000 个字符") String message,
            @NotNull(message = "context 必填") @Size(max = 2, message = "context 最多两条") List<@Valid ContextMessage> context) {
    }

    public record ContextMessage(
            @NotBlank(message = "context role 不能为空") String role,
            @NotBlank(message = "context content 不能为空") @Size(max = 2000, message = "context content 不能超过 2000 个字符") String content) {
    }
}
