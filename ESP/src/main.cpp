#include <Arduino.h>

#include <NimBLEDevice.h>
#include <EEPROM.h>
#include <USBHIDKeyboard.h>
#include <USB.h>
#include <OneButton.h>

#include <crypto.h>

SET_LOOP_TASK_STACK_SIZE(32 * 1024);

// #define NEW_KEY

#define BTN_PIN 0

OneButton button(BTN_PIN, true);

String TOKEN = "BLE Auth token";

bool is_auth[1024] = {0};
NimBLEServer *pServer;
mbedtls_pk_context pk;

USBHIDKeyboard keyboard;

char *decrypted;

class serverCallbacks : public NimBLEServerCallbacks
{
    void onConnect(NimBLEServer *pServer, NimBLEConnInfo &connInfo)
    {
        pServer->startAdvertising();
        if (connInfo.getConnHandle() > 1000) {
            pServer->disconnect(connInfo);
        }
    }

    void onDisconnect(NimBLEServer* pServer, NimBLEConnInfo& connInfo, int reason) {
        is_auth[connInfo.getConnHandle()] = false;
    }
};

class dataCharacteristicCallbacks : public NimBLECharacteristicCallbacks
{
    void onWrite(NimBLECharacteristic *pCharacteristic, NimBLEConnInfo &connInfo)
    {
        Serial.println("DATA");
        const char* token = pCharacteristic->getValue().c_str();
        int n = pCharacteristic->getValue().length();
        for (int i = 0; i < n; i++) {
            Serial.printf("0x%02x, ", token[i]);
        }
        Serial.println();
        Serial.flush();

        if (is_auth[connInfo.getConnHandle()]) {
            NimBLEAttValue value = pCharacteristic->getValue();
            const uint8_t *data = value.data();
            // char *decrypted = (char *)malloc(mbedtls_pk_rsa(pk)->len);
            memset(decrypted, 0, mbedtls_pk_rsa(pk)->len);
            int res;
            if ((res = decrypt(&pk, data, decrypted)) != 0) {
                Serial.printf("Decryption error: -0x%04x\n", -res);
            } else {
                Serial.print("Decrypted: ");
                Serial.println(decrypted);
                Serial.flush();
            }
        } else {
            Serial.println("Not authenticated");
        }
        Serial.flush();
    }
};

class tokenCharacteristicCallbacks : public NimBLECharacteristicCallbacks
{
    void onWrite(NimBLECharacteristic *pCharacteristic, NimBLEConnInfo &connInfo)
    {
        Serial.println("TOKEN");
        const char* token = pCharacteristic->getValue().c_str();
        int n = pCharacteristic->getValue().length();
        for (int i = 0; i < n; i++) {
            Serial.printf("0x%02x, ", token[i]);
        }
        Serial.println();
        Serial.flush();
        if (TOKEN.equals(pCharacteristic->getValue().c_str())) {
            is_auth[connInfo.getConnHandle()] = true;
        } else {
            pServer->disconnect(connInfo);
        }
    }
};

void setup()
{
    // Serial.begin(115200);
    EEPROM.begin(4096);
    delay(4000);
    Serial.println("Start");
    Serial.flush();

#ifdef NEW_KEY
    if (new_key(pk) != 0) {
        Serial.println("Key generation error");
        Serial.flush();
        return;
    }
#else
    int ret;
    if ((ret = load_key(pk)) != 0) {
        Serial.printf("Key loading error: %d\n", ret);
        Serial.flush();
        return;
    }     
    unsigned char output_buf[8192];
    if ((ret = mbedtls_pk_write_pubkey_pem(&pk, output_buf, sizeof(output_buf))) != 0)
    {
        printf("Failed to write public key: -0x%04x\n", -ret);
        Serial.flush();
        return;
    }
    printf("Public key:\n%s\n", output_buf);
    Serial.flush();
#endif
    keyboard.begin();
    USB.begin();

    decrypted = (char*)malloc(mbedtls_pk_rsa(pk)->len);

    button.attachClick([] {
        keyboard.write((const uint8_t*)decrypted, strlen(decrypted));
    });

    NimBLEDevice::init("<BLE device name>");
    NimBLEDevice::setSecurityAuth(true, true, true); /** bonding, MITM, BLE secure connections */
    NimBLEDevice::setSecurityPasskey(123456);
    NimBLEDevice::setSecurityIOCap(BLE_HS_IO_DISPLAY_ONLY); /** Display only passkey */
    pServer = NimBLEDevice::createServer();
    pServer->advertiseOnDisconnect(true);
    pServer->setCallbacks(new serverCallbacks(), true);
    Serial.flush();
    NimBLEService *pService = pServer->createService("58882f50-2cf8-4468-a65b-34ae3a5f7a88");
    NimBLECharacteristic *pDataCharacteristic =
        pService->createCharacteristic("56b4864d-9ac2-4699-b31b-3bb23ca96ee4",
                                       NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_ENC | NIMBLE_PROPERTY::WRITE_AUTHEN);
    pDataCharacteristic->setCallbacks(new dataCharacteristicCallbacks());

    NimBLECharacteristic *pTokenCharacteristic = 
        pService->createCharacteristic("d7e035d2-b43a-40c7-8941-0f17078214de",
                                       NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_ENC | NIMBLE_PROPERTY::WRITE_AUTHEN);
    pTokenCharacteristic->setCallbacks(new tokenCharacteristicCallbacks());

    pService->start();
    pServer->startAdvertising();
}

void loop()
{
    button.tick();
    delay(5);
}
