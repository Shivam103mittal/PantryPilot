package com.pantrypilot.util;

import java.util.HashMap;
import java.util.Map;

public class UnitConverter {

    private static final Map<String, Double> weightUnits = new HashMap<>();
    private static final Map<String, Double> volumeUnits = new HashMap<>();

    static {
        weightUnits.put("mg", 0.001);
        weightUnits.put("g", 1.0);
        weightUnits.put("kg", 1000.0);

        volumeUnits.put("ml", 1.0);
        volumeUnits.put("l", 1000.0);
        volumeUnits.put("tbsp", 15.0);
    }

    public static double convert(double quantity, String fromUnit, String toUnit) {
        if (fromUnit == null || toUnit == null) return quantity;

        fromUnit = fromUnit.toLowerCase();
        toUnit = toUnit.toLowerCase();

        if (weightUnits.containsKey(fromUnit) && weightUnits.containsKey(toUnit)) {
            return quantity * weightUnits.get(fromUnit) / weightUnits.get(toUnit);
        } else if (volumeUnits.containsKey(fromUnit) && volumeUnits.containsKey(toUnit)) {
            return quantity * volumeUnits.get(fromUnit) / volumeUnits.get(toUnit);
        }

        return quantity; // If units are unrecognized
    }
}
