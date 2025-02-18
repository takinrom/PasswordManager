#include <mbedtls/pk.h>

int new_key(mbedtls_pk_context &res);
int load_key(mbedtls_pk_context &res);
int decrypt(mbedtls_pk_context *pk, const uint8_t *data, char *decrypted);