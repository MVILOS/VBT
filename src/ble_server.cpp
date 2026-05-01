#include "ble_server.h"

// --- Server Connection Callbacks ---
class VBTBleServer::ServerCallbacks : public BLEServerCallbacks {
    VBTBleServer* parent;
public:
    ServerCallbacks(VBTBleServer* p) : parent(p) {}

    void onConnect(BLEServer* pServer) override {
        parent->deviceConnected = true;
        // Stop advertising - single connection only
        BLEDevice::getAdvertising()->stop();
        Serial.println("BLE: Client connected");
    }

    void onDisconnect(BLEServer* pServer) override {
        parent->deviceConnected = false;
        Serial.println("BLE: Client disconnected");
        // Restart advertising after short delay
        delay(500);
        BLEDevice::startAdvertising();
        Serial.println("BLE: Advertising restarted");
    }
};

// --- Command Write Callbacks ---
class VBTBleServer::CommandCallbacks : public BLECharacteristicCallbacks {
    VBTBleServer* parent;
public:
    CommandCallbacks(VBTBleServer* p) : parent(p) {}

    void onWrite(BLECharacteristic* pCharacteristic) override {
        std::string value = pCharacteristic->getValue();
        if (value.length() < 1) return;

        uint8_t cmd = value[0];

        switch (cmd) {
            case 0x01: // Reset rep counter
                Serial.println("BLE CMD: Reset rep counter");
                // Note: repCount is in LiftDetector, we'd need a reset method
                // For now, this is handled by the app tracking its own count
                break;

            case 0x02: // Clear device history
                Serial.println("BLE CMD: Clear history");
                parent->storage->clearHistory();
                break;

            case 0x03: // Set exercise parameters
                if (value.length() >= 4) {
                    float minLiftVel = (float)value[1] / 100.0f;
                    float endLiftVel = (float)value[2] / 100.0f;
                    float minRepDist = (float)value[3] / 100.0f;
                    parent->detector->setExerciseParams(minLiftVel, endLiftVel, minRepDist);
                    Serial.printf("BLE CMD: Exercise params set: %.2f, %.2f, %.2f\n",
                                  minLiftVel, endLiftVel, minRepDist);
                }
                break;

            case 0x04: // Ping
                Serial.println("BLE CMD: Ping received");
                break;

            default:
                Serial.printf("BLE CMD: Unknown command 0x%02X\n", cmd);
                break;
        }
    }
};

// --- VBTBleServer Implementation ---

VBTBleServer::VBTBleServer(LiftDetector* det, DataStorage* stor)
    : pServer(nullptr),
      pService(nullptr),
      pLiveVelocity(nullptr),
      pRepResult(nullptr),
      pDeviceStatus(nullptr),
      pCommand(nullptr),
      detector(det),
      storage(stor),
      deviceConnected(false),
      oldDeviceConnected(false),
      lastNotifyTime(0),
      lastSentVelocity(0.0f),
      lastLiftingState(false),
      newResultFlag(false)
{
}

void VBTBleServer::begin() {
    // Initialize BLE
    BLEDevice::init("VBT-Vector");
    BLEDevice::setPower(ESP_PWR_LVL_P3); // 3dBm - good range vs power balance

    // Create server
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new ServerCallbacks(this));

    // Create service
    pService = pServer->createService(VBT_SERVICE_UUID);

    // --- Characteristic: Live Velocity (FF01) - Notify ---
    pLiveVelocity = pService->createCharacteristic(
        CHAR_LIVE_VELOCITY_UUID,
        BLECharacteristic::PROPERTY_NOTIFY
    );
    pLiveVelocity->addDescriptor(new BLE2902());

    // --- Characteristic: Rep Result (FF02) - Notify + Read ---
    pRepResult = pService->createCharacteristic(
        CHAR_REP_RESULT_UUID,
        BLECharacteristic::PROPERTY_NOTIFY | BLECharacteristic::PROPERTY_READ
    );
    pRepResult->addDescriptor(new BLE2902());

    // --- Characteristic: Device Status (FF03) - Read ---
    pDeviceStatus = pService->createCharacteristic(
        CHAR_DEVICE_STATUS_UUID,
        BLECharacteristic::PROPERTY_READ
    );

    // --- Characteristic: Command (FF04) - Write ---
    pCommand = pService->createCharacteristic(
        CHAR_COMMAND_UUID,
        BLECharacteristic::PROPERTY_WRITE
    );
    pCommand->setCallbacks(new CommandCallbacks(this));

    // Start service
    pService->start();

    // Start advertising
    BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(VBT_SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    pAdvertising->setMinPreferred(0x06); // 7.5ms connection interval hint
    pAdvertising->setMaxPreferred(0x12); // 22.5ms connection interval hint
    BLEDevice::startAdvertising();

    // Set initial device status
    uint8_t statusData[8] = {0};
    // Byte 0: firmware major version
    statusData[0] = 2;
    // Byte 1: firmware minor version
    statusData[1] = 0;
    // Byte 2: firmware patch version
    statusData[2] = 0;
    pDeviceStatus->setValue(statusData, 8);

    Serial.println("BLE Server started - advertising as VBT-Vector");
}

