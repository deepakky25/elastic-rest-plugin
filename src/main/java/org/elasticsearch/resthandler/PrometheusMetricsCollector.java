package org.elasticsearch.resthandler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ClusterStatsData;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.node.stats.NodeStats;
import org.elasticsearch.action.admin.indices.stats.CommonStats;
import org.elasticsearch.action.admin.indices.stats.IndexStats;
import org.elasticsearch.action.admin.indices.stats.IndicesStatsResponse;
import org.elasticsearch.cluster.node.DiscoveryNodeRole;
import org.elasticsearch.cluster.health.ClusterIndexHealth;
import org.elasticsearch.indices.NodeIndicesStats;
import org.elasticsearch.transport.TransportStats;
import org.elasticsearch.threadpool.ThreadPoolStats;
import org.elasticsearch.http.HttpStats;
import org.elasticsearch.indices.breaker.AllCircuitBreakerStats;
import org.elasticsearch.indices.breaker.CircuitBreakerStats;
import org.elasticsearch.ingest.IngestStats;
import org.elasticsearch.monitor.fs.FsInfo;
import org.elasticsearch.monitor.jvm.JvmStats;
import org.elasticsearch.monitor.os.OsStats;
import org.elasticsearch.script.ScriptStats;
import org.elasticsearch.monitor.process.ProcessStats;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.prometheus.client.Summary;

/**
 * A class that describes a Prometheus metrics collector.
 */
public class PrometheusMetricsCollector {

    private final Logger logger = LogManager.getLogger(getClass());

    private boolean isPrometheusClusterSettings;
    private boolean isPrometheusIndices;
    private PrometheusMetricsCatalog catalog;

    public PrometheusMetricsCollector(PrometheusMetricsCatalog catalog,
                                      boolean isPrometheusIndices,
                                      boolean isPrometheusClusterSettings) {
        this.isPrometheusClusterSettings = isPrometheusClusterSettings;
        this.isPrometheusIndices = isPrometheusIndices;
        this.catalog = catalog;
    }

    public void registerMetrics() {
        catalog.registerSummaryTimer("metrics_generate_time_seconds", "Time spent while generating metrics");

        registerClusterMetrics();
        if (isPrometheusIndices) {
            registerPerIndexMetrics();
        }
        if (isPrometheusClusterSettings) {
            registerESSettings();
        }

        registerNodeMetrics();
        registerIndicesMetrics();
        registerTransportMetrics();
        registerHTTPMetrics();
        registerThreadPoolMetrics();
        registerIngestMetrics();
        registerCircuitBreakerMetrics();
        registerScriptMetrics();
        registerProcessMetrics();
        registerJVMMetrics();
        registerOsMetrics();
        registerFsMetrics();
    }

    private void registerClusterMetrics() {
        catalog.registerClusterGauge("cluster_status", "Cluster status");

        catalog.registerClusterGauge("cluster_nodes_number", "Number of nodes in the cluster");
        catalog.registerClusterGauge("cluster_datanodes_number", "Number of data nodes in the cluster");

        catalog.registerClusterGauge("cluster_shards_active_percent", "Percent of active shards");
        catalog.registerClusterGauge("cluster_shards_number", "Number of shards", "type");

        catalog.registerClusterGauge("cluster_pending_tasks_number", "Number of pending tasks");
        catalog.registerClusterGauge("cluster_task_max_waiting_time_seconds", "Max waiting time for tasks");

        catalog.registerClusterGauge("cluster_is_timedout_bool", "Is the cluster timed out ?");

        catalog.registerClusterGauge("cluster_inflight_fetch_number", "Number of in flight fetches");
    }

    private void updateClusterMetrics(ClusterHealthResponse chr) {
        if (chr != null) {
            catalog.setClusterGauge("cluster_status", chr.getStatus().value());

            catalog.setClusterGauge("cluster_nodes_number", chr.getNumberOfNodes());
            catalog.setClusterGauge("cluster_datanodes_number", chr.getNumberOfDataNodes());

            catalog.setClusterGauge("cluster_shards_active_percent", chr.getActiveShardsPercent());

            catalog.setClusterGauge("cluster_shards_number", chr.getActiveShards(), "active");
            catalog.setClusterGauge("cluster_shards_number", chr.getActivePrimaryShards(), "active_primary");
            catalog.setClusterGauge("cluster_shards_number", chr.getDelayedUnassignedShards(), "unassigned");
            catalog.setClusterGauge("cluster_shards_number", chr.getInitializingShards(), "initializing");
            catalog.setClusterGauge("cluster_shards_number", chr.getRelocatingShards(), "relocating");
            catalog.setClusterGauge("cluster_shards_number", chr.getUnassignedShards(), "unassigned");

            catalog.setClusterGauge("cluster_pending_tasks_number", chr.getNumberOfPendingTasks());
            catalog.setClusterGauge("cluster_task_max_waiting_time_seconds", chr.getTaskMaxWaitingTime().getSeconds());

            catalog.setClusterGauge("cluster_is_timedout_bool", chr.isTimedOut() ? 1 : 0);

            catalog.setClusterGauge("cluster_inflight_fetch_number", chr.getNumberOfInFlightFetch());
        }
    }

    private void registerNodeMetrics() {
        catalog.registerNodeGauge("node_role_bool", "Node role", "role");
    }

    private void updateNodeMetrics(String nodeName, String nodeId, NodeStats ns) {
        if (ns != null) {

            // Plugins can introduce custom node roles from 7.3.0: https://github.com/elastic/elasticsearch/pull/43175
            // TODO(lukas-vlcek): List of node roles can not be static but needs to be created dynamically.
            Map<String, Integer> roles = new HashMap<>();

            roles.put("master", 0);
            roles.put("data", 0);
            roles.put("ingest", 0);

            for (DiscoveryNodeRole r : ns.getNode().getRoles()) {
                roles.put(r.roleName(), 1);
            }

            for (String k : roles.keySet()) {
                catalog.setNodeGauge("node_role_bool", roles.get(k), nodeName, nodeId, k);
            }
        }
    }

