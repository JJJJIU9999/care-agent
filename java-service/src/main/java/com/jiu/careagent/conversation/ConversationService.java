package com.jiu.careagent.conversation;

import com.jiu.careagent.common.ApiException;
import com.jiu.careagent.domain.Conversation;
import com.jiu.careagent.domain.MessageMetadata;
import com.jiu.careagent.repository.AppUserRepository;
import com.jiu.careagent.repository.ConversationRepository;
import com.jiu.careagent.repository.MessageMetadataRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ConversationService {
    private final AppUserRepository users;
    private final ConversationRepository conversations;
    private final MessageMetadataRepository messages;

    public ConversationService(AppUserRepository users, ConversationRepository conversations,
                               MessageMetadataRepository messages) {
        this.users = users;
        this.conversations = conversations;
        this.messages = messages;
    }

    @Transactional
    public UUID create(UUID userId) {
        if (!users.existsById(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        }
        return conversations.save(new Conversation(userId)).getId();
    }

    @Transactional
    public void startMessage(UUID conversationId, UUID userId, String content) {
        Conversation conversation = conversations.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "会话不存在"));
        conversation.touch();
        messages.save(new MessageMetadata(conversationId, "USER", codePoints(content), null, "RECEIVED"));
    }

    @Transactional
    public void finishMessage(UUID conversationId, long durationMs, String status) {
        messages.save(new MessageMetadata(conversationId, "ASSISTANT", 0, durationMs, status));
    }

    private int codePoints(String content) {
        return content.codePointCount(0, content.length());
    }
}
