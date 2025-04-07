package me.takinrom.passwordmanager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends AppCompatActivity {

    private final String SERVER_URL = "https://ip:port";
    private final String SECRET = "Server Auth token";
    private final String BLE_SECURITY_TOKEN = "BLE Auth token";
    private final String BLE_DEVICE_NAME = "BLE device name";
    private final String BLE_SERVICE_UUID = "58882f50-2cf8-4468-a65b-34ae3a5f7a88";
    private final String BLE_TOKEN_CHARACTERISTIC_UUID = "d7e035d2-b43a-40c7-8941-0f17078214de";
    private final String BLE_DATA_CHARACTERISTIC_UUID = "56b4864d-9ac2-4699-b31b-3bb23ca96ee4";
    private Account[] accounts;
    private OkHttpClient httpClient;
    private BLEConnector bleConnector;
    private static MainActivity activity;
    private PublicKey publicKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = this;
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        publicKey = loadPublicKey();

        CertificateFactory certificateFactory;
        try {
            certificateFactory = CertificateFactory.getInstance("X.509");
            Certificate certificate;
            try (var certInputStream = getResources().openRawResource(R.raw.cert)) {
                certificate = certificateFactory.generateCertificate(certInputStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("self-signed", certificate);

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, null);

            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            httpClient = (new OkHttpClient.Builder()).sslSocketFactory(sslSocketFactory, (X509TrustManager) trustManagers[0]).hostnameVerifier((a, b) -> true).connectTimeout(5, TimeUnit.SECONDS).writeTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS).build();
        } catch (CertificateException | NoSuchAlgorithmException | KeyStoreException |
                 KeyManagementException | IOException e) {
            throw new RuntimeException(e);
        }

        updateAccountsArray();


        bleConnector = new BLEConnector(getSystemService(BluetoothManager.class));
        FloatingActionButton addButton = findViewById(R.id.add_button);
        addButton.setOnClickListener(v -> showFormDialog());
        MaterialButton reconnectButton = findViewById(R.id.reconnect_button);
        reconnectButton.setOnClickListener(v -> bleConnector.run());
    }

    private void updateAccountsArray() {
        Request request = new Request.Builder().url(SERVER_URL + "/logins").header("Authorization", SECRET).build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> showErrorDialog(e.toString()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    ResponseBody body = response.body();
                    if (body == null) {
                        runOnUiThread(() -> showErrorDialog("Server response is empty"));
                        return;
                    }
                    Gson gson = new Gson();
                    List<List<String>> json = gson.fromJson(body.string(), new TypeToken<Collection<Collection<String>>>() {
                    }.getType());
                    int n = json.size();
                    accounts = new Account[n];
                    for (int i = 0; i < n; i++) {
                        List<String> item = json.get(i);
                        if (item.get(0) == null || item.get(1) == null) {
                            accounts[i] = new Account("Error", "Error");
                        } else {
                            accounts[i] = new Account(item.get(0), item.get(1));
                        }
                    }
                    runOnUiThread(() -> {
                        RecyclerView recyclerView = findViewById(R.id.list);
                        AccountAdapter adapter = new AccountAdapter(MainActivity.this, accounts, ((account, position) -> {
                            Request request = new Request.Builder().url(SERVER_URL + "/pass?service=" + account.getService() + "&login=" + account.getLogin()).header("Authorization", SECRET).build();
                            httpClient.newCall(request).enqueue(new Callback() {
                                @Override
                                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                    runOnUiThread(() -> {
                                        showErrorDialog(e.toString());
                                    });
                                }

                                @Override
                                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                                    if (response.isSuccessful()) {
                                        ResponseBody body = response.body();
                                        if (body == null) {
                                            runOnUiThread(() -> showErrorDialog("Server response is empty"));
                                        } else {
                                            String encrypted_password = body.string();
                                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "OK", Toast.LENGTH_SHORT).show());
                                            bleConnector.writeData(Base64.getDecoder().decode(encrypted_password));
                                        }
                                    } else {
                                        runOnUiThread(() -> showErrorDialog(response.code() + ": " + response.message()));
                                    }
                                }
                            });
                        }));
                        recyclerView.setAdapter(adapter);
                    });
                } else {
                    runOnUiThread(() -> showErrorDialog(response.code() + ": " + response.message()));
                }
            }
        });
    }

    private void showFormDialog() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_form, null);

        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        EditText editTextService = dialogView.findViewById(R.id.editTextService);
        EditText editTextLogin = dialogView.findViewById(R.id.editTextLogin);
        EditText editTextPassword = dialogView.findViewById(R.id.editTextTextPassword);
        TextView passwordLengthLabel = dialogView.findViewById(R.id.passwordLengthLabel);
        EditText editTextPasswordLength = dialogView.findViewById(R.id.editTextPasswordLength);
        CheckBox generateCheckBox = dialogView.findViewById(R.id.generateCheckBox);
        CheckBox asciiCheckBox = dialogView.findViewById(R.id.asciiCheckBox);
        CheckBox digitsCheckBox = dialogView.findViewById(R.id.digitsCheckBox);
        CheckBox specialsCheckBox = dialogView.findViewById(R.id.specialsCheckBox);
        Button submitButton = dialogView.findViewById(R.id.submitButton);

        submitButton.setOnClickListener(view -> {
            String password;
            if (generateCheckBox.isChecked()) {
                password = generatePassword(Integer.parseInt(editTextPasswordLength.getText().toString()), asciiCheckBox.isChecked(), digitsCheckBox.isChecked(), specialsCheckBox.isChecked());
            } else {
                password = editTextPassword.getText().toString();
            }
            Request request = new Request.Builder().header("Authorization", SECRET).url(SERVER_URL + "/add").post(new FormBody.Builder().add("service", editTextService.getText().toString()).add("login", editTextLogin.getText().toString())
                    .add("encrypted_password", Base64.getEncoder().encodeToString(encrypt(password.getBytes())))
                    .build()).build();
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> showErrorDialog(e.toString()));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "OK", Toast.LENGTH_SHORT).show();
                            updateAccountsArray();
                        });
                    } else {
                        runOnUiThread(() -> showErrorDialog(response.code() + ": " + response.message()));
                    }
                }
            });
            dialog.dismiss();
        });

        generateCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                editTextPassword.setEnabled(false);
                asciiCheckBox.setEnabled(true);
                passwordLengthLabel.setEnabled(true);
                editTextPasswordLength.setEnabled(true);
                digitsCheckBox.setEnabled(true);
                specialsCheckBox.setEnabled(true);
            } else {
                editTextPassword.setEnabled(true);
                asciiCheckBox.setEnabled(false);
                passwordLengthLabel.setEnabled(false);
                editTextPasswordLength.setEnabled(false);
                digitsCheckBox.setEnabled(false);
                specialsCheckBox.setEnabled(false);
            }
        });
        generateCheckBox.setChecked(true);
        editTextPasswordLength.setText(R.string.DefaultPasswordLength);
        asciiCheckBox.setChecked(true);
        digitsCheckBox.setChecked(true);
        specialsCheckBox.setChecked(true);

        dialog.show();
    }

    private String generatePassword(int length, boolean ascii_flag, boolean digits_flag, boolean specials_flag) {
        String special_chars = "!\"#$%&\\'()*+,-./:;<=>?@[\\]^_`{|}~";
        String digits = "0123456789";
        String ascii = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String chars = (ascii_flag ? ascii : "") + (digits_flag ? digits : "") + (specials_flag ? special_chars : "");
        char[] result = new char[length];
        SecureRandom secureRandom = new SecureRandom();
        for (int i = 0; i < length; i++) {
            result[i] = chars.charAt(Math.abs(secureRandom.nextInt()) % chars.length());
        }
        return new String(result);
    }

    class BLEConnector {

        private BluetoothLeScanner bluetoothLeScanner;
        private BluetoothGattCharacteristic dataCharacteristic;
        private BluetoothGattCharacteristic tokenCharacteristic;
        private BluetoothGatt connectedGatt;
        private boolean isConnectedFlag = false;

        public BLEConnector(BluetoothManager bluetoothManager) {
            if (bluetoothManager == null) {
                runOnUiThread(() -> showErrorDialog("Bluetooth manager error"));
                return;
            }
            BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter == null) {
                runOnUiThread(() -> showErrorDialog("Bluetooth adapter error"));
                return;
            }
            if (!bluetoothAdapter.isEnabled()) {
                showErrorDialog("Bluetooth adapter is disabled");
                return;
            }
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        public void run() {
            if (bluetoothLeScanner != null) {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    showErrorDialog("Bluetooth scan permission error");
                    return;
                }
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    showErrorDialog("Bluetooth connect permission error");
                    return;
                }
                bluetoothLeScanner.startScan(leScanCallback);
            }
        }

        private final ScanCallback leScanCallback = new ScanCallback() {

            @SuppressLint("MissingPermission")
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                BluetoothDevice device = result.getDevice();
                if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                    if (BLE_DEVICE_NAME.equals(device.getName())) {
                        bluetoothLeScanner.stopScan(leScanCallback);
                        connectedGatt = device.connectGatt(getApplicationContext(), false, new BluetoothGattCallback() {
                            @Override
                            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                                super.onConnectionStateChange(gatt, status, newState);
                                Log.i("NEW STATE", "State: " + newState);
                                gatt.requestMtu(517);
                                if (newState == BluetoothProfile.STATE_CONNECTED) {
                                    gatt.discoverServices();
                                } else {
                                    isConnectedFlag = false;
                                }
                            }

                            @Override
                            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                                super.onCharacteristicWrite(gatt, characteristic, status);
                                Log.i("Characteristic write", "Status: " + status);
                            }

                            @Override
                            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                                super.onServicesDiscovered(gatt, status);
                                BluetoothGattService service = gatt.getService(UUID.fromString(BLE_SERVICE_UUID));
                                if (service == null) {
                                    showErrorDialog("BLE Service not found");
                                    gatt.disconnect();
                                    return;
                                }
                                dataCharacteristic = service.getCharacteristic(UUID.fromString(BLE_DATA_CHARACTERISTIC_UUID));
                                if (dataCharacteristic == null) {
                                    showErrorDialog("BLE Characteristic not found");
                                    gatt.disconnect();
                                    return;
                                }
                                tokenCharacteristic = service.getCharacteristic(UUID.fromString(BLE_TOKEN_CHARACTERISTIC_UUID));
                                if (tokenCharacteristic == null) {
                                    showErrorDialog("BLE Characteristic not found");
                                    gatt.disconnect();
                                    return;
                                }
                                gatt.writeCharacteristic(tokenCharacteristic, BLE_SECURITY_TOKEN.getBytes(StandardCharsets.UTF_8), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                                isConnectedFlag = true;
                                Log.i("BLE", "Connected");
                                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connected", Toast.LENGTH_SHORT).show());
                            }
                        });
                    }
                }
            }
        };

        @SuppressLint("MissingPermission")
        void writeData(byte[] data) {
            if (!isConnectedFlag) {
                showErrorDialog("BLE device is not connected");
                return;
            }
            connectedGatt.writeCharacteristic(dataCharacteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            Log.i("WRITE", "WRITE");
        }
    }

    public static void showErrorDialog(String message) {
        activity.runOnUiThread(() -> new MaterialAlertDialogBuilder(activity).setTitle("Error").setMessage(message).setPositiveButton("OK", (dialog, which) -> dialog.dismiss()).show());
    }

    public PublicKey loadPublicKey() {
        String keyContent;
        try (InputStream inputStream = activity.getResources().openRawResource(R.raw.key)) {
            keyContent = new String(inputStream.readAllBytes());
        } catch (IOException e) {
            Log.e("Key loading", "Key file reading error");
            throw new RuntimeException(e);
        }

        keyContent = keyContent.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(keyContent);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);

        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            Log.e("Key loading", "Key parding error");
            throw new RuntimeException(e);
        }
    }

    public byte[] encrypt(byte[] data) {
        Cipher cipher;
        try {
            cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            Log.e("Encryption", "Unknown encryption system");
            throw new RuntimeException(e);
        }
        try {
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        } catch (InvalidKeyException e) {
            Log.e("Encryption", "Invalid key");
            throw new RuntimeException(e);
        }
        try {
            return cipher.doFinal(data);
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            Log.e("Encryption", "Invalid data");
            throw new RuntimeException(e);
        }
    }
}
