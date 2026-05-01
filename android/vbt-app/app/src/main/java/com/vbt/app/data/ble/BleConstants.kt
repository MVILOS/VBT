package com.vbt.app.data.ble

import java.util.UUID

object BleConstants {
    val VBT_SERVICE_UUID: UUID = UUID.fromString("0000FF00-1234-5678-9ABC-DEF012345678")
    val LIVE_VELOCITY_UUID: UUID = UUID.fromString("0000FF01-1234-5678-9ABC-DEF012345678")
    val REP_RESULT_UUID: UUID = UUID.fromString("0000FF02-1234-5678-9ABC-DEF012345678")
    val DEVICE_STATUS_UUID: UUID = UUID.fromString("0000FF03-1234-5678-9ABC-DEF012345678")
    val COMMAND_UUID: UUID = UUID.fromString("0000FF04-1234-5678-9ABC-DEF012345678")

    const val CMD_RESET_REPS: Byte = 0x01
    const val CMD_CLEAR_HISTORY: Byte = 0x02
    const val CMD_SET_EXERCISE_PARAMS: Byte = 0x03
    const val CMD_PING: Byte = 0x04

    const val DEVICE_NAME = "VBT-Vector"
}
