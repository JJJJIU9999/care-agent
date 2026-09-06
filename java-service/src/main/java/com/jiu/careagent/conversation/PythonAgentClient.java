package com.jiu.careagent.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiu.careagent.common.RequestIds;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Component
public class PythonAgentClient {
    private static final Logger log = LoggerFactory.getLogger(PythonAgentClient.class);
    private static final Set<String> ALLOWED_EVENTS = Set.of(
            "status", "token", "citation", "service_card", "tool_confirmation", "done", "error");

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String internalToken;
    private final ExecutorService streams;

    @Autowired
    public PythonAgentClient(RestClient.Builder builder, ObjectMapper objectMapper,
                             @Value("${careagent.python-base-url}") String baseUrl,
                             @Value("${careagent.internal-token}") String internalToken) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(300_000);
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.internalToken = internalToken;
        this.streams = newStreamExecutor();
    }

    PythonAgentClient(RestClient client, ObjectMapper objectMapper, String internalToken,
                      ExecutorService streams) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.internalToken = internalToken;
        this.streams = streams;
    }

    private static ExecutorService newStreamExecutor() {
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "python-agent-sse");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void forward(AgentRequest body, SseEmitter emitter, Consumer<Terminal> terminalConsumer) {
        StreamState state = new StreamState(body.requestId(), emitter, terminalConsumer);
        emitter.onCompletion(state::cancelIfOpen);
        emitter.onTimeout(state::cancelIfOpen);
        emitter.onError(ignored -> state.cancelIfOpen());
        state.setWorker(streams.submit(() -> run(body, state)));
    }

    private void run(AgentRequest body, StreamState state) {
        long started = System.nanoTime();
        try {
            client.post()
                    .uri("/internal/v1/agent/runs")
                    .header("X-Internal-Token", internalToken)
                    .header(RequestIds.HEADER, body.requestId().toString())
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new IOException("Python agent returned " + response.getStatusCode().value());
                        }
                        state.setInput(response.getBody());
                        forwardEvents(response.getBody(), body.requestId(), state, started);
                        return null;
                    });
            if (state.isOpen()) {
                state.fail("AGENT_UPSTREAM_ENDED", "智能服务连接意外结束", elapsedMs(started));
            }
        } catch (Exception exception) {
            if (state.isOpen()) {
                log.warn("request_id={} stage=proxy status=failed error_code=AGENT_UPSTREAM_ERROR",
                        body.requestId());
                state.fail("AGENT_UPSTREAM_ERROR", "智能服务暂时不可用，请稍后重试", elapsedMs(started));
            }
        }
    }

    private void forwardEvents(InputStream input, UUID requestId, StreamState state, long started) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String event = null;
            StringBuilder data = new StringBuilder();
            String line;
            while (state.isOpen() && (line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (event != null && !data.isEmpty()) {
                        dispatch(event, data.toString(), requestId, state, started);
                    }
                    event = null;
                    data.setLength(0);
                } else if (line.startsWith("event:")) {
                    event = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    if (!data.isEmpty()) data.append('\n');
                    data.append(line.substring(5).trim());
                }
            }
        }
    }

    private void dispatch(String event, String data, UUID requestId, StreamState state, long started) throws IOException {
        if (!ALLOWED_EVENTS.contains(event)) {
            throw new IOException("Unexpected SSE event");
        }
        JsonNode payload = objectMapper.readTree(data);
        if (!requestId.toString().equals(payload.path("requestId").asText())) {
            throw new IOException("SSE request ID mismatch");
        }
        state.send(event, data);
        if ("done".equals(event)) {
            state.finish(new Terminal("COMPLETED", null, elapsedMs(started)));
        } else if ("error".equals(event)) {
            state.finish(new Terminal("FAILED", payload.path("code").asText("AGENT_ERROR"), elapsedMs(started)));
        }
    }

    private static long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    @PreDestroy
    void close() {
        streams.shutdownNow();
    }

    public record AgentRequest(UUID userId, UUID conversationId, String message,
                               java.util.List<ContextMessage> context, UUID requestId) {
    }

    public record ContextMessage(String role, String content) {
    }

    public record Terminal(String status, String errorCode, long durationMs) {
    }

    static final class StreamState {
        private final UUID requestId;
        private final SseEmitter emitter;
        private final Consumer<Terminal> terminalConsumer;
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final AtomicReference<InputStream> input = new AtomicReference<>();
        private final AtomicReference<Future<?>> worker = new AtomicReference<>();

        StreamState(UUID requestId, SseEmitter emitter, Consumer<Terminal> terminalConsumer) {
            this.requestId = requestId;
            this.emitter = emitter;
            this.terminalConsumer = terminalConsumer;
        }

        void setInput(InputStream value) {
            input.set(value);
            if (!open.get()) closeInput();
        }

        void setWorker(Future<?> value) {
            worker.set(value);
            if (!open.get()) value.cancel(true);
        }

        boolean isOpen() {
            return open.get();
        }

        void send(String event, String data) throws IOException {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (IOException exception) {
                cancelIfOpen();
                throw exception;
            }
        }

        void fail(String code, String message, long durationMs) {
            try {
                String data = "{\"requestId\":\"" + requestId + "\",\"code\":\"" + code
                        + "\",\"message\":\"" + message + "\"}";
                emitter.send(SseEmitter.event().name("error").data(data));
            } catch (IOException ignored) {
                // Browser is already gone; cancellation still closes Python upstream.
            }
            finish(new Terminal("FAILED", code, durationMs));
        }

        void finish(Terminal terminal) {
            if (open.compareAndSet(true, false)) {
                closeInput();
                try {
                    terminalConsumer.accept(terminal);
                } finally {
                    emitter.complete();
                }
            }
        }

        void cancelIfOpen() {
            if (open.compareAndSet(true, false)) {
                closeInput();
                Future<?> future = worker.get();
                if (future != null) future.cancel(true);
                terminalConsumer.accept(new Terminal("CANCELLED", null, 0));
            }
        }

        private void closeInput() {
            InputStream value = input.getAndSet(null);
            if (value != null) {
                try {
                    value.close();
                } catch (IOException ignored) {
                    // Closing is best effort during cancellation.
                }
            }
        }
    }
}