    private void registerIndicesMetrics() {
        catalog.registerNodeGauge("indices_doc_number", "Total number of documents");
        catalog.registerNodeGauge("indices_doc_deleted_number", "Number of deleted documents");

        catalog.registerNodeGauge("indices_store_size_bytes", "Store size of the indices in bytes");

        catalog.registerNodeGauge("indices_indexing_delete_count", "Count of documents deleted");
        catalog.registerNodeGauge("indices_indexing_delete_current_number", "Current rate of documents deleted");
        catalog.registerNodeGauge("indices_indexing_delete_time_seconds", "Time spent while deleting documents");
        catalog.registerNodeGauge("indices_indexing_index_count", "Count of documents indexed");
        catalog.registerNodeGauge("indices_indexing_index_current_number", "Current rate of documents indexed");
        catalog.registerNodeGauge("indices_indexing_index_failed_count", "Count of failed to index documents");
        catalog.registerNodeGauge("indices_indexing_index_time_seconds", "Time spent while indexing documents");
        catalog.registerNodeGauge("indices_indexing_noop_update_count", "Count of noop document updates");
        catalog.registerNodeGauge("indices_indexing_is_throttled_bool", "Is indexing throttling ?");
        catalog.registerNodeGauge("indices_indexing_throttle_time_seconds", "Time spent while throttling");

        catalog.registerNodeGauge("indices_get_count", "Count of get commands");
        catalog.registerNodeGauge("indices_get_time_seconds", "Time spent while get commands");
        catalog.registerNodeGauge("indices_get_exists_count", "Count of existing documents when get command");
        catalog.registerNodeGauge("indices_get_exists_time_seconds", "Time spent while existing documents get command");
        catalog.registerNodeGauge("indices_get_missing_count", "Count of missing documents when get command");
        catalog.registerNodeGauge("indices_get_missing_time_seconds", "Time spent while missing documents get command");
        catalog.registerNodeGauge("indices_get_current_number", "Current rate of get commands");

        catalog.registerNodeGauge("indices_search_open_contexts_number", "Number of search open contexts");
        catalog.registerNodeGauge("indices_search_fetch_count", "Count of search fetches");
        catalog.registerNodeGauge("indices_search_fetch_current_number", "Current rate of search fetches");
        catalog.registerNodeGauge("indices_search_fetch_time_seconds", "Time spent while search fetches");
        catalog.registerNodeGauge("indices_search_query_count", "Count of search queries");
        catalog.registerNodeGauge("indices_search_query_current_number", "Current rate of search queries");
        catalog.registerNodeGauge("indices_search_query_time_seconds", "Time spent while search queries");
        catalog.registerNodeGauge("indices_search_scroll_count", "Count of search scrolls");
        catalog.registerNodeGauge("indices_search_scroll_current_number", "Current rate of search scrolls");
        catalog.registerNodeGauge("indices_search_scroll_time_seconds", "Time spent while search scrolls");

        catalog.registerNodeGauge("indices_merges_current_number", "Current rate of merges");
        catalog.registerNodeGauge("indices_merges_current_docs_number", "Current rate of documents merged");
        catalog.registerNodeGauge("indices_merges_current_size_bytes", "Current rate of bytes merged");
        catalog.registerNodeGauge("indices_merges_total_number", "Count of merges");
        catalog.registerNodeGauge("indices_merges_total_time_seconds", "Time spent while merging");
        catalog.registerNodeGauge("indices_merges_total_docs_count", "Count of documents merged");
        catalog.registerNodeGauge("indices_merges_total_size_bytes", "Count of bytes of merged documents");
        catalog.registerNodeGauge("indices_merges_total_stopped_time_seconds", "Time spent while merge process stopped");
        catalog.registerNodeGauge("indices_merges_total_throttled_time_seconds", "Time spent while merging when throttling");
        catalog.registerNodeGauge("indices_merges_total_auto_throttle_bytes", "Bytes merged while throttling");

        catalog.registerNodeGauge("indices_refresh_total_count", "Count of refreshes");
        catalog.registerNodeGauge("indices_refresh_total_time_seconds", "Time spent while refreshes");
        catalog.registerNodeGauge("indices_refresh_listeners_number", "Number of refresh listeners");

        catalog.registerNodeGauge("indices_flush_total_count", "Count of flushes");
        catalog.registerNodeGauge("indices_flush_total_time_seconds", "Total time spent while flushes");

        catalog.registerNodeGauge("indices_querycache_cache_count", "Count of queries in cache");
        catalog.registerNodeGauge("indices_querycache_cache_size_bytes", "Query cache size");
        catalog.registerNodeGauge("indices_querycache_evictions_count", "Count of evictions in query cache");
        catalog.registerNodeGauge("indices_querycache_hit_count", "Count of hits in query cache");
        catalog.registerNodeGauge("indices_querycache_memory_size_bytes", "Memory usage of query cache");
        catalog.registerNodeGauge("indices_querycache_miss_number", "Count of misses in query cache");
        catalog.registerNodeGauge("indices_querycache_total_number", "Count of usages of query cache");

        catalog.registerNodeGauge("indices_fielddata_memory_size_bytes", "Memory usage of field date cache");
        catalog.registerNodeGauge("indices_fielddata_evictions_count", "Count of evictions in field data cache");

        catalog.registerNodeGauge("indices_percolate_count", "Count of percolates");
        catalog.registerNodeGauge("indices_percolate_current_number", "Rate of percolates");
        catalog.registerNodeGauge("indices_percolate_memory_size_bytes", "Percolate memory size");
        catalog.registerNodeGauge("indices_percolate_queries_count", "Count of queries percolated");
        catalog.registerNodeGauge("indices_percolate_time_seconds", "Time spent while percolating");

        catalog.registerNodeGauge("indices_completion_size_bytes", "Size of completion suggest statistics");

        catalog.registerNodeGauge("indices_segments_number", "Current number of segments");
        catalog.registerNodeGauge("indices_segments_memory_bytes", "Memory used by segments", "type");

        catalog.registerNodeGauge("indices_suggest_current_number", "Current rate of suggests");
        catalog.registerNodeGauge("indices_suggest_count", "Count of suggests");
        catalog.registerNodeGauge("indices_suggest_time_seconds", "Time spent while making suggests");

        catalog.registerNodeGauge("indices_requestcache_memory_size_bytes", "Memory used for request cache");
        catalog.registerNodeGauge("indices_requestcache_hit_count", "Number of hits in request cache");
        catalog.registerNodeGauge("indices_requestcache_miss_count", "Number of misses in request cache");
        catalog.registerNodeGauge("indices_requestcache_evictions_count", "Number of evictions in request cache");

        catalog.registerNodeGauge("indices_recovery_current_number", "Current number of recoveries", "type");
        catalog.registerNodeGauge("indices_recovery_throttle_time_seconds", "Time spent while throttling recoveries");
    }

