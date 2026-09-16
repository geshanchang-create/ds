/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.dolphinscheduler.api.configuration.AlgorithmPlatformConfiguration;
import org.apache.dolphinscheduler.api.dto.AlgorithmCatalog;
import org.apache.dolphinscheduler.api.enums.Status;
import org.apache.dolphinscheduler.api.exceptions.ServiceException;
import org.apache.dolphinscheduler.api.service.impl.AlgorithmPlatformClientImpl;
import org.apache.dolphinscheduler.common.utils.JSONUtils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlgorithmCatalogClientTest {

    private MockWebServer server;
    private AlgorithmPlatformConfiguration config;
    private AlgorithmPlatformClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        config = new AlgorithmPlatformConfiguration();
        config.setEnabled(true);
        config.setBaseUrl(server.url("/").toString());
        config.setApiKey("private-service-key");
        config.setConnectTimeoutMillis(500);
        config.setReadTimeoutMillis(500);
        client = new AlgorithmPlatformClientImpl(config);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void sendsServiceKeyAndWhitelistsResponseFields() throws InterruptedException {
        enqueue("[{\"id\":9007199254740993,\"name\":\"Demo\",\"description\":null,"
                + "\"file_path\":\"secret-path\",\"owner_id\":8,\"api_key\":\"private-service-key\"}]");
        AlgorithmCatalog.Algorithm item = client.queryAlgorithms(20, 21).get(0);
        RecordedRequest request = server.takeRequest();
        assertEquals("/api/service/catalog/algorithms?offset=20&limit=21", request.getPath());
        assertEquals("private-service-key", request.getHeader("X-API-Key"));
        String json = JSONUtils.toJsonString(item);
        assertTrue(json.contains("\"id\":\"9007199254740993\""));
        assertFalse(json.contains("secret-path"));
        assertFalse(json.contains("owner"));
        assertFalse(json.contains("private-service-key"));
    }

    @Test
    void mapsVersionAndModelFields() throws InterruptedException {
        enqueue("[{\"id\":20,\"algorithm_id\":10,\"version\":\"0.3\","
                + "\"available\":false,\"unavailable_reasons\":[\"ALGORITHM_FILE_MISSING\"]}]");
        AlgorithmCatalog.Version version = client.queryAlgorithmVersions(10).get(0);
        assertEquals(10L, version.getAlgorithmId());
        assertFalse(version.getAvailable());
        assertEquals("/api/service/catalog/algorithms/10/versions", server.takeRequest().getPath());
        enqueue("[{\"id\":30,\"algorithm_version_id\":20,\"version\":\"m1\","
                + "\"status\":\"AVAILABLE\",\"available\":true,\"unavailable_reasons\":[]}]");
        assertEquals(20L, client.queryAlgorithmModels(20, true).get(0).getAlgorithmVersionId());
        assertEquals("/api/service/catalog/versions/20/models?include_unavailable=true",
                server.takeRequest().getPath());
    }

    @Test
    void rejectsMalformedOrInconsistentCatalogs() {
        String[] invalid = {"{}", "null", "[null]", "[{\"id\":1,\"name\":\"a\"}]",
                "[{\"id\":\"1\",\"name\":\"a\",\"description\":null}]",
                "[{\"id\":1,\"name\":\"a\",\"description\":null},"
                        + "{\"id\":1,\"name\":\"b\",\"description\":null}]"};
        for (String body : invalid) {
            enqueue(body);
            assertCode(Status.ALGORITHM_CATALOG_INVALID, () -> client.queryAlgorithms(0, 20));
        }
        enqueue("[{\"id\":30,\"algorithm_version_id\":20,\"version\":\"m1\","
                + "\"status\":\"FAILED\",\"available\":true,\"unavailable_reasons\":[]}]");
        assertCode(Status.ALGORITHM_CATALOG_INVALID, () -> client.queryAlgorithmModels(20, false));
        enqueue("[{\"id\":20,\"algorithm_id\":10,\"version\":\"v1\","
                + "\"available\":false,\"unavailable_reasons\":[]}]");
        assertCode(Status.ALGORITHM_CATALOG_INVALID, () -> client.queryAlgorithmVersions(10));
    }

    @Test
    void mapsRemoteErrorsWithoutExposingBodies() {
        int[] statuses = {401, 403, 404, 503};
        Status[] expected = {Status.ALGORITHM_PLATFORM_AUTHENTICATION_FAILED,
                Status.ALGORITHM_PLATFORM_AUTHENTICATION_FAILED,
                Status.ALGORITHM_CATALOG_NOT_FOUND, Status.ALGORITHM_CATALOG_UNAVAILABLE};
        for (int i = 0; i < statuses.length; i++) {
            server.enqueue(new MockResponse().setResponseCode(statuses[i]).setBody("private-service-key"));
            assertCode(expected[i], () -> client.queryAlgorithms(0, 20));
        }
    }

    @Test
    void doesNotFollowRedirectsOrSendKeyToAnotherServer() throws IOException {
        try (MockWebServer other = new MockWebServer()) {
            other.start();
            other.enqueue(new MockResponse().setBody("[]"));
            server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", other.url("/")));
            assertCode(Status.ALGORITHM_CATALOG_UNAVAILABLE, () -> client.queryAlgorithms(0, 20));
            assertEquals(0, other.getRequestCount());
        }
    }

    @Test
    void boundsChunkedResponsesAndMapsTimeouts() {
        config.setMaxResponseBytes(8);
        server.enqueue(new MockResponse().setChunkedBody("[{\"id\":123}]", 3));
        assertCode(Status.ALGORITHM_CATALOG_INVALID, () -> client.queryAlgorithms(0, 20));
        server.enqueue(new MockResponse().setBody("[]").setHeadersDelay(2, TimeUnit.SECONDS));
        assertCode(Status.ALGORITHM_CATALOG_UNAVAILABLE, () -> client.queryAlgorithms(0, 20));
    }

    @Test
    void disabledOrMissingKeyDoesNotCallPlatform() {
        config.setEnabled(false);
        assertCode(Status.ALGORITHM_PLATFORM_DISABLED, () -> client.queryAlgorithms(0, 20));
        config.setEnabled(true);
        config.setApiKey("");
        assertCode(Status.ALGORITHM_CATALOG_UNAVAILABLE, () -> client.queryAlgorithms(0, 20));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void acceptsEmptyCatalog() {
        enqueue("[]");
        assertTrue(client.queryAlgorithms(0, 20).isEmpty());
    }

    private void enqueue(String json) {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(json));
    }

    private void assertCode(Status expected, org.junit.jupiter.api.function.Executable action) {
        ServiceException error = assertThrows(ServiceException.class, action);
        assertEquals(expected.getCode(), error.getCode());
        assertFalse(error.getMessage().contains("private-service-key"));
    }
}
