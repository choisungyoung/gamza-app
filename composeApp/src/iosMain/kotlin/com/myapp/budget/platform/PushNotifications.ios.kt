package com.myapp.budget.platform

actual fun registerFcmToken(onToken: (String) -> Unit) {
    // iOS APNs integration is out of scope; no-op stub
}