    private void updateIndicesMetrics(String nodeName, String nodeId, NodeIndicesStats idx) {
        if (idx != null) {
            catalog.setNodeGauge("indices_doc_number", idx.getDocs().getCount(), nodeName, nodeId);
            catalog.setNodeGauge("indices_doc_deleted_number", idx.getDocs().getDeleted(), nodeName, nodeId);

            catalog.setNodeGauge("indices_store_size_bytes", idx.getStore().getSizeInBytes(), nodeName, nodeId);

            catalog.setNodeGauge("indices_indexing_delete_count", idx.getIndexing().getTotal().getDeleteCount(), nodeName, nodeId);
            catalog.setNodeGauge("indices_indexing_delete_current_number", idx.getIndexing().getTotal().getDeleteCurrent(), nodeName, nodeId);
            catalog.setNodeGauge("indices_indexing_delete_time_seconds",
                    idx.getIndexing().getTotal().getDeleteTime().seconds(), nodeName, nodeId);
            catalog.setNodeGauge("indices_indexing_index_count", idx.getIndexing().getTotal().getIndexCount(), nodeName, nodeId);
            catalog.setNodeGauge("indices_indexing_index_current_number", idx.getIndexing().getTotal().getIndexCurrent(), nodeName, nodeId);
            catalog.setNodeGauge("indices_indexing_index_failed_count", idx.getIndexing().getTotal().getIndexFailedCount(), nodeName, nodeId);
            catalog.setNodeGauge("indices_indexing_index_time_seconds", idx.getIndexing().getTotal().getIndexTime().seconds(), nodeName, nodeId);
            catalog.setNodeGauge("indices_indexing_noop_update_count", idx.getIndexing().getTotal().getNoopUpdateCount(), nodeName, nodeId);
            catalog.setNodeGauge("indices_indexing_is_throttled_bool", idx.getIndexing().getTotal().isThrottled() ? 1 : 0, nodeName, nodeId);
            catalog.setNodeGauge("indices_indexing_throttle_time_seconds",
                    idx.getIndexing().getTotal().getThrottleTime().seconds(), nodeName, nodeId);

            catalog.setNodeGauge("indices_get_count", idx.getGet().getCount(), nodeName, nodeId);
            catalog.setNodeGauge("indices_get_time_seconds", idx.getGet().getTimeInMillis() / 1000.0, nodeName, nodeId);
            catalog.setNodeGauge("indices_get_exists_count", idx.getGet().getExistsCount(), nodeName, nodeId);
            catalog.setNodeGauge("indices_get_exists_time_seconds", idx.getGet().getExistsTimeInMillis() / 1000.0, nodeName, nodeId);
            catalog.setNodeGauge("indices_get_missing_count", idx.getGet().getMissingCount(), nodeName, nodeId);
            catalog.setNodeGauge("indices_get_missing_time_seconds", idx.getGet().getMissingTimeInMillis() / 1000.0, nodeName, nodeId);
            catalog.setNodeGauge("indices_get_current_number", idx.getGet().current(), nodeName, nodeId);

            catalog.setNodeGauge("indices_search_open_contexts_number", idx.getSearch().getOpenContexts(), nodeName, nodeId);
            catalog.setNodeGauge("indices_search_fetch_count", idx.getSearch().getTotal().getFetchCount(), nodeName, nodeId);
            catalog.setNodeGauge("indices_search_fetch_current_number", idx.getSearch().getTotal().getFetchCurrent(), nodeName, nodeId);
            catalog.setNodeGauge("indices_search_fetch_time_seconds",
                    idx.getSearch().getTotal().getFetchTimeInMillis() / 1000.0, nodeName, nodeId);
            catalog.setNodeGauge("indices_search_query_count", idx.getSearch().getTotal().getQueryCount(), nodeName, nodeId);
            catalog.setNodeGauge("indices_search_query_current_number", idx.getSearch().getTotal().getQueryCurrent(), nodeName, nodeId);
            catalog.setNodeGauge("indices_search_query_time_seconds",
                    idx.getSearch().getTotal().getQueryTimeInMillis() / 1000.0, nodeName, nodeId);
            catalog.setNodeGauge("indices_search_scroll_count", idx.getSearch().getTotal().getScrollCount(), nodeName, nodeId);
            catalog.setNodeGauge("indices_search_scroll_current_number", idx.getSearch().getTotal().getScrollCurrent(), nodeName, nodeId);
            catalog.setNodeGauge("indices_search_scroll_time_seconds",
                    idx.getSearch().getTotal().getScrollTimeInMillis() / 1000.0, nodeName, nodeId);

            catalog.setNodeGauge("indices_merges_current_number", idx.getMerge().getCurrent(), nodeName, nodeId);
            catalog.setNodeGauge("indices_merges_current_docs_number", idx.getMerge().getCurrentNumDocs(), nodeName, nodeId);
            catalog.setNodeGauge("indices_merges_current_size_bytes", idx.getMerge().getCurrentSizeInBytes(), nodeName, nodeId);
            catalog.setNodeGauge("indices_merges_total_number", idx.getMerge().getTotal(), nodeName, nodeId);
            catalog.setNodeGauge("indices_merges_total_time_seconds", idx.getMerge().getTotalTimeInMillis() / 1000.0, nodeName, nodeId);
            catalog.setNodeGauge("indices_merges_total_docs_count", idx.getMerge().getTotalNumDocs(), nodeName, nodeId);
            catalog.setNodeGauge("indices_merges_total_size_bytes", idx.getMerge().getTotalSizeInBytes(), nodeName, nodeId);
            catalog.setNodeGauge("indices_merges_total_stopped_time_seconds",
                    idx.getMerge().getTotalStoppedTimeInMillis() / 1000.0, nodeName, nodeId);
            catalog.setNodeGauge("indices_merges_total_throttled_time_seconds",
                    idx.getMerge().getTotalThrottledTimeInMillis() / 1000.0, nodeName, nodeId);
            catalog.setNodeGauge("indices_merges_total_auto_throttle_bytes", idx.getMerge().getTotalBytesPerSecAutoThrottle(), nodeName, nodeId);

            catalog.setNodeGauge("indices_refresh_total_count", idx.getRefresh().getTotal(), nodeName, nodeId);
            catalog.setNodeGauge("indices_refresh_total_time_seconds", idx.getRefresh().getTotalTimeInMillis() / 1000.0, nodeName, nodeId);
            catalog.setNodeGauge("indices_refresh_listeners_number", idx.getRefresh().getListeners(), nodeName, nodeId);

            catalog.setNodeGauge("indices_flush_total_count", idx.getFlush().getTotal(), nodeName, nodeId);
            catalog.setNodeGauge("indices_flush_total_time_seconds", idx.getFlush().getTotalTimeInMillis() / 1000.0, nodeName, nodeId);

            catalog.setNodeGauge("indices_querycache_cache_count", idx.getQueryCache().getCacheCount(), nodeName, nodeId);
            catalog.setNodeGauge("indices_querycache_cache_size_bytes", idx.getQueryCache().getCacheSize(), nodeName, nodeId);
            catalog.setNodeGauge("indices_querycache_evictions_count", idx.getQueryCache().getEvictions(), nodeName, nodeId);
            catalog.setNodeGauge("indices_querycache_hit_count", idx.getQueryCache().getHitCount(), nodeName, nodeId);
            catalog.setNodeGauge("indices_querycache_memory_size_bytes", idx.getQueryCache().getMemorySizeInBytes(), nodeName, nodeId);
            catalog.setNodeGauge("indices_querycache_miss_number", idx.getQueryCache().getMissCount(), nodeName, nodeId);
            catalog.setNodeGauge("indices_querycache_total_number", idx.getQueryCache().getTotalCount(), nodeName, nodeId);

            catalog.setNodeGauge("indices_fielddata_memory_size_bytes", idx.getFieldData().getMemorySizeInBytes(), nodeName, nodeId);
            catalog.setNodeGauge("indices_fielddata_evictions_count", idx.getFieldData().getEvictions(), nodeName, nodeId);

            catalog.setNodeGauge("indices_completion_size_bytes", idx.getCompletion().getSizeInBytes(), nodeName, nodeId);

            catalog.setNodeGauge("indices_segments_number", idx.getSegments().getCount(), nodeName, nodeId);
            
            // catalog.setNodeGauge("indices_segments_memory_bytes", idx.getSegments().getMemoryInBytes(), nodeName, nodeId, "all");
            catalog.setNodeGauge("indices_segments_memory_bytes", idx.getSegments().getBitsetMemoryInBytes(), nodeName, nodeId, "bitset");
            // catalog.setNodeGauge("indices_segments_memory_bytes", idx.getSegments().getDocValuesMemoryInBytes(), nodeName, nodeId, "docvalues");
            catalog.setNodeGauge("indices_segments_memory_bytes", idx.getSegments().getIndexWriterMemoryInBytes(), nodeName, nodeId, "indexwriter");
            // catalog.setNodeGauge("indices_segments_memory_bytes", idx.getSegments().getNormsMemoryInBytes(), nodeName, nodeId, "norms");
            // catalog.setNodeGauge("indices_segments_memory_bytes", idx.getSegments().getStoredFieldsMemoryInBytes(),
            //         nodeName, nodeId, "storefields");
            // catalog.setNodeGauge("indices_segments_memory_bytes", idx.getSegments().getTermsMemoryInBytes(), nodeName, nodeId, "terms");
            // catalog.setNodeGauge("indices_segments_memory_bytes", idx.getSegments().getTermVectorsMemoryInBytes(),
                    // nodeName, nodeId, "termvectors");
            catalog.setNodeGauge("indices_segments_memory_bytes", idx.getSegments().getVersionMapMemoryInBytes(), nodeName, nodeId, "versionmap");
            // catalog.setNodeGauge("indices_segments_memory_bytes", idx.getSegments().getPointsMemoryInBytes(), nodeName, nodeId, "points");

            catalog.setNodeGauge("indices_suggest_current_number", idx.getSearch().getTotal().getSuggestCurrent(), nodeName, nodeId);
            catalog.setNodeGauge("indices_suggest_count", idx.getSearch().getTotal().getSuggestCount(), nodeName, nodeId);
            catalog.setNodeGauge("indices_suggest_time_seconds", idx.getSearch().getTotal().getSuggestTimeInMillis() / 1000.0, nodeName, nodeId);

            catalog.setNodeGauge("indices_requestcache_memory_size_bytes", idx.getRequestCache().getMemorySizeInBytes(), nodeName, nodeId);
            catalog.setNodeGauge("indices_requestcache_hit_count", idx.getRequestCache().getHitCount(), nodeName, nodeId);
            catalog.setNodeGauge("indices_requestcache_miss_count", idx.getRequestCache().getMissCount(), nodeName, nodeId);
            catalog.setNodeGauge("indices_requestcache_evictions_count", idx.getRequestCache().getEvictions(), nodeName, nodeId);

            catalog.setNodeGauge("indices_recovery_current_number", idx.getRecoveryStats().currentAsSource(), nodeName, nodeId, "source");
            catalog.setNodeGauge("indices_recovery_current_number", idx.getRecoveryStats().currentAsTarget(), nodeName, nodeId, "target");
            catalog.setNodeGauge("indices_recovery_throttle_time_seconds", idx.getRecoveryStats().throttleTime().getSeconds(), nodeName, nodeId);
        }
    }

