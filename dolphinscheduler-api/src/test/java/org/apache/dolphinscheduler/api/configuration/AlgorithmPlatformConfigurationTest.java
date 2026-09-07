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

package org.apache.dolphinscheduler.api.configuration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;

class AlgorithmPlatformConfigurationTest {

    @Test
    void disabledConfigurationDoesNotRequireCredentials() {
        AlgorithmPlatformConfiguration configuration = new AlgorithmPlatformConfiguration();
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(configuration, "configuration");

        configuration.validate(configuration, errors);

        Assertions.assertFalse(errors.hasErrors());
    }

    @Test
    void enabledConfigurationRequiresAValidUrlAndApiKey() {
        AlgorithmPlatformConfiguration configuration = new AlgorithmPlatformConfiguration();
        configuration.setEnabled(true);
        configuration.setBaseUrl("file:///tmp/results");
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(configuration, "configuration");

        configuration.validate(configuration, errors);

        Assertions.assertTrue(errors.hasFieldErrors("baseUrl"));
        Assertions.assertTrue(errors.hasFieldErrors("apiKey"));
    }

    @Test
    void apiKeyIsExcludedFromToString() {
        AlgorithmPlatformConfiguration configuration = new AlgorithmPlatformConfiguration();
        configuration.setApiKey("secret-value-must-not-leak");

        Assertions.assertFalse(configuration.toString().contains("secret-value-must-not-leak"));
    }
}
