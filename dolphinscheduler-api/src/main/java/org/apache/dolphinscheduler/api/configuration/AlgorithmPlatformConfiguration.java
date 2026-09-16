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

package org.apache.dolphinscheduler.api.configuration;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;

import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;

@Slf4j
@Data
@ToString(exclude = "apiKey")
@Validated
@Configuration
@ConfigurationProperties(prefix = "algorithm-platform")
public class AlgorithmPlatformConfiguration implements Validator {

    private boolean enabled = false;

    private String baseUrl;

    private String apiKey;

    private String executionIdVariable = "algorithm_execution_id";

    private int connectTimeoutMillis = 3000;

    private int readTimeoutMillis = 10000;

    private long maxResponseBytes = 10 * 1024 * 1024;

    @Override
    public boolean supports(Class<?> clazz) {
        return AlgorithmPlatformConfiguration.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        if (!enabled) {
            log.info("Algorithm platform integration is disabled");
            return;
        }

        if (!isValidHttpUrl(baseUrl)) {
            errors.rejectValue("baseUrl", null, "must be an absolute HTTP or HTTPS URL when enabled");
        }
        if (StringUtils.isBlank(apiKey)) {
            errors.rejectValue("apiKey", null, "must not be empty when enabled");
        }
        if (StringUtils.isBlank(executionIdVariable)) {
            errors.rejectValue("executionIdVariable", null, "must not be empty when enabled");
        }
        if (connectTimeoutMillis <= 0) {
            errors.rejectValue("connectTimeoutMillis", null, "must be greater than zero");
        }
        if (readTimeoutMillis <= 0) {
            errors.rejectValue("readTimeoutMillis", null, "must be greater than zero");
        }
        if (maxResponseBytes <= 0) {
            errors.rejectValue("maxResponseBytes", null, "must be greater than zero");
        }

        log.info(
                "Algorithm platform integration is enabled: baseUrl={}, executionIdVariable={}, "
                        + "connectTimeoutMillis={}, readTimeoutMillis={}, maxResponseBytes={}",
                baseUrl,
                executionIdVariable,
                connectTimeoutMillis,
                readTimeoutMillis,
                maxResponseBytes);
    }

    private boolean isValidHttpUrl(String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return uri.isAbsolute()
                    && uri.getHost() != null
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
