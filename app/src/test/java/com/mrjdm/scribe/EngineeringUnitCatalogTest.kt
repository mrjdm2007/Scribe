package com.mrjdm.scribe

import org.junit.Assert.*
import org.junit.Test

class EngineeringUnitCatalogTest {
    @Test fun convertsElectricalValues() {
        assertEquals(4700.0, EngineeringUnitCatalog.convert(4.7, "kΩ", "Ω")!!, 1e-9)
        assertEquals(47e-9, EngineeringUnitCatalog.convert(47.0, "nF", "F")!!, 1e-15)
        assertEquals(0.0022, EngineeringUnitCatalog.convert(2.2, "kΩ", "MΩ")!!, 1e-12)
    }
    @Test fun convertsTemperatureWithOffsets() {
        assertEquals(0.0, EngineeringUnitCatalog.convert(32.0, "°F", "°C")!!, 1e-9)
        assertEquals(373.15, EngineeringUnitCatalog.convert(100.0, "°C", "K")!!, 1e-9)
        assertEquals(212.0, EngineeringUnitCatalog.convert(100.0, "°C", "°F")!!, 1e-8)
    }
    @Test fun convertsPhysicalQuantities() {
        assertEquals(101325.0, EngineeringUnitCatalog.convert(1.0, "atm", "Pa")!!, 1e-6)
        assertEquals(3.6, EngineeringUnitCatalog.convert(1.0, "m/s", "km/h")!!, 1e-12)
        assertEquals(3600.0, EngineeringUnitCatalog.convert(1.0, "Ah", "C")!!, 1e-9)
        assertEquals(1000.0, EngineeringUnitCatalog.convert(1.0, "g/cm³", "kg/m³")!!, 1e-9)
    }
    @Test fun rejectsInvalidOrCrossDimensionConversions() {
        assertNull(EngineeringUnitCatalog.convert(1.0, "V", "A"))
        assertNull(EngineeringUnitCatalog.convert(Double.NaN, "m", "cm"))
    }
}
