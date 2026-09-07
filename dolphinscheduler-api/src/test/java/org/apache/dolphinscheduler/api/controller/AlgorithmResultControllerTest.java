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

import static org.mockito.Mockito.when;

import org.apache.dolphinscheduler.api.dto.AlgorithmResultView;
import org.apache.dolphinscheduler.api.enums.Status;
import org.apache.dolphinscheduler.api.service.AlgorithmResultService;
import org.apache.dolphinscheduler.api.utils.Result;
import org.apache.dolphinscheduler.dao.entity.User;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlgorithmResultControllerTest {

    @Mock
    private AlgorithmResultService algorithmResultService;

    @Test
    void wrapsTheNormalizedResultInTheStandardDsResponse() {
        User loginUser = new User();
        AlgorithmResultView resultView = new AlgorithmResultView();
        resultView.setExecutionId(908L);
        when(algorithmResultService.queryTaskInstanceResult(loginUser, 10L, 20)).thenReturn(resultView);
        AlgorithmResultController controller = new AlgorithmResultController(algorithmResultService);

        Result<AlgorithmResultView> result = controller.queryAlgorithmResult(loginUser, 10L, 20);

        Assertions.assertEquals(Status.SUCCESS.getCode(), result.getCode());
        Assertions.assertSame(resultView, result.getData());
    }
}
