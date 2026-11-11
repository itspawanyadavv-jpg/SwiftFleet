package com.swiftfleet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftfleet.config.AppProperties;
import com.swiftfleet.model.StoreConfig;
import com.swiftfleet.store.FleetGeoLockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Provides store hub configuration and optional live sync from DRC Store's /api/store endpoint.
 */
@Service
public class StoreConfigService {

    private static final Logger log = LoggerFactory.getLogger(StoreConfigService.class);

    private final AppProperties properties;
    private final FleetGeoLockStore store;
    private final InspectorService inspectorService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private volatile double activeRadiusKm;
    private volatile boolean drcSynced;
    private volatile String activeDrcUrl;

    public StoreConfigService(
            AppProperties properties,
            FleetGeoLockStore store,
            InspectorService inspectorService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.store = store;
        this.inspectorService = inspectorService;
        this.objectMapper = objectMapper;
        this.activeRadiusKm = properties.dispatchRadiusKm();
        this.activeDrcUrl = properties.drcStoreApiUrl() == null ? "" : properties.drcStoreApiUrl().trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public StoreConfig currentConfig() {
        return new StoreConfig(
                properties.storeName(),
                properties.storeLat(),
                properties.storeLng(),
                activeRadiusKm,
                properties.deliveryGeofenceMeters(),
                properties.orderLockTtlSeconds(),
                properties.otpMaxAttempts(),
                store.backendMode(),
                drcSynced,
                activeDrcUrl
        );
    }

    public StoreConfig syncFromDrc(String overrideUrl) {
        String targetBase = (overrideUrl != null && !overrideUrl.isBlank())
                ? overrideUrl.trim().replaceAll("/+$", "")
                : activeDrcUrl.replaceAll("/+$", "");

        if (targetBase.isBlank()) {
            throw new FleetException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "DRC_URL_REQUIRED",
                    "No DRC store URL provided. Set DRC_STORE_API_URL or enter your DRC Store URL."
            );
        }

        String endpoint = targetBase.endsWith("/api/store") ? targetBase : targetBase + "/api/store";
        long start = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("deliveryRadiusKm") && root.get("deliveryRadiusKm").asDouble() > 0) {
                    this.activeRadiusKm = root.get("deliveryRadiusKm").asDouble();
                }
                this.activeDrcUrl = targetBase;
                this.drcSynced = true;

                long elapsed = (System.nanoTime() - start) / 1_000_000;
                inspectorService.record(
                        "DRC_SYNC",
                        "GET " + endpoint,
                        String.format("Synced deliveryRadiusKm=%.1f km from DRC Store", activeRadiusKm),
                        elapsed,
                        store.backendMode()
                );
                return currentConfig();
            }
            throw new FleetException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "DRC_SYNC_FAILED",
                    "DRC Store API returned HTTP " + response.statusCode()
            );
        } catch (FleetException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("DRC sync failed for {}: {}", endpoint, ex.getMessage());
            throw new FleetException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "DRC_SYNC_FAILED",
                    "Could not reach DRC Store at " + endpoint + ": " + ex.getMessage()
            );
        }
    }
}
