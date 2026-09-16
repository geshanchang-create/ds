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

package org.apache.dolphinscheduler.api.controller;

import static org.apache.dolphinscheduler.api.enums.Status.QUERY_ALGORITHM_CATALOG_ERROR;

import org.apache.dolphinscheduler.api.dto.AlgorithmCatalog;
import org.apache.dolphinscheduler.api.exceptions.ApiException;
import org.apache.dolphinscheduler.api.service.AlgorithmCatalogService;
import org.apache.dolphinscheduler.api.utils.Result;
import org.apache.dolphinscheduler.common.constants.Constants;
import org.apache.dolphinscheduler.dao.entity.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "ALGORITHM_CATALOG_TAG")
@RestController
@RequestMapping("/projects/{projectCode}/industrial")
public class AlgorithmCatalogController extends BaseController {

    private final AlgorithmCatalogService service;

    @Autowired
    public AlgorithmCatalogController(AlgorithmCatalogService service) {
        this.service = service;
    }

    @Operation(summary = "queryAlgorithms", description = "Query the shared algorithm catalog")
    @GetMapping("/algorithms")
    @ApiException(QUERY_ALGORITHM_CATALOG_ERROR)
    public Result<AlgorithmCatalog.Page<AlgorithmCatalog.Algorithm>> queryAlgorithms(
                                                                                     @Parameter(hidden = true) @RequestAttribute(Constants.SESSION_USER) User user,
                                                                                     @Parameter(required = true) @PathVariable("projectCode") long projectCode,
                                                                                     @Parameter(description = "Page number, starting at 1") @RequestParam(name = "pageNo", defaultValue = "1") int pageNo,
                                                                                     @Parameter(description = "Page size, 1 to 100") @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return Result.success(service.queryAlgorithms(user, projectCode, pageNo, pageSize));
    }

    @Operation(summary = "queryAlgorithmVersions", description = "Query versions including unavailability reasons")
    @GetMapping("/algorithms/{algorithmId}/versions")
    @ApiException(QUERY_ALGORITHM_CATALOG_ERROR)
    public Result<AlgorithmCatalog.Items<AlgorithmCatalog.Version>> queryVersions(
                                                                                  @Parameter(hidden = true) @RequestAttribute(Constants.SESSION_USER) User user,
                                                                                  @Parameter(required = true) @PathVariable("projectCode") long projectCode,
                                                                                  @Parameter(required = true) @PathVariable("algorithmId") long algorithmId) {
        return Result.success(service.queryVersions(user, projectCode, algorithmId));
    }

    @Operation(summary = "queryAlgorithmModels", description = "Query models compatible with the selected version")
    @GetMapping("/algorithm-versions/{versionId}/models")
    @ApiException(QUERY_ALGORITHM_CATALOG_ERROR)
    public Result<AlgorithmCatalog.Items<AlgorithmCatalog.Model>> queryModels(
                                                                              @Parameter(hidden = true) @RequestAttribute(Constants.SESSION_USER) User user,
                                                                              @Parameter(required = true) @PathVariable("projectCode") long projectCode,
                                                                              @Parameter(required = true) @PathVariable("versionId") long versionId,
                                                                              @Parameter(description = "Include disabled choices with reasons") @RequestParam(name = "includeUnavailable", defaultValue = "false") boolean includeUnavailable) {
        return Result.success(service.queryModels(user, projectCode, versionId, includeUnavailable));
    }
}