    @SuppressWarnings("checkstyle:LineLength")
    private void registerPerIndexMetrics() {
        catalog.registerClusterGauge("index_status", "Index status", "index");
        catalog.registerClusterGauge("index_replicas_number", "Number of replicas", "index");
        catalog.registerClusterGauge("index_shards_number", "Number of shards", "type", "index");

        catalog.registerClusterGauge("index_doc_number", "Total number of documents", "index", "context");
        catalog.registerClusterGauge("index_doc_deleted_number", "Number of deleted documents", "index", "context");

        catalog.registerClusterGauge("index_store_size_bytes", "Store size of the indices in bytes", "index", "context");

        catalog.registerClusterGauge("index_indexing_delete_count", "Count of documents deleted", "index", "context");
        catalog.registerClusterGauge("index_indexing_delete_current_number", "Current rate of documents deleted", "index", "context");
        catalog.registerClusterGauge("index_indexing_delete_time_seconds", "Time spent while deleting documents", "index", "context");
        catalog.registerClusterGauge("index_indexing_index_count", "Count of documents indexed", "index", "context");
        catalog.registerClusterGauge("index_indexing_index_current_number", "Current rate of documents indexed", "index", "context");
        catalog.registerClusterGauge("index_indexing_index_failed_count", "Count of failed to index documents", "index", "context");
        catalog.registerClusterGauge("index_indexing_index_time_seconds", "Time spent while indexing documents", "index", "context");
        catalog.registerClusterGauge("index_indexing_noop_update_count", "Count of noop document updates", "index", "context");
        catalog.registerClusterGauge("index_indexing_is_throttled_bool", "Is indexing throttling ?", "index", "context");
        catalog.registerClusterGauge("index_indexing_throttle_time_seconds", "Time spent while throttling", "index", "context");

        catalog.registerClusterGauge("index_get_count", "Count of get commands", "index", "context");
        catalog.registerClusterGauge("index_get_time_seconds", "Time spent while get commands", "index", "context");
        catalog.registerClusterGauge("index_get_exists_count", "Count of existing documents when get command", "index", "context");
        catalog.registerClusterGauge("index_get_exists_time_seconds", "Time spent while existing documents get command", "index", "context");
        catalog.registerClusterGauge("index_get_missing_count", "Count of missing documents when get command", "index", "context");
        catalog.registerClusterGauge("index_get_missing_time_seconds", "Time spent while missing documents get command", "index", "context");
        catalog.registerClusterGauge("index_get_current_number", "Current rate of get commands", "index", "context");

        catalog.registerClusterGauge("index_search_open_contexts_number", "Number of search open contexts", "index", "context");
        catalog.registerClusterGauge("index_search_fetch_count", "Count of search fetches", "index", "context");
        catalog.registerClusterGauge("index_search_fetch_current_number", "Current rate of search fetches", "index", "context");
        catalog.registerClusterGauge("index_search_fetch_time_seconds", "Time spent while search fetches", "index", "context");
        catalog.registerClusterGauge("index_search_query_count", "Count of search queries", "index", "context");
        catalog.registerClusterGauge("index_search_query_current_number", "Current rate of search queries", "index", "context");
        catalog.registerClusterGauge("index_search_query_time_seconds", "Time spent while search queries", "index", "context");
        catalog.registerClusterGauge("index_search_scroll_count", "Count of search scrolls", "index", "context");
        catalog.registerClusterGauge("index_search_scroll_current_number", "Current rate of search scrolls", "index", "context");
        catalog.registerClusterGauge("index_search_scroll_time_seconds", "Time spent while search scrolls", "index", "context");

        catalog.registerClusterGauge("index_merges_current_number", "Current rate of merges", "index", "context");
        catalog.registerClusterGauge("index_merges_current_docs_number", "Current rate of documents merged", "index", "context");
        catalog.registerClusterGauge("index_merges_current_size_bytes", "Current rate of bytes merged", "index", "context");
        catalog.registerClusterGauge("index_merges_total_number", "Count of merges", "index", "context");
        catalog.registerClusterGauge("index_merges_total_time_seconds", "Time spent while merging", "index", "context");
        catalog.registerClusterGauge("index_merges_total_docs_count", "Count of documents merged", "index", "context");
        catalog.registerClusterGauge("index_merges_total_size_bytes", "Count of bytes of merged documents", "index", "context");
        catalog.registerClusterGauge("index_merges_total_stopped_time_seconds", "Time spent while merge process stopped", "index", "context");
        catalog.registerClusterGauge("index_merges_total_throttled_time_seconds", "Time spent while merging when throttling", "index", "context");
        catalog.registerClusterGauge("index_merges_total_auto_throttle_bytes", "Bytes merged while throttling", "index", "context");

        catalog.registerClusterGauge("index_refresh_total_count", "Count of refreshes", "index", "context");
        catalog.registerClusterGauge("index_refresh_total_time_seconds", "Time spent while refreshes", "index", "context");
        catalog.registerClusterGauge("index_refresh_listeners_number", "Number of refresh listeners", "index", "context");

        catalog.registerClusterGauge("index_flush_total_count", "Count of flushes", "index", "context");
        catalog.registerClusterGauge("index_flush_total_time_seconds", "Total time spent while flushes", "index", "context");

        catalog.registerClusterGauge("index_querycache_cache_count", "Count of queries in cache", "index", "context");
        catalog.registerClusterGauge("index_querycache_cache_size_bytes", "Query cache size", "index", "context");
        catalog.registerClusterGauge("index_querycache_evictions_count", "Count of evictions in query cache", "index", "context");
        catalog.registerClusterGauge("index_querycache_hit_count", "Count of hits in query cache", "index", "context");
        catalog.registerClusterGauge("index_querycache_memory_size_bytes", "Memory usage of query cache", "index", "context");
        catalog.registerClusterGauge("index_querycache_miss_number", "Count of misses in query cache", "index", "context");
        catalog.registerClusterGauge("index_querycache_total_number", "Count of usages of query cache", "index", "context");

        catalog.registerClusterGauge("index_fielddata_memory_size_bytes", "Memory usage of field date cache", "index", "context");
        catalog.registerClusterGauge("index_fielddata_evictions_count", "Count of evictions in field data cache", "index", "context");

        // Percolator cache was removed in ES 5.x
        // See https://github.com/elastic/elasticsearch/commit/80fee8666ff5dd61ba29b175857cf42ce3b9eab9

        catalog.registerClusterGauge("index_completion_size_bytes", "Size of completion suggest statistics", "index", "context");

        catalog.registerClusterGauge("index_segments_number", "Current number of segments", "index", "context");
        catalog.registerClusterGauge("index_segments_memory_bytes", "Memory used by segments", "type", "index", "context");

        catalog.registerClusterGauge("index_suggest_current_number", "Current rate of suggests", "index", "context");
        catalog.registerClusterGauge("index_suggest_count", "Count of suggests", "index", "context");
        catalog.registerClusterGauge("index_suggest_time_seconds", "Time spent while making suggests", "index", "context");

        catalog.registerClusterGauge("index_requestcache_memory_size_bytes", "Memory used for request cache", "index", "context");
        catalog.registerClusterGauge("index_requestcache_hit_count", "Number of hits in request cache", "index", "context");
        catalog.registerClusterGauge("index_requestcache_miss_count", "Number of misses in request cache", "index", "context");
        catalog.registerClusterGauge("index_requestcache_evictions_count", "Number of evictions in request cache", "index", "context");

        catalog.registerClusterGauge("index_recovery_current_number", "Current number of recoveries", "type", "index", "context");
        catalog.registerClusterGauge("index_recovery_throttle_time_seconds", "Time spent while throttling recoveries", "index", "context");

        catalog.registerClusterGauge("index_translog_operations_number", "Current number of translog operations", "index", "context");
        catalog.registerClusterGauge("index_translog_size_bytes", "Translog size", "index", "context");
        catalog.registerClusterGauge("index_translog_uncommitted_operations_number", "Current number of uncommitted translog operations", "index", "context");
        catalog.registerClusterGauge("index_translog_uncommitted_size_bytes", "Translog uncommitted size", "index", "context");

        catalog.registerClusterGauge("index_warmer_current_number", "Current number of warmer", "index", "context");
        catalog.registerClusterGauge("index_warmer_time_seconds", "Time spent during warmers", "index", "context");
        catalog.registerClusterGauge("index_warmer_count", "Counter of warmers", "index", "context");
    }

    private void updatePerIndexMetrics(ClusterHealthResponse chr, IndicesStatsResponse isr) {

        if (chr != null && isr != null) {
            for (Map.Entry<String, IndexStats> entry : isr.getIndices().entrySet()) {
                String indexName = entry.getKey();
                ClusterIndexHealth cih = chr.getIndices().get(indexName);
                catalog.setClusterGauge("index_status", cih.getStatus().value(), indexName);
                catalog.setClusterGauge("index_replicas_number", cih.getNumberOfReplicas(), indexName);
                catalog.setClusterGauge("index_shards_number", cih.getActiveShards(), "active", indexName);
                catalog.setClusterGauge("index_shards_number", cih.getNumberOfShards(), "shards", indexName);
                catalog.setClusterGauge("index_shards_number", cih.getActivePrimaryShards(), "active_primary", indexName);
                catalog.setClusterGauge("index_shards_number", cih.getInitializingShards(), "initializing", indexName);
                catalog.setClusterGauge("index_shards_number", cih.getRelocatingShards(), "relocating", indexName);
                catalog.setClusterGauge("index_shards_number", cih.getUnassignedShards(), "unassigned", indexName);
                IndexStats indexStats = entry.getValue();
                updatePerIndexContextMetrics(indexName, "total", indexStats.getTotal());
                updatePerIndexContextMetrics(indexName, "primaries", indexStats.getPrimaries());
            }
        }
    }

