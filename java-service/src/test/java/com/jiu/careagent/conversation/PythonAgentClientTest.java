package com.jiu.careagent.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PythonAgentClientTest {
    @Test
    void cancellingTheJavaStreamClosesThePythonResponseBody() {
        CloseTrackingInputStream input = new CloseTrackingInputStream();
        AtomicReference<PythonAgentClient.Terminal> terminal = new AtomicReference<>();
        PythonAgentClient.StreamState state = new PythonAgentClient.StreamState(
                UUID.randomUUID(), new SseEmitter(), terminal::set);
        state.setInput(input);

        state.cancelIfOpen();

        assertThat(input.closed).isTrue();
        assertThat(terminal.get().status()).isEqualTo("CANCELLED");
    }

    @Test
    void forwardsNormalizedIdentityAndAcceptsOnlyMatchingTerminalEvents() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        String stream = "event: status\ndata: {\"requestId\":\"" + requestId
                + "\",\"stage\":\"classifying\",\"message\":\"ok\"}\n\n"
                + "event: done\ndata: {\"requestId\":\"" + requestId + "\"}\n\n";
        server.expect(requestTo("/internal/v1/agent/runs"))
                .andExpect(header("X-Internal-Token", "internal-test-token"))
                .andExpect(header("X-Request-ID", requestId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.conversationId").value(conversationId.toString()))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andRespond(withSuccess(stream, MediaType.TEXT_EVENT_STREAM));

        var executor = Executors.newSingleThreadExecutor();
        PythonAgentClient client = new PythonAgentClient(builder.build(), new ObjectMapper(),
                "internal-test-token", executor);
        AtomicReference<PythonAgentClient.Terminal> terminal = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);
        client.forward(new PythonAgentClient.AgentRequest(userId, conversationId, "问题", List.of(), requestId),
                new SseEmitter(), result -> { terminal.set(result); finished.countDown(); });

        assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(terminal.get().status()).isEqualTo("COMPLETED");
        server.verify();
        client.close();
    }

    private static final class CloseTrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private CloseTrackingInputStream() {
            super(new byte[0]);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
