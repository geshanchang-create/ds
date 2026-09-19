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
import org.apache.dolphinscheduler.api.dto.AlgorithmRun;
import org.apache.dolphinscheduler.api.enums.Status;
import org.apache.dolphinscheduler.api.exceptions.ServiceException;
import org.apache.dolphinscheduler.api.service.impl.AlgorithmPlatformClientImpl;
import org.apache.dolphinscheduler.common.utils.JSONUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import com.fasterxml.jackson.databind.JsonNode;

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
    void submitsTrainingWithServiceCredentialsAndSnakeCaseBody() throws Exception {
        server.enqueue(jsonResponse(201,
                "{\"training_id\":71,\"request_id\":\"run-1-train\","
                        + "\"workflow_id\":\"ds-workflow-1\",\"task_id\":\"ds-task-2\","
                        + "\"algorithm_version_id\":34,\"status\":\"RUNNING\","
                        + "\"retry_count\":0,\"max_retries\":1}"));
        AlgorithmRun.TrainingRequest request = new AlgorithmRun.TrainingRequest();
        request.setRequestId("run-1-train");
        request.setAlgorithmVersionId(34L);
        request.setRequestedModelVersion("model-v1");
        request.setWorkflowId("ds-workflow-1");
        request.setTaskId("ds-task-2");
        request.setInputConfig(Collections.singletonMap("file_id", 12));
        request.setMaxRetries(1);

        AlgorithmRun.TrainingReference result = client.submitTraining(request);
        RecordedRequest recorded = server.takeRequest();
        JsonNode body = JSONUtils.parseObject(recorded.getBody().readUtf8(), JsonNode.class);

        Assertions.assertEquals(71L, result.getTrainingId());
        Assertions.assertEquals("POST", recorded.getMethod());
        Assertions.assertEquals("/api/trainings/service", recorded.getPath());
        Assertions.assertEquals("test-service-key", recorded.getHeader("X-API-Key"));
        Assertions.assertEquals("run-1-train", body.path("request_id").asText());
        Assertions.assertEquals(34L, body.path("algorithm_version_id").asLong());
        Assertions.assertEquals(12, body.path("input_config").path("file_id").asInt());
    }

    @Test
    void queriesStopsAndReadsTrainingLogs() throws Exception {
        String training = "{\"training_id\":71,\"status\":\"RUNNING\"}";
        server.enqueue(jsonResponse(200, training));
        server.enqueue(jsonResponse(200, "{\"training_id\":71,\"status\":\"STOPPING\"}"));
        server.enqueue(jsonResponse(200,
                "[{\"id\":1,\"training_job_id\":71,\"level\":\"INFO\","
                        + "\"stream\":\"system\",\"message\":\"started\","
                        + "\"created_at\":\"2026-09-17T10:00:00+08:00\"}]"));

        Assertions.assertEquals("RUNNING", client.queryTraining(71).getStatus());
        Assertions.assertEquals("STOPPING", client.stopTraining(71).getStatus());
        List<AlgorithmRun.LogEntry> logs = client.queryTrainingLogs(71);

        Assertions.assertEquals(1, logs.size());
        Assertions.assertEquals(71L, logs.get(0).getTrainingJobId());
        Assertions.assertEquals("/api/trainings/service/71", server.takeRequest().getPath());
        RecordedRequest stop = server.takeRequest();
        Assertions.assertEquals("POST", stop.getMethod());
        Assertions.assertEquals("/api/trainings/service/71/stop", stop.getPath());
        Assertions.assertEquals("/api/trainings/service/71/logs", server.takeRequest().getPath());
    }

    @Test
    void submitsAndStopsPredictionExecution() throws Exception {
        server.enqueue(jsonResponse(201,
                "{\"execution_id\":908,\"request_id\":\"run-1-predict\","
                        + "\"workflow_id\":\"ds-workflow-1\",\"task_id\":\"ds-task-9-predict\","
                        + "\"algorithm_version_id\":34,\"model_version_id\":56,"
                        + "\"run_type\":\"PREDICT\",\"status\":\"RUNNING\"}"));
        server.enqueue(jsonResponse(200, "{\"execution_id\":908,\"status\":\"STOPPING\"}"));
        AlgorithmRun.ExecutionRequest request = new AlgorithmRun.ExecutionRequest();
        request.setRequestId("run-1-predict");
        request.setAlgorithmVersionId(34L);
        request.setModelVersionId(56L);
        request.setRunType("PREDICT");
        request.setWorkflowId("ds-workflow-1");
        request.setTaskId("ds-task-9-predict");
        request.setInputConfig(Collections.singletonMap("file_id", 12));

        AlgorithmExecutionReference submitted = client.submitExecution(request);
        AlgorithmExecutionReference stopped = client.stopExecution(908);
        RecordedRequest submit = server.takeRequest();
        JsonNode body = JSONUtils.parseObject(submit.getBody().readUtf8(), JsonNode.class);

        Assertions.assertEquals(908L, submitted.getExecutionId());
        Assertions.assertEquals("STOPPING", stopped.getStatus());
        Assertions.assertEquals("/api/executions", submit.getPath());
        Assertions.assertEquals("PREDICT", body.path("run_type").asText());
        Assertions.assertEquals(56L, body.path("model_version_id").asLong());
        Assertions.assertEquals("/api/executions/908/stop", server.takeRequest().getPath());
    }

    @Test
    void readsExecutionLogs() throws Exception {
        server.enqueue(jsonResponse(200,
                "[{\"id\":2,\"task_id\":908,\"level\":\"INFO\",\"stream\":\"stdout\","
                        + "\"message\":\"done\",\"created_at\":\"2026-09-17T10:00:01+08:00\"}]"));

        List<AlgorithmRun.LogEntry> logs = client.queryExecutionLogs(908);

        Assertions.assertEquals(908L, logs.get(0).getTaskId());
        Assertions.assertEquals("/api/executions/908/logs", server.takeRequest().getPath());
    }

    @Test
    void mapsRunValidationConflictNotFoundAndAuthenticationFailures() {
        AlgorithmRun.ExecutionRequest request = new AlgorithmRun.ExecutionRequest();
        request.setRequestId("run-errors");
        server.enqueue(new MockResponse().setResponseCode(422));
        assertRunError(Status.ALGORITHM_RUN_REQUEST_INVALID, () -> client.submitExecution(request));

        server.enqueue(new MockResponse().setResponseCode(409));
        assertRunError(Status.ALGORITHM_RUN_CONFLICT, () -> client.submitExecution(request));

        server.enqueue(new MockResponse().setResponseCode(404));
        assertRunError(Status.ALGORITHM_RUN_NOT_FOUND, () -> client.queryTraining(99));

        server.enqueue(new MockResponse().setResponseCode(401));
        assertRunError(Status.ALGORITHM_PLATFORM_AUTHENTICATION_FAILED, () -> client.queryTraining(99));
    }

    @Test
    void rejectsMalformedAndOversizedRunResponses() {
        AlgorithmRun.TrainingRequest request = new AlgorithmRun.TrainingRequest();
        request.setRequestId("run-invalid-response");
        server.enqueue(jsonResponse(201, "{}"));
        assertRunError(Status.ALGORITHM_RUN_RESPONSE_INVALID, () -> client.submitTraining(request));

        configuration.setMaxResponseBytes(8);
        server.enqueue(jsonResponse(200, "{\"training_id\":71,\"status\":\"RUNNING\"}"));
        assertRunError(Status.ALGORITHM_RUN_RESPONSE_INVALID, () -> client.queryTraining(71));
    }

    @Test
    void queriesExecutionWithServerSideApiKey() throws InterruptedException {
        server.enqueue(jsonResponse(200,
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
        server.enqueue(jsonResponse(200,
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
        server.enqueue(jsonResponse(200, "{}"));
        ServiceException malformed = Assertions.assertThrows(
                ServiceException.class,
                () -> client.queryExecutionResult(908));
        Assertions.assertEquals(Status.ALGORITHM_RESULT_INVALID.getCode(), malformed.getCode());

        configuration.setMaxResponseBytes(8);
        server.enqueue(jsonResponse(200, "{\"execution_id\":908}"));
        ServiceException oversized = Assertions.assertThrows(
                ServiceException.class,
                () -> client.queryExecutionResult(908));
        Assertions.assertEquals(Status.ALGORITHM_RESULT_INVALID.getCode(), oversized.getCode());
    }

    private void assertRunError(Status expected, Executable executable) {
        ServiceException exception = Assertions.assertThrows(ServiceException.class, executable);
        Assertions.assertEquals(expected.getCode(), exception.getCode());
        Assertions.assertFalse(exception.getMessage().contains("test-service-key"));
    }

    private MockResponse jsonResponse(int statusCode, String body) {
        return new MockResponse()
                .setResponseCode(statusCode)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
