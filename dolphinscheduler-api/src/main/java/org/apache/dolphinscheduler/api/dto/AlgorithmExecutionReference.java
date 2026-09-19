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

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlgorithmExecutionReference {

    @JsonAlias("execution_id")
    private Long executionId;

    @JsonAlias("request_id")
    private String requestId;

    @JsonAlias("algorithm_version_id")
    private Long algorithmVersionId;

    @JsonAlias("model_version_id")
    private Long modelVersionId;

    @JsonAlias("run_type")
    private String runType;

    @JsonAlias("workflow_id")
    private String workflowId;

    @JsonAlias("task_id")
    private String taskId;

    private String status;

    @JsonAlias("error_message")
    private String errorMessage;

    @JsonAlias("retry_count")
    private Integer retryCount;

    @JsonAlias("max_retries")
    private Integer maxRetries;

    @JsonAlias("created_at")
    private String createdAt;

    @JsonAlias("started_at")
    private String startedAt;

    @JsonAlias("finished_at")
    private String finishedAt;
}
