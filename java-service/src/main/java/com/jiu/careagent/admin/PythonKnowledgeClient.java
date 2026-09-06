package com.jiu.careagent.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.jiu.careagent.common.ApiException;
import com.jiu.careagent.common.RequestIds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

@Component
public class PythonKnowledgeClient {
    private final RestClient client;
    private final String internalToken;

    public PythonKnowledgeClient(RestClient.Builder builder,
                                 @Value("${careagent.python-base-url}") String baseUrl,
                                 @Value("${careagent.internal-token}") String internalToken) {
        this.client = builder.baseUrl(baseUrl).build();
        this.internalToken = internalToken;
    }

    public JsonNode upload(byte[] content, String filename, String contentType, String title,
                           String issuingOrganization, String sourceUrl, String effectiveDate, UUID requestId) {
        ByteArrayResource file = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType));
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new HttpEntity<>(file, fileHeaders));
        parts.add("title", title);
        parts.add("issuingOrganization", issuingOrganization);
        parts.add("sourceUrl", sourceUrl == null ? "" : sourceUrl);
        parts.add("effectiveDate", effectiveDate == null ? "" : effectiveDate);
        try {
            return client.post()
                    .uri("/internal/v1/knowledge/documents")
                    .header("X-Internal-Token", internalToken)
                    .header(RequestIds.HEADER, requestId.toString())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw upstreamFailure(exception);
        }
    }

    public JsonNode status(UUID documentId, UUID requestId) {
        try {
            return client.get()
                    .uri("/internal/v1/knowledge/documents/{documentId}", documentId)
                    .header("X-Internal-Token", internalToken)
                    .header(RequestIds.HEADER, requestId.toString())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw upstreamFailure(exception);
        }
    }

    private ApiException upstreamFailure(RestClientResponseException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null || status.is5xxServerError()) {
            status = HttpStatus.BAD_GATEWAY;
        }
        return new ApiException(status, "KNOWLEDGE_SERVICE_ERROR", "知识库请求处理失败");
    }
}
