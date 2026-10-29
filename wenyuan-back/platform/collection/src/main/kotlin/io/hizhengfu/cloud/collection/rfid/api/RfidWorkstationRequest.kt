package io.hizhengfu.cloud.collection.rfid.api

import jakarta.servlet.http.HttpServletRequest

internal const val RFID_WORKSTATION_COOKIE = "RFID_WORKSTATION"
internal const val RFID_WORKSTATION_CLIENT_HEADER = "X-RFID-Client-Instance"

internal fun HttpServletRequest.rfidWorkstationCredential(): String? =
    cookies?.firstOrNull { it.name == RFID_WORKSTATION_COOKIE }?.value

internal fun HttpServletRequest.rfidWorkstationClientInstance(): String? =
    getHeader(RFID_WORKSTATION_CLIENT_HEADER)
