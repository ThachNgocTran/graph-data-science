/*
 * Copyright (c) 2017-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gds.embeddings.graphsage.proc;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.gds.embeddings.graphsage.ActivationFunction;
import org.neo4j.graphalgo.GdsCypher;
import org.neo4j.graphalgo.config.GraphCreateFromStoreConfig;
import org.neo4j.graphdb.QueryExecutionException;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.graphalgo.utils.ExceptionUtil.rootCause;
import static org.neo4j.graphalgo.utils.StringFormatting.formatWithLocale;

class GraphSageStreamProcTest extends GraphSageBaseProcTest {

    @ParameterizedTest
    @MethodSource("org.neo4j.gds.embeddings.graphsage.proc.GraphSageBaseProcTest#configVariations")
    void testStreaming(int embeddingSize, String aggregator, ActivationFunction activationFunction) {
        train(embeddingSize, aggregator, activationFunction);

        String query = GdsCypher.call().explicitCreation("embeddingsGraph")
            .algo("gds.alpha.graphSage")
            .streamMode()
            .addParameter("concurrency", 1)
            .addParameter("modelName", modelName)
            .yields();

        runQueryWithRowConsumer(query, Map.of("embeddingSize", embeddingSize), row -> {
            Number nodeId = row.getNumber("nodeId");
            assertNotNull(nodeId);

            Object o = row.get("embedding");
            assertTrue(o instanceof List);
            Collection<Double> nodeEmbeddings = (List<Double>) o;
            assertEquals(embeddingSize, nodeEmbeddings.size());
        });
    }

    @ParameterizedTest(name = "Graph Properties: {2} - Algo Properties: {1}")
    @MethodSource("missingNodeProperties")
    void shouldFailOnMissingNodeProperties(GraphCreateFromStoreConfig config, String nodeProperties, String graphProperties, String label) {
        train(42, "mean", ActivationFunction.SIGMOID);

        String query = GdsCypher.call().implicitCreation(config)
            .algo("gds.alpha.graphSage")
            .streamMode()
            .addParameter("concurrency", 1)
            .addParameter("modelName", modelName)
            .yields();

        String expectedFail = formatWithLocale("Node properties [%s] not found in graph with node properties: [%s] in all node labels: ['%s']", nodeProperties, graphProperties, label);
        Throwable throwable = rootCause(assertThrows(QueryExecutionException.class, () -> runQuery(query)));
        assertEquals(IllegalArgumentException.class, throwable.getClass());
        assertEquals(expectedFail, throwable.getMessage());
    }
}
