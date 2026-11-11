package com.swiftfleet;

import com.swiftfleet.service.GeoMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoMathTest {

    @Test
    void haversineDistanceIsZeroForSamePointAndRealisticForShortHop() {
        assertEquals(0.0, GeoMath.haversineKm(28.646705, 77.444478, 28.646705, 77.444478), 1e-6);
        double distKm = GeoMath.haversineKm(28.646705, 77.444478, 28.655705, 77.454478);
        assertTrue(distKm > 1.0 && distKm < 2.0, "Expected ~1.4 km hop, got " + distKm);
    }

    @Test
    void bearingAndEtaBehaveAsExpected() {
        double northBearing = GeoMath.bearingDegrees(28.64, 77.44, 28.65, 77.44);
        assertEquals(0.0, northBearing, 1.0);
        assertEquals(0, GeoMath.estimateEtaMinutes(0.03, 25.0));
        assertTrue(GeoMath.estimateEtaMinutes(1.5, 30.0) >= 3);
    }
}

