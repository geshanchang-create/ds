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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

@Slf4j
@Service
public class AlgorithmPlatformClientImpl implements AlgorithmPlatformClient {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final OkHttpClient CATALOG_HTTP_CLIENT = new OkHttpClient.Builder()
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
            OkHttpClient http = CATALOG_HTTP_CLIENT.newBuilder()
                    .connectTimeout(configuration.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS)
                    .readTimeout(configuration.getReadTimeoutMillis(), TimeUnit.MILLISECONDS)
                    .callTimeout((long) configuration.getConnectTimeoutMillis()
                            + configuration.getReadTimeoutMillis(), TimeUnit.MILLISECONDS)
                    .build();
            Request request = new Request.Builder().url(buildUrl(path))
                    .header("Accept", "application/json")
                    .header(API_KEY_HEADER, configuration.getApiKey()).get().build();
            try (Response response = http.newCall(request).execute()) {
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
                if (response.body() == null) {
                    throw new ServiceException(Status.ALGORITHM_CATALOG_INVALID);
                }
                // Bound the actual stream, including chunked responses without Content-Length.
                try (
                        InputStream input = response.body().byteStream();
                        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    long bytes = 0;
                    while ((count = input.read(buffer)) != -1) {
                        bytes += count;
                        if (bytes > configuration.getMaxResponseBytes()) {
                            throw new ServiceException(Status.ALGORITHM_CATALOG_INVALID);
                        }
                        output.write(buffer, 0, count);
                    }
                    body = new String(output.toByteArray(), StandardCharsets.UTF_8);
                }
            }
        } catch (ServiceException ex) {
            throw ex;
        } catch (IOException | IllegalArgumentException ex) {
            // Never include credentials, upstream response bodies, or request headers in errors.
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

    private boolean positiveId(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToLong() && value.longValue() > 0;
    }

    private boolean nonBlankText(JsonNode value) {
        return value.isTextual() && StringUtils.isNotBlank(value.textValue());
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
    public AlgorithmResultView queryExecutionResult(long executionId) {
        AlgorithmResultView result = get(
                "/api/executions/" + executionId + "/result-view",
                AlgorithmResultView.class,
                executionId);
        validateResult(result, executionId);
        return result;
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
