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

package org.apache.dolphinscheduler.api.service.impl;

import org.apache.dolphinscheduler.api.configuration.AlgorithmPlatformConfiguration;
import org.apache.dolphinscheduler.api.dto.AlgorithmCatalog;
import org.apache.dolphinscheduler.api.dto.AlgorithmExecutionReference;
import org.apache.dolphinscheduler.api.dto.AlgorithmResultView;
import org.apache.dolphinscheduler.api.dto.AlgorithmRun;
import org.apache.dolphinscheduler.api.enums.Status;
import org.apache.dolphinscheduler.api.exceptions.ServiceException;
import org.apache.dolphinscheduler.api.service.AlgorithmPlatformClient;
import org.apache.dolphinscheduler.common.model.OkHttpRequestHeaders;
import org.apache.dolphinscheduler.common.model.OkHttpResponse;
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.common.utils.OkHttpUtils;

import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

@Slf4j
@Service
public class AlgorithmPlatformClientImpl implements AlgorithmPlatformClient {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final OkHttpClient PLATFORM_HTTP_CLIENT = new OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    private final AlgorithmPlatformConfiguration configuration;

    @Autowired
    public AlgorithmPlatformClientImpl(AlgorithmPlatformConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public List<AlgorithmCatalog.Algorithm> queryAlgorithms(long offset, int limit) {
        List<AlgorithmCatalog.Algorithm> rows = getCatalog(
                "/api/service/catalog/algorithms?offset=" + offset + "&limit=" + limit,
                AlgorithmCatalog.Algorithm.class);
        if (rows.size() > limit) {
            throw new ServiceException(Status.ALGORITHM_CATALOG_INVALID);
        }
        return rows;
    }

    @Override
    public List<AlgorithmCatalog.Version> queryAlgorithmVersions(long algorithmId) {
        return getCatalog("/api/service/catalog/algorithms/" + algorithmId + "/versions",
                AlgorithmCatalog.Version.class);
    }

    @Override
    public List<AlgorithmCatalog.Model> queryAlgorithmModels(long versionId, boolean includeUnavailable) {
        return getCatalog("/api/service/catalog/versions/" + versionId + "/models?include_unavailable="
                + includeUnavailable, AlgorithmCatalog.Model.class);
    }

    private <T> List<T> getCatalog(String path, Class<T> type) {
        ensureEnabled();
        if (StringUtils.isBlank(configuration.getApiKey()) || StringUtils.isBlank(configuration.getBaseUrl())) {
            throw new ServiceException(Status.ALGORITHM_CATALOG_UNAVAILABLE);
        }
        String body;
        try {
            Request request = new Request.Builder().url(buildUrl(path))
                    .header("Accept", "application/json")
                    .header(API_KEY_HEADER, configuration.getApiKey()).get().build();
            try (Response response = httpClient().newCall(request).execute()) {
                switch (response.code()) {
                    case 200:
                        break;
                    case 401:
                    case 403:
                        throw new ServiceException(Status.ALGORITHM_PLATFORM_AUTHENTICATION_FAILED);
                    case 404:
                        throw new ServiceException(Status.ALGORITHM_CATALOG_NOT_FOUND);
                    default:
                        throw new ServiceException(Status.ALGORITHM_CATALOG_UNAVAILABLE);
                }
                body = readBoundedBody(response, "catalog", Status.ALGORITHM_CATALOG_INVALID);
            }
        } catch (ServiceException ex) {
            throw ex;
        } catch (IOException | IllegalArgumentException ex) {
            throw new ServiceException(Status.ALGORITHM_CATALOG_UNAVAILABLE);
        }
        try {
            JsonNode array = JSONUtils.parseObject(body, JsonNode.class);
            if (array == null || !array.isArray()) {
                throw new ServiceException(Status.ALGORITHM_CATALOG_INVALID);
            }
            List<T> result = new ArrayList<>();
            Set<Long> ids = new HashSet<>();
            for (JsonNode row : array) {
                validateCatalogRow(row, type);
                if (!ids.add(row.path("id").longValue())) {
                    throw new ServiceException(Status.ALGORITHM_CATALOG_INVALID);
                }
                result.add(JSONUtils.parseObject(row.toString(), type));
            }
            return result;
        } catch (ServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ServiceException(Status.ALGORITHM_CATALOG_INVALID);
        }
    }

    private void validateCatalogRow(JsonNode row, Class<?> type) {
        boolean valid = row.isObject() && positiveId(row.path("id"));
        if (type == AlgorithmCatalog.Algorithm.class) {
            valid &= nonBlankText(row.path("name"))
                    && row.has("description")
                    && (row.path("description").isNull() || row.path("description").isTextual());
        } else {
            JsonNode reasons = row.path("unavailable_reasons");
            valid &= nonBlankText(row.path("version")) && row.path("available").isBoolean() && reasons.isArray();
            if (reasons.isArray()) {
                for (JsonNode reason : reasons) {
                    valid &= nonBlankText(reason);
                }
                valid &= row.path("available").asBoolean() == (reasons.size() == 0);
            }
            if (type == AlgorithmCatalog.Version.class) {
                valid &= positiveId(row.path("algorithm_id"));
            } else {
                valid &= positiveId(row.path("algorithm_version_id")) && nonBlankText(row.path("status"));
                valid &= !row.path("available").asBoolean() || "AVAILABLE".equals(row.path("status").asText());
            }
        }
        if (!valid) {
            throw new ServiceException(Status.ALGORITHM_CATALOG_INVALID);
        }
    }

    @Override
    public AlgorithmRun.TrainingReference submitTraining(AlgorithmRun.TrainingRequest request) {
        String reference = request == null ? "training" : StringUtils.defaultIfBlank(request.getRequestId(), "training");
        AlgorithmRun.TrainingReference training = runRequest(
                "POST", "/api/trainings/service", request, AlgorithmRun.TrainingReference.class, reference, 200, 201);
        validateTraining(training, reference);
        return training;
    }

    @Override
    public AlgorithmRun.TrainingReference queryTraining(long trainingId) {
        String reference = Long.toString(trainingId);
        AlgorithmRun.TrainingReference training = runRequest(
                "GET", "/api/trainings/service/" + trainingId, null,
                AlgorithmRun.TrainingReference.class, reference, 200);
        validateTraining(training, reference);
        if (training.getTrainingId() != trainingId) {
            throw new ServiceException(Status.ALGORITHM_RUN_RESPONSE_INVALID, reference);
        }
        return training;
    }

    @Override
    public List<AlgorithmRun.LogEntry> queryTrainingLogs(long trainingId) {
        return runLogRequest("/api/trainings/service/" + trainingId + "/logs", Long.toString(trainingId));
    }

    @Override
    public AlgorithmRun.TrainingReference stopTraining(long trainingId) {
        String reference = Long.toString(trainingId);
        AlgorithmRun.TrainingReference training = runRequest(
                "POST", "/api/trainings/service/" + trainingId + "/stop", null,
                AlgorithmRun.TrainingReference.class, reference, 200);
        validateTraining(training, reference);
        if (training.getTrainingId() != trainingId) {
            throw new ServiceException(Status.ALGORITHM_RUN_RESPONSE_INVALID, reference);
        }
        return training;
    }

    @Override
    public AlgorithmExecutionReference submitExecution(AlgorithmRun.ExecutionRequest request) {
        String reference = request == null ? "execution" : StringUtils.defaultIfBlank(request.getRequestId(), "execution");
        AlgorithmExecutionReference execution = runRequest(
                "POST", "/api/executions", request, AlgorithmExecutionReference.class, reference, 200, 201);
        validateExecution(execution, reference);
        return execution;
    }

    @Override
    public AlgorithmExecutionReference queryExecution(long executionId) {
        AlgorithmExecutionReference execution = get(
                "/api/executions/" + executionId,
                AlgorithmExecutionReference.class,
                executionId);
        if (execution.getExecutionId() == null || execution.getExecutionId() != executionId) {
            throw new ServiceException(Status.ALGORITHM_RESULT_INVALID, executionId);
        }
        return execution;
    }

    @Override
    public List<AlgorithmRun.LogEntry> queryExecutionLogs(long executionId) {
        return runLogRequest("/api/executions/" + executionId + "/logs", Long.toString(executionId));
    }

    @Override
    public AlgorithmExecutionReference stopExecution(long executionId) {
        String reference = Long.toString(executionId);
        AlgorithmExecutionReference execution = runRequest(
                "POST", "/api/executions/" + executionId + "/stop", null,
                AlgorithmExecutionReference.class, reference, 200);
        validateExecution(execution, reference);
        if (execution.getExecutionId() != executionId) {
            throw new ServiceException(Status.ALGORITHM_RUN_RESPONSE_INVALID, reference);
        }
        return execution;
    }

    @Override
    public AlgorithmResultView queryExecutionResult(long executionId) {
        AlgorithmResultView result = get(
                "/api/executions/" + executionId + "/result-view",
                AlgorithmResultView.class,
                executionId);
        validateResult(result, executionId);
        return result;
    }

    private List<AlgorithmRun.LogEntry> runLogRequest(String path, String reference) {
        String body = executeRunRequest("GET", path, null, reference, 200);
        try {
            JsonNode array = JSONUtils.parseObject(body, JsonNode.class);
            if (array == null || !array.isArray()) {
                throw new ServiceException(Status.ALGORITHM_RUN_RESPONSE_INVALID, reference);
            }
            List<AlgorithmRun.LogEntry> logs = new ArrayList<>();
            for (JsonNode row : array) {
                AlgorithmRun.LogEntry logEntry = JSONUtils.parseObject(row.toString(), AlgorithmRun.LogEntry.class);
                if (logEntry == null || logEntry.getId() == null || logEntry.getId() <= 0
                        || StringUtils.isBlank(logEntry.getLevel()) || StringUtils.isBlank(logEntry.getStream())
                        || logEntry.getMessage() == null || StringUtils.isBlank(logEntry.getCreatedAt())) {
                    throw new ServiceException(Status.ALGORITHM_RUN_RESPONSE_INVALID, reference);
                }
                logs.add(logEntry);
            }
            return logs;
        } catch (ServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ServiceException(Status.ALGORITHM_RUN_RESPONSE_INVALID, reference);
        }
    }

    private <T> T runRequest(String method,
                             String path,
                             Object requestBody,
                             Class<T> responseType,
                             String reference,
                             int... successCodes) {
        String body = executeRunRequest(method, path, requestBody, reference, successCodes);
        try {
            T value = JSONUtils.parseObject(body, responseType);
            if (value == null) {
                throw new ServiceException(Status.ALGORITHM_RUN_RESPONSE_INVALID, reference);
            }
            return value;
        } catch (ServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ServiceException(Status.ALGORITHM_RUN_RESPONSE_INVALID, reference);
        }
    }

    private String executeRunRequest(String method,
                                     String path,
                                     Object requestBody,
                                     String reference,
                                     int... successCodes) {
        ensureEnabled();
        if (StringUtils.isBlank(configuration.getApiKey()) || StringUtils.isBlank(configuration.getBaseUrl())) {
            throw new ServiceException(Status.ALGORITHM_RUN_UNAVAILABLE, reference);
        }
        try {
            Request.Builder builder = new Request.Builder().url(buildUrl(path))
                    .header("Accept", "application/json")
                    .header(API_KEY_HEADER, configuration.getApiKey());
            if ("POST".equals(method)) {
                String json = requestBody == null ? "" : JSONUtils.toJsonString(requestBody);
                builder.post(RequestBody.create(json, JSON_MEDIA_TYPE));
            } else {
                builder.get();
            }
            try (Response response = httpClient().newCall(builder.build()).execute()) {
                if (Arrays.stream(successCodes).noneMatch(code -> code == response.code())) {
                    throw mapRunStatus(response.code(), reference);
                }
                return readBoundedBody(response, reference, Status.ALGORITHM_RUN_RESPONSE_INVALID);
            }
        } catch (ServiceException ex) {
            throw ex;
        } catch (IOException | IllegalArgumentException ex) {
            log.warn("Algorithm platform run request failed for {}: {}", reference, ex.getClass().getSimpleName());
            throw new ServiceException(Status.ALGORITHM_RUN_UNAVAILABLE, reference);
        }
    }

    private ServiceException mapRunStatus(int statusCode, String reference) {
        switch (statusCode) {
            case 400:
            case 422:
                return new ServiceException(Status.ALGORITHM_RUN_REQUEST_INVALID, reference);
            case 401:
            case 403:
                return new ServiceException(Status.ALGORITHM_PLATFORM_AUTHENTICATION_FAILED);
            case 404:
                return new ServiceException(Status.ALGORITHM_RUN_NOT_FOUND, reference);
            case 409:
                return new ServiceException(Status.ALGORITHM_RUN_CONFLICT, reference);
            default:
                return new ServiceException(Status.ALGORITHM_RUN_UNAVAILABLE, reference);
        }
    }

    private void validateTraining(AlgorithmRun.TrainingReference training, String reference) {
        if (training.getTrainingId() == null || training.getTrainingId() <= 0
                || StringUtils.isBlank(training.getStatus())) {
            throw new ServiceException(Status.ALGORITHM_RUN_RESPONSE_INVALID, reference);
        }
    }

    private void validateExecution(AlgorithmExecutionReference execution, String reference) {
        if (execution.getExecutionId() == null || execution.getExecutionId() <= 0
                || StringUtils.isBlank(execution.getStatus())) {
            throw new ServiceException(Status.ALGORITHM_RUN_RESPONSE_INVALID, reference);
        }
    }

    private <T> T get(String path, Class<T> responseType, long executionId) {
        ensureEnabled();
        OkHttpRequestHeaders requestHeaders = new OkHttpRequestHeaders();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put(API_KEY_HEADER, configuration.getApiKey());
        requestHeaders.setHeaders(headers);

        OkHttpResponse response;
        try {
            response = OkHttpUtils.get(
                    buildUrl(path),
                    requestHeaders,
                    null,
                    configuration.getConnectTimeoutMillis(),
                    configuration.getReadTimeoutMillis(),
                    configuration.getReadTimeoutMillis());
        } catch (Exception ex) {
            log.warn("Algorithm platform request failed for executionId={}: {}", executionId, ex.getMessage());
            throw new ServiceException(Status.ALGORITHM_PLATFORM_UNAVAILABLE, executionId);
        }

        ensureSuccessful(response, executionId);
        String body = response.getBody();
        if (body == null || body.getBytes(StandardCharsets.UTF_8).length > configuration.getMaxResponseBytes()) {
            throw new ServiceException(Status.ALGORITHM_RESULT_INVALID, executionId);
        }
        try {
            T value = JSONUtils.parseObject(body, responseType);
            if (value == null) {
                throw new ServiceException(Status.ALGORITHM_RESULT_INVALID, executionId);
            }
            return value;
        } catch (ServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Algorithm platform returned invalid JSON for executionId={}", executionId);
            throw new ServiceException(Status.ALGORITHM_RESULT_INVALID, executionId);
        }
    }

    private OkHttpClient httpClient() {
        return PLATFORM_HTTP_CLIENT.newBuilder()
                .connectTimeout(configuration.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS)
                .writeTimeout(configuration.getReadTimeoutMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(configuration.getReadTimeoutMillis(), TimeUnit.MILLISECONDS)
                .callTimeout((long) configuration.getConnectTimeoutMillis()
                        + configuration.getReadTimeoutMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    private String readBoundedBody(Response response, String reference, Status invalidStatus) throws IOException {
        if (response.body() == null) {
            throw new ServiceException(invalidStatus, reference);
        }
        try (
                InputStream input = response.body().byteStream();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            long bytes = 0;
            while ((count = input.read(buffer)) != -1) {
                bytes += count;
                if (bytes > configuration.getMaxResponseBytes()) {
                    throw new ServiceException(invalidStatus, reference);
                }
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void ensureEnabled() {
        if (!configuration.isEnabled()) {
            throw new ServiceException(Status.ALGORITHM_PLATFORM_DISABLED);
        }
    }

    private void ensureSuccessful(OkHttpResponse response, long executionId) {
        switch (response.getStatusCode()) {
            case 200:
                return;
            case 400:
                throw new ServiceException(Status.ALGORITHM_RESULT_NOT_READY, executionId);
            case 401:
            case 403:
                throw new ServiceException(Status.ALGORITHM_PLATFORM_AUTHENTICATION_FAILED);
            case 404:
                throw new ServiceException(Status.ALGORITHM_RESULT_NOT_FOUND, executionId);
            case 422:
                throw new ServiceException(Status.ALGORITHM_RESULT_INVALID, executionId);
            default:
                throw new ServiceException(Status.ALGORITHM_PLATFORM_UNAVAILABLE, executionId);
        }
    }

    private String buildUrl(String path) {
        String baseUrl = StringUtils.removeEnd(configuration.getBaseUrl(), "/");
        return baseUrl + path;
    }

    private boolean positiveId(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToLong() && value.longValue() > 0;
    }

    private boolean nonBlankText(JsonNode value) {
        return value.isTextual() && StringUtils.isNotBlank(value.textValue());
    }

    private void validateResult(AlgorithmResultView result, long executionId) {
        boolean valid = result.getExecutionId() != null
                && result.getExecutionId() == executionId
                && StringUtils.isNotBlank(result.getStatus())
                && StringUtils.isNotBlank(result.getSchemaVersion())
                && StringUtils.isNotBlank(result.getTaskType())
                && result.getRecordCount() != null
                && result.getRecordCount() >= 0
                && result.getRecords() != null
                && result.getRecordCount() == result.getRecords().size()
                && result.getRequiredFields() != null
                && result.getOptionalFields() != null
                && result.getFieldDefinitions() != null
                && result.getMetadata() != null
                && result.getWarnings() != null;
        if (!valid) {
            throw new ServiceException(Status.ALGORITHM_RESULT_INVALID, executionId);
        }
    }
}
