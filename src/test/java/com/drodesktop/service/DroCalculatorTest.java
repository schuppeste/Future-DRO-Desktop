package com.drodesktop.service;

import com.drodesktop.model.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class DroCalculatorTest {

    @Test
    void radiusToDiameterAndTargetRemainingWork() {
        assertEquals(40.0, DroCalculator.radiusToDiameter(20.0), 0.0001);
        assertEquals(15.0, DroCalculator.targetRemaining(35.0, 50.0), 0.0001);
    }

    @Test
    void midpointAndDistanceWork() {
        Vector3 a = new Vector3(0, 0, 0);
        Vector3 b = new Vector3(4, 6, 0);

        Vector3 mid = DroCalculator.midpoint(a, b);
        assertEquals(2.0, mid.x, 0.0001);
        assertEquals(3.0, mid.y, 0.0001);
        assertEquals(0.0, mid.z, 0.0001);
        assertEquals(7.2111, DroCalculator.distanceBetween(a, b), 0.0001);
    }

    @Test
    void holeCircleAndPointOnCircleWork() {
        double[] positions = DroCalculator.holeCircle(4, 40.0, 0.0, 100.0, 100.0);
        assertEquals(8, positions.length);
        assertEquals(120.0, positions[0], 0.0001);
        assertEquals(100.0, positions[1], 0.0001);

        Vector3 point = DroCalculator.pointOnCircle(10.0, 90.0, 50.0, 20.0);
        assertEquals(50.0, point.x, 0.0001);
        assertEquals(30.0, point.y, 0.0001);
    }

    @Test
    void toolCorrectionComputationWorks() {
        assertEquals(0.25, DroCalculator.toolCorrection(12.75, 13.0), 0.0001);
    }
}
