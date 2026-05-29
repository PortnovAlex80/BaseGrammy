# Руководство по деплою APK на Android устройство

## 🎯 Созданные агенты

В проекте теперь есть два специализированных агента для работы с APK:

### 1. **apk-builder** - Сборка APK
- **Назначение:** Сборка debug/release APK с учетом Windows Gradle wrapper workaround
- **Когда использовать:** Нужно собрать APK для тестирования или публикации
- **Вызов:** "Используй apk-builder агента для сборки debug APK"

### 2. **apk-deployer** - Передача APK на устройство
- **Назначение:** Исследование и выполнение передачи APK на Android устройство
- **Когда использовать:** APK собран и нужно установить на устройство
- **Вызов:** "Используй apk-deployer агента для передачи APK на телефон"

## 📱 Методы передачи APK

### Текущий статус окружения:

✅ **APK файл существует:** `app/build/outputs/apk/debug/grammermate.apk` (119MB)
✅ **Python 3.13.7 доступен** - можно использовать HTTP server
❌ **ADB не установлен** - нужно установить или использовать альтернативные методы

### Рекомендуемые методы (в порядке приоритета):

#### 1. HTTP Server (РЕКОМЕНДУЕТСЯ для вашего случая)

**Запустите HTTP server:**
```bash
cd app/build/outputs/apk/debug/
python -m http.server 8000
```

**На Android устройстве:**
1. Подключитесь к той же WiFi сети как компьютер
2. Откройте браузер и перейдите на: `http://192.168.1.176:8000/grammermate.apk`
3. Файл начнёт скачиваться
4. После скачивания откройте файл и установите

**Преимущества:**
- Работает без проводов
- Быстрая передача в локальной сети
- Не требует установки ADB
- Подходит для файлов любого размера

#### 2. Установка ADB (для development)

**Скачайте Android SDK Platform Tools:**
- Скачать: https://developer.android.com/studio#platform-tools
- Распакуйте в папку, например `C:\Android\platform-tools`

**Добавьте в PATH:**
```cmd
set PATH=%PATH%;C:\Android\platform-tools
```

**Проверьте:**
```cmd
adb devices
```

**Установите APK:**
```cmd
adb install app/build/outputs/apk/debug/grammermate.apk
```

#### 3. Bluetooth Transfer

**Для этого метода:**
1. Включите Bluetooth на обоих устройствах
2. Спарьте устройства
3. Отправьте APK через Bluetooth
4. Примите и установите на Android

**Ограничения:** Может быть медленно для 119MB файла

#### 4. Cloud Storage

**Google Drive/Dropbox:**
```cmd
# Скопируйте APK в синхронизируемую папку
copy app\build\outputs\apk\debug\grammermate.apk "%USERPROFILE%\Google Drive\"
copy app\build\outputs\apk\debug\grammermate.apk "%USERPROFILE%\Dropbox\"
```

**На Android устройстве:**
1. Откройте Google Drive/Dropbox app
2. Найдите APK файл
3. Скачайте и установите

**Преимущества:** Работает удаленно, нет ограничений по расстоянию

#### 5. Direct USB Copy

**Если Android устройство подключен как MTP:**
1. Подключите Android устройство к ПК via USB
2. Выберите "File Transfer" mode
3. Скопируйте APK в Download folder на устройстве
4. На устройстве откройте File Manager и установите APK

## 🚀 Быстрый старт

### Использование агентов:

**Сборка APK:**
```
Используй apk-builder агента для сборки debug APK
```

**Передача на устройство:**
```
Используй apk-deployer агента для передачи grammermate.apk на телефон
```

### Прямая команда для HTTP server:

```bash
cd app/build/outputs/apk/debug/
python -m http.server 8000
```

Затем на Android устройстве откройте: `http://192.168.1.176:8000/grammermate.apk`

## 📋 Полный автоматизированный workflow

Если ADB будет установлен:

```bash
# 1. Сборка APK
"C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin\java.exe" -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug

# 2. Установка на устройство
adb install -r app/build/outputs/apk/debug/grammermate.apk

# 3. Запуск приложения (опционально)
adb shell monkey -p com.alexpo.grammermate -c android.intent.action.MAIN
```

## ⚠️ Важные заметки

- APK файл имеет размер 119MB - учитывайте это при выборе метода передачи
- Email/мессенджеры не подойдут из-за ограничений на размер файла
- HTTP server method требует нахождения обоих устройств в одной WiFi сети
- ADB - наиболее надежный метод для development, но требует установки
- При первом установке может понадобиться включить "Install from unknown sources" в Android security settings

## 🔧 Устранение проблем

### HTTP server не доступен
- Проверьте firewall настройки
- Убедитесь что оба устройства в одной сети
- Попробуйте другой порт (8080, 8888)

### Установка APK блокирована
- Settings → Security → Unknown sources → Включите
- Settings → Apps → Special access → Install unknown apps → Разрешите browser/file manager

### Приложение не устанавливается
- Проверьте что APK файл не поврежден
- Попробуйте пересобрать APK
- Убедитесь что Android версия >= 7.0 (API 24)

## 📚 Дополнительные ресурсы

- [Инструкция по сборке APK](java.txt)
- [Build Instructions](docs/BUILD_INSTRUCTIONS.md)
- [Создание локальных агентов](.claude/HOW_TO_CREATE_LOCAL_AGENTS.md)
