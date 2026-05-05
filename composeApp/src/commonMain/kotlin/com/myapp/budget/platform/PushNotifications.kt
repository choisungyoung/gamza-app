package com.myapp.budget.platform

/** 플랫폼별 FCM/APNs 토큰을 비동기로 가져와 콜백에 전달 */
expect fun registerFcmToken(onToken: (String) -> Unit)
