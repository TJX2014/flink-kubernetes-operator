/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.kubernetes.operator.autoscaler;

import org.apache.flink.autoscaler.state.HeapMemoryState;
import org.apache.flink.configuration.PipelineOptions;
import org.apache.flink.kubernetes.operator.api.FlinkDeployment;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.Maps;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.apache.flink.kubernetes.operator.utils.FlinkUtils.getTaskManagerSpec;
import static org.assertj.core.api.Assertions.assertThat;

/** Tests for KubernetesScalingRealizer. */
public class KubernetesScalingRealizerTest {

    @Test
    public void testApplyOverrides() {
        KubernetesJobAutoScalerContext ctx =
                TestingKubernetesAutoscalerUtils.createContext("test", null);

        new KubernetesScalingRealizer().realize(ctx, Map.of("a", "1", "b", "2"));

        assertThat(
                        ctx.getResource()
                                .getSpec()
                                .getFlinkConfiguration()
                                .get(PipelineOptions.PARALLELISM_OVERRIDES.key()))
                .satisfiesAnyOf(
                        // Currently no enforced order inside the overrides string
                        overrides -> assertThat(overrides).isEqualTo("a:1,b:2"),
                        overrides -> assertThat(overrides).isEqualTo("b:2,a:1"));
    }

    @Test
    public void testMemoryOverrides() {
        KubernetesJobAutoScalerContext ctx =
                TestingKubernetesAutoscalerUtils.createContext("test", null);

        new KubernetesScalingRealizer()
                .rescaleMemory(ctx, HeapMemoryState.TOO_FULL, Maps.newHashMap());

        assertThat(getTaskManagerSpec(ctx).getResource().getMemory()).isEqualTo("2457m");
    }

    @Test
    public void testAutoscalerOverridesStringDoesNotChangeUnlessOverridesChange() {
        // Create an overrides map which returns the keys in a deterministic order
        LinkedHashMap<String, String> newOverrides = new LinkedHashMap<>();
        newOverrides.put("b", "2");
        newOverrides.put("a", "1");

        assertOverridesDoNotChange("a:1,b:2", newOverrides);
        assertOverridesDoNotChange("b:2,a:1", newOverrides);
    }

    private void assertOverridesDoNotChange(
            String currentOverrides, LinkedHashMap<String, String> newOverrides) {

        KubernetesJobAutoScalerContext ctx =
                TestingKubernetesAutoscalerUtils.createContext("test", null);
        FlinkDeployment resource = (FlinkDeployment) ctx.getResource();

        // Create resource with existing parallelism overrides
        resource.getSpec()
                .getFlinkConfiguration()
                .put(PipelineOptions.PARALLELISM_OVERRIDES.key(), currentOverrides);
        resource.getStatus()
                .getReconciliationStatus()
                .serializeAndSetLastReconciledSpec(resource.getSpec(), resource);
        resource.getSpec()
                .getFlinkConfiguration()
                .remove(PipelineOptions.PARALLELISM_OVERRIDES.key());

        new KubernetesScalingRealizer().realize(ctx, newOverrides);

        assertThat(
                        ctx.getResource()
                                .getSpec()
                                .getFlinkConfiguration()
                                .get(PipelineOptions.PARALLELISM_OVERRIDES.key()))
                .isEqualTo(currentOverrides);
    }
}