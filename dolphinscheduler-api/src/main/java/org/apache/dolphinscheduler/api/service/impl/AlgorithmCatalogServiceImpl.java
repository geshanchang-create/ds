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

import static org.apache.dolphinscheduler.api.constants.ApiFuncIdentificationConstant.PROJECT;

import org.apache.dolphinscheduler.api.dto.AlgorithmCatalog;
import org.apache.dolphinscheduler.api.enums.Status;
import org.apache.dolphinscheduler.api.exceptions.ServiceException;
import org.apache.dolphinscheduler.api.service.AlgorithmCatalogService;
import org.apache.dolphinscheduler.api.service.AlgorithmPlatformClient;
import org.apache.dolphinscheduler.api.service.ProjectService;
import org.apache.dolphinscheduler.dao.entity.User;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AlgorithmCatalogServiceImpl implements AlgorithmCatalogService {

    private final ProjectService projectService;
    private final AlgorithmPlatformClient client;

    @Autowired
    public AlgorithmCatalogServiceImpl(ProjectService projectService, AlgorithmPlatformClient client) {
        this.projectService = projectService;
        this.client = client;
    }

    @Override
    public AlgorithmCatalog.Page<AlgorithmCatalog.Algorithm> queryAlgorithms(User user, long projectCode,
                                                                             int pageNo, int pageSize) {
        authorize(user, projectCode);
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "pageNo/pageSize");
        }
        long offset = ((long) pageNo - 1) * pageSize;
        List<AlgorithmCatalog.Algorithm> rows = client.queryAlgorithms(offset, pageSize + 1);
        boolean hasNext = rows.size() > pageSize;
        return new AlgorithmCatalog.Page<>(
                new ArrayList<>(rows.subList(0, Math.min(pageSize, rows.size()))),
                pageNo, pageSize, null, hasNext);
    }

    @Override
    public AlgorithmCatalog.Items<AlgorithmCatalog.Version> queryVersions(User user, long projectCode,
                                                                          long algorithmId) {
        authorize(user, projectCode);
        requirePositive(algorithmId, "algorithmId");
        List<AlgorithmCatalog.Version> rows = client.queryAlgorithmVersions(algorithmId);
        if (rows.stream().anyMatch(row -> row.getAlgorithmId() == null || row.getAlgorithmId() != algorithmId)) {
            throw new ServiceException(Status.ALGORITHM_CATALOG_INVALID);
        }
        return new AlgorithmCatalog.Items<>(rows);
    }

    @Override
    public AlgorithmCatalog.Items<AlgorithmCatalog.Model> queryModels(User user, long projectCode,
                                                                      long versionId, boolean includeUnavailable) {
        authorize(user, projectCode);
        requirePositive(versionId, "versionId");
        List<AlgorithmCatalog.Model> rows = client.queryAlgorithmModels(versionId, includeUnavailable);
        if (rows.stream().anyMatch(row -> row.getAlgorithmVersionId() == null
                || row.getAlgorithmVersionId() != versionId)) {
            throw new ServiceException(Status.ALGORITHM_CATALOG_INVALID);
        }
        return new AlgorithmCatalog.Items<>(rows.stream()
                .filter(row -> includeUnavailable || Boolean.TRUE.equals(row.getAvailable())
                        && "AVAILABLE".equals(row.getStatus()) && row.getUnavailableReasons().isEmpty())
                .collect(Collectors.toList()));
    }

    private void authorize(User user, long projectCode) {
        requirePositive(projectCode, "projectCode");
        projectService.checkProjectAndAuthThrowException(user, projectCode, PROJECT);
    }

    private void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, name);
        }
    }
}
