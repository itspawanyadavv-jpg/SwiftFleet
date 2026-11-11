package com.swiftfleet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftfleet.model.LatLng;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves road-snapped driving routes via OSRM (OpenStreetMap),
 * gracefully falling back to a realistic multi-turn street-grid polyline if offline.
 */
@Service
public class RouteService {

    private static final Logger log = LoggerFactory.getLogger(RouteService.class);

    public record RoutePlan(List<LatLng> points, String source, double totalKm) {
    }

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RouteService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(2200))
                .build();
    }

    public RoutePlan planLeg(LatLng start, LatLng end) {
        try {
            String url = String.format(
                    java.util.Locale.US,
                    "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                    start.lng(), start.lat(), end.lng(), end.lat()
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "SwiftFleet-Logistics-Demo/1.0")
                    .timeout(Duration.ofMillis(2500))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode routes = root.path("routes");
                if (routes.isArray() && !routes.isEmpty()) {
                    JsonNode first = routes.get(0);
                    JsonNode coords = first.path("geometry").path("coordinates");
                    if (coords.isArray() && coords.size() >= 2) {
                        List<LatLng> parsed = new ArrayList<>();
                        for (JsonNode pt : coords) {
                            parsed.add(new LatLng(pt.get(1).asDouble(), pt.get(0).asDouble()));
                        }
                        List<LatLng> densified = densify(parsed, 22);
                        double km = first.path("distance").asDouble() / 1000.0;
                        return new RoutePlan(densified, "OSRM_STREET", GeoMath.round(km, 2));
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("OSRM lookup unavailable ({}), using street-grid polyline fallback", ex.getMessage());
        }

        List<LatLng> fallback = buildStreetGridFallback(start, end);
        double km = computePathLengthKm(fallback);
        return new RoutePlan(fallback, "STREET_GRID_FALLBACK", GeoMath.round(km, 2));
    }

    /**
     * Builds an orthogonal multi-turn street-grid polyline so simulated riders
     * follow realistic city blocks even when external routing is offline.
     */
    public List<LatLng> buildStreetGridFallback(LatLng start, LatLng end) {
        double midLat1 = start.lat() + (end.lat() - start.lat()) * 0.45;
        double midLng1 = start.lng();
        double midLat2 = midLat1;
        double midLng2 = start.lng() + (end.lng() - start.lng()) * 0.65;
        double midLat3 = end.lat();
        double midLng3 = midLng2;

        List<LatLng> anchors = List.of(
                start,
                new LatLng(midLat1, midLng1),
                new LatLng(midLat2, midLng2),
                new LatLng(midLat3, midLng3),
                end
        );
        return densify(anchors, 20);
    }

    private List<LatLng> densify(List<LatLng> anchors, int targetPoints) {
        if (anchors.size() < 2) {
            return anchors;
        }
        double totalLength = computePathLengthKm(anchors);
        if (totalLength < 0.01) {
            return List.of(anchors.get(0), anchors.get(anchors.size() - 1));
        }
        List<LatLng> output = new ArrayList<>();
        output.add(anchors.get(0));
        double stepKm = totalLength / Math.max(8, targetPoints);

        for (int i = 0; i < anchors.size() - 1; i++) {
            LatLng a = anchors.get(i);
            LatLng b = anchors.get(i + 1);
            double segKm = GeoMath.haversineKm(a.lat(), a.lng(), b.lat(), b.lng());
            int segments = Math.max(1, (int) Math.round(segKm / stepKm));
            for (int s = 1; s <= segments; s++) {
                double ratio = (double) s / segments;
                double lat = a.lat() + (b.lat() - a.lat()) * ratio;
                double lng = a.lng() + (b.lng() - a.lng()) * ratio;
                output.add(new LatLng(GeoMath.round(lat, 6), GeoMath.round(lng, 6)));
            }
        }
        return output;
    }

    public double computePathLengthKm(List<LatLng> path) {
        double total = 0.0;
        for (int i = 0; i < path.size() - 1; i++) {
            LatLng a = path.get(i);
            LatLng b = path.get(i + 1);
            total += GeoMath.haversineKm(a.lat(), a.lng(), b.lat(), b.lng());
        }
        return total;
    }
}

