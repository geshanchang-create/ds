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

import org.apache.dolphinscheduler.api.configuration.AlgorithmPlatformConfiguration;
import org.apache.dolphinscheduler.api.dto.AlgorithmExecutionReference;
import org.apache.dolphinscheduler.api.dto.AlgorithmResultView;
import org.apache.dolphinscheduler.api.enums.Status;
import org.apache.dolphinscheduler.api.exceptions.ServiceException;
import org.apache.dolphinscheduler.api.service.impl.AlgorithmPlatformClientImpl;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

class AlgorithmPlatformClientTest {

    private MockWebServer server;
    private AlgorithmPlatformConfiguration configuration;
    private AlgorithmPlatformClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        configuration = new AlgorithmPlatformConfiguration();
        configuration.setEnabled(true);
        configuration.setBaseUrl(server.url("/").toString());
        configuration.setApiKey("test-service-key");
        configuration.setConnectTimeoutMillis(1000);
        configuration.setReadTimeoutMillis(1000);
        client = new AlgorithmPlatformClientImpl(configuration);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void queriesExecutionWithServerSideApiKey() throws InterruptedException {
        server.enqueue(jsonResponse(
                "{\"execution_id\":908,\"workflow_id\":\"ds-workflow-2\","
                        + "\"task_id\":\"ds-task-2-predict\",\"status\":\"SUCCESS\"}"));

        AlgorithmExecutionReference execution = client.queryExecution(908);
        RecordedRequest request = server.takeRequest();

        Assertions.assertEquals(908L, execution.getExecutionId());
        Assertions.assertEquals("/api/executions/908", request.getPath());
        Assertions.assertEquals("test-service-key", request.getHeader("X-API-Key"));
    }

    @Test
    void parsesNormalizedResultContract() {
        server.enqueue(jsonResponse(
                "{\"execution_id\":908,\"status\":\"SUCCESS\",\"schema_version\":\"1.0\","
                        + "\"task_type\":\"anomaly_detection\",\"result_status\":\"ok\","
                        + "\"record_count\":1,\"records\":[{\"score\":0.91,\"prediction\":1}],"
                        + "\"required_fields\":[\"score\",\"prediction\"],\"optional_fields\":[],"
                        + "\"field_definitions\":{},\"metrics\":{\"accuracy\":0.9},"
                        + "\"metadata\":{},\"warnings\":[]}"));

        AlgorithmResultView result = client.queryExecutionResult(908);

        Assertions.assertEquals("anomaly_detection", result.getTaskType());
        Assertions.assertEquals(1, result.getRecordCount());
        Assertions.assertEquals(0.91, result.getRecords().get(0).get("score"));
    }

    @Test
    void mapsAuthenticationFailureWithoutLeakingTheKey() {
        server.enqueue(new MockResponse().setResponseCode(401));

        ServiceException exception = Assertions.assertThrows(
                ServiceException.class,
                () -> client.queryExecution(908));

        Assertions.assertEquals(Status.ALGORITHM_PLATFORM_AUTHENTICATION_FAILED.getCode(), exception.getCode());
        Assertions.assertFalse(exception.getMessage().contains("test-service-key"));
    }

    @Test
    void rejectsMalformedAndOversizedResults() {
        server.enqueue(jsonResponse("{}"));
        ServiceException malformed = Assertions.assertThrows(
                ServiceException.class,
                () -> client.queryExecutionResult(908));
        Assertions.assertEquals(Status.ALGORITHM_RESULT_INVALID.getCode(), malformed.getCode());

        configuration.setMaxResponseBytes(8);
        server.enqueue(jsonResponse("{\"execution_id\":908}"));
        ServiceException oversized = Assertions.assertThrows(
                ServiceException.class,
                () -> client.queryExecutionResult(908));
        Assertions.assertEquals(Status.ALGORITHM_RESULT_INVALID.getCode(), oversized.getCode());
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
