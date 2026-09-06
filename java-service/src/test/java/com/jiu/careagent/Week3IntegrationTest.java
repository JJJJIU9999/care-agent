package com.jiu.careagent;

import com.jiu.careagent.appointment.AppointmentService;
import com.jiu.careagent.appointment.DraftService;
import com.jiu.careagent.common.ApiException;
import com.jiu.careagent.conversation.PythonAgentClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class Week3IntegrationTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SERVICE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SLOT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("careagent")
            .withUsername("careagent")
            .withPassword("careagent_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("careagent.jwt.secret", () -> "careagent-test-jwt-secret-at-least-32-bytes");
        registry.add("careagent.internal-token", () -> "careagent-test-internal-token");
    }

    @Autowired MockMvc mvc;
    @Autowired DraftService drafts;
    @Autowired AppointmentService appointments;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean PythonAgentClient pythonAgent;

    @BeforeEach
    void resetBusinessData() {
        jdbc.update("DELETE FROM app.critical_audit_event");
        jdbc.update("DELETE FROM app.appointment");
        jdbc.update("DELETE FROM app.appointment_draft");
        jdbc.update("DELETE FROM app.message_metadata");
        jdbc.update("DELETE FROM app.conversation");
        jdbc.update("UPDATE app.service_slot SET remaining_capacity = total_capacity, updated_at = now()");
    }

    @Test
    void loginAuthorizationRequestIdAndUnknownFieldsFollowTheContract() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo_user\",\"password\":\"demo-user-2026\"}"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.role").value("USER"));

        mvc.perform(get("/api/v1/services"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(get("/api/v1/services")
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString())))
                        .header("X-Request-ID", "not-a-uuid"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.items.length()").value(2));

        mvc.perform(post("/api/v1/appointments")
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString())))
                        .header("Idempotency-Key", "unknown-field")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"draftId\":\"00000000-0000-0000-0000-000000000099\",\"price\":\"0.01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void sixthFailedLoginIsRateLimited() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/api/v1/auth/login")
                            .with(request -> { request.setRemoteAddr("192.0.2.10"); return request; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"missing_user\",\"password\":\"wrong-password\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        mvc.perform(post("/api/v1/auth/login")
                        .with(request -> { request.setRemoteAddr("192.0.2.10"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"missing_user\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
    }

    @Test
    void regularUserCannotUseTheAdminKnowledgeProxy() throws Exception {
        mvc.perform(multipart("/api/v1/admin/knowledge/documents")
                        .file("file", "# policy".getBytes())
                        .param("title", "Policy")
                        .param("issuingOrganization", "Test organization")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void internalToolsRejectBadTokenAndUnknownDraftFields() throws Exception {
        mvc.perform(get("/internal/v1/tools/services")
                        .header("X-Internal-Token", "wrong")
                        .header("X-Request-ID", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_INTERNAL_TOKEN"));

        mvc.perform(get("/internal/v1/tools/services")
                        .header("X-Internal-Token", "careagent-test-internal-token")
                        .header("X-Request-ID", UUID.randomUUID())
                        .param("district", "武侯区")
                        .param("category", "CLEANING")
                        .param("date", "2026-09-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].serviceId").value(SERVICE_ID.toString()))
                .andExpect(jsonPath("$.items[0].price").value("80.00"));

        String validDraftBody = "{\"userId\":\"" + USER_ID + "\",\"serviceId\":\"" + SERVICE_ID
                + "\",\"slotId\":\"" + SLOT_ID + "\"}";
        mvc.perform(post("/internal/v1/tools/appointment-drafts")
                        .header("X-Internal-Token", "wrong")
                        .header("X-Request-ID", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validDraftBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_INTERNAL_TOKEN"));

        mvc.perform(post("/internal/v1/tools/appointment-drafts")
                        .header("X-Internal-Token", "careagent-test-internal-token")
                        .header("X-Request-ID", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + USER_ID + "\",\"serviceId\":\"" + SERVICE_ID
                                + "\",\"slotId\":\"" + SLOT_ID + "\",\"price\":\"0.01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(post("/internal/v1/tools/appointment-drafts")
                        .header("X-Internal-Token", "careagent-test-internal-token")
                        .header("X-Request-ID", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + USER_ID + "\",\"serviceId\":\"10000000-0000-0000-0000-000000000002\""
                                + ",\"slotId\":\"" + SLOT_ID + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SLOT_NOT_FOUND"));
    }

    @Test
    void conversationSseUsesJwtUserNormalizedRequestIdAndStoresMetadataOnly() throws Exception {
        AtomicReference<PythonAgentClient.AgentRequest> forwarded = new AtomicReference<>();
        doAnswer(invocation -> {
            PythonAgentClient.AgentRequest body = invocation.getArgument(0);
            SseEmitter emitter = invocation.getArgument(1);
            @SuppressWarnings("unchecked")
            Consumer<PythonAgentClient.Terminal> terminal = invocation.getArgument(2);
            forwarded.set(body);
            String requestId = body.requestId().toString();
            emitter.send(SseEmitter.event().name("status")
                    .data("{\"requestId\":\"" + requestId + "\",\"stage\":\"classifying\",\"message\":\"正在识别需求\"}"));
            emitter.send(SseEmitter.event().name("done").data("{\"requestId\":\"" + requestId + "\"}"));
            terminal.accept(new PythonAgentClient.Terminal("COMPLETED", null, 12));
            emitter.complete();
            return null;
        }).when(pythonAgent).forward(any(), any(), any());

        MvcResult created = mvc.perform(post("/api/v1/conversations")
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID conversationId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString())
                .path("conversationId").asText());

        mvc.perform(post("/api/v1/conversations/{id}/messages", conversationId)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"问题\",\"context\":[{\"role\":\"assistant\",\"content\":\"错序\"}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_CONTEXT"));

        MvcResult streaming = mvc.perform(post("/api/v1/conversations/{id}/messages", conversationId)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString())))
                        .header("X-Request-ID", "not-a-uuid")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"武侯区助洁服务\",\"context\":[]}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mvc.perform(asyncDispatch(streaming))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")));

        assertThat(forwarded.get().userId()).isEqualTo(USER_ID);
        assertThat(forwarded.get().conversationId()).isEqualTo(conversationId);
        assertThat(forwarded.get().requestId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.message_metadata WHERE conversation_id = ?", Integer.class,
                conversationId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT char_count FROM app.message_metadata WHERE conversation_id = ? AND role='USER'",
                Integer.class, conversationId)).isEqualTo(7);
    }

    @Test
    void confirmationIsIdempotentAndCancellationRestoresCapacityOnlyOnce() {
        UUID requestId = UUID.randomUUID();
        DraftService.DraftResponse draft = drafts.create(USER_ID, SERVICE_ID, SLOT_ID);
        assertThat(draft.displayPrice()).isEqualTo("80.00");
        UUID draftId = draft.draftId();

        AppointmentService.ConfirmationResult first = appointments.confirm(USER_ID, draftId, "same-key", requestId);
        AppointmentService.ConfirmationResult retry = appointments.confirm(USER_ID, draftId, "same-key", UUID.randomUUID());
        assertThat(first.replayed()).isFalse();
        assertThat(retry.replayed()).isTrue();
        assertThat(retry.appointment().appointmentId()).isEqualTo(first.appointment().appointmentId());
        assertThat(remainingCapacity()).isEqualTo(9);

        appointments.cancel(USER_ID, first.appointment().appointmentId(), "cancel-key", requestId);
        appointments.cancel(USER_ID, first.appointment().appointmentId(), "cancel-key", UUID.randomUUID());
        assertThat(remainingCapacity()).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.critical_audit_event WHERE event_type='CANCEL_APPOINTMENT'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void oneHundredConcurrentConfirmationsCannotOversellCapacityTen() throws Exception {
        List<UUID> draftIds = java.util.stream.IntStream.range(0, 100)
                .mapToObj(ignored -> drafts.create(USER_ID, SERVICE_ID, SLOT_ID).draftId())
                .toList();
        var executor = Executors.newFixedThreadPool(20);
        try {
            List<Callable<Boolean>> calls = draftIds.stream()
                    .<Callable<Boolean>>map(draftId -> () -> {
                        try {
                            appointments.confirm(USER_ID, draftId, "load-" + draftId, UUID.randomUUID());
                            return true;
                        } catch (ApiException exception) {
                            assertThat(exception.code()).isEqualTo("SLOT_FULL");
                            return false;
                        }
                    }).toList();
            long successes = executor.invokeAll(calls).stream().filter(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }).count();

            assertThat(successes).isEqualTo(10);
            assertThat(remainingCapacity()).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.appointment", Integer.class)).isEqualTo(10);
        } finally {
            executor.shutdownNow();
        }
    }

    private int remainingCapacity() {
        return jdbc.queryForObject("SELECT remaining_capacity FROM app.service_slot WHERE id = ?", Integer.class, SLOT_ID);
    }
}
