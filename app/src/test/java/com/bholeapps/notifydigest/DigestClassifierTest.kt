package com.bholeapps.notifydigest

import org.junit.Assert.assertEquals
import org.junit.Test

class DigestClassifierTest {
    @Test
    fun otpIsPriority() {
        val result = DigestClassifier.classify(
            title = "Login code",
            text = "Use 123456 as your OTP to verify sign in",
            app = "Messages"
        )

        assertEquals("Priority", result.priority)
    }

    @Test
    fun bankDebitIsMoneyAndPriority() {
        val result = DigestClassifier.classify(
            title = "HDFC Bank",
            text = "Rs. 500 debited from your account via UPI",
            app = "Messages"
        )

        assertEquals("Money", result.category)
        assertEquals("Priority", result.priority)
    }

    @Test
    fun cashbackOfferIsNoise() {
        val result = DigestClassifier.classify(
            title = "Big cashback offer",
            text = "Limited time deal. Shop now and get rewards",
            app = "Shopping"
        )

        assertEquals("Noise", result.category)
        assertEquals("Noise", result.priority)
    }

    @Test
    fun deliveryUpdateIsOrderPriority() {
        val result = DigestClassifier.classify(
            title = "Out for delivery",
            text = "Your order is arriving today",
            app = "Amazon"
        )

        assertEquals("Orders", result.category)
        assertEquals("Priority", result.priority)
    }

    @Test
    fun meetingReminderIsCalendarPriority() {
        val result = DigestClassifier.classify(
            title = "Meeting reminder",
            text = "Design review starts at 5 pm today",
            app = "Calendar"
        )

        assertEquals("Calendar", result.category)
        assertEquals("Priority", result.priority)
    }

    @Test
    fun systemAlertIsSystemNotPriority() {
        val result = DigestClassifier.classify(
            title = "Battery low",
            text = "Connect charger",
            app = "Android System"
        )

        assertEquals("System", result.category)
        assertEquals("Later", result.priority)
    }
}
