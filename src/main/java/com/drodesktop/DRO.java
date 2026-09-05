package com.drodesktop;

import com.drodesktop.model.Vector3;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.prefs.Preferences;

public class DRO {
    private Vector3 machinePosition = new Vector3(0, 0, 0);
    private final Map<String, Vector3> workOffsets = new LinkedHashMap<>();
    private final Map<String, Vector3> referencePoints = new LinkedHashMap<>();
    private final List<Vector3> referenceList = new ArrayList<>();
    private final Map<String, Double> linearFactors = new LinkedHashMap<>();
    private final Preferences preferences = Preferences.userNodeForPackage(DRO.class);
    private String activeWorkOffset = "G54";
    private Vector3 markPoint = null;

    public DRO() {
        workOffsets.put("G54", new Vector3(0, 0, 0));
        workOffsets.put("G55", new Vector3(100, 0, 0));
        workOffsets.put("G56", new Vector3(0, 50, 0));
        loadWorkOffset("G54");
        loadWorkOffset("G55");
        loadWorkOffset("G56");
        activeWorkOffset = preferences.get("activeWorkOffset", "G54");
        if (!workOffsets.containsKey(activeWorkOffset)) {
            activeWorkOffset = "G54";
        }
        linearFactors.put("X", preferences.getDouble("factorX", 1.0));
        linearFactors.put("Y", preferences.getDouble("factorY", 1.0));
        linearFactors.put("Z", preferences.getDouble("factorZ", 1.0));
    }

    public void setMachinePosition(Vector3 p) {
        machinePosition = new Vector3(
            p.x * getLinearFactor("X"),
            p.y * getLinearFactor("Y"),
            p.z * getLinearFactor("Z"));
    }

    public Vector3 getMachinePosition() {
        return machinePosition;
    }

    public Vector3 getWorkPosition() {
        Vector3 offset = workOffsets.getOrDefault(activeWorkOffset, new Vector3(0, 0, 0));
        return machinePosition.subtract(offset);
    }

    public void setWorkPosition(Vector3 position) {
        workOffsets.put(activeWorkOffset, machinePosition.subtract(position));
        saveWorkOffset(activeWorkOffset);
    }

    public void setZeroAll() {
        workOffsets.put(activeWorkOffset, machinePosition.copy());
        saveWorkOffset(activeWorkOffset);
    }

    public void setZeroX() {
        workOffsets.get(activeWorkOffset).x = machinePosition.x;
        saveWorkOffset(activeWorkOffset);
    }

    public void setZeroY() {
        workOffsets.get(activeWorkOffset).y = machinePosition.y;
        saveWorkOffset(activeWorkOffset);
    }

    public void setZeroZ() {
        workOffsets.get(activeWorkOffset).z = machinePosition.z;
        saveWorkOffset(activeWorkOffset);
    }

    public void setActiveWorkOffset(String name) {
        if (workOffsets.containsKey(name)) {
            activeWorkOffset = name;
            preferences.put("activeWorkOffset", name);
        }
    }

    public String getActiveWorkOffset() {
        return activeWorkOffset;
    }

    public void saveReference(String name) {
        referencePoints.put(name, machinePosition.copy());
    }

    public Vector3 getReference(String name) {
        return referencePoints.getOrDefault(name, new Vector3(0, 0, 0));
    }

    public void addReferenceListPoint(Vector3 point) {
        referenceList.add(point.copy());
    }

    public void removeReferenceListPoint(int index) {
        referenceList.remove(index);
    }

    public void updateReferenceListPoint(int index, Vector3 point) {
        referenceList.set(index, point.copy());
    }

    public void clearReferenceList() {
        referenceList.clear();
    }

    public void replaceReferenceList(List<Vector3> points) {
        referenceList.clear();
        for (Vector3 point : points) {
            referenceList.add(point.copy());
        }
    }

    public List<Vector3> getReferenceList() {
        return List.copyOf(referenceList);
    }

    public void setMark() {
        markPoint = machinePosition.copy();
    }

    public Vector3 getMarkDelta() {
        if (markPoint == null) {
            return new Vector3(0, 0, 0);
        }
        return machinePosition.subtract(markPoint);
    }

    public void addWorkOffset(String name, Vector3 offset) {
        workOffsets.put(name, offset);
        saveWorkOffset(name);
    }

    public Map<String, Vector3> getWorkOffsets() {
        return workOffsets;
    }

    public Map<String, Vector3> getReferences() {
        return referencePoints;
    }

    public double getLinearFactor(String axis) {
        return linearFactors.getOrDefault(axis, 1.0);
    }

    public void setLinearFactor(String axis, double factor) {
        if (factor <= 0 || !Double.isFinite(factor)) {
            throw new IllegalArgumentException("Linear factor must be positive");
        }
        linearFactors.put(axis, factor);
        preferences.putDouble("factor" + axis, factor);
    }

    private void loadWorkOffset(String name) {
        Vector3 offset = workOffsets.get(name);
        offset.x = preferences.getDouble("offset_" + name + "_x", offset.x);
        offset.y = preferences.getDouble("offset_" + name + "_y", offset.y);
        offset.z = preferences.getDouble("offset_" + name + "_z", offset.z);
    }

    private void saveWorkOffset(String name) {
        Vector3 offset = workOffsets.get(name);
        if (offset == null) {
            return;
        }
        preferences.putDouble("offset_" + name + "_x", offset.x);
        preferences.putDouble("offset_" + name + "_y", offset.y);
        preferences.putDouble("offset_" + name + "_z", offset.z);
    }
}
