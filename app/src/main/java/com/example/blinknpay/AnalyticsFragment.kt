package com.example.blinknpay

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import java.text.SimpleDateFormat

import com.github.mikephil.charting.listener.OnChartValueSelectedListener
// Add this at the top of AnalyticsFragment.kt with your other imports
import com.example.blinknpay.setDrawHighlightIndicators




class AnalyticsFragment : Fragment() {










    private lateinit var tvCountReceived: TextView
    private lateinit var tvCountSent: TextView




    private lateinit var tvTotalBalance: TextView
    private lateinit var tvQuickReceived: TextView
    private lateinit var tvQuickSent: TextView
    private lateinit var barChart: BarChart
    private lateinit var toggleGroup: MaterialButtonToggleGroup

    private var allTransactions = mutableListOf<Payment>()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var transactionListener: ListenerRegistration? = null



    // At the top of your class
    private var calendarCursor = Calendar.getInstance()
    private lateinit var tvCurrentMonth: TextView
    private lateinit var btnPrevMonth: View
    private lateinit var btnNextMonth: View

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {


        // Set default display to today
// Add this where you initialize calendarCursor
        calendarCursor.firstDayOfWeek = Calendar.MONDAY
        calendarCursor.minimalDaysInFirstWeek = 1


        val view = inflater.inflate(R.layout.fragment_analytics, container, false)

        // 1. Initialize New Navigation Views
        tvCurrentMonth = view.findViewById(R.id.tvCurrentMonth)
        btnPrevMonth = view.findViewById(R.id.btnPrevMonth)
        btnNextMonth = view.findViewById(R.id.btnNextMonth)

        // 2. Initialize Existing Summary Views
        tvCountReceived = view.findViewById(R.id.tvCountReceived)
        tvCountSent = view.findViewById(R.id.tvCountSent)
        tvTotalBalance = view.findViewById(R.id.tvTotalBalance)
        tvQuickReceived = view.findViewById(R.id.tvQuickReceived)
        tvQuickSent = view.findViewById(R.id.tvQuickSent)
        barChart = view.findViewById(R.id.barChart)
        toggleGroup = view.findViewById(R.id.toggleGroup)

        // 3. Setup Logic & Listeners
        setupChartDefaults()
        setupToggleListener()
        setupMonthNavigation() // New logic for the scrollable header
        setupTransactionListener()
        updateMonthDisplay()
        return view
    }

    /**
     * Handles the logic for the month selector at the top of the BlinknPay dashboard.
     */
    private fun setupMonthNavigation() {
        // 1. Initialize with the current date (March 8, 2026)
        updateMonthDisplay()

        // 2. Previous Month Action
        btnPrevMonth.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            calendarCursor.add(Calendar.MONTH, -1)

            // Auto-switch to Monthly view when navigating months
            toggleGroup.check(R.id.btnMonthly)
            updateMonthDisplay()
        }

        // 3. Next Month Action (with Future-Proof Guard)
        btnNextMonth.setOnClickListener {
            val now = Calendar.getInstance()

            // Safety: Prevent scrolling into months that haven't happened yet
            val isBeforeCurrentMonth = calendarCursor.get(Calendar.YEAR) < now.get(Calendar.YEAR) ||
                    (calendarCursor.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                            calendarCursor.get(Calendar.MONTH) < now.get(Calendar.MONTH))

            if (isBeforeCurrentMonth) {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                calendarCursor.add(Calendar.MONTH, 1)

                toggleGroup.check(R.id.btnMonthly)
                updateMonthDisplay()
            } else {
                // Optional: Haptic "error" feedback if they try to go to the future
                it.performHapticFeedback(android.view.HapticFeedbackConstants.REJECT)
            }
        }

