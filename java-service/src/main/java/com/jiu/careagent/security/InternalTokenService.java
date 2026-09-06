package com.jiu.careagent.security;

import com.jiu.careagent.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalTokenService {
    private final byte[] expected;

    public InternalTokenService(@Value("${careagent.internal-token}") String token) {
        this.expected = token.getBytes(StandardCharsets.UTF_8);
    }

    public void requireValid(String provided) {
        byte[] actual = provided == null ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8);
        if (expected.length == 0 || !MessageDigest.isEqual(expected, actual)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_INTERNAL_TOKEN", "内部服务凭证无效");
        }
    }
}
