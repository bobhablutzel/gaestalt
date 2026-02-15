/*
 * Copyright (c) 2026 Bob Hablutzel. All rights reserved.
 *
 * Licensed under a dual-license model: freely available for non-commercial use;
 * commercial use requires a separate license. See LICENSE file for details.
 * Contact license@gaestalt.com for commercial licensing.
 */

package com.gaestalt.address.provider.usps;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UspsOAuthServiceTest {

    private UspsConfig uspsConfig;
    private RestClient restClient;
    private RestClient.ResponseSpec responseSpec;
    private Tracer tracer;
    private UspsOAuthService oAuthService;

    @BeforeEach
    void setUp() {
        uspsConfig = new UspsConfig();
        uspsConfig.setClientId("test-client-id");
        uspsConfig.setClientSecret("test-client-secret");
        uspsConfig.setBaseUrl("https://apis.usps.com");

        tracer = OpenTelemetry.noop().getTracer("test");

        // Use RETURNS_DEEP_STUBS so the entire fluent chain auto-stubs
        restClient = mock(RestClient.class, Answers.RETURNS_DEEP_STUBS);
        responseSpec = mock(RestClient.ResponseSpec.class);

        // Wire the terminal .retrieve() to our controlled responseSpec
        when(restClient.post()
                .uri(any(String.class))
                .contentType(any())
                .body(any(Object.class))
                .retrieve())
                .thenReturn(responseSpec);

        final var restClientBuilder = mock(RestClient.Builder.class);
        when(restClientBuilder.baseUrl(any(String.class))).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        oAuthService = new UspsOAuthService(uspsConfig, restClientBuilder, tracer);

        // Clear the invocation recorded during deep-stub setup
        clearInvocations(restClient);
    }

    @Test
    void firstCallRequestsNewToken() {
        final var tokenResponse = new UspsOAuthTokenResponse();
        tokenResponse.setAccessToken("test-token-abc");
        tokenResponse.setExpiresIn("3600");

        when(responseSpec.body(UspsOAuthTokenResponse.class)).thenReturn(tokenResponse);

        final var token = oAuthService.getAccessToken();

        assertEquals("test-token-abc", token);
        verify(restClient, times(1)).post();
    }

    @Test
    void secondCallReturnsCachedToken() {
        final var tokenResponse = new UspsOAuthTokenResponse();
        tokenResponse.setAccessToken("cached-token");
        tokenResponse.setExpiresIn("3600");

        when(responseSpec.body(UspsOAuthTokenResponse.class)).thenReturn(tokenResponse);

        final var first = oAuthService.getAccessToken();
        final var second = oAuthService.getAccessToken();

        assertEquals("cached-token", first);
        assertEquals("cached-token", second);
        verify(restClient, times(1)).post();
    }

    @Test
    void expiredTokenTriggersRefresh() {
        final var tokenResponse1 = new UspsOAuthTokenResponse();
        tokenResponse1.setAccessToken("first-token");
        tokenResponse1.setExpiresIn("3600");

        final var tokenResponse2 = new UspsOAuthTokenResponse();
        tokenResponse2.setAccessToken("refreshed-token");
        tokenResponse2.setExpiresIn("3600");

        when(responseSpec.body(UspsOAuthTokenResponse.class))
                .thenReturn(tokenResponse1)
                .thenReturn(tokenResponse2);

        final var first = oAuthService.getAccessToken();
        assertEquals("first-token", first);

        // Force expiry by setting tokenExpiry to the past
        ReflectionTestUtils.setField(oAuthService, "tokenExpiry", Instant.now().minusSeconds(10));

        final var refreshed = oAuthService.getAccessToken();
        assertEquals("refreshed-token", refreshed);
        verify(restClient, times(2)).post();
    }

    @Test
    void nullResponseThrowsRuntimeException() {
        when(responseSpec.body(UspsOAuthTokenResponse.class)).thenReturn(null);

        assertThrows(RuntimeException.class, () -> oAuthService.getAccessToken());
    }

    @Test
    void responseWithNullAccessTokenThrowsRuntimeException() {
        final var tokenResponse = new UspsOAuthTokenResponse();
        tokenResponse.setAccessToken(null);
        tokenResponse.setExpiresIn("3600");

        when(responseSpec.body(UspsOAuthTokenResponse.class)).thenReturn(tokenResponse);

        assertThrows(RuntimeException.class, () -> oAuthService.getAccessToken());
    }

    @Test
    void invalidateTokenClearsCacheSoNextCallReRequests() {
        final var tokenResponse1 = new UspsOAuthTokenResponse();
        tokenResponse1.setAccessToken("token-before-invalidate");
        tokenResponse1.setExpiresIn("3600");

        final var tokenResponse2 = new UspsOAuthTokenResponse();
        tokenResponse2.setAccessToken("token-after-invalidate");
        tokenResponse2.setExpiresIn("3600");

        when(responseSpec.body(UspsOAuthTokenResponse.class))
                .thenReturn(tokenResponse1)
                .thenReturn(tokenResponse2);

        final var before = oAuthService.getAccessToken();
        assertEquals("token-before-invalidate", before);

        oAuthService.invalidateToken();

        final var after = oAuthService.getAccessToken();
        assertEquals("token-after-invalidate", after);
        verify(restClient, times(2)).post();
    }
}