    @SuppressWarnings("checkstyle:LineLength")
    private void updatePerIndexContextMetrics(String indexName, String context, CommonStats idx) {
        catalog.setClusterGauge("index_doc_number", idx.getDocs().getCount(), indexName, context);
        catalog.setClusterGauge("index_doc_deleted_number", idx.getDocs().getDeleted(), indexName, context);

        catalog.setClusterGauge("index_store_size_bytes", idx.getStore().getSizeInBytes(), indexName, context);

        catalog.setClusterGauge("index_indexing_delete_count", idx.getIndexing().getTotal().getDeleteCount(), indexName, context);
        catalog.setClusterGauge("index_indexing_delete_current_number", idx.getIndexing().getTotal().getDeleteCurrent(), indexName, context);
        catalog.setClusterGauge("index_indexing_delete_time_seconds", idx.getIndexing().getTotal().getDeleteTime().seconds(), indexName, context);
        catalog.setClusterGauge("index_indexing_index_count", idx.getIndexing().getTotal().getIndexCount(), indexName, context);
        catalog.setClusterGauge("index_indexing_index_current_number", idx.getIndexing().getTotal().getIndexCurrent(), indexName, context);
        catalog.setClusterGauge("index_indexing_index_failed_count", idx.getIndexing().getTotal().getIndexFailedCount(), indexName, context);
        catalog.setClusterGauge("index_indexing_index_time_seconds", idx.getIndexing().getTotal().getIndexTime().seconds(), indexName, context);
        catalog.setClusterGauge("index_indexing_noop_update_count", idx.getIndexing().getTotal().getNoopUpdateCount(), indexName, context);
        catalog.setClusterGauge("index_indexing_is_throttled_bool", idx.getIndexing().getTotal().isThrottled() ? 1 : 0, indexName, context);
        catalog.setClusterGauge("index_indexing_throttle_time_seconds", idx.getIndexing().getTotal().getThrottleTime().seconds(), indexName, context);

        catalog.setClusterGauge("index_get_count", idx.getGet().getCount(), indexName, context);
        catalog.setClusterGauge("index_get_time_seconds", idx.getGet().getTimeInMillis() / 1000.0, indexName, context);
        catalog.setClusterGauge("index_get_exists_count", idx.getGet().getExistsCount(), indexName, context);
        catalog.setClusterGauge("index_get_exists_time_seconds", idx.getGet().getExistsTimeInMillis() / 1000.0, indexName, context);
        catalog.setClusterGauge("index_get_missing_count", idx.getGet().getMissingCount(), indexName, context);
        catalog.setClusterGauge("index_get_missing_time_seconds", idx.getGet().getMissingTimeInMillis() / 1000.0, indexName, context);
        catalog.setClusterGauge("index_get_current_number", idx.getGet().current(), indexName, context);

        catalog.setClusterGauge("index_search_open_contexts_number", idx.getSearch().getOpenContexts(), indexName, context);
        catalog.setClusterGauge("index_search_fetch_count", idx.getSearch().getTotal().getFetchCount(), indexName, context);
        catalog.setClusterGauge("index_search_fetch_current_number", idx.getSearch().getTotal().getFetchCurrent(), indexName, context);
        catalog.setClusterGauge("index_search_fetch_time_seconds", idx.getSearch().getTotal().getFetchTimeInMillis() / 1000.0, indexName, context);
        catalog.setClusterGauge("index_search_query_count", idx.getSearch().getTotal().getQueryCount(), indexName, context);
        catalog.setClusterGauge("index_search_query_current_number", idx.getSearch().getTotal().getQueryCurrent(), indexName, context);
        catalog.setClusterGauge("index_search_query_time_seconds", idx.getSearch().getTotal().getQueryTimeInMillis() / 1000.0, indexName, context);
        catalog.setClusterGauge("index_search_scroll_count", idx.getSearch().getTotal().getScrollCount(), indexName, context);
        catalog.setClusterGauge("index_search_scroll_current_number", idx.getSearch().getTotal().getScrollCurrent(), indexName, context);
        catalog.setClusterGauge("index_search_scroll_time_seconds", idx.getSearch().getTotal().getScrollTimeInMillis() / 1000.0, indexName, context);

        catalog.setClusterGauge("index_merges_current_number", idx.getMerge().getCurrent(), indexName, context);
        catalog.setClusterGauge("index_merges_current_docs_number", idx.getMerge().getCurrentNumDocs(), indexName, context);
        catalog.setClusterGauge("index_merges_current_size_bytes", idx.getMerge().getCurrentSizeInBytes(), indexName, context);
        catalog.setClusterGauge("index_merges_total_number", idx.getMerge().getTotal(), indexName, context);
        catalog.setClusterGauge("index_merges_total_time_seconds", idx.getMerge().getTotalTimeInMillis() / 1000.0, indexName, context);
        catalog.setClusterGauge("index_merges_total_docs_count", idx.getMerge().getTotalNumDocs(), indexName, context);
        catalog.setClusterGauge("index_merges_total_size_bytes", idx.getMerge().getTotalSizeInBytes(), indexName, context);
        catalog.setClusterGauge("index_merges_total_stopped_time_seconds", idx.getMerge().getTotalStoppedTimeInMillis() / 1000.0, indexName, context);
        catalog.setClusterGauge("index_merges_total_throttled_time_seconds", idx.getMerge().getTotalThrottledTimeInMillis() / 1000.0, indexName, context);
        catalog.setClusterGauge("index_merges_total_auto_throttle_bytes", idx.getMerge().getTotalBytesPerSecAutoThrottle(), indexName, context);

        catalog.setClusterGauge("index_refresh_total_count", idx.getRefresh().getTotal(), indexName, context);
        catalog.setClusterGauge("index_refresh_total_time_seconds", idx.getRefresh().getTotalTimeInMillis() / 1000.0, indexName, context);
        catalog.setClusterGauge("index_refresh_listeners_number", idx.getRefresh().getListeners(), indexName, context);

        catalog.setClusterGauge("index_flush_total_count", idx.getFlush().getTotal(), indexName, context);
        catalog.setClusterGauge("index_flush_total_time_seconds", idx.getFlush().getTotalTimeInMillis() / 1000.0, indexName, context);

        catalog.setClusterGauge("index_querycache_cache_count", idx.getQueryCache().getCacheCount(), indexName, context);
        catalog.setClusterGauge("index_querycache_cache_size_bytes", idx.getQueryCache().getCacheSize(), indexName, context);
        catalog.setClusterGauge("index_querycache_evictions_count", idx.getQueryCache().getEvictions(), indexName, context);
        catalog.setClusterGauge("index_querycache_hit_count", idx.getQueryCache().getHitCount(), indexName, context);
        catalog.setClusterGauge("index_querycache_memory_size_bytes", idx.getQueryCache().getMemorySizeInBytes(), indexName, context);
        catalog.setClusterGauge("index_querycache_miss_number", idx.getQueryCache().getMissCount(), indexName, context);
        catalog.setClusterGauge("index_querycache_total_number", idx.getQueryCache().getTotalCount(), indexName, context);

        catalog.setClusterGauge("index_fielddata_memory_size_bytes", idx.getFieldData().getMemorySizeInBytes(), indexName, context);
        catalog.setClusterGauge("index_fielddata_evictions_count", idx.getFieldData().getEvictions(), indexName, context);

        // Percolator cache was removed in ES 5.x
        // See https://github.com/elastic/elasticsearch/commit/80fee8666ff5dd61ba29b175857cf42ce3b9eab9

        catalog.setClusterGauge("index_completion_size_bytes", idx.getCompletion().getSizeInBytes(), indexName, context);

        catalog.setClusterGauge("index_segments_number", idx.getSegments().getCount(), indexName, context);
        // catalog.setClusterGauge("index_segments_memory_bytes", idx.getSegments().getMemoryInBytes(), "all", indexName, context);
        catalog.setClusterGauge("index_segments_memory_bytes", idx.getSegments().getBitsetMemoryInBytes(), "bitset", indexName, context);
        // catalog.setClusterGauge("index_segments_memory_bytes", idx.getSegments().getDocValuesMemoryInBytes(), "docvalues", indexName, context);
        catalog.setClusterGauge("index_segments_memory_bytes", idx.getSegments().getIndexWriterMemoryInBytes(), "indexwriter", indexName, context);
        // catalog.setClusterGauge("index_segments_memory_bytes", idx.getSegments().getNormsMemoryInBytes(), "norms", indexName, context);
        // catalog.setClusterGauge("index_segments_memory_bytes", idx.getSegments().getStoredFieldsMemoryInBytes(), "storefields", indexName, context);
        // catalog.setClusterGauge("index_segments_memory_bytes", idx.getSegments().getTermsMemoryInBytes(), "terms", indexName, context);
        // catalog.setClusterGauge("index_segments_memory_bytes", idx.getSegments().getTermVectorsMemoryInBytes(), "termvectors", indexName, context);
        catalog.setClusterGauge("index_segments_memory_bytes", idx.getSegments().getVersionMapMemoryInBytes(), "versionmap", indexName, context);
        // catalog.setClusterGauge("index_segments_memory_bytes", idx.getSegments().getPointsMemoryInBytes(), "points", indexName, context);

        catalog.setClusterGauge("index_suggest_current_number", idx.getSearch().getTotal().getSuggestCurrent(), indexName, context);
        catalog.setClusterGauge("index_suggest_count", idx.getSearch().getTotal().getSuggestCount(), indexName, context);
        catalog.setClusterGauge("index_suggest_time_seconds", idx.getSearch().getTotal().getSuggestTimeInMillis() / 1000.0, indexName, context);

        catalog.setClusterGauge("index_requestcache_memory_size_bytes", idx.getRequestCache().getMemorySizeInBytes(), indexName, context);
        catalog.setClusterGauge("index_requestcache_hit_count", idx.getRequestCache().getHitCount(), indexName, context);
        catalog.setClusterGauge("index_requestcache_miss_count", idx.getRequestCache().getMissCount(), indexName, context);
        catalog.setClusterGauge("index_requestcache_evictions_count", idx.getRequestCache().getEvictions(), indexName, context);

        catalog.setClusterGauge("index_recovery_current_number", idx.getRecoveryStats().currentAsSource(), "source", indexName, context);
        catalog.setClusterGauge("index_recovery_current_number", idx.getRecoveryStats().currentAsTarget(), "target", indexName, context);
        catalog.setClusterGauge("index_recovery_throttle_time_seconds", idx.getRecoveryStats().throttleTime().getSeconds(), indexName, context);

        catalog.setClusterGauge("index_translog_operations_number", idx.getTranslog().estimatedNumberOfOperations(), indexName, context);
        catalog.setClusterGauge("index_translog_size_bytes", idx.getTranslog().getTranslogSizeInBytes(), indexName, context);
        catalog.setClusterGauge("index_translog_uncommitted_operations_number", idx.getTranslog().getUncommittedOperations(), indexName, context);
        catalog.setClusterGauge("index_translog_uncommitted_size_bytes", idx.getTranslog().getUncommittedSizeInBytes(), indexName, context);

        catalog.setClusterGauge("index_warmer_current_number", idx.getWarmer().current(), indexName, context);
        catalog.setClusterGauge("index_warmer_time_seconds", idx.getWarmer().totalTimeInMillis(), indexName, context);
        catalog.setClusterGauge("index_warmer_count", idx.getWarmer().total(), indexName, context);
    }

