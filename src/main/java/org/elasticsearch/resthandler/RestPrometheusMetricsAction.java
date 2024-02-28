package org.elasticsearch.resthandler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.elasticsearch.action.NodePrometheusMetricsRequest;
import org.elasticsearch.action.NodePrometheusMetricsResponse;
import org.elasticsearch.action.admin.cluster.node.stats.NodeStats;
import org.elasticsearch.client.internal.node.NodeClient;
import org.elasticsearch.common.network.NetworkAddress;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.action.RestResponseListener;

import java.util.List;
import java.util.Locale;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import static org.elasticsearch.action.NodePrometheusMetricsAction.INSTANCE;
import static org.elasticsearch.rest.RestRequest.Method.GET;

/**
 * REST action class for Prometheus Exporter plugin.
 */
public class RestPrometheusMetricsAction extends BaseRestHandler {

    private final PrometheusSettings prometheusSettings;
    private final Logger logger = LogManager.getLogger(getClass());
 
     public RestPrometheusMetricsAction(Settings settings, ClusterSettings clusterSettings) {
        this.prometheusSettings = new PrometheusSettings(settings, clusterSettings);
    }

    @Override
    public List<Route> routes() {
        return unmodifiableList(asList(
            new Route(GET, "/_prometheus/metrics"))
        );
    }

    @Override
    public String getName() {
        return "prometheus_metrics_action";
    }

    // This method does not throw any IOException because there are no request parameters to be parsed
     // and processed. This may change in the future.
    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) {
        String remoteAddress = NetworkAddress.format(request.getHttpChannel().getRemoteAddress());
        logger.info(String.format(Locale.ENGLISH, "Received request for Prometheus metrics from %s",
                remoteAddress));

        NodePrometheusMetricsRequest metricsRequest = new NodePrometheusMetricsRequest();

        return channel -> client.execute(INSTANCE, metricsRequest,
                new RestResponseListener<NodePrometheusMetricsResponse>(channel) {

                    @Override
                    public RestResponse buildResponse(NodePrometheusMetricsResponse response) throws Exception {
                        String clusterName = response.getClusterHealth().getClusterName();
                        logger.info("Prepare new Prometheus metric collector for: [{}]", clusterName);

                        PrometheusMetricsCatalog catalog = new PrometheusMetricsCatalog(clusterName, "es_");
                        PrometheusMetricsCollector collector = new PrometheusMetricsCollector(catalog,
                            prometheusSettings.getPrometheusIndices(), prometheusSettings.getPrometheusClusterSettings());

                        collector.registerMetrics();
                        collector.updateMetrics(response.getClusterHealth(), response.getNodesStats(), response.getIndicesStats(),
                                response.getClusterStatsData());

                        return new RestResponse(RestStatus.OK, collector.getCatalog().toTextFormat());
                    }
        });
    }
}