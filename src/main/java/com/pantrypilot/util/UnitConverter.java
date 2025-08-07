package com.pantrypilot.util;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class UnitConverter {

    private final Map<String, Double> conversionToBase;

    public UnitConverter() {
        conversionToBase = new HashMap<>();

        
        conversionToBase.put("mg", 0.001);
        conversionToBase.put("g", 1.0);
        conversionToBase.put("kg", 1000.0);

        
        conversionToBase.put("ml", 1.0);
        conversionToBase.put("l", 1000.0);
        conversionToBase.put("tbsp", 15.0);  // 1 tbsp = 15 ml
    }

    /**
     * Converts a quantity from one unit to another.
     * @param quantity the input amount
     * @param fromUnit unit of the input
     * @param toUnit unit of the output
     * @return converted amount in toUnit
     */
    public double convert(double quantity, String fromUnit, String toUnit) {
        if (fromUnit == null || toUnit == null) return quantity;

        fromUnit = fromUnit.toLowerCase();
        toUnit = toUnit.toLowerCase();

        Double fromFactor = conversionToBase.get(fromUnit);
        Double toFactor = conversionToBase.get(toUnit);

        if (fromFactor == null || toFactor == null) {

            return quantity;
        }

    
        double quantityInBase = quantity * fromFactor;
        return quantityInBase / toFactor;
    }
}
