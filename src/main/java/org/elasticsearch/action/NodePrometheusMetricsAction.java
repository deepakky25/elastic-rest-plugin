package org.elasticsearch.action;

/**
 * Action class for Prometheus Exporter plugin.
 */
public class NodePrometheusMetricsAction extends ActionType<NodePrometheusMetricsResponse> {

    public static final NodePrometheusMetricsAction INSTANCE = new NodePrometheusMetricsAction();
    public static final String NAME = "cluster:monitor/prometheus/metrics";

    private NodePrometheusMetricsAction() {
        super(NAME,  NodePrometheusMetricsResponse::new);
    }
}