        // 4. Manual Date Picker (Single Click on Date Text)
        tvCurrentMonth.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)

            val datePickerDialog = android.app.DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    calendarCursor.set(year, month, dayOfMonth)

                    // Force "DAILY" mode for specific date selection
                    toggleGroup.check(R.id.btnDaily)
                    updateMonthDisplay()
                },
                calendarCursor.get(Calendar.YEAR),
                calendarCursor.get(Calendar.MONTH),
                calendarCursor.get(Calendar.DAY_OF_MONTH)
            )

            datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
            datePickerDialog.show()
        }

        // 5. Reset to Today (Long Click on Date Text)
        tvCurrentMonth.setOnLongClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)

            // Reset to current system time
            calendarCursor = Calendar.getInstance()

            // Default back to Weekly view on reset
            toggleGroup.check(R.id.btnWeekly)
            updateMonthDisplay()

            android.widget.Toast.makeText(context, "Jumped to Today", android.widget.Toast.LENGTH_SHORT).show()
            true // Consumes the long click
        }
    }



    /**
     * Helper to determine which filter (Daily/Weekly/Monthly) is currently active.
     */
    private fun getCurrentMode(): String {
        // 1. Get the current checked ID
        val checkedId = toggleGroup.checkedButtonId

        // 2. Map ID to Mode String
        return when (checkedId) {
            R.id.btnDaily -> "DAILY"
            R.id.btnMonthly -> "MONTHLY"
            R.id.btnWeekly -> "WEEKLY"
            else -> {
                // Fallback: If nothing is selected, force Weekly and update UI
                toggleGroup.check(R.id.btnWeekly)
                "WEEKLY"
            }
        }
    }













    private fun setupChartDefaults() {
        barChart.description.isEnabled = false
        barChart.setDrawGridBackground(false)
        barChart.xAxis.setDrawGridLines(false)
        barChart.axisLeft.setDrawGridLines(false)
        barChart.axisRight.isEnabled = false
    }

    private fun setupToggleListener() {
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnDaily -> updateAnalytics("DAILY")
                    R.id.btnWeekly -> updateAnalytics("WEEKLY")
                    R.id.btnMonthly -> updateAnalytics("MONTHLY")
                }
            }
        }
    }

    private fun setupTransactionListener() {
        val userId = auth.currentUser?.uid ?: return
        transactionListener = db.collection("users").document(userId)
            .collection("payments")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    allTransactions.clear()
                    for (doc in snapshot.documents) {
                        val payment = doc.toObject(Payment::class.java)
                        payment?.let { allTransactions.add(it) }
                    }
                    // Default to Weekly view on load
                    updateAnalytics("WEEKLY")
                }
            }
    }

    /**
     * Refreshes the Analytics Dashboard UI, Chart, and Balance.
     * Made public to allow external triggers (QR scans/Bluetooth handshake)
     * to refresh the UI immediately.
     */

    fun updateAnalytics(mode: String) {
        val userId = auth.currentUser?.uid ?: return
        val filteredList = filterTransactionsByTime(mode)

        // 1. Handle Empty State
        if (filteredList.isEmpty()) {
            resetUIForEmptyState()
            setupBarChartData(emptyList(), userId)
            return
        }

        // 2. Data Processing
        // Replace with this:
        val receivedTransactions = filteredList.filter { it.direction == "RECEIVED" }
        val sentTransactions = filteredList.filter { it.direction == "SENT" }

        val totalIn = receivedTransactions.sumOf { it.amount }
        val totalOut = sentTransactions.sumOf { it.amount }
        val currentBalance = totalIn - totalOut

        // 3. UI Updates with Animation
        view?.let {
            // Retrieve the previous balance from the text to start animation from there
            val previousBalance = parseCurrency(tvTotalBalance.text.toString())

            // Premium "Roll-up" animation for the main balance
            animateBalance(previousBalance, currentBalance, tvTotalBalance)

            // Totals with signs and currency extension
            tvQuickReceived.text = "+${totalIn.toCurrency()}"
            tvQuickSent.text = "-${totalOut.toCurrency()}"

            // Transaction Counts
            tvCountReceived.text = "${receivedTransactions.size} Transactions"
            tvCountSent.text = "${sentTransactions.size} Transactions"

            // Haptic buzz for tactile feedback
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }

        // 4. Chart Refresh
        setupBarChartData(filteredList, userId)

        Log.d("BlinknPay_Analytics", "Dashboard updated: $mode | Balance: $currentBalance")
    }

    /**
     * Helper to animate the balance counting up or down
     */
    private fun animateBalance(start: Double, end: Double, textView: TextView) {
        val animator = android.animation.ValueAnimator.ofFloat(start.toFloat(), end.toFloat())
        animator.duration = 1000 // 1 second duration
        animator.addUpdateListener { valueAnimator ->
            val animatedValue = valueAnimator.animatedValue as Float
            textView.text = animatedValue.toDouble().toCurrency()
        }
        animator.start()
    }

    /**
     * Utility to parse KSh string back to Double for animation starting points
     */
    private fun parseCurrency(text: String): Double {
        return try {
            text.replace("KSh", "").replace(",", "").trim().toDouble()
        } catch (e: Exception) {
            0.0
        }
    }

    private fun resetUIForEmptyState() {
        tvTotalBalance.text = "KSh 0.00"
        tvQuickReceived.text = "+KSh 0.00"
        tvQuickSent.text = "-KSh 0.00"
        tvCountReceived.text = "0 Transactions"
        tvCountSent.text = "0 Transactions"
    }



    private fun filterTransactionsByTime(mode: String): List<Payment> {
        // Standardize the reference cursor (Remove time noise)
        val refCal = (calendarCursor.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            firstDayOfWeek = Calendar.MONDAY
        }

        return allTransactions.filter { payment ->
            val paymentCal = Calendar.getInstance().apply {
                timeInMillis = payment.timestamp
                firstDayOfWeek = Calendar.MONDAY
                minimalDaysInFirstWeek = 1
            }

            when (mode) {
                "DAILY" -> {
                    paymentCal.get(Calendar.YEAR) == refCal.get(Calendar.YEAR) &&
                            paymentCal.get(Calendar.DAY_OF_YEAR) == refCal.get(Calendar.DAY_OF_YEAR)
                }

                "WEEKLY" -> {
                    // Ensure we compare the week of the year within the same calendar year
                    val weekTarget = refCal.get(Calendar.WEEK_OF_YEAR)
                    val yearTarget = refCal.get(Calendar.YEAR)

                    paymentCal.get(Calendar.WEEK_OF_YEAR) == weekTarget &&
                            paymentCal.get(Calendar.YEAR) == yearTarget
                }

                "MONTHLY" -> {
                    paymentCal.get(Calendar.MONTH) == refCal.get(Calendar.MONTH) &&
                            paymentCal.get(Calendar.YEAR) == refCal.get(Calendar.YEAR)
                }

                else -> true
            }
        }
    }







    private fun setupBarChartData(data: List<Payment>, currentUserId: String) {
        if (data.isEmpty()) {
            barChart.clear()
            barChart.setNoDataText("No transactions for this period")
            barChart.invalidate()
            return
        }

        val sentEntries = ArrayList<BarEntry>()
        val receivedEntries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()
        val mode = getCurrentMode()

        // 1. DUAL AGGREGATION
        when (mode) {
            "DAILY" -> {
                val sentTotals = mutableMapOf<Int, Float>().apply { (0..23).forEach { put(it, 0f) } }
                val receivedTotals = mutableMapOf<Int, Float>().apply { (0..23).forEach { put(it, 0f) } }
                val cal = Calendar.getInstance()

                data.forEach { payment ->
                    cal.timeInMillis = payment.timestamp
                    val hour = cal.get(Calendar.HOUR_OF_DAY)

                    // FIX: Use direction field instead of unresolved 'sender'
                    if (payment.direction == "SENT") {
                        sentTotals[hour] = (sentTotals[hour] ?: 0f) + payment.amount.toFloat()
                    } else {
                        receivedTotals[hour] = (receivedTotals[hour] ?: 0f) + payment.amount.toFloat()
                    }
                }

                for (i in 0..23) {
                    sentEntries.add(BarEntry(i.toFloat(), sentTotals[i] ?: 0f))
                    receivedEntries.add(BarEntry(i.toFloat(), receivedTotals[i] ?: 0f))
                    labels.add(if (i % 4 == 0) "${i}h" else "")
                }
            }

            "WEEKLY" -> {
                val dayKeys = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                val sentTotals = mutableMapOf<String, Float>().apply { dayKeys.forEach { put(it, 0f) } }
                val receivedTotals = mutableMapOf<String, Float>().apply { dayKeys.forEach { put(it, 0f) } }
                val df = SimpleDateFormat("EEE", Locale.US)

                data.forEach { payment ->
                    val dayName = df.format(Date(payment.timestamp))
                    val key = dayKeys.find { it.equals(dayName, ignoreCase = true) } ?: return@forEach

                    // FIX: Use direction field
                    if (payment.direction == "SENT") {
                        sentTotals[key] = (sentTotals[key] ?: 0f) + payment.amount.toFloat()
                    } else {
                        receivedTotals[key] = (receivedTotals[key] ?: 0f) + payment.amount.toFloat()
                    }
                }

                dayKeys.forEachIndexed { index, day ->
                    sentEntries.add(BarEntry(index.toFloat(), sentTotals[day] ?: 0f))
                    receivedEntries.add(BarEntry(index.toFloat(), receivedTotals[day] ?: 0f))
                    labels.add(day)
                }
            }

            "MONTHLY" -> {
                val sentTotals = mutableMapOf<Int, Float>().apply { (1..5).forEach { put(it, 0f) } }
                val receivedTotals = mutableMapOf<Int, Float>().apply { (1..5).forEach { put(it, 0f) } }
                val cal = Calendar.getInstance().apply { firstDayOfWeek = Calendar.MONDAY }

                data.forEach { payment ->
                    cal.timeInMillis = payment.timestamp
                    val weekNo = cal.get(Calendar.WEEK_OF_MONTH).coerceIn(1, 5)

                    // FIX: Use direction field
                    if (payment.direction == "SENT") {
                        sentTotals[weekNo] = (sentTotals[weekNo] ?: 0f) + payment.amount.toFloat()
                    } else {
                        receivedTotals[weekNo] = (receivedTotals[weekNo] ?: 0f) + payment.amount.toFloat()
                    }
                }

                for (i in 1..5) {
                    sentEntries.add(BarEntry((i - 1).toFloat(), sentTotals[i] ?: 0f))
                    receivedEntries.add(BarEntry((i - 1).toFloat(), receivedTotals[i] ?: 0f))
                    labels.add("Wk $i")
                }
            }
        }

        // 2. DATASET STYLING
        val setSent = BarDataSet(sentEntries, "Sent").apply {
            color = Color.parseColor("#C2185B")
            setDrawValues(false)
            setDrawHighlightIndicators(false)
        }

        val setReceived = BarDataSet(receivedEntries, "Received").apply {
            color = Color.parseColor("#6CAF10")
            setDrawValues(false)
            setDrawHighlightIndicators(false)
        }

        // 3. GROUPING LOGIC (Math must equal 1.0)
        val barData = BarData(setReceived, setSent)
        val groupSpace = 0.34f
        val barSpace = 0.03f
        val barWidth = 0.30f

        barData.barWidth = barWidth
        barChart.data = barData

        // 4. AXIS & SCALING
        barChart.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(labels)
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            isGranularityEnabled = true
            axisMinimum = 0f
            axisMaximum = labels.size.toFloat()
            setCenterAxisLabels(true)
            setDrawGridLines(false)
            textColor = Color.DKGRAY
        }

        barChart.axisLeft.apply {
            axisMinimum = 0f
            setDrawGridLines(true)
            gridColor = Color.LTGRAY
        }
        barChart.axisRight.isEnabled = false

        // 5. EXECUTE GROUPING & ANIMATION
        barChart.groupBars(0f, groupSpace, barSpace)
        barChart.description.isEnabled = false
        barChart.legend.isEnabled = true
        barChart.setScaleEnabled(true)
        barChart.setPinchZoom(true)
        barChart.animateY(1000)
        barChart.invalidate()
    }

    private fun updateMonthDisplay() {
        // 1. Determine the format based on the current toggle mode
        val mode = getCurrentMode()

        // Pattern: "8 MARCH 2026" for Daily, "MARCH 2026" for others
        val pattern = if (mode == "DAILY") "dd MMMM yyyy" else "MMMM yyyy"
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())

        // 2. Apply the formatted text
        val formattedDate = sdf.format(calendarCursor.time).toUpperCase()
        tvCurrentMonth.text = formattedDate

        // 3. Trigger the data refresh with the current mode
        // This ensures that if you are in 'WEEKLY' mode and scroll, it stays in 'WEEKLY'
        updateAnalytics(mode)

        Log.d("BlinknPay_UI", "Display updated to: $formattedDate | Mode: $mode")
    }




    // Helper Functions
    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val fmt = java.text.SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return fmt.format(date1) == fmt.format(date2)
    }

    private fun isWithinLastDays(date: Date, days: Int): Boolean {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return date.after(cal.time)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        transactionListener?.remove()
    }
}
