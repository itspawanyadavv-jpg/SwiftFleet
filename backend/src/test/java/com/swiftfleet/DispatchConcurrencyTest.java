package com.swiftfleet;

import com.swiftfleet.model.DeliveryOrder;
import com.swiftfleet.model.OrderStatus;
import com.swiftfleet.model.RaceConditionReport;
import com.swiftfleet.service.DemoBootstrapService;
import com.swiftfleet.service.DispatchService;
import com.swiftfleet.service.FleetException;
import com.swiftfleet.service.RiderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {"swiftfleet.store-mode=memory"})
class DispatchConcurrencyTest {

    @Autowired
    private DemoBootstrapService bootstrapService;

    @Autowired
    private DispatchService dispatchService;

    @Autowired
    private RiderService riderService;

    @BeforeEach
    void reset() {
        bootstrapService.resetDemo();
    }

    @Test
    void onlyOneRiderWinsWhenAcceptingSameOrderConcurrently() throws Exception {
        DeliveryOrder order = dispatchService.createOrder(
                "Test Customer", "Indirapuram", "Milk", 28.6530, 77.4520
        );

        List<String> riderIds = List.of("rdr-1", "rdr-2", "rdr-3");
        ExecutorService pool = Executors.newFixedThreadPool(riderIds.size());
        CountDownLatch ready = new CountDownLatch(riderIds.size());
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (String riderId : riderIds) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    dispatchService.acceptOrder(order.id(), riderId);
                    winners.incrementAndGet();
                } catch (FleetException ex) {
                    if (ex.getStatus() == HttpStatus.CONFLICT) {
                        conflicts.incrementAndGet();
                    }
                }
                return null;
            }));
        }

        ready.await();
        go.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdownNow();

        assertEquals(1, winners.get(), "Exactly 1 rider must claim the order");
        assertEquals(2, conflicts.get(), "Remaining 2 riders must receive 409 Conflict");
    }

    @Test
    void raceConditionSimulationProducesExactlyOneWinnerAndOneConflict() {
        RaceConditionReport report = dispatchService.simulateRaceCondition(null);
        assertNotNull(report.winnerRiderId());
        long wonCount = report.attempts().stream().filter(RaceConditionReport.AttemptResult::won).count();
        long conflictCount = report.attempts().stream().filter(a -> a.httpStatus() == 409).count();
        assertEquals(1, wonCount);
        assertEquals(1, conflictCount);
    }

    @Test
    void geofenceAndOtpGuardEnforcedBeforeDeliveryCompletion() {
        double dropLat = 28.6550;
        double dropLng = 77.4540;
        DeliveryOrder order = dispatchService.createOrder("Priya", "Sector 5", "Groceries", dropLat, dropLng);
        dispatchService.acceptOrder(order.id(), "rdr-1");
        dispatchService.markPickedUp(order.id(), "rdr-1");

        // Rider is still far away from drop-off -> 422 outside geofence
        FleetException geofenceEx = assertThrows(FleetException.class,
                () -> dispatchService.completeDelivery(order.id(), "rdr-1", order.deliveryOtp()));
        assertEquals("OUTSIDE_DELIVERY_GEOFENCE", geofenceEx.getCode());

        // Move rider inside 150m geofence
        riderService.updateLocation("rdr-1", dropLat + 0.0002, dropLng + 0.0002, 5.0, 0.0, false);

        // Wrong OTP -> 400 BAD_REQUEST
        FleetException otpEx = assertThrows(FleetException.class,
                () -> dispatchService.completeDelivery(order.id(), "rdr-1", "0000".equals(order.deliveryOtp()) ? "1111" : "0000"));
        assertEquals("INVALID_OTP", otpEx.getCode());

        // Exact OTP -> DELIVERED
        DeliveryOrder completed = dispatchService.completeDelivery(order.id(), "rdr-1", order.deliveryOtp());
        assertEquals(OrderStatus.DELIVERED, completed.status());
        assertTrue(riderService.getRider("rdr-1").activeOrderId() == null);
    }
}

