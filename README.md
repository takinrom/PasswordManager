# Аппаратный менеджер паролей
Такин Роман\
Проект для ВСОШ 2024/2025 

На данный момент реализован не весь заявленный функционал, но проект уже работоспособен.

### [Видео - демонстрация работы проекта](https://drive.google.com/file/d/1oHy7WgTBFhv0NKYMoyjn2fBdEqQDrZJ6/view?usp=sharing)

Система состоит из 3 частей. Каждая собирается и запускается отдельно

## 1. Сервер
### Настройка

```
$ cd Server/src
$ python3 -m venv venv
$ source ./venv/bin/activate
$ pip3 install -r requirements.txt
```
Отредактировать: 
```
app.py:
    ...  
    SECRET = '<Server Auth token>'
    ...
start.sh:
    ... --bind=0.0.0.0:<port> ...
```
#### Необходимо положить свой сертификат и ключ к нему в src/cert.pem и src/key.pem
### Запуск
```
$ ./start.sh 
```

## 2. Микроконтроллер 
Разработка велась для [LILYGO T-DONGLE S3](https://lilygo.cc/products/t-dongle-s3), но можно использовать и другие платы
### Настройка
Отредактировать: 
``` 
main.cpp:
    ...
    String TOKEN = "BLE Auth token";
    ...
    NimBLEDevice::init("<BLE device name>");
    ...
```
### Сборка
Достатьчно открыть проект в PlatformIO и нажать Собрать & Загрузить

Для генерации новых ключей необходимо раскоментировать ```#define NEW_KEY``` в main.cpp и прошить устройство. После генерации ключи сохранятся в память микроконтроллера. Для использования сохраненных ключей необходимо закомментировать ```#define NEW_KEY``` и снова прошить устройство.

### !!!!!!!!!!!
Перед использованием микроконтроллер и смартфон необходимо связать, например через [nRF Connect](https://play.google.com/store/apps/details?id=no.nordicsemi.android.mcp&pcampaignid=web_share)  

## 3. Android приложение

### Настройка 
Отредактировать: 
```
MainActivity.java:
    private final String SERVER_URL = "https://<ip>:<port>";
    private final String SECRET = "<Server Auth token>";
    private final String BLE_SECURITY_TOKEN = "<BLE Auth token>";
    private final String BLE_DEVICE_NAME = "<BLE device name>";
```
#### Необходимо положить сертификат сервера в src/main/res/raw/cert.pem и открытый ключ шифрования паролей(выводится микроконтроллером в Serial при каждом запуске) в src/main/res/raw/key.pem

### Сборка 
Рекомендуется собирать в Android studio

