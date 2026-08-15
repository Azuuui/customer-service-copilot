package com.example.copilot.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.Map;

@Service
public class RetrievalClient {
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public RetrievalClient(RestClient.Builder builder, ObjectMapper objectMapper,
                           @Value("${copilot.retrieval.base-url:http://127.0.0.1:8765}") String baseUrl) {
        this.client = builder.requestFactory(new SimpleClientHttpRequestFactory()).baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    public JsonNode search(String query, int limit, int offset) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "query", query, "limit", limit, "offset", offset));
            return client.post().uri("/internal/v1/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve().body(JsonNode.class);
        } catch (Exception exception) {
            return objectMapper.valueToTree(Map.of(
                    "query", query, "limit", limit, "offset", offset, "results", java.util.List.of(),
                    "embedding_status", "unavailable", "degraded", true,
                    "degradation_reason", "retrieval_unavailable"));
        }
    }

    public JsonNode health() {
        try {
            return client.get().uri("/internal/v1/health").retrieve().body(JsonNode.class);
        } catch (RestClientException exception) {
            return null;
        }
    }

    public boolean reindex(String sourceKey) {
        try {
            client.post().uri("/internal/v1/index/{sourceKey}", sourceKey)
                    .retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException exception) {
            return false;
        }
    }
}
