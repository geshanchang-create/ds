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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.apache.dolphinscheduler.api.dto.AlgorithmCatalog;
import org.apache.dolphinscheduler.api.service.AlgorithmCatalogService;
import org.apache.dolphinscheduler.api.utils.Result;
import org.apache.dolphinscheduler.common.constants.Constants;
import org.apache.dolphinscheduler.dao.entity.User;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AlgorithmCatalogControllerTest {

    private AlgorithmCatalogService service;
    private AlgorithmCatalogController controller;
    private MockMvc mvc;
    private final User user = new User();
    private static final String ROOT = "/projects/123/industrial";

    @BeforeEach
    void setUp() {
        service = mock(AlgorithmCatalogService.class);
        controller = new AlgorithmCatalogController(service);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void bindsPageDefaultsAndReturnsStandardEnvelope() throws Exception {
        when(service.queryAlgorithms(user, 123, 1, 20)).thenReturn(
                new AlgorithmCatalog.Page<>(Collections.emptyList(), 1, 20, null, false));
        mvc.perform(get(ROOT + "/algorithms").requestAttr(Constants.SESSION_USER, user))
                .andExpect(status().isOk());

        Result<AlgorithmCatalog.Page<AlgorithmCatalog.Algorithm>> result =
                controller.queryAlgorithms(user, 123, 1, 20);
        assertEquals(0, result.getCode());
        assertEquals(20, result.getData().getPageSize());
        assertFalse(result.getData().isHasNext());
        assertNull(result.getData().getTotal());
    }

    @Test
    void bindsVersionAndModelRoutes() throws Exception {
        when(service.queryVersions(user, 123, 10)).thenReturn(new AlgorithmCatalog.Items<>(Collections.emptyList()));
        when(service.queryModels(user, 123, 20, false))
                .thenReturn(new AlgorithmCatalog.Items<>(Collections.emptyList()));
        when(service.queryModels(user, 123, 20, true))
                .thenReturn(new AlgorithmCatalog.Items<>(Collections.emptyList()));
        mvc.perform(get(ROOT + "/algorithms/10/versions").requestAttr(Constants.SESSION_USER, user))
                .andExpect(status().isOk());
        mvc.perform(get(ROOT + "/algorithm-versions/20/models").requestAttr(Constants.SESSION_USER, user))
                .andExpect(status().isOk());
        mvc.perform(get(ROOT + "/algorithm-versions/20/models").param("includeUnavailable", "true")
                .requestAttr(Constants.SESSION_USER, user)).andExpect(status().isOk());
        verify(service).queryModels(user, 123, 20, false);
        verify(service).queryModels(user, 123, 20, true);
        assertEquals(Collections.emptyList(), controller.queryVersions(user, 123, 10).getData().getItems());
    }

    @Test
    void requiresLoginAttributeAndRejectsMalformedParams() throws Exception {
        mvc.perform(get(ROOT + "/algorithms")).andExpect(status().isBadRequest());
        mvc.perform(get(ROOT + "/algorithms").param("pageNo", "bad")
                .requestAttr(Constants.SESSION_USER, user)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
