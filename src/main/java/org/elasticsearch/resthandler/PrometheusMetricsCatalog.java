package org.elasticsearch.resthandler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Locale;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.Summary;
import io.prometheus.client.exporter.common.TextFormat;

/**
 * A class that describes a Prometheus metrics catalog.
 */

public class PrometheusMetricsCatalog {
    private static final Logger logger = LogManager.getLogger(RestPrometheusMetricsAction.class);

    private String clusterName;

    private String metricPrefix;

    private HashMap<String, Object> metrics;
    private CollectorRegistry registry;

    public PrometheusMetricsCatalog(String clusterName, String metricPrefix) {
        this.clusterName = clusterName;

        this.metricPrefix = metricPrefix;

        metrics = new HashMap<>();
        registry = new CollectorRegistry();
    }

    private String[] getExtendedClusterLabelNames(String... labelNames) {
        String[] extended = new String[labelNames.length + 1];
        extended[0] = "cluster";

        System.arraycopy(labelNames, 0, extended, 1, labelNames.length);

        return extended;
    }

    private String[] getExtendedClusterLabelValues(String... labelValues) {
        String[] extended = new String[labelValues.length + 1];
        extended[0] = clusterName;

        System.arraycopy(labelValues, 0, extended, 1, labelValues.length);

        return extended;
    }

    private String[] getExtendedNodeLabelNames(String... labelNames) {
        String[] extended = new String[labelNames.length + 3];
        extended[0] = "cluster";
        extended[1] = "node";
        extended[2] = "nodeid";

        System.arraycopy(labelNames, 0, extended, 3, labelNames.length);

        return extended;
    }

    public void registerClusterGauge(String metric, String help, String... labels) {
        Gauge gauge = Gauge.build().
                name(metricPrefix + metric).
                help(help).
                labelNames(getExtendedClusterLabelNames(labels)).
                register(registry);

        metrics.put(metric, gauge);

        logger.info(String.format(Locale.ENGLISH, "Registered new cluster gauge %s", metric));
    }

    public void setClusterGauge(String metric, double value, String... labelValues) {
        Gauge gauge = (Gauge) metrics.get(metric);
        gauge.labels(getExtendedClusterLabelValues(labelValues)).set(value);
    }

    public void registerNodeGauge(String metric, String help, String... labels) {
        Gauge gauge = Gauge.build().
                name(metricPrefix + metric).
                help(help).
                labelNames(getExtendedNodeLabelNames(labels)).
                register(registry);

        metrics.put(metric, gauge);

        logger.info(String.format(Locale.ENGLISH, "Registered new node gauge %s", metric));
    }

    public void setNodeGauge(String metric, double value, String... labelValues) {
        Gauge gauge = (Gauge) metrics.get(metric);
        gauge.labels(getExtendedClusterLabelValues(labelValues)).set(value);
    }

    public void registerSummaryTimer(String metric, String help, String... labels) {
        Summary summary = Summary.build().
                name(metricPrefix + metric).
                help(help).
                labelNames(getExtendedClusterLabelNames(labels)).
                register(registry);

        metrics.put(metric, summary);

        logger.info(String.format(Locale.ENGLISH, "Registered new summary %s", metric));
    }

    public Summary.Timer startSummaryTimer(String metric, String... labelValues) {
        Summary summary = (Summary) metrics.get(metric);
        return summary.labels(getExtendedClusterLabelValues(labelValues)).startTimer();
    }

    public String toTextFormat() throws IOException {
        Writer writer = new StringWriter();
        TextFormat.write004(writer, registry.metricFamilySamples());
        return writer.toString();
    }
}