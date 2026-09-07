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

import java.util.List;
import java.util.Map;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlgorithmResultView {

    private Integer workflowInstanceId;

    private Integer taskInstanceId;

    @JsonAlias("execution_id")
    private Long executionId;

    private String status;

    @JsonAlias("schema_version")
    private String schemaVersion;

    @JsonAlias("task_type")
    private String taskType;

    @JsonAlias("result_status")
    private String resultStatus;

    @JsonAlias("record_count")
    private Integer recordCount;

    private List<Map<String, Object>> records;

    @JsonAlias("required_fields")
    private List<String> requiredFields;

    @JsonAlias("optional_fields")
    private List<String> optionalFields;

    @JsonAlias("field_definitions")
    private Map<String, Object> fieldDefinitions;

    private Object metrics;

    private Map<String, Object> metadata;

    private List<String> warnings;
}
