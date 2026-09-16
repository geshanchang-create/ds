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

import static org.apache.dolphinscheduler.api.constants.ApiFuncIdentificationConstant.PROJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.dolphinscheduler.api.dto.AlgorithmCatalog;
import org.apache.dolphinscheduler.api.enums.Status;
import org.apache.dolphinscheduler.api.exceptions.ServiceException;
import org.apache.dolphinscheduler.api.service.impl.AlgorithmCatalogServiceImpl;
import org.apache.dolphinscheduler.dao.entity.User;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlgorithmCatalogServiceTest {

    @Mock
    private ProjectService projectService;
    @Mock
    private AlgorithmPlatformClient client;
    private AlgorithmCatalogService service;
    private final User user = new User();

    @BeforeEach
    void setUp() {
        service = new AlgorithmCatalogServiceImpl(projectService, client);
    }

    @Test
    void paginatesWithLookaheadWithoutInventingTotal() {
        AlgorithmCatalog.Algorithm a = new AlgorithmCatalog.Algorithm();
        AlgorithmCatalog.Algorithm b = new AlgorithmCatalog.Algorithm();
        when(client.queryAlgorithms(1, 2)).thenReturn(Arrays.asList(a, b));
        AlgorithmCatalog.Page<AlgorithmCatalog.Algorithm> page = service.queryAlgorithms(user, 123, 2, 1);
        assertEquals(Collections.singletonList(a), page.getItems());
        assertTrue(page.isHasNext());
        assertNull(page.getTotal());
        assertEquals(2, page.getPageNo());
        verify(projectService).checkProjectAndAuthThrowException(user, 123L, PROJECT);
    }

    @Test
    void handlesEmptyLastPageAndLargeOffset() {
        long offset = ((long) Integer.MAX_VALUE - 1) * 100;
        when(client.queryAlgorithms(offset, 101)).thenReturn(Collections.emptyList());
        AlgorithmCatalog.Page<AlgorithmCatalog.Algorithm> page =
                service.queryAlgorithms(user, 123, Integer.MAX_VALUE, 100);
        assertFalse(page.isHasNext());
        assertTrue(page.getItems().isEmpty());
    }

    @Test
    void rejectsInvalidParametersBeforeCallingPlatform() {
        assertThrows(ServiceException.class, () -> service.queryAlgorithms(user, 123, 0, 20));
        assertThrows(ServiceException.class, () -> service.queryAlgorithms(user, 123, 1, 101));
        assertThrows(ServiceException.class, () -> service.queryAlgorithms(user, 123, 1, 0));
        assertThrows(ServiceException.class, () -> service.queryVersions(user, 123, -1));
        assertThrows(ServiceException.class, () -> service.queryModels(user, 123, 0, false));
        verifyNoInteractions(client);
    }

    @Test
    void deniedProjectCannotQueryAnyCatalogRoute() {
        doThrow(new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "project"))
                .when(projectService).checkProjectAndAuthThrowException(user, 123L, PROJECT);
        assertThrows(ServiceException.class, () -> service.queryAlgorithms(user, 123, 1, 20));
        assertThrows(ServiceException.class, () -> service.queryVersions(user, 123, 10));
        assertThrows(ServiceException.class, () -> service.queryModels(user, 123, 20, false));
        verifyNoInteractions(client);
    }

    @Test
    void retainsUnavailableVersionsWithReasons() {
        AlgorithmCatalog.Version version = new AlgorithmCatalog.Version();
        version.setAlgorithmId(10L);
        version.setAvailable(false);
        version.setUnavailableReasons(Collections.singletonList("ALGORITHM_FILE_MISSING"));
        when(client.queryAlgorithmVersions(10)).thenReturn(Collections.singletonList(version));
        assertEquals(version, service.queryVersions(user, 123, 10).getItems().get(0));
    }

    @Test
    void rejectsWrongAlgorithmAndWrongModelVersion() {
        AlgorithmCatalog.Version version = new AlgorithmCatalog.Version();
        version.setAlgorithmId(11L);
        when(client.queryAlgorithmVersions(10)).thenReturn(Collections.singletonList(version));
        assertEquals(Status.ALGORITHM_CATALOG_INVALID.getCode(),
                assertThrows(ServiceException.class, () -> service.queryVersions(user, 123, 10)).getCode());
        when(client.queryAlgorithmModels(20, false)).thenReturn(Collections.singletonList(model(21, true)));
        assertThrows(ServiceException.class, () -> service.queryModels(user, 123, 20, false));
    }

    @Test
    void defaultsToAvailableModelsAndSupportsDiagnosticChoices() {
        AlgorithmCatalog.Model available = model(20, true);
        AlgorithmCatalog.Model unavailable = model(20, false);
        when(client.queryAlgorithmModels(20, false)).thenReturn(Arrays.asList(available, unavailable));
        when(client.queryAlgorithmModels(20, true)).thenReturn(Arrays.asList(available, unavailable));
        assertEquals(Collections.singletonList(available), service.queryModels(user, 123, 20, false).getItems());
        assertEquals(2, service.queryModels(user, 123, 20, true).getItems().size());
    }

    private AlgorithmCatalog.Model model(long versionId, boolean available) {
        AlgorithmCatalog.Model model = new AlgorithmCatalog.Model();
        model.setAlgorithmVersionId(versionId);
        model.setAvailable(available);
        model.setStatus(available ? "AVAILABLE" : "FAILED");
        model.setUnavailableReasons(available ? Collections.emptyList()
                : Collections.singletonList("MODEL_NOT_AVAILABLE"));
        return model;
    }
}
