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

package org.apache.dolphinscheduler.api.service;

import static org.apache.dolphinscheduler.api.constants.ApiFuncIdentificationConstant.TASK_INSTANCE;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.dolphinscheduler.api.configuration.AlgorithmPlatformConfiguration;
import org.apache.dolphinscheduler.api.dto.AlgorithmExecutionReference;
import org.apache.dolphinscheduler.api.dto.AlgorithmResultView;
import org.apache.dolphinscheduler.api.enums.Status;
import org.apache.dolphinscheduler.api.exceptions.ServiceException;
import org.apache.dolphinscheduler.api.service.impl.AlgorithmResultServiceImpl;
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.dao.entity.TaskInstance;
import org.apache.dolphinscheduler.dao.entity.User;
import org.apache.dolphinscheduler.dao.repository.TaskInstanceDao;
import org.apache.dolphinscheduler.plugin.task.api.enums.DataType;
import org.apache.dolphinscheduler.plugin.task.api.enums.Direct;
import org.apache.dolphinscheduler.plugin.task.api.enums.TaskExecutionStatus;
import org.apache.dolphinscheduler.plugin.task.api.model.Property;

import java.util.Collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlgorithmResultServiceTest {

    private static final long PROJECT_CODE = 183116052518592L;
    private static final int TASK_INSTANCE_ID = 2;
    private static final int WORKFLOW_INSTANCE_ID = 2;
    private static final long EXECUTION_ID = 908L;

    @Mock
    private ProjectService projectService;

    @Mock
    private TaskInstanceDao taskInstanceDao;

    @Mock
    private AlgorithmPlatformClient algorithmPlatformClient;

    private User loginUser;
    private AlgorithmResultService service;
    private TaskInstance taskInstance;

    @BeforeEach
    void setUp() {
        loginUser = new User();
        AlgorithmPlatformConfiguration configuration = new AlgorithmPlatformConfiguration();
        configuration.setExecutionIdVariable("algorithm_execution_id");
        service = new AlgorithmResultServiceImpl(
                projectService,
                taskInstanceDao,
                algorithmPlatformClient,
                configuration);

        taskInstance = new TaskInstance();
        taskInstance.setId(TASK_INSTANCE_ID);
        taskInstance.setProjectCode(PROJECT_CODE);
        taskInstance.setWorkflowInstanceId(WORKFLOW_INSTANCE_ID);
        taskInstance.setState(TaskExecutionStatus.SUCCESS);
        taskInstance.setVarPool(JSONUtils.toJsonString(Collections.singletonList(
                new Property("algorithm_execution_id", Direct.OUT, DataType.LONG, String.valueOf(EXECUTION_ID)))));

        doNothing().when(projectService).checkProjectAndAuthThrowException(loginUser, PROJECT_CODE, TASK_INSTANCE);
        when(taskInstanceDao.queryById(TASK_INSTANCE_ID)).thenReturn(taskInstance);
    }

    @Test
    void returnsResultOnlyForTheBoundExecution() {
        when(algorithmPlatformClient.queryExecution(EXECUTION_ID)).thenReturn(matchingExecution());
        AlgorithmResultView platformResult = new AlgorithmResultView();
        platformResult.setExecutionId(EXECUTION_ID);
        when(algorithmPlatformClient.queryExecutionResult(EXECUTION_ID)).thenReturn(platformResult);

        AlgorithmResultView result = service.queryTaskInstanceResult(
                loginUser,
                PROJECT_CODE,
                TASK_INSTANCE_ID);

        Assertions.assertEquals(TASK_INSTANCE_ID, result.getTaskInstanceId());
        Assertions.assertEquals(WORKFLOW_INSTANCE_ID, result.getWorkflowInstanceId());
        verify(projectService).checkProjectAndAuthThrowException(loginUser, PROJECT_CODE, TASK_INSTANCE);
    }

    @Test
    void rejectsMissingExecutionOutputVariable() {
        taskInstance.setVarPool(null);

        ServiceException exception = Assertions.assertThrows(
                ServiceException.class,
                () -> service.queryTaskInstanceResult(loginUser, PROJECT_CODE, TASK_INSTANCE_ID));

        Assertions.assertEquals(Status.ALGORITHM_EXECUTION_ID_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    void rejectsCrossTaskExecutionBinding() {
        AlgorithmExecutionReference execution = matchingExecution();
        execution.setTaskId("ds-task-999-predict");
        when(algorithmPlatformClient.queryExecution(EXECUTION_ID)).thenReturn(execution);

        ServiceException exception = Assertions.assertThrows(
                ServiceException.class,
                () -> service.queryTaskInstanceResult(loginUser, PROJECT_CODE, TASK_INSTANCE_ID));

        Assertions.assertEquals(Status.ALGORITHM_RESULT_BINDING_MISMATCH.getCode(), exception.getCode());
    }

    @Test
    void rejectsTaskThatHasNotSucceeded() {
        taskInstance.setState(TaskExecutionStatus.RUNNING_EXECUTION);

        ServiceException exception = Assertions.assertThrows(
                ServiceException.class,
                () -> service.queryTaskInstanceResult(loginUser, PROJECT_CODE, TASK_INSTANCE_ID));

        Assertions.assertEquals(Status.ALGORITHM_TASK_RESULT_NOT_READY.getCode(), exception.getCode());
    }

    @Test
    void rejectsTaskFromAnotherProject() {
        taskInstance.setProjectCode(PROJECT_CODE + 1);

        ServiceException exception = Assertions.assertThrows(
                ServiceException.class,
                () -> service.queryTaskInstanceResult(loginUser, PROJECT_CODE, TASK_INSTANCE_ID));

        Assertions.assertEquals(Status.TASK_INSTANCE_NOT_EXISTS.getCode(), exception.getCode());
    }

    private AlgorithmExecutionReference matchingExecution() {
        AlgorithmExecutionReference execution = new AlgorithmExecutionReference();
        execution.setExecutionId(EXECUTION_ID);
        execution.setWorkflowId("ds-workflow-" + WORKFLOW_INSTANCE_ID);
        execution.setTaskId("ds-task-" + TASK_INSTANCE_ID + "-predict");
        execution.setStatus("SUCCESS");
        return execution;
    }
}