    private void registerTransportMetrics() {
        catalog.registerNodeGauge("transport_server_open_number", "Opened server connections");

        catalog.registerNodeGauge("transport_rx_packets_count", "Received packets");
        catalog.registerNodeGauge("transport_tx_packets_count", "Sent packets");

        catalog.registerNodeGauge("transport_rx_bytes_count", "Bytes received");
        catalog.registerNodeGauge("transport_tx_bytes_count", "Bytes sent");
    }

    private void updateTransportMetrics(String nodeName, String nodeId, TransportStats ts) {
        if (ts != null) {
            catalog.setNodeGauge("transport_server_open_number", ts.getServerOpen(), nodeName, nodeId);

            catalog.setNodeGauge("transport_rx_packets_count", ts.getRxCount(), nodeName, nodeId);
            catalog.setNodeGauge("transport_tx_packets_count", ts.getTxCount(), nodeName, nodeId);

            catalog.setNodeGauge("transport_rx_bytes_count", ts.getRxSize().getBytes(), nodeName, nodeId);
            catalog.setNodeGauge("transport_tx_bytes_count", ts.getTxSize().getBytes(), nodeName, nodeId);
        }
    }

    private void registerHTTPMetrics() {
        catalog.registerNodeGauge("http_open_server_number", "Number of open server connections");
        catalog.registerNodeGauge("http_open_total_count", "Count of opened connections");
    }

    private void updateHTTPMetrics(String nodeName, String nodeId, HttpStats http) {
        if (http != null) {
            catalog.setNodeGauge("http_open_server_number", http.getServerOpen(), nodeName, nodeId);
            catalog.setNodeGauge("http_open_total_count", http.getTotalOpen(), nodeName, nodeId);
        }
    }

    private void registerThreadPoolMetrics() {
        catalog.registerNodeGauge("threadpool_threads_number", "Number of threads in thread pool", "name", "type");
        catalog.registerNodeGauge("threadpool_threads_count", "Count of threads in thread pool", "name", "type");
        catalog.registerNodeGauge("threadpool_tasks_number", "Number of tasks in thread pool", "name", "type");
    }

    private void updateThreadPoolMetrics(String nodeName, String nodeId, ThreadPoolStats tps) {
        if (tps != null) {
            for (ThreadPoolStats.Stats st : tps) {
                String name = st.name();
                catalog.setNodeGauge("threadpool_threads_number", st.threads(), nodeName, nodeId, name, "threads");
                catalog.setNodeGauge("threadpool_threads_number", st.active(), nodeName, nodeId, name, "active");
                catalog.setNodeGauge("threadpool_threads_number", st.largest(), nodeName, nodeId, name, "largest");
                catalog.setNodeGauge("threadpool_threads_count", st.completed(), nodeName, nodeId, name, "completed");
                catalog.setNodeGauge("threadpool_threads_count", st.rejected(), nodeName, nodeId, name, "rejected");
                catalog.setNodeGauge("threadpool_tasks_number", st.queue(), nodeName, nodeId, name, "queue");
            }
        }
    }

    @SuppressWarnings("checkstyle:LineLength")
    private void registerIngestMetrics() {
        catalog.registerNodeGauge("ingest_total_count", "Ingestion total number");
        catalog.registerNodeGauge("ingest_total_time_seconds", "Ingestion total time in seconds");
        catalog.registerNodeGauge("ingest_total_current", "Ingestion total current");
        catalog.registerNodeGauge("ingest_total_failed_count", "Ingestion total failed");

        catalog.registerNodeGauge("ingest_pipeline_total_count", "Ingestion total number", "pipeline");
        catalog.registerNodeGauge("ingest_pipeline_total_time_seconds", "Ingestion total time in seconds", "pipeline");
        catalog.registerNodeGauge("ingest_pipeline_total_current", "Ingestion total current", "pipeline");
        catalog.registerNodeGauge("ingest_pipeline_total_failed_count", "Ingestion total failed", "pipeline");

        catalog.registerNodeGauge("ingest_pipeline_processor_total_count", "Ingestion total number", "pipeline", "processor");
        catalog.registerNodeGauge("ingest_pipeline_processor_total_time_seconds", "Ingestion total time in seconds", "pipeline", "processor");
        catalog.registerNodeGauge("ingest_pipeline_processor_total_current", "Ingestion total current", "pipeline", "processor");
        catalog.registerNodeGauge("ingest_pipeline_processor_total_failed_count", "Ingestion total failed", "pipeline", "processor");
    }

    @SuppressWarnings("checkstyle:LineLength")
    private void updateIngestMetrics(String nodeName, String nodeId, IngestStats is) {
        if (is != null) {
            catalog.setNodeGauge("ingest_total_count", is.totalStats().ingestCount(), nodeName, nodeId);
            catalog.setNodeGauge("ingest_total_time_seconds", is.totalStats().ingestTimeInMillis() / 1000.0, nodeName, nodeId);
            catalog.setNodeGauge("ingest_total_current", is.totalStats().ingestCurrent(), nodeName, nodeId);
            catalog.setNodeGauge("ingest_total_failed_count", is.totalStats().ingestFailedCount(), nodeName, nodeId);

            for (IngestStats.PipelineStat st : is.pipelineStats()) {
                String pipeline = st.pipelineId();
                catalog.setNodeGauge("ingest_pipeline_total_count", st.stats().ingestCount(), nodeName, nodeId, pipeline);
                catalog.setNodeGauge("ingest_pipeline_total_time_seconds", st.stats().ingestTimeInMillis() / 1000.0,
                    nodeName, nodeId, pipeline);
                catalog.setNodeGauge("ingest_pipeline_total_current", st.stats().ingestCurrent(), nodeName, nodeId, pipeline);
                catalog.setNodeGauge("ingest_pipeline_total_failed_count", st.stats().ingestFailedCount(), nodeName, nodeId, pipeline);

                List<IngestStats.ProcessorStat> pss = is.processorStats().get(pipeline);
                if (pss != null) {
                    for (IngestStats.ProcessorStat ps : pss) {
                        String processor = ps.name();
                        catalog.setNodeGauge("ingest_pipeline_processor_total_count", ps.stats().ingestCount(), nodeName, nodeId, pipeline, processor);
                        catalog.setNodeGauge("ingest_pipeline_processor_total_time_seconds", ps.stats().ingestTimeInMillis() / 1000.0,
                            nodeName, nodeId, pipeline, processor);
                        catalog.setNodeGauge("ingest_pipeline_processor_total_current", ps.stats().ingestCurrent(), nodeName, nodeId, pipeline, processor);
                        catalog.setNodeGauge("ingest_pipeline_processor_total_failed_count", ps.stats().ingestFailedCount(), nodeName, nodeId, pipeline, processor);
                    }
                }
            }
        }
    }

    private void registerCircuitBreakerMetrics() {
        catalog.registerNodeGauge("circuitbreaker_estimated_bytes", "Circuit breaker estimated size", "name");
        catalog.registerNodeGauge("circuitbreaker_limit_bytes", "Circuit breaker size limit", "name");
        catalog.registerNodeGauge("circuitbreaker_overhead_ratio", "Circuit breaker overhead ratio", "name");
        catalog.registerNodeGauge("circuitbreaker_tripped_count", "Circuit breaker tripped count", "name");
    }

    private void updateCircuitBreakersMetrics(String nodeName, String nodeId, AllCircuitBreakerStats acbs) {
        if (acbs != null) {
            for (CircuitBreakerStats cbs : acbs.getAllStats()) {
                String name = cbs.getName();
                catalog.setNodeGauge("circuitbreaker_estimated_bytes", cbs.getEstimated(), nodeName, nodeId, name);
                catalog.setNodeGauge("circuitbreaker_limit_bytes", cbs.getLimit(), nodeName, nodeId, name);
                catalog.setNodeGauge("circuitbreaker_overhead_ratio", cbs.getOverhead(), nodeName, nodeId, name);
                catalog.setNodeGauge("circuitbreaker_tripped_count", cbs.getTrippedCount(), nodeName, nodeId, name);
            }
        }
    }

    private void registerScriptMetrics() {
        catalog.registerNodeGauge("script_cache_evictions_count", "Number of evictions in scripts cache");
        catalog.registerNodeGauge("script_compilations_count", "Number of scripts compilations");
    }

    private void updateScriptMetrics(String nodeName, String nodeId, ScriptStats sc) {
        if (sc != null) {
            catalog.setNodeGauge("script_cache_evictions_count", sc.getCacheEvictions(), nodeName, nodeId);
            catalog.setNodeGauge("script_compilations_count", sc.getCompilations(), nodeName, nodeId);
        }
    }

