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

import static org.apache.dolphinscheduler.api.enums.Status.QUERY_ALGORITHM_RESULT_ERROR;

import org.apache.dolphinscheduler.api.dto.AlgorithmResultView;
import org.apache.dolphinscheduler.api.exceptions.ApiException;
import org.apache.dolphinscheduler.api.service.AlgorithmResultService;
import org.apache.dolphinscheduler.api.utils.Result;
import org.apache.dolphinscheduler.common.constants.Constants;
import org.apache.dolphinscheduler.dao.entity.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "ALGORITHM_RESULT_TAG")
@RestController
@RequestMapping("/projects/{projectCode}/task-instances")
public class AlgorithmResultController extends BaseController {

    private final AlgorithmResultService algorithmResultService;

    @Autowired
    public AlgorithmResultController(AlgorithmResultService algorithmResultService) {
        this.algorithmResultService = algorithmResultService;
    }

    @Operation(
            summary = "queryAlgorithmResult",
            description = "Query the normalized algorithm result associated with a task instance")
    @GetMapping("/{taskInstanceId}/algorithm-result")
    @ResponseStatus(HttpStatus.OK)
    @ApiException(QUERY_ALGORITHM_RESULT_ERROR)
    public Result<AlgorithmResultView> queryAlgorithmResult(
                                                            @Parameter(hidden = true) @RequestAttribute(
                                                                    value = Constants.SESSION_USER) User loginUser,
                                                            @Parameter(required = true) @PathVariable long projectCode,
                                                            @Parameter(required = true) @PathVariable int taskInstanceId) {
        return Result.success(
                algorithmResultService.queryTaskInstanceResult(loginUser, projectCode, taskInstanceId));
    }
}
