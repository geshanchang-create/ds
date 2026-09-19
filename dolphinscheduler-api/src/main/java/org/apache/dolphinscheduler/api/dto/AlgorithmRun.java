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

package org.apache.dolphinscheduler.api.dto;

import java.util.Map;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class AlgorithmRun {

    private AlgorithmRun() {
        throw new UnsupportedOperationException("Utility class");
    }

    @Data
    public static class TrainingRequest {

        @JsonProperty("request_id")
        private String requestId;

        @JsonProperty("algorithm_version_id")
        private Long algorithmVersionId;

        @JsonProperty("requested_model_version")
        private String requestedModelVersion;

        @JsonProperty("workflow_id")
        private String workflowId;

        @JsonProperty("task_id")
        private String taskId;

        private Map<String, Object> params;

        @JsonProperty("input_config")
        private Map<String, Object> inputConfig;

        @JsonProperty("output_config")
        private Map<String, Object> outputConfig;

        @JsonProperty("timeout_seconds")
        private Integer timeoutSeconds;

        @JsonProperty("max_retries")
        private Integer maxRetries;
    }

    @Data
    public static class ExecutionRequest {

        @JsonProperty("request_id")
        private String requestId;

        @JsonProperty("algorithm_version_id")
        private Long algorithmVersionId;

        @JsonProperty("model_version_id")
        private Long modelVersionId;

        @JsonProperty("run_type")
        private String runType;

        @JsonProperty("workflow_id")
        private String workflowId;

        @JsonProperty("task_id")
        private String taskId;

        private Map<String, Object> params;

        @JsonProperty("input_config")
        private Map<String, Object> inputConfig;

        @JsonProperty("output_config")
        private Map<String, Object> outputConfig;

        @JsonProperty("timeout_seconds")
        private Integer timeoutSeconds;

        @JsonProperty("max_retries")
        private Integer maxRetries;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TrainingReference {

        @JsonProperty("training_id")
        private Long trainingId;

        @JsonProperty("request_id")
        private String requestId;

        @JsonProperty("workflow_id")
        private String workflowId;

        @JsonProperty("task_id")
        private String taskId;

        @JsonProperty("algorithm_version_id")
        private Long algorithmVersionId;

        private String status;

        @JsonProperty("error_message")
        private String errorMessage;

        @JsonProperty("retry_count")
        private Integer retryCount;

        @JsonProperty("max_retries")
        private Integer maxRetries;

        @JsonProperty("model_version_id")
        private Long modelVersionId;

        @JsonProperty("model_version_status")
        private String modelVersionStatus;

        private Map<String, Object> metrics;

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("started_at")
        private String startedAt;

        @JsonProperty("finished_at")
        private String finishedAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LogEntry {

        private Long id;

        @JsonProperty("training_job_id")
        private Long trainingJobId;

        @JsonProperty("task_id")
        private Long taskId;

        private String level;

        private String stream;

        private String message;

        @JsonProperty("created_at")
        private String createdAt;
    }
}
