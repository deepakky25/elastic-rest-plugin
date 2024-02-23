package org.elasticsearch.resthandler;

import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;

/**
 * A container to keep settings for prometheus up to date with cluster setting changes.
 *
 * In order to make the settings dynamically updatable we took some inspiration from implementation
 * and use of DiskThresholdSettings class in Elasticsearch.
 */
public class PrometheusSettings {

    // These settings become part of cluster state available via HTTP at
    // curl <elasticsearch>/_cluster/settings?include_defaults=true&filter_path=defaults.prometheus
    // It is important to keep it under reasonable namespace to avoid collision with
    // other plugins or future/commercial parts of Elastic Stack itself.
    // Namespace "prometheus" sounds like safe bet for now.
    public static final Setting<Boolean> PROMETHEUS_CLUSTER_SETTINGS =
            Setting.boolSetting("prometheus.cluster.settings", true,
                    Setting.Property.Dynamic, Setting.Property.NodeScope);
    public static final Setting<Boolean> PROMETHEUS_INDICES =
            Setting.boolSetting("prometheus.indices", false,
                    Setting.Property.Dynamic, Setting.Property.NodeScope);

    private volatile boolean clusterSettings;
    private volatile boolean indices;

    public PrometheusSettings(Settings settings, ClusterSettings clusterSettings) {
        setPrometheusClusterSettings(PROMETHEUS_CLUSTER_SETTINGS.get(settings));
        setPrometheusIndices(PROMETHEUS_INDICES.get(settings));
        clusterSettings.addSettingsUpdateConsumer(PROMETHEUS_CLUSTER_SETTINGS, this::setPrometheusClusterSettings);
        clusterSettings.addSettingsUpdateConsumer(PROMETHEUS_INDICES, this::setPrometheusIndices);
    }

    private void setPrometheusClusterSettings(boolean flag) {
        this.clusterSettings = flag;
    }

    private void setPrometheusIndices(boolean flag) {
        this.indices = flag;
    }

    public boolean getPrometheusClusterSettings() {
        return this.clusterSettings;
    }

    public boolean getPrometheusIndices() {
        return this.indices;
    }
}