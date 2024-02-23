package org.elasticsearch.action.admin.indices.stats;

import org.elasticsearch.common.io.stream.StreamInput;

import java.io.IOException;

/**
 * Utility methods.
 */
public class PackageAccessHelper {

    /**
     * Shortcut to IndicesStatsResponse constructor which has package access restriction.
     * @param in StreamInput
     * @return IndicesStatsResponse
     * @throws IOException When something goes wrong
     */
    public static IndicesStatsResponse createIndicesStatsResponse(StreamInput in) throws IOException {
        return in.readOptionalWriteable(IndicesStatsResponse::new);
    }
}