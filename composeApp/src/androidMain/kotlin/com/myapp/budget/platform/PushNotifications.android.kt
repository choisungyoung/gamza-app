package com.myapp.budget.platform

import com.google.firebase.messaging.FirebaseMessaging

actual fun registerFcmToken(onToken: (String) -> Unit) {
    FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
        onToken(token)
    }
}
