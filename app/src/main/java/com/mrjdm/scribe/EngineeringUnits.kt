package com.mrjdm.scribe

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

data class EngineeringUnit(
    val symbol: String,
    val description: String,
    val factor: Double,
    val offset: Double = 0.0
)

/** SI-based conversion catalogue. canonical = value * factor + offset. */
object EngineeringUnitCatalog {
    val groups: Map<String, List<EngineeringUnit>> = linkedMapOf(
        "Voltage" to listOf(u("V","volt",1.0),u("mV","millivolt",1e-3),u("µV","microvolt",1e-6),u("kV","kilovolt",1e3)),
        "Current" to listOf(u("A","ampere",1.0),u("mA","milliampere",1e-3),u("µA","microampere",1e-6),u("nA","nanoampere",1e-9)),
        "Resistance" to listOf(u("Ω","ohm",1.0),u("kΩ","kiloohm",1e3),u("MΩ","megaohm",1e6),u("GΩ","gigaohm",1e9)),
        "Capacitance" to listOf(u("F","farad",1.0),u("mF","millifarad",1e-3),u("µF","microfarad",1e-6),u("nF","nanofarad",1e-9),u("pF","picofarad",1e-12)),
        "Inductance" to listOf(u("H","henry",1.0),u("mH","millihenry",1e-3),u("µH","microhenry",1e-6),u("nH","nanohenry",1e-9)),
        "Conductance" to listOf(u("S","siemens",1.0),u("mS","millisiemens",1e-3),u("µS","microsiemens",1e-6)),
        "Frequency" to listOf(u("Hz","hertz",1.0),u("kHz","kilohertz",1e3),u("MHz","megahertz",1e6),u("GHz","gigahertz",1e9),u("rpm","revolutions/min",1.0/60.0)),
        "Power" to listOf(u("W","watt",1.0),u("mW","milliwatt",1e-3),u("kW","kilowatt",1e3),u("MW","megawatt",1e6),u("hp","mechanical horsepower",745.6998716)),
        "Energy" to listOf(u("J","joule",1.0),u("kJ","kilojoule",1e3),u("Wh","watt-hour",3600.0),u("kWh","kilowatt-hour",3.6e6),u("eV","electronvolt",1.602176634e-19)),
        "Charge" to listOf(u("C","coulomb",1.0),u("mC","millicoulomb",1e-3),u("µC","microcoulomb",1e-6),u("Ah","ampere-hour",3600.0),u("mAh","milliampere-hour",3.6)),
        "Length" to listOf(u("m","metre",1.0),u("mm","millimetre",1e-3),u("cm","centimetre",1e-2),u("km","kilometre",1e3),u("µm","micrometre",1e-6),u("nm","nanometre",1e-9),u("in","inch",0.0254),u("ft","foot",0.3048),u("mi","mile",1609.344)),
        "Area" to listOf(u("m²","square metre",1.0),u("cm²","square centimetre",1e-4),u("mm²","square millimetre",1e-6),u("km²","square kilometre",1e6),u("ha","hectare",1e4),u("ft²","square foot",0.09290304)),
        "Volume" to listOf(u("m³","cubic metre",1.0),u("L","litre",1e-3),u("mL","millilitre",1e-6),u("cm³","cubic centimetre",1e-6),u("ft³","cubic foot",0.028316846592),u("gal","US gallon",0.003785411784)),
        "Mass" to listOf(u("kg","kilogram",1.0),u("g","gram",1e-3),u("mg","milligram",1e-6),u("t","metric tonne",1e3),u("lb","pound",0.45359237),u("oz","ounce",0.028349523125)),
        "Temperature" to listOf(u("K","kelvin",1.0),u("°C","degree Celsius",1.0,273.15),u("°F","degree Fahrenheit",5.0/9.0,255.3722222222222)),
        "Time" to listOf(u("s","second",1.0),u("ms","millisecond",1e-3),u("µs","microsecond",1e-6),u("ns","nanosecond",1e-9),u("min","minute",60.0),u("h","hour",3600.0),u("day","day",86400.0)),
        "Speed" to listOf(u("m/s","metre/second",1.0),u("km/h","kilometre/hour",1.0/3.6),u("mph","mile/hour",0.44704),u("ft/s","foot/second",0.3048)),
        "Acceleration" to listOf(u("m/s²","metre/second²",1.0),u("ft/s²","foot/second²",0.3048),u("g₀","standard gravity",9.80665)),
        "Force" to listOf(u("N","newton",1.0),u("kN","kilonewton",1e3),u("MN","meganewton",1e6),u("lbf","pound-force",4.4482216152605)),
        "Pressure" to listOf(u("Pa","pascal",1.0),u("kPa","kilopascal",1e3),u("MPa","megapascal",1e6),u("bar","bar",1e5),u("atm","atmosphere",101325.0),u("psi","pound/in²",6894.757293168),u("mmHg","millimetre mercury",133.322387415)),
        "Density" to listOf(u("kg/m³","kilogram/cubic metre",1.0),u("g/cm³","gram/cubic centimetre",1000.0),u("g/L","gram/litre",1.0),u("lb/ft³","pound/cubic foot",16.01846337)),
        "Torque" to listOf(u("N·m","newton-metre",1.0),u("kN·m","kilonewton-metre",1e3),u("lb·ft","pound-foot",1.3558179483)),
        "Magnetic field" to listOf(u("T","tesla",1.0),u("mT","millitesla",1e-3),u("µT","microtesla",1e-6),u("G","gauss",1e-4)),
        "Angle" to listOf(u("rad","radian",1.0),u("°","degree",Math.PI/180.0),u("rev","revolution",2.0*Math.PI))
    )

    fun convert(value: Double, from: String, to: String): Double? {
        if (!value.isFinite()) return null
        val source = groups.values.flatten().firstOrNull { it.symbol == from } ?: return null
        val target = groups.values.flatten().firstOrNull { it.symbol == to } ?: return null
        if (groups.entries.first { source in it.value }.key != groups.entries.first { target in it.value }.key) return null
        return ((value * source.factor + source.offset) - target.offset) / target.factor
    }

    fun format(value: Double): String {
        if (!value.isFinite()) return "Out of range"
        if (value == 0.0) return "0"
        val magnitude = abs(value)
        return if (magnitude >= 1e7 || magnitude < 1e-5) "%.7e".format(Locale.US, value)
        else "%.9g".format(Locale.US, value)
    }

    private fun u(symbol: String, description: String, factor: Double, offset: Double = 0.0) =
        EngineeringUnit(symbol, description, factor, offset)
}
