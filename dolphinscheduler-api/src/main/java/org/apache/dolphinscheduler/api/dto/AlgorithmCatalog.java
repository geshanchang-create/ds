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

import lombok.AllArgsConstructor;
import lombok.Data;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public final class AlgorithmCatalog {

    private AlgorithmCatalog() {
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Algorithm {

        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String name;
        private String description;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Version {

        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        @JsonAlias("algorithm_id")
        @JsonSerialize(using = ToStringSerializer.class)
        private Long algorithmId;
        private String version;
        private Boolean available;
        @JsonAlias("unavailable_reasons")
        private List<String> unavailableReasons;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Model {

        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        @JsonAlias("algorithm_version_id")
        @JsonSerialize(using = ToStringSerializer.class)
        private Long algorithmVersionId;
        private String version;
        private String status;
        private Boolean available;
        @JsonAlias("unavailable_reasons")
        private List<String> unavailableReasons;
    }

    @Data
    @AllArgsConstructor
    public static class Items<T> {

        private List<T> items;
    }

    @Data
    @AllArgsConstructor
    public static class Page<T> {

        private List<T> items;
        private int pageNo;
        private int pageSize;
        private Long total;
        private boolean hasNext;
    }
}
