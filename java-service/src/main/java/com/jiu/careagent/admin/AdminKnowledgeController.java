package com.jiu.careagent.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.jiu.careagent.common.ApiException;
import com.jiu.careagent.common.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/knowledge/documents")
public class AdminKnowledgeController {
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of(".md", ".pdf");

    private final PythonKnowledgeClient python;

    public AdminKnowledgeController(PythonKnowledgeClient python) {
        this.python = python;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JsonNode upload(
            @RequestParam MultipartFile file,
            @RequestParam @NotBlank @Size(max = 200) String title,
            @RequestParam @NotBlank @Size(max = 200) String issuingOrganization,
            @RequestParam(required = false) @Size(max = 1000) String sourceUrl,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveDate,
            HttpServletRequest request) throws IOException {
        String filename = file.getOriginalFilename();
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EMPTY_FILE", "文件不能为空");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "文件不能超过 10 MB");
        }
        if (!safeFilename(filename) || EXTENSIONS.stream().noneMatch(filename.toLowerCase(Locale.ROOT)::endsWith)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", "仅支持 Markdown 和 PDF 文件");
        }
        return python.upload(file.getBytes(), filename, file.getContentType(), title.trim(), issuingOrganization.trim(),
                sourceUrl, effectiveDate == null ? null : effectiveDate.toString(), RequestIds.current(request));
    }

    @GetMapping("/{documentId}")
    public JsonNode status(@PathVariable UUID documentId, HttpServletRequest request) {
        return python.status(documentId, RequestIds.current(request));
    }

    private boolean safeFilename(String filename) {
        return filename != null && !filename.isBlank() && !filename.contains("/") && !filename.contains("\\");
    }
}
