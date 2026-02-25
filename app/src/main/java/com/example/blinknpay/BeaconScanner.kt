package com.example.blinknpay

// Abstract class example
abstract class BeaconScanner {
    // Abstract property 'r' of type Double
    abstract val r: Double

    // Example function using 'r'
    fun printDistance() {
        println("Distance r = $r meters")
    }
}

// Concrete subclass must implement 'r'
class MyBeaconScanner(override val r: Double) : BeaconScanner() {
    // You can also add more functions here
}

// Optional: You can remove main() if running in Android
fun main() {
    val scanner = MyBeaconScanner(2.75)
    scanner.printDistance()  // Output: Distance r = 2.75 meters
}
