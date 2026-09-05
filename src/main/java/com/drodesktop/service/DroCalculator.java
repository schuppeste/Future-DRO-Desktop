package com.drodesktop.service;

import com.drodesktop.model.Vector3;

public class DroCalculator {
    public static double radiusToDiameter(double radiusMm) {
        return radiusMm * 2.0;
    }

    public static double diameterToRadius(double diameterMm) {
        return diameterMm / 2.0;
    }

    public static double addValue(double currentMm, double deltaMm) {
        return currentMm + deltaMm;
    }

    public static double subtractValue(double currentMm, double deltaMm) {
        return currentMm - deltaMm;
    }

    public static double targetRemaining(double currentMm, double targetMm) {
        return targetMm - currentMm;
    }

    public static double distanceBetween(Vector3 p1, Vector3 p2) {
        double dx = p1.x - p2.x;
        double dy = p1.y - p2.y;
        double dz = p1.z - p2.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static Vector3 midpoint(Vector3 p1, Vector3 p2) {
        return new Vector3(
            (p1.x + p2.x) / 2.0,
            (p1.y + p2.y) / 2.0,
            (p1.z + p2.z) / 2.0
        );
    }

    public static Vector3 pointOnCircle(double radiusMm, double angleDeg, double centerX, double centerY) {
        double angleRad = Math.toRadians(angleDeg);
        return new Vector3(
            centerX + radiusMm * Math.cos(angleRad),
            centerY + radiusMm * Math.sin(angleRad),
            0.0
        );
    }

    public static double toolCorrection(double measuredMm, double targetMm) {
        return targetMm - measuredMm;
    }

    public static double[] holeCircle(double count, double diameterMm, double startAngleDeg, double centerX, double centerY) {
        int holeCount = (int) Math.max(1, count);
        double[] result = new double[holeCount * 2];
        double radius = diameterMm / 2.0;

        for (int i = 0; i < holeCount; i++) {
            double angle = Math.toRadians(startAngleDeg + (360.0 / holeCount) * i);
            result[i * 2] = centerX + radius * Math.cos(angle);
            result[i * 2 + 1] = centerY + radius * Math.sin(angle);
        }

        return result;
    }
}