    private void registerProcessMetrics() {
        catalog.registerNodeGauge("process_cpu_percent", "CPU percentage used by ES process");
        catalog.registerNodeGauge("process_cpu_time_seconds", "CPU time used by ES process");

        catalog.registerNodeGauge("process_mem_total_virtual_bytes", "Memory used by ES process");

        catalog.registerNodeGauge("process_file_descriptors_open_number", "Open file descriptors");
        catalog.registerNodeGauge("process_file_descriptors_max_number", "Max file descriptors");
    }

    private void updateProcessMetrics(String nodeName, String nodeId, ProcessStats ps) {
        if (ps != null) {
            catalog.setNodeGauge("process_cpu_percent", ps.getCpu().getPercent(), nodeName, nodeId);
            catalog.setNodeGauge("process_cpu_time_seconds", ps.getCpu().getTotal().getSeconds(), nodeName, nodeId);

            catalog.setNodeGauge("process_mem_total_virtual_bytes", ps.getMem().getTotalVirtual().getBytes(), nodeName, nodeId);

            catalog.setNodeGauge("process_file_descriptors_open_number", ps.getOpenFileDescriptors(), nodeName, nodeId);
            catalog.setNodeGauge("process_file_descriptors_max_number", ps.getMaxFileDescriptors(), nodeName, nodeId);
        }
    }

    private void registerJVMMetrics() {
        catalog.registerNodeGauge("jvm_uptime_seconds", "JVM uptime");
        catalog.registerNodeGauge("jvm_mem_heap_max_bytes", "Maximum used memory in heap");
        catalog.registerNodeGauge("jvm_mem_heap_used_bytes", "Memory used in heap");
        catalog.registerNodeGauge("jvm_mem_heap_used_percent", "Percentage of memory used in heap");
        catalog.registerNodeGauge("jvm_mem_nonheap_used_bytes", "Memory used apart from heap");
        catalog.registerNodeGauge("jvm_mem_heap_committed_bytes", "Committed bytes in heap");
        catalog.registerNodeGauge("jvm_mem_nonheap_committed_bytes", "Committed bytes apart from heap");

        catalog.registerNodeGauge("jvm_mem_pool_max_bytes", "Maximum usage of memory pool", "pool");
        catalog.registerNodeGauge("jvm_mem_pool_peak_max_bytes", "Maximum usage peak of memory pool", "pool");
        catalog.registerNodeGauge("jvm_mem_pool_used_bytes", "Used memory in memory pool", "pool");
        catalog.registerNodeGauge("jvm_mem_pool_peak_used_bytes", "Used memory peak in memory pool", "pool");

        catalog.registerNodeGauge("jvm_threads_number", "Number of threads");
        catalog.registerNodeGauge("jvm_threads_peak_number", "Peak number of threads");

        catalog.registerNodeGauge("jvm_gc_collection_count", "Count of GC collections", "gc");
        catalog.registerNodeGauge("jvm_gc_collection_time_seconds", "Time spent for GC collections", "gc");

        catalog.registerNodeGauge("jvm_bufferpool_number", "Number of buffer pools", "bufferpool");
        catalog.registerNodeGauge("jvm_bufferpool_total_capacity_bytes", "Total capacity provided by buffer pools",
                "bufferpool");
        catalog.registerNodeGauge("jvm_bufferpool_used_bytes", "Used memory in buffer pools", "bufferpool");

        catalog.registerNodeGauge("jvm_classes_loaded_number", "Count of loaded classes");
        catalog.registerNodeGauge("jvm_classes_total_loaded_number", "Total count of loaded classes");
        catalog.registerNodeGauge("jvm_classes_unloaded_number", "Count of unloaded classes");
    }

    private void updateJVMMetrics(String nodeName, String nodeId, JvmStats jvm) {
        if (jvm != null) {
            catalog.setNodeGauge("jvm_uptime_seconds", jvm.getUptime().getSeconds(), nodeName, nodeId);

            catalog.setNodeGauge("jvm_mem_heap_max_bytes", jvm.getMem().getHeapMax().getBytes(), nodeName, nodeId);
            catalog.setNodeGauge("jvm_mem_heap_used_bytes", jvm.getMem().getHeapUsed().getBytes(), nodeName, nodeId);
            catalog.setNodeGauge("jvm_mem_heap_used_percent", jvm.getMem().getHeapUsedPercent(), nodeName, nodeId);
            catalog.setNodeGauge("jvm_mem_nonheap_used_bytes", jvm.getMem().getNonHeapUsed().getBytes(), nodeName, nodeId);
            catalog.setNodeGauge("jvm_mem_heap_committed_bytes", jvm.getMem().getHeapCommitted().getBytes(), nodeName, nodeId);
            catalog.setNodeGauge("jvm_mem_nonheap_committed_bytes", jvm.getMem().getNonHeapCommitted().getBytes(), nodeName, nodeId);

            for (JvmStats.MemoryPool mp : jvm.getMem()) {
                String name = mp.getName();

                catalog.setNodeGauge("jvm_mem_pool_max_bytes", mp.getMax().getBytes(), nodeName, nodeId, name);
                catalog.setNodeGauge("jvm_mem_pool_max_bytes", mp.getMax().getBytes(), nodeName, nodeId, name);
                catalog.setNodeGauge("jvm_mem_pool_used_bytes", mp.getUsed().getBytes(), nodeName, nodeId, name);
                catalog.setNodeGauge("jvm_mem_pool_used_bytes", mp.getUsed().getBytes(), nodeName, nodeId, name);
            }

            catalog.setNodeGauge("jvm_threads_number", jvm.getThreads().getCount(), nodeName, nodeId);
            catalog.setNodeGauge("jvm_threads_peak_number", jvm.getThreads().getPeakCount(), nodeName, nodeId);

            for (JvmStats.GarbageCollector gc : jvm.getGc().getCollectors()) {
                String name = gc.getName();
                catalog.setNodeGauge("jvm_gc_collection_count", gc.getCollectionCount(), nodeName, nodeId, name);
                catalog.setNodeGauge("jvm_gc_collection_time_seconds", gc.getCollectionTime().getSeconds(), nodeName, nodeId, name);
            }

            for (JvmStats.BufferPool bp : jvm.getBufferPools()) {
                String name = bp.getName();
                catalog.setNodeGauge("jvm_bufferpool_number", bp.getCount(), nodeName, nodeId, name);
                catalog.setNodeGauge("jvm_bufferpool_total_capacity_bytes", bp.getTotalCapacity().getBytes(), nodeName, nodeId, name);
                catalog.setNodeGauge("jvm_bufferpool_used_bytes", bp.getUsed().getBytes(), nodeName, nodeId, name);
            }
            if (jvm.getClasses() != null) {
                catalog.setNodeGauge("jvm_classes_loaded_number", jvm.getClasses().getLoadedClassCount(), nodeName, nodeId);
                catalog.setNodeGauge("jvm_classes_total_loaded_number", jvm.getClasses().getTotalLoadedClassCount(), nodeName, nodeId);
                catalog.setNodeGauge("jvm_classes_unloaded_number", jvm.getClasses().getUnloadedClassCount(), nodeName, nodeId);
            }
        }
    }

    private void registerOsMetrics() {
        catalog.registerNodeGauge("os_cpu_percent", "CPU usage in percent");

        catalog.registerNodeGauge("os_load_average_one_minute", "CPU load");
        catalog.registerNodeGauge("os_load_average_five_minutes", "CPU load");
        catalog.registerNodeGauge("os_load_average_fifteen_minutes", "CPU load");

        catalog.registerNodeGauge("os_mem_free_bytes", "Memory free");
        catalog.registerNodeGauge("os_mem_free_percent", "Memory free in percent");
        catalog.registerNodeGauge("os_mem_used_bytes", "Memory used");
        catalog.registerNodeGauge("os_mem_used_percent", "Memory used in percent");
        catalog.registerNodeGauge("os_mem_total_bytes", "Total memory size");

        catalog.registerNodeGauge("os_swap_free_bytes", "Swap free");
        catalog.registerNodeGauge("os_swap_used_bytes", "Swap used");
        catalog.registerNodeGauge("os_swap_total_bytes", "Total swap size");
    }

