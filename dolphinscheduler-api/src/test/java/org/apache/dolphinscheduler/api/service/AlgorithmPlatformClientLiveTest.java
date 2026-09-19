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

import org.apache.dolphinscheduler.api.configuration.AlgorithmPlatformConfiguration;
import org.apache.dolphinscheduler.api.service.impl.AlgorithmPlatformClientImpl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class AlgorithmPlatformClientLiveTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "ALGORITHM_PLATFORM_LIVE_TEST", matches = "true")
    void readsCatalogAndExistingRunsFromLivePlatform() {
        AlgorithmPlatformConfiguration configuration = new AlgorithmPlatformConfiguration();
        configuration.setEnabled(true);
        configuration.setBaseUrl(requiredEnvironment("ALGORITHM_PLATFORM_BASE_URL"));
        configuration.setApiKey(requiredEnvironment("ALGORITHM_PLATFORM_API_KEY"));
        configuration.setConnectTimeoutMillis(3000);
        configuration.setReadTimeoutMillis(10000);
        configuration.setMaxResponseBytes(10 * 1024 * 1024);

        AlgorithmPlatformClient client = new AlgorithmPlatformClientImpl(configuration);
        long trainingId = positiveEnvironmentId("ALGORITHM_PLATFORM_TRAINING_ID");
        long executionId = positiveEnvironmentId("ALGORITHM_PLATFORM_EXECUTION_ID");

        Assertions.assertFalse(client.queryAlgorithms(0, 5).isEmpty());

        Assertions.assertEquals(trainingId, client.queryTraining(trainingId).getTrainingId());
        Assertions.assertNotNull(client.queryTrainingLogs(trainingId));

        Assertions.assertEquals(executionId, client.queryExecution(executionId).getExecutionId());
        Assertions.assertNotNull(client.queryExecutionLogs(executionId));
        Assertions.assertEquals(executionId, client.queryExecutionResult(executionId).getExecutionId());
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        Assertions.assertNotNull(value, name + " is required for the live test");
        Assertions.assertFalse(value.isBlank(), name + " is required for the live test");
        return value;
    }

    private long positiveEnvironmentId(String name) {
        long value = Long.parseLong(requiredEnvironment(name));
        Assertions.assertTrue(value > 0, name + " must be positive");
        return value;
    }
}
