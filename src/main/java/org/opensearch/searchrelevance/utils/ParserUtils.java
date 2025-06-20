/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.searchrelevance.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.rest.RestRequest;
import org.opensearch.searchrelevance.model.SearchParams;

public class ParserUtils {
    public static SearchParams parseSearchParams(RestRequest request) throws IOException {
        SearchParams.Builder builder = SearchParams.builder();

        if (request.hasContent()) {
            XContentParser parser = request.contentParser();

            XContentParser.Token token = parser.currentToken();
            if (token == null) {
                token = parser.nextToken();
            }

            if (token == XContentParser.Token.START_OBJECT) {
                while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                    if (token == XContentParser.Token.FIELD_NAME) {
                        String fieldName = parser.currentName();
                        token = parser.nextToken();

                        switch (fieldName) {
                            case "size":
                                if (token == XContentParser.Token.VALUE_NUMBER) {
                                    builder.size(parser.intValue());
                                }
                                break;
                            case "sort":
                                if (token == XContentParser.Token.START_OBJECT) {
                                    parseSortObject(parser, builder);
                                }
                                break;
                        }
                    }
                }
            }
        }

        return builder.build();
    }

    public static void parseSortObject(XContentParser parser, SearchParams.Builder builder) throws IOException {
        XContentParser.Token token;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                String sortField = parser.currentName();
                builder.sortField(sortField);

                token = parser.nextToken();
                if (token == XContentParser.Token.START_OBJECT) {
                    while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                        if (token == XContentParser.Token.FIELD_NAME && "order".equals(parser.currentName())) {
                            token = parser.nextToken();
                            if (token == XContentParser.Token.VALUE_STRING) {
                                builder.sortOrder(parser.text());
                            }
                        }
                    }
                }
            }
        }
    }

    public static List<String> convertObjToList(Map<String, Object> source, String fieldName) {
        List<String> result = new ArrayList<>();
        Object rawList = source.get(fieldName);

        if (rawList instanceof List<?>) {
            ((List<?>) rawList).forEach(item -> {
                if (item instanceof String) {
                    result.add((String) item);
                }
            });
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> convertObjToListOfMaps(Map<String, Object> source, String fieldName) {
        List<Map<String, Object>> result = new ArrayList<>();
        Object rawList = source.get(fieldName);

        if (rawList instanceof List<?>) {
            ((List<?>) rawList).forEach(item -> {
                if (item instanceof Map<?, ?>) {
                    result.add((Map<String, Object>) item);
                }
            });
        }
        return result;
    }

    public static String convertListToSortedStr(List<String> list) {
        List<String> sortedList = new ArrayList<>(list);
        Collections.sort(sortedList);
        return String.join(",", sortedList);
    }

    public static List<String> convertSortedStrToList(String str) {
        if (str == null || str.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> list = new ArrayList<>(Arrays.asList(str.split(",")));
        Collections.sort(list);
        return list;
    }

    /**
     * unique key for queryText, compositeKey and contextFields for judgment cache
     */
    public static String generateUniqueId(String queryText, String compositeKey, List<String> contextFields) {
        String contextFieldsStr = contextFields != null ? String.join(",", contextFields) : "";
        return Base64.getUrlEncoder()
            .encodeToString((queryText + "::" + compositeKey + "::" + contextFieldsStr).getBytes(StandardCharsets.UTF_8));
    }

    public static String combinedIndexAndDocId(String index, String docId) {
        if (index == null) {
            return docId;
        }
        return String.join("::", index, docId);
    }

    public static String getDocIdFromCompositeKey(String compositeKey) {
        return compositeKey.split("::")[1];
    }

}
