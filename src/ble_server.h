#ifndef BLE_SERVER_H
#define BLE_SERVER_H

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include "lift_detector.h"
#include "data_storage.h"

#define VBT_SERVICE_UUID        "0000FF00-1234-5678-9ABC-DEF012345678"
#define CHAR_LIVE_VELOCITY_UUID "0000FF01-1234-5678-9ABC-DEF012345678"
#define CHAR_REP_RESULT_UUID    "0000FF02-1234-5678-9ABC-DEF012345678"
#define CHAR_DEVICE_STATUS_UUID "0000FF03-1234-5678-9ABC-DEF012345678"
#define CHAR_COMMAND_UUID       "0000FF04-1234-5678-9ABC-DEF012345678"

class VBTBleServer {
private:
    BLEServer* pServer;
    BLEService* pService;
    BLECharacteristic* pLiveVelocity;
    BLECharacteristic* pRepResult;
    BLECharacteristic* pDeviceStatus;
    BLECharacteristic* pCommand;

    LiftDetector* detector;
    DataStorage* storage;

    bool deviceConnected;
    bool oldDeviceConnected;

    unsigned long lastNotifyTime;
    float lastSentVelocity;
    bool lastLiftingState;
    bool newResultFlag;

    // Inner callback classes
    class ServerCallbacks;
    class CommandCallbacks;

    void sendLiveVelocity();
    void sendRepResult(const LiftResult& result);

public:
    VBTBleServer(LiftDetector* det, DataStorage* stor);
    void begin();
    void update();
    void notifyNewResult();
    bool isConnected() const;
};

#endif
