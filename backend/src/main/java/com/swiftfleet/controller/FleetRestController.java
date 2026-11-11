package com.swiftfleet.controller;

import com.swiftfleet.model.DeliveryOrder;
import com.swiftfleet.model.GpsTelemetryEvent;
import com.swiftfleet.model.InspectorLogEntry;
import com.swiftfleet.model.NearbyRiderMatch;
import com.swiftfleet.model.RaceConditionReport;
import com.swiftfleet.model.Rider;
import com.swiftfleet.model.RiderStatus;
import com.swiftfleet.model.StoreConfig;
import com.swiftfleet.service.DemoBootstrapService;
import com.swiftfleet.service.DispatchService;
import com.swiftfleet.service.FleetException;
import com.swiftfleet.service.InspectorService;
import com.swiftfleet.service.RiderService;
import com.swiftfleet.service.SimulationService;
import com.swiftfleet.service.StoreConfigService;
import com.swiftfleet.store.FleetGeoLockStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FleetRestController {

    public record OnlineStatusRequest(@NotNull Boolean online) {}
    public record LocationUpdateRequest(@NotNull Double lat, @NotNull Double lng, Double speedKmh, Double bearingDeg) {}
    public record CreateOrderRequest(String customerName, String customerAddress, String itemsSummary, @NotNull Double dropLat, @NotNull Double dropLng) {}
    public record RiderActionRequest(@NotBlank String riderId) {}
    public record CompleteDeliveryRequest(@NotBlank String riderId, @NotBlank String otp) {}
    public record SimulateRequest(Boolean autoCompleteOtp) {}
    public record RaceConditionRequest(String orderId) {}
    public record SyncDrcRequest(String url) {}
    public record ApiError(String code, String message, Instant timestamp) {}

    private final FleetGeoLockStore store;
    private final StoreConfigService storeConfigService;
    private final RiderService riderService;
    private final DispatchService dispatchService;
    private final SimulationService simulationService;
    private final DemoBootstrapService demoBootstrapService;
    private final InspectorService inspectorService;

    public FleetRestController(
            FleetGeoLockStore store,
            StoreConfigService storeConfigService,
            RiderService riderService,
            DispatchService dispatchService,
            SimulationService simulationService,
            DemoBootstrapService demoBootstrapService,
            InspectorService inspectorService
    ) {
        this.store = store;
        this.storeConfigService = storeConfigService;
        this.riderService = riderService;
        this.dispatchService = dispatchService;
        this.simulationService = simulationService;
        this.demoBootstrapService = demoBootstrapService;
        this.inspectorService = inspectorService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "swiftfleet-backend",
                "storeMode", store.backendMode(),
                "redisLive", "REDIS_LIVE".equals(store.backendMode()),
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/config")
    public StoreConfig getConfig() {
        return storeConfigService.currentConfig();
    }

    @PostMapping("/config/sync-drc")
    public StoreConfig syncDrc(@RequestBody(required = false) SyncDrcRequest request) {
        return storeConfigService.syncFromDrc(request != null ? request.url() : null);
    }

    @GetMapping("/riders")
    public List<Rider> listRiders() {
        return riderService.listRiders();
    }

    @GetMapping("/riders/nearby")
    public List<NearbyRiderMatch> nearbyRiders(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false) Double radiusKm
    ) {
        return dispatchService.findNearbyRiders(lat, lng, radiusKm);
    }

    @PatchMapping("/riders/{riderId}/status")
    public Rider setRiderOnline(@PathVariable String riderId, @Valid @RequestBody OnlineStatusRequest request) {
        return riderService.setOnlineStatus(riderId, request.online());
    }

    @PostMapping("/riders/{riderId}/location")
    public ResponseEntity<Map<String, Object>> updateRiderLocation(
            @PathVariable String riderId,
            @Valid @RequestBody LocationUpdateRequest request
    ) {
        GpsTelemetryEvent telemetry = riderService.updateLocation(
                riderId, request.lat(), request.lng(), request.speedKmh(), request.bearingDeg(), true
        );
        return ResponseEntity.ok(Map.of(
                "rider", riderService.getRider(riderId),
                "telemetry", telemetry == null ? Map.of() : telemetry
        ));
    }

    @GetMapping("/orders")
    public List<DeliveryOrder> listOrders() {
        return dispatchService.listOrders();
    }

    @GetMapping("/orders/{orderId}")
    public DeliveryOrder getOrder(@PathVariable String orderId) {
        return dispatchService.getOrder(orderId);
    }

    @PostMapping("/orders")
    public ResponseEntity<DeliveryOrder> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        DeliveryOrder created = dispatchService.createOrder(
                request.customerName(),
                request.customerAddress(),
                request.itemsSummary(),
                request.dropLat(),
                request.dropLng()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/orders/{orderId}/accept")
    public DeliveryOrder acceptOrder(@PathVariable String orderId, @Valid @RequestBody RiderActionRequest request) {
        return dispatchService.acceptOrder(orderId, request.riderId());
    }

    @PostMapping("/orders/{orderId}/pickup")
    public DeliveryOrder pickupOrder(@PathVariable String orderId, @Valid @RequestBody RiderActionRequest request) {
        return dispatchService.markPickedUp(orderId, request.riderId());
    }

    @PostMapping("/orders/{orderId}/complete")
    public DeliveryOrder completeOrder(@PathVariable String orderId, @Valid @RequestBody CompleteDeliveryRequest request) {
        return dispatchService.completeDelivery(orderId, request.riderId(), request.otp());
    }

    @PostMapping("/orders/{orderId}/simulate")
    public Map<String, Object> simulateRide(
            @PathVariable String orderId,
            @RequestBody(required = false) SimulateRequest request
    ) {
        boolean autoComplete = request != null && Boolean.TRUE.equals(request.autoCompleteOtp());
        simulationService.startSimulation(orderId, autoComplete);
        return Map.of("orderId", orderId, "simulating", true, "autoCompleteOtp", autoComplete);
    }

    @PostMapping("/demo/race-condition")
    public RaceConditionReport simulateRace(@RequestBody(required = false) RaceConditionRequest request) {
        return dispatchService.simulateRaceCondition(request != null ? request.orderId() : null);
    }

    @PostMapping("/demo/quick-start")
    public DeliveryOrder quickStartDemo() {
        StoreConfig cfg = storeConfigService.currentConfig();
        DeliveryOrder created = dispatchService.createOrder(
                "Ananya Kapoor",
                "Windsor Street, Vaibhav Khand",
                "Organic Milk 1L, Artisan Bread, Cold Coffee",
                cfg.storeLat() + 0.0092,
                cfg.storeLng() + 0.0084
        );

        String targetRiderId = created.candidateRiders().stream()
                .map(NearbyRiderMatch::riderId)
                .findFirst()
                .orElseGet(() -> riderService.listRiders().stream()
                        .filter(r -> r.status() == RiderStatus.IDLE)
                        .map(Rider::id)
                        .findFirst()
                        .orElseThrow(() -> new FleetException(HttpStatus.CONFLICT, "NO_IDLE_RIDER", "No idle rider available. Reset fleet first.")));

        DeliveryOrder assigned = dispatchService.acceptOrder(created.id(), targetRiderId);
        simulationService.startSimulation(assigned.id(), false);
        return assigned;
    }

    @PostMapping("/demo/reset")
    public Map<String, Object> resetDemo() {
        demoBootstrapService.resetDemo();
        return Map.of("reset", true, "riders", riderService.listRiders());
    }

    @GetMapping("/inspector")
    public List<InspectorLogEntry> getInspectorLogs() {
        return inspectorService.recentEntries();
    }

    @ExceptionHandler(FleetException.class)
    public ResponseEntity<ApiError> handleFleetException(FleetException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiError(ex.getCode(), ex.getMessage(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request payload");
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", msg, Instant.now()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", ex.getMessage(), Instant.now()));
    }
}

