/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.indices.recovery;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.action.index.MappingUpdatedAction;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.index.deletionpolicy.SnapshotIndexCommit;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.index.translog.Translog;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;

/**
 * A recovery handler that skips phase 1 as well as sending the snapshot. During phase 3 the shard is marked
 * as relocated an closed to ensure that the engine is closed and the target can acquire the IW write lock.
 */
public class SharedFSRecoveryHandler extends ShardRecoveryHandler {

    private final IndexShard shard;
    private final StartRecoveryRequest request;

    public SharedFSRecoveryHandler(IndexShard shard, StartRecoveryRequest request, RecoverySettings recoverySettings, TransportService transportService, ClusterService clusterService, IndicesService indicesService, MappingUpdatedAction mappingUpdatedAction, ESLogger logger) {
        super(shard, request, recoverySettings, transportService, clusterService, indicesService, mappingUpdatedAction, logger);
        this.shard = shard;
        this.request = request;
    }

    @Override
    public void phase1(SnapshotIndexCommit snapshot) throws ElasticsearchException {
        logger.trace("{} recovery [phase2] to {}: skipping phase 1 for shared filesystem", request.shardId(), request.targetNode());
    }

    @Override
    protected int sendSnapshot(Translog.Snapshot snapshot) throws ElasticsearchException {
        logger.trace("{} recovery [phase3] to {}: skipping transaction log operations for file sync", shard.shardId(), request.targetNode());
        return 0;
    }

    @Override
    public void phase3(Translog.Snapshot snapshot) throws ElasticsearchException {
        if (request.markAsRelocated()) {
            try {
                shard.relocated("to " + request.targetNode());
                shard.close("relocated on shared filesystem");
            } catch (IOException e) {

            }
        }
        super.phase3(snapshot);
    }
}