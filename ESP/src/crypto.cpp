#include <Arduino.h>

#include <EEPROM.h>
#include <FS.h>
#include <SD_MMC.h>

#include <mbedtls/pk.h>
#include <mbedtls/rsa.h>
#include <mbedtls/entropy.h>
#include <mbedtls/ctr_drbg.h>

#include <bootloader_random.h>

#define KEY_SIZE 1024 
#define EXPONENT 65537

int SD_CLK = 12;
int SD_CMD = 16;
int SD_D0 = 14;
int SD_D1 = 17;
int SD_D2 = 21;
int SD_D3 = 18;

String generateRandomString(size_t length);

int entropy_callback(void *data, unsigned char *output, size_t len, size_t *olen)
{
    esp_fill_random(output, len);
    *olen = len;
    return 0;
}

int new_key(mbedtls_pk_context &res)
{
    int ret;
    mbedtls_rsa_context rsa;
    mbedtls_entropy_context entropy;
    mbedtls_ctr_drbg_context ctr_drbg;

    String new_name;
    File file;

    unsigned char output_buf[8192] = {0};

    const char *pers = "rsa_keygen_deJcXsRLYkTTeaut";
    mbedtls_rsa_init(&rsa, MBEDTLS_RSA_PKCS_V21, MBEDTLS_MD_SHA256);
    mbedtls_ctr_drbg_init(&ctr_drbg);
    mbedtls_entropy_init(&entropy);

    mbedtls_entropy_add_source(&entropy, (mbedtls_entropy_f_source_ptr)entropy_callback, NULL, 8, MBEDTLS_ENTROPY_SOURCE_STRONG);
    bootloader_random_enable();
    if ((ret = mbedtls_ctr_drbg_seed(&ctr_drbg, mbedtls_entropy_func, &entropy, (const unsigned char *)pers, strlen(pers))) != 0)
    {
        printf("mbedtls_ctr_drbg_seed failed: -0x%04x\n", -ret);
        goto exit;
    }

    printf("Generating %d-bit RSA key...", KEY_SIZE);
    Serial.flush();
    if ((ret = mbedtls_rsa_gen_key(&rsa, mbedtls_ctr_drbg_random, &ctr_drbg, KEY_SIZE, EXPONENT)) != 0)
    {
        printf("mbedtls_rsa_gen_key failed: -0x%04x\n", -ret);
        goto exit;
    }
    Serial.println("Done");
    Serial.flush();

    mbedtls_pk_context pk;
    mbedtls_pk_init(&pk);
    if ((ret = mbedtls_pk_setup(&pk, mbedtls_pk_info_from_type(MBEDTLS_PK_RSA))) != 0)
    {
        printf("mbedtls_pk_setup failed: %d\n", ret);
        goto exit;
    }
    pk.pk_ctx = &rsa;

    if ((ret = mbedtls_pk_write_key_pem(&pk, output_buf, sizeof(output_buf))) != 0)
    {
        printf("Failed to write private key: -0x%04x\n", -ret);
        goto exit;
    }
    printf("Private key:\n%s\n", output_buf);

    EEPROM.writeString(1, *(new String((char *)output_buf)));
    EEPROM.writeByte(0, 1);
    EEPROM.commit();

    if (!SD_MMC.setPins(SD_CLK, SD_CMD, SD_D0, SD_D1, SD_D2, SD_D3)) {
      Serial.println("Pin change failed!");
      goto exit;
    }
    if (!SD_MMC.begin()) {
      Serial.println("Card Mount Failed");
      goto exit;
    }

    new_name = "/key" + generateRandomString(12);
    if (SD_MMC.exists("/key")) {
        SD_MMC.rename("/key", new_name);
        Serial.println("/key already exists. Renamed to " + new_name);
    }
    file = SD_MMC.open("/key", "w", true);

    for (int i = 0; output_buf[i] != '\0'; i++) {
        file.write(output_buf[i]);
    }
    file.close();
    SD_MMC.end();

    memset(output_buf, 0, sizeof(output_buf));
    if ((ret = mbedtls_pk_write_pubkey_pem(&pk, output_buf, sizeof(output_buf))) != 0)
    {
        printf("Failed to write public key: -0x%04x\n", -ret);
        goto exit;
    }
    printf("Public key:\n%s\n", output_buf);

exit:
    mbedtls_ctr_drbg_free(&ctr_drbg);
    mbedtls_entropy_free(&entropy);
    if (ret == 0) {
        res.pk_ctx = pk.pk_ctx;
        res.pk_info = pk.pk_info;
    }
    return ret;
}

int load_key(mbedtls_pk_context &res)
{
    if (EEPROM.readByte(0) != 1)
    {
        return 1;
    }

    String key = EEPROM.readString(1);
    mbedtls_pk_context pk;
    mbedtls_pk_init(&pk);
    int ret;

    char new_key[key.length()]; 
    memcpy(new_key, key.c_str(), key.length());
    new_key[key.length() - 1] = 0;
    if ((ret = mbedtls_pk_parse_key(&pk, (const unsigned char*)new_key, key.length(), NULL, 0)) != 0)
    {
        Serial.printf("parse error: -0x%04x\n", -ret);
        Serial.flush();
        return 2;
    }
    res.pk_ctx = pk.pk_ctx;
    res.pk_info = pk.pk_info;
    return 0;
}

/**
 * \param decrypted the size must be equal to the key size
*/
int decrypt(mbedtls_pk_context *pk, const uint8_t *data, char *decrypted)
{
    int res = 0;

    mbedtls_rsa_set_padding(mbedtls_pk_rsa(*pk), MBEDTLS_RSA_PKCS_V21, MBEDTLS_MD_SHA256);

    mbedtls_entropy_context entropy;
    mbedtls_ctr_drbg_context ctr_drbg;
    mbedtls_ctr_drbg_init(&ctr_drbg);
    mbedtls_entropy_init(&entropy);
    bootloader_random_enable();
    mbedtls_entropy_add_source(&entropy, (mbedtls_entropy_f_source_ptr)entropy_callback, NULL, 8, MBEDTLS_ENTROPY_SOURCE_STRONG);

    const char *pers = "rsa_keygen_deJcXsRLYkTTeaut";
    if ((res = mbedtls_ctr_drbg_seed(&ctr_drbg, mbedtls_entropy_func, &entropy, (const unsigned char *)pers, strlen(pers))) != 0)
    {
        Serial.printf("drbg seed error: -0x%04x\n", -res);
        return res;
    }

    size_t decrypted_length = 0;
    res = mbedtls_rsa_rsaes_oaep_decrypt( mbedtls_pk_rsa(*pk), mbedtls_ctr_drbg_random, &ctr_drbg, MBEDTLS_RSA_PRIVATE, NULL, 0, &decrypted_length, (const uint8_t *)data, (unsigned char*)decrypted, mbedtls_pk_rsa(*pk)->len);
    if (res != 0) {
        Serial.printf("decrypting error: -0x%04x\n", -res);
        return res;
    }
    return 0;
}

String generateRandomString(size_t length) {
    String result = "";
    const char charset[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    const size_t charsetSize = sizeof(charset) - 1;
  
    for (size_t i = 0; i < length; i++) {
        uint32_t randIndex = esp_random() % charsetSize;
        result += charset[randIndex];
    }
  
    return result;
  }