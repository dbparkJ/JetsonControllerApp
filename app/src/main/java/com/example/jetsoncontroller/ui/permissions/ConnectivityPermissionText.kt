package com.example.jetsoncontroller.ui.permissions

import android.os.Build

internal fun bluetoothPermissionText(sdkInt: Int): String =
    if (sdkInt < Build.VERSION_CODES.S) {
        "가까운 장치를 찾으려면 위치 권한이 필요합니다. 위치 서비스도 켜세요."
    } else {
        "가까운 장치를 찾으려면 블루투스 권한이 필요합니다."
    }