void VBTBleServer::update() {
    // Handle connection state changes
    if (!deviceConnected && oldDeviceConnected) {
        // Just disconnected - cleanup
        oldDeviceConnected = false;
        lastSentVelocity = 0.0f;
        lastLiftingState = false;
    }
    if (deviceConnected && !oldDeviceConnected) {
        // Just connected
        oldDeviceConnected = true;
    }

    if (!deviceConnected) return;

    // Update device status periodically
    static unsigned long lastStatusUpdate = 0;
    if (millis() - lastStatusUpdate > 5000) {
        uint8_t statusData[8] = {0};
        statusData[0] = 2; // firmware major
        statusData[1] = 0; // firmware minor
        statusData[2] = 0; // firmware patch
        // Bytes 3-4: free heap in KB (uint16_t)
        uint16_t freeHeapKB = ESP.getFreeHeap() / 1024;
        statusData[3] = freeHeapKB & 0xFF;
        statusData[4] = (freeHeapKB >> 8) & 0xFF;
        // Bytes 5-6: uptime in minutes (uint16_t)
        uint16_t uptimeMin = millis() / 60000;
        statusData[5] = uptimeMin & 0xFF;
        statusData[6] = (uptimeMin >> 8) & 0xFF;
        pDeviceStatus->setValue(statusData, 8);
        lastStatusUpdate = millis();
    }

    // Send live velocity notifications at up to 50Hz (every 20ms)
    if (millis() - lastNotifyTime >= 20) {
        sendLiveVelocity();
        lastNotifyTime = millis();
    }
}

void VBTBleServer::sendLiveVelocity() {
    float currentVelocity = detector->getCurrentVelocity();
    bool isLifting = detector->isCurrentlyLifting();

    // Throttling - only send on meaningful change
    if (abs(currentVelocity - lastSentVelocity) < 0.005f &&
        isLifting == lastLiftingState &&
        !newResultFlag) {
        return;
    }

    lastSentVelocity = currentVelocity;
    lastLiftingState = isLifting;

    // Pack 6 bytes
    uint8_t data[6] = {0};

    // Bytes 0-1: int16_t velocity (m/s * 1000)
    int16_t velInt = (int16_t)(currentVelocity * 1000.0f);
    data[0] = velInt & 0xFF;
    data[1] = (velInt >> 8) & 0xFF;

    // Byte 2: flags
    uint8_t flags = 0;
    if (isLifting) flags |= 0x01;
    if (newResultFlag) flags |= 0x02;
    data[2] = flags;

    // Bytes 3-4: repCount (uint16_t)
    uint16_t repCount = detector->getRepCount();
    data[3] = repCount & 0xFF;
    data[4] = (repCount >> 8) & 0xFF;

    // Byte 5: reserved
    data[5] = 0;

    pLiveVelocity->setValue(data, 6);
    pLiveVelocity->notify();

    // Reset new result flag after sending
    if (newResultFlag) {
        newResultFlag = false;
    }
}

void VBTBleServer::sendRepResult(const LiftResult& result) {
    // Pack 16 bytes
    uint8_t data[16] = {0};

    // Bytes 0-1: meanVelocity (uint16_t, m/s * 1000)
    uint16_t vel = (uint16_t)(result.meanVelocity * 1000.0f);
    data[0] = vel & 0xFF;
    data[1] = (vel >> 8) & 0xFF;

    // Bytes 2-3: distance (uint16_t, m * 1000)
    uint16_t dist = (uint16_t)(result.distance * 1000.0f);
    data[2] = dist & 0xFF;
    data[3] = (dist >> 8) & 0xFF;

    // Bytes 4-5: duration (uint16_t, ms)
    data[4] = result.duration & 0xFF;
    data[5] = (result.duration >> 8) & 0xFF;

    // Bytes 6-9: timestamp (uint32_t, ms from boot)
    uint32_t ts = result.timestamp;
    data[6] = ts & 0xFF;
    data[7] = (ts >> 8) & 0xFF;
    data[8] = (ts >> 16) & 0xFF;
    data[9] = (ts >> 24) & 0xFF;

    // Bytes 10-11: repIndex (uint16_t)
    uint16_t repIdx = detector->getRepCount();
    data[10] = repIdx & 0xFF;
    data[11] = (repIdx >> 8) & 0xFF;

    // Bytes 14-15: reserved
    data[14] = 0;
    data[15] = 0;

    pRepResult->setValue(data, 16);
    pRepResult->notify();
}

void VBTBleServer::notifyNewResult() {
    newResultFlag = true;

    if (deviceConnected) {
        // Get the latest result from storage
        LiftResult result = storage->getResultByIndex(0);
        sendRepResult(result);
    }
}

bool VBTBleServer::isConnected() const {
    return deviceConnected;
}
