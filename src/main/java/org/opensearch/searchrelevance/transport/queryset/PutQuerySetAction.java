/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.transport.queryset;

import static org.opensearch.searchrelevance.common.PluginConstants.TRANSPORT_ACTION_NAME_PREFIX;

import org.opensearch.action.ActionType;
import org.opensearch.action.index.IndexResponse;

/**
 * External Action for public facing RestPutQuerySetAction
 */
public class PutQuerySetAction extends ActionType<IndexResponse> {
    /** The name of this action */
    public static final String NAME = TRANSPORT_ACTION_NAME_PREFIX + "queryset/put";

    /** An instance of this action */
    public static final PutQuerySetAction INSTANCE = new PutQuerySetAction();

    private PutQuerySetAction() {
        super(NAME, IndexResponse::new);
    }
}
