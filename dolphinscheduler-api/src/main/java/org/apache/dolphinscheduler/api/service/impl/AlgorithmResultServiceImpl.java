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

import static org.apache.dolphinscheduler.api.constants.ApiFuncIdentificationConstant.TASK_INSTANCE;

import org.apache.dolphinscheduler.api.configuration.AlgorithmPlatformConfiguration;
import org.apache.dolphinscheduler.api.dto.AlgorithmExecutionReference;
import org.apache.dolphinscheduler.api.dto.AlgorithmResultView;
import org.apache.dolphinscheduler.api.enums.Status;
import org.apache.dolphinscheduler.api.exceptions.ServiceException;
import org.apache.dolphinscheduler.api.service.AlgorithmPlatformClient;
import org.apache.dolphinscheduler.api.service.AlgorithmResultService;
import org.apache.dolphinscheduler.api.service.ProjectService;
import org.apache.dolphinscheduler.dao.entity.TaskInstance;
import org.apache.dolphinscheduler.dao.entity.User;
import org.apache.dolphinscheduler.dao.repository.TaskInstanceDao;
import org.apache.dolphinscheduler.plugin.task.api.enums.Direct;
import org.apache.dolphinscheduler.plugin.task.api.model.Property;
import org.apache.dolphinscheduler.plugin.task.api.utils.VarPoolUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AlgorithmResultServiceImpl implements AlgorithmResultService {

    private static final String DS_WORKFLOW_ID_PREFIX = "ds-workflow-";
    private static final String DS_TASK_ID_PREFIX = "ds-task-";
    private static final String PREDICTION_TASK_ID_SUFFIX = "-predict";

    private final ProjectService projectService;
    private final TaskInstanceDao taskInstanceDao;
    private final AlgorithmPlatformClient algorithmPlatformClient;
    private final AlgorithmPlatformConfiguration configuration;

    @Autowired
    public AlgorithmResultServiceImpl(ProjectService projectService,
                                      TaskInstanceDao taskInstanceDao,
                                      AlgorithmPlatformClient algorithmPlatformClient,
                                      AlgorithmPlatformConfiguration configuration) {
        this.projectService = projectService;
        this.taskInstanceDao = taskInstanceDao;
        this.algorithmPlatformClient = algorithmPlatformClient;
        this.configuration = configuration;
    }

    @Override
    public AlgorithmResultView queryTaskInstanceResult(User loginUser, long projectCode, int taskInstanceId) {
        projectService.checkProjectAndAuthThrowException(loginUser, projectCode, TASK_INSTANCE);

        TaskInstance taskInstance = taskInstanceDao.queryById(taskInstanceId);
        if (taskInstance == null
                || taskInstance.getProjectCode() == null
                || taskInstance.getProjectCode() != projectCode) {
            throw new ServiceException(Status.TASK_INSTANCE_NOT_EXISTS, taskInstanceId);
        }
        if (taskInstance.getState() == null || !taskInstance.getState().isSuccess()) {
            throw new ServiceException(Status.ALGORITHM_TASK_RESULT_NOT_READY, taskInstanceId);
        }

        long executionId = resolveExecutionId(taskInstance);
        AlgorithmExecutionReference execution = algorithmPlatformClient.queryExecution(executionId);
        validateExecutionBinding(taskInstance, execution);
        if (!"SUCCESS".equalsIgnoreCase(execution.getStatus())) {
            throw new ServiceException(Status.ALGORITHM_RESULT_NOT_READY, executionId);
        }

        AlgorithmResultView result = algorithmPlatformClient.queryExecutionResult(executionId);
        result.setWorkflowInstanceId(taskInstance.getWorkflowInstanceId());
        result.setTaskInstanceId(taskInstance.getId());
        return result;
    }

    private long resolveExecutionId(TaskInstance taskInstance) {
        List<Property> properties;
        try {
            properties = VarPoolUtils.deserializeVarPool(taskInstance.getVarPool());
        } catch (RuntimeException ex) {
            throw new ServiceException(Status.ALGORITHM_EXECUTION_ID_INVALID, taskInstance.getId());
        }

        String variableName = configuration.getExecutionIdVariable();
        for (Property property : properties) {
            if (property != null
                    && Direct.OUT.equals(property.getDirect())
                    && variableName.equals(property.getProp())
                    && StringUtils.isNotBlank(property.getValue())) {
                try {
                    long executionId = Long.parseLong(property.getValue());
                    if (executionId > 0) {
                        return executionId;
                    }
                } catch (NumberFormatException ignored) {
                    // Converted to a stable API error below.
                }
                throw new ServiceException(Status.ALGORITHM_EXECUTION_ID_INVALID, taskInstance.getId());
            }
        }
        throw new ServiceException(Status.ALGORITHM_EXECUTION_ID_NOT_FOUND, taskInstance.getId(), variableName);
    }

    private void validateExecutionBinding(TaskInstance taskInstance, AlgorithmExecutionReference execution) {
        String expectedWorkflowId = DS_WORKFLOW_ID_PREFIX + taskInstance.getWorkflowInstanceId();
        String expectedTaskId = DS_TASK_ID_PREFIX + taskInstance.getId();
        boolean taskIdMatches = expectedTaskId.equals(execution.getTaskId())
                || (expectedTaskId + PREDICTION_TASK_ID_SUFFIX).equals(execution.getTaskId());
        if (!expectedWorkflowId.equals(execution.getWorkflowId()) || !taskIdMatches) {
            throw new ServiceException(
                    Status.ALGORITHM_RESULT_BINDING_MISMATCH,
                    execution.getExecutionId(),
                    taskInstance.getId());
        }
    }
}
