package org.elasticsearch.action;

import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.node.stats.NodeStats;
import org.elasticsearch.action.admin.cluster.state.ClusterStateResponse;
import org.elasticsearch.action.admin.indices.stats.IndicesStatsResponse;
import org.elasticsearch.action.admin.indices.stats.PackageAccessHelper;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * Action response class for Prometheus Exporter plugin.
 */
public class NodePrometheusMetricsResponse extends ActionResponse {
    private ClusterHealthResponse clusterHealth;
    private List<NodeStats> nodesStats;
    @Nullable private IndicesStatsResponse indicesStats;
    private ClusterStatsData clusterStatsData = null;

    public NodePrometheusMetricsResponse(StreamInput in) throws IOException {
        super(in);
        clusterHealth = new ClusterHealthResponse(in);
        nodesStats = List.of(new NodeStats(in));
        indicesStats = PackageAccessHelper.createIndicesStatsResponse(in);
        clusterStatsData = new ClusterStatsData(in);
    }

    public NodePrometheusMetricsResponse(ClusterHealthResponse clusterHealth, List<NodeStats> nodesStats,
                                         @Nullable IndicesStatsResponse indicesStats,
                                         @Nullable ClusterStateResponse clusterStateResponse,
                                         Settings settings,
                                         ClusterSettings clusterSettings) {
        this.clusterHealth = clusterHealth;
        this.nodesStats = nodesStats;
        this.indicesStats = indicesStats;
        if (clusterStateResponse != null) {
            this.clusterStatsData = new ClusterStatsData(clusterStateResponse, settings, clusterSettings);
        }
    }

    public ClusterHealthResponse getClusterHealth() {
        return this.clusterHealth;
    }

    public List<NodeStats> getNodesStats() {
        return this.nodesStats;
    }

    @Nullable
    public IndicesStatsResponse getIndicesStats() {
        return this.indicesStats;
    }

    @Nullable
    public ClusterStatsData getClusterStatsData() {
        return this.clusterStatsData;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        clusterHealth.writeTo(out);

        for (NodeStats nodeStats : nodesStats) {
            nodeStats.writeTo(out);
        }

        out.writeOptionalWriteable(indicesStats);
        clusterStatsData.writeTo(out);
    }
}