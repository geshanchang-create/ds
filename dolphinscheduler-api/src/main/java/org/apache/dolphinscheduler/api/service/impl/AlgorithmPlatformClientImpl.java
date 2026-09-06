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

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AlgorithmPlatformClientImpl implements AlgorithmPlatformClient {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final AlgorithmPlatformConfiguration configuration;

    @Autowired
    public AlgorithmPlatformClientImpl(AlgorithmPlatformConfiguration configuration) {
        this.configuration = configuration;
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
