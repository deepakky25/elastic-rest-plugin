package org.elasticsearch.action;

import org.elasticsearch.action.support.master.MasterNodeReadRequest;
import org.elasticsearch.common.io.stream.StreamInput;

import java.io.IOException;

/**
 * Action request class for Prometheus Exporter plugin.
 */
public class NodePrometheusMetricsRequest extends MasterNodeReadRequest<NodePrometheusMetricsRequest> {

    public NodePrometheusMetricsRequest() {
        super();
    }

    public NodePrometheusMetricsRequest(StreamInput in) throws IOException {
        super(in);
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }
}