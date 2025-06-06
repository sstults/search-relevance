/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance;

import static org.opensearch.client.RestClientBuilder.DEFAULT_MAX_CONN_PER_ROUTE;
import static org.opensearch.client.RestClientBuilder.DEFAULT_MAX_CONN_TOTAL;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.junit.After;
import org.opensearch.client.Request;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.WarningsHandler;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.MediaType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.test.rest.OpenSearchRestTestCase;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;

@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
public class BaseSearchRelevanceIT extends OpenSearchRestTestCase {
    private static final String PROTOCOL_HTTP = "http";
    private static final String PROTOCOL_HTTPS = "https";
    private static final String SYS_PROPERTY_KEY_HTTPS = "https";
    private static final String SYS_PROPERTY_KEY_CLUSTER_ENDPOINT = "tests.rest.cluster";
    private static final String SYS_PROPERTY_KEY_USER = "user";
    private static final String SYS_PROPERTY_KEY_PASSWORD = "password";
    private static final String DEFAULT_SOCKET_TIMEOUT = "60s";
    private static final String INTERNAL_INDICES_PREFIX = ".";
    public static final String DEFAULT_USER_AGENT = "Kibana";
    private static String protocol;

    protected final ClassLoader classLoader = this.getClass().getClassLoader();

    protected static Response makeRequest(
        RestClient client,
        String method,
        String endpoint,
        Map<String, String> params,
        HttpEntity entity,
        List<Header> headers
    ) throws IOException {
        return makeRequest(client, method, endpoint, params, entity, headers, false);
    }

    protected static Response makeRequest(
        RestClient client,
        String method,
        String endpoint,
        Map<String, String> params,
        HttpEntity entity,
        List<Header> headers,
        boolean strictDeprecationMode
    ) throws IOException {
        Request request = new Request(method, endpoint);

        RequestOptions.Builder options = RequestOptions.DEFAULT.toBuilder();
        if (headers != null) {
            headers.forEach(header -> options.addHeader(header.getName(), header.getValue()));
        }
        options.setWarningsHandler(strictDeprecationMode ? WarningsHandler.STRICT : WarningsHandler.PERMISSIVE);
        request.setOptions(options.build());

        if (params != null) {
            params.forEach(request::addParameter);
        }
        if (entity != null) {
            request.setEntity(entity);
        }
        return client.performRequest(request);
    }

    public static HttpEntity toHttpEntity(String jsonString) {
        return new StringEntity(jsonString, ContentType.APPLICATION_JSON);
    }

    @Override
    protected String getProtocol() {
        if (protocol == null) {
            protocol = readProtocolFromSystemProperty();
        }
        return protocol;
    }

    private String readProtocolFromSystemProperty() {
        final boolean isHttps = Optional.ofNullable(System.getProperty(SYS_PROPERTY_KEY_HTTPS)).map("true"::equalsIgnoreCase).orElse(false);
        if (!isHttps) {
            return PROTOCOL_HTTP;
        }

        // currently only external cluster is supported for security enabled testing
        if (Optional.ofNullable(System.getProperty(SYS_PROPERTY_KEY_CLUSTER_ENDPOINT)).isEmpty()) {
            throw new RuntimeException("cluster url should be provided for security enabled testing");
        }
        return PROTOCOL_HTTPS;
    }

    @Override
    protected RestClient buildClient(Settings settings, HttpHost[] hosts) throws IOException {
        final RestClientBuilder builder = RestClient.builder(hosts);
        if (PROTOCOL_HTTPS.equals(getProtocol())) {
            configureHttpsClient(builder, settings);
        } else {
            configureClient(builder, settings);
        }

        return builder.build();
    }

    private void configureHttpsClient(final RestClientBuilder builder, final Settings settings) {
        final Map<String, String> headers = ThreadContext.buildDefaultHeaders(settings);
        final Header[] defaultHeaders = new Header[headers.size()];
        int i = 0;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            defaultHeaders[i++] = new BasicHeader(entry.getKey(), entry.getValue());
        }
        builder.setDefaultHeaders(defaultHeaders);
        builder.setHttpClientConfigCallback(httpClientBuilder -> {
            final String userName = Optional.ofNullable(System.getProperty(SYS_PROPERTY_KEY_USER))
                .orElseThrow(() -> new RuntimeException("user name is missing"));
            final String password = Optional.ofNullable(System.getProperty(SYS_PROPERTY_KEY_PASSWORD))
                .orElseThrow(() -> new RuntimeException("password is missing"));
            final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            final AuthScope anyScope = new AuthScope(null, -1);
            credentialsProvider.setCredentials(anyScope, new UsernamePasswordCredentials(userName, password.toCharArray()));
            try {
                final TlsStrategy tlsStrategy = ClientTlsStrategyBuilder.create()
                    .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .setSslContext(SSLContextBuilder.create().loadTrustMaterial(null, (chains, authType) -> true).build())
                    .build();
                final PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
                    .setMaxConnPerRoute(DEFAULT_MAX_CONN_PER_ROUTE)
                    .setMaxConnTotal(DEFAULT_MAX_CONN_TOTAL)
                    .setTlsStrategy(tlsStrategy)
                    .build();
                return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider).setConnectionManager(connectionManager);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        final String socketTimeoutString = settings.get(CLIENT_SOCKET_TIMEOUT);
        final TimeValue socketTimeout = TimeValue.parseTimeValue(
            socketTimeoutString == null ? DEFAULT_SOCKET_TIMEOUT : socketTimeoutString,
            CLIENT_SOCKET_TIMEOUT
        );
        builder.setRequestConfigCallback(conf -> {
            Timeout timeout = Timeout.ofMilliseconds(Math.toIntExact(socketTimeout.getMillis()));
            conf.setConnectTimeout(timeout);
            conf.setResponseTimeout(timeout);
            return conf;
        });
        if (settings.hasValue(CLIENT_PATH_PREFIX)) {
            builder.setPathPrefix(settings.get(CLIENT_PATH_PREFIX));
        }
    }

    /**
     * wipeAllIndices won't work since it cannot delete security index. Use deleteExternalIndices instead.
     */
    @Override
    protected boolean preserveIndicesUponCompletion() {
        return true;
    }

    @After
    public void deleteExternalIndices() throws IOException, ParseException {
        final Response response = client().performRequest(new Request("GET", "/_cat/indices?format=json" + "&expand_wildcards=all"));
        final MediaType xContentType = MediaType.fromMediaType(response.getEntity().getContentType());
        try (
            final XContentParser parser = xContentType.xContent()
                .createParser(
                    NamedXContentRegistry.EMPTY,
                    DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                    response.getEntity().getContent()
                )
        ) {
            final XContentParser.Token token = parser.nextToken();
            final List<Map<String, Object>> parserList;
            if (token == XContentParser.Token.START_ARRAY) {
                parserList = parser.listOrderedMap().stream().map(obj -> (Map<String, Object>) obj).collect(Collectors.toList());
            } else {
                parserList = Collections.singletonList(parser.mapOrdered());
            }

            final List<String> externalIndices = parserList.stream()
                .map(index -> (String) index.get("index"))
                .filter(indexName -> indexName != null)
                .filter(indexName -> !indexName.startsWith(INTERNAL_INDICES_PREFIX))
                .collect(Collectors.toList());

            for (final String indexName : externalIndices) {
                adminClient().performRequest(new Request("DELETE", "/" + indexName));
            }
        }
    }
}