    private void updateOsMetrics(String nodeName, String nodeId, OsStats os) {
        if (os != null) {
            if (os.getCpu() != null) {
                catalog.setNodeGauge("os_cpu_percent", os.getCpu().getPercent(), nodeName, nodeId);
                double[] loadAverage = os.getCpu().getLoadAverage();
                if (loadAverage != null && loadAverage.length == 3) {
                    catalog.setNodeGauge("os_load_average_one_minute", os.getCpu().getLoadAverage()[0], nodeName, nodeId);
                    catalog.setNodeGauge("os_load_average_five_minutes", os.getCpu().getLoadAverage()[1], nodeName, nodeId);
                    catalog.setNodeGauge("os_load_average_fifteen_minutes", os.getCpu().getLoadAverage()[2], nodeName, nodeId);
                }
            }

            if (os.getMem() != null) {
                catalog.setNodeGauge("os_mem_free_bytes", os.getMem().getFree().getBytes(), nodeName, nodeId);
                catalog.setNodeGauge("os_mem_free_percent", os.getMem().getFreePercent(), nodeName, nodeId);
                catalog.setNodeGauge("os_mem_used_bytes", os.getMem().getUsed().getBytes(), nodeName, nodeId);
                catalog.setNodeGauge("os_mem_used_percent", os.getMem().getUsedPercent(), nodeName, nodeId);
                catalog.setNodeGauge("os_mem_total_bytes", os.getMem().getTotal().getBytes(), nodeName, nodeId);
            }

            if (os.getSwap() != null) {
                catalog.setNodeGauge("os_swap_free_bytes", os.getSwap().getFree().getBytes(), nodeName, nodeId);
                catalog.setNodeGauge("os_swap_used_bytes", os.getSwap().getUsed().getBytes(), nodeName, nodeId);
                catalog.setNodeGauge("os_swap_total_bytes", os.getSwap().getTotal().getBytes(), nodeName, nodeId);
            }
        }
    }

    private void registerFsMetrics() {
        catalog.registerNodeGauge("fs_total_total_bytes", "Total disk space for all mount points");
        catalog.registerNodeGauge("fs_total_available_bytes", "Available disk space for all mount points");
        catalog.registerNodeGauge("fs_total_free_bytes", "Free disk space for all mountpoints");

        catalog.registerNodeGauge("fs_path_total_bytes", "Total disk space", "path", "mount", "type");
        catalog.registerNodeGauge("fs_path_available_bytes", "Available disk space", "path", "mount", "type");
        catalog.registerNodeGauge("fs_path_free_bytes", "Free disk space", "path", "mount", "type");

        catalog.registerNodeGauge("fs_io_total_operations", "Total IO operations");
        catalog.registerNodeGauge("fs_io_total_read_operations", "Total IO read operations");
        catalog.registerNodeGauge("fs_io_total_write_operations", "Total IO write operations");
        catalog.registerNodeGauge("fs_io_total_read_bytes", "Total IO read bytes");
        catalog.registerNodeGauge("fs_io_total_write_bytes", "Total IO write bytes");
    }

    private void updateFsMetrics(String nodeName, String nodeId, FsInfo fs) {
        if (fs != null) {
            catalog.setNodeGauge("fs_total_total_bytes", fs.getTotal().getTotal().getBytes(), nodeName, nodeId);
            catalog.setNodeGauge("fs_total_available_bytes", fs.getTotal().getAvailable().getBytes(), nodeName, nodeId);
            catalog.setNodeGauge("fs_total_free_bytes", fs.getTotal().getFree().getBytes(), nodeName, nodeId);

            for (FsInfo.Path fspath : fs) {
                String path = fspath.getPath();
                String mount = fspath.getMount();
                String type = fspath.getType();
                catalog.setNodeGauge("fs_path_total_bytes", fspath.getTotal().getBytes(), nodeName, nodeId, path, mount, type);
                catalog.setNodeGauge("fs_path_available_bytes", fspath.getAvailable().getBytes(), nodeName, nodeId, path, mount, type);
                catalog.setNodeGauge("fs_path_free_bytes", fspath.getFree().getBytes(), nodeName, nodeId, path, mount, type);
            }

            FsInfo.IoStats ioStats = fs.getIoStats();
            if (ioStats != null) {
                catalog.setNodeGauge("fs_io_total_operations", fs.getIoStats().getTotalOperations(), nodeName, nodeId);
                catalog.setNodeGauge("fs_io_total_read_operations", fs.getIoStats().getTotalReadOperations(), nodeName, nodeId);
                catalog.setNodeGauge("fs_io_total_write_operations", fs.getIoStats().getTotalWriteOperations(), nodeName, nodeId);
                catalog.setNodeGauge("fs_io_total_read_bytes", fs.getIoStats().getTotalReadKilobytes() * 1024, nodeName, nodeId);
                catalog.setNodeGauge("fs_io_total_write_bytes", fs.getIoStats().getTotalWriteKilobytes() * 1024, nodeName, nodeId);
            }
        }
    }

    @SuppressWarnings("checkstyle:LineLength")
    private void registerESSettings() {
        catalog.registerClusterGauge("cluster_routing_allocation_disk_threshold_enabled", "Disk allocation decider is enabled");
        //
        catalog.registerClusterGauge("cluster_routing_allocation_disk_watermark_low_bytes", "Low watermark for disk usage in bytes");
        catalog.registerClusterGauge("cluster_routing_allocation_disk_watermark_high_bytes", "High watermark for disk usage in bytes");
        catalog.registerClusterGauge("cluster_routing_allocation_disk_watermark_flood_stage_bytes", "Flood stage for disk usage in bytes");
        //
        catalog.registerClusterGauge("cluster_routing_allocation_disk_watermark_low_pct", "Low watermark for disk usage in pct");
        catalog.registerClusterGauge("cluster_routing_allocation_disk_watermark_high_pct", "High watermark for disk usage in pct");
        catalog.registerClusterGauge("cluster_routing_allocation_disk_watermark_flood_stage_pct", "Flood stage watermark for disk usage in pct");
    }

    @SuppressWarnings({"checkstyle:LineLength", "checkstyle:LeftCurly"})
    private void updateESSettings(ClusterStatsData stats) {
        
        if (stats != null) {
            catalog.setClusterGauge("cluster_routing_allocation_disk_threshold_enabled", Boolean.TRUE.equals(stats.getThresholdEnabled()) ? 1 : 0);
            // According to Elasticsearch documentation the following settings must be set either in pct or bytes size.
            // Mixing is not allowed. We rely on Elasticsearch to do all necessary checks and we simply
            // output all those metrics that are not null. If this will lead to mixed metric then we do not
            // consider it our fault.
            if (stats.getDiskLowInBytes() != null) { catalog.setClusterGauge("cluster_routing_allocation_disk_watermark_low_bytes", stats.getDiskLowInBytes()); }
            if (stats.getDiskHighInBytes() != null) { catalog.setClusterGauge("cluster_routing_allocation_disk_watermark_high_bytes", stats.getDiskHighInBytes()); }
            if (stats.getFloodStageInBytes() != null) { catalog.setClusterGauge("cluster_routing_allocation_disk_watermark_flood_stage_bytes", stats.getFloodStageInBytes()); }
            //
            if (stats.getDiskLowInPct() != null) { catalog.setClusterGauge("cluster_routing_allocation_disk_watermark_low_pct", stats.getDiskLowInPct()); }
            if (stats.getDiskHighInPct() != null) { catalog.setClusterGauge("cluster_routing_allocation_disk_watermark_high_pct", stats.getDiskHighInPct()); }
            if (stats.getFloodStageInPct() != null) { catalog.setClusterGauge("cluster_routing_allocation_disk_watermark_flood_stage_pct", stats.getFloodStageInPct()); }
        }
    }

    public void updateMetrics(ClusterHealthResponse clusterHealthResponse, List<NodeStats> nodesStats,
                              IndicesStatsResponse indicesStats, ClusterStatsData clusterStatsData) {
        Summary.Timer timer = catalog.startSummaryTimer("metrics_generate_time_seconds");

        updateClusterMetrics(clusterHealthResponse);
        if (isPrometheusIndices) {
            updatePerIndexMetrics(clusterHealthResponse, indicesStats);
        }
        if (isPrometheusClusterSettings) {
            updateESSettings(clusterStatsData);
        }

        for (NodeStats nodeStats : nodesStats) {
            String nodeName = nodeStats.getNode().getName();
            String nodeId = nodeStats.getNode().getId();
            logger.info("Prepare new Prometheus metric collector for: [{}], [{}]", nodeId, nodeName);

            updateNodeMetrics(nodeName, nodeId, nodeStats);
            updateIndicesMetrics(nodeName, nodeId, nodeStats.getIndices());
            updateTransportMetrics(nodeName, nodeId, nodeStats.getTransport());
            updateHTTPMetrics(nodeName, nodeId, nodeStats.getHttp());
            updateThreadPoolMetrics(nodeName, nodeId, nodeStats.getThreadPool());
            updateIngestMetrics(nodeName, nodeId, nodeStats.getIngestStats());
            updateCircuitBreakersMetrics(nodeName, nodeId, nodeStats.getBreaker());
            updateScriptMetrics(nodeName, nodeId, nodeStats.getScriptStats());
            updateProcessMetrics(nodeName, nodeId, nodeStats.getProcess());
            updateJVMMetrics(nodeName, nodeId, nodeStats.getJvm());
            updateOsMetrics(nodeName, nodeId, nodeStats.getOs());
            updateFsMetrics(nodeName, nodeId, nodeStats.getFs());
        }

        timer.observeDuration();
    }

    public PrometheusMetricsCatalog getCatalog() {
        return catalog;
    }
}