# Сборка APK GrammarMate

## Быстрая команда (одна строка)

```cmd
build.bat assembleDebug
```

APK будет здесь: `app\build\outputs\apk\debug\grammermate.apk`

---

## Если build.bat не работает

### Проверь Java

```cmd
java -version
```

Должна быть версия 17. Если нет или другая версия:
- Используй Java из IntelliJ IDEA: `"C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin\java.exe" -version`
- Или скачай JDK 17: https://adoptium.net/

### Полная команда для сборки

**С Java из IntelliJ (рекомендуется):**
```cmd
"C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin\java.exe" -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug
```

**С системным Java:**
```cmd
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug
```

---

## Все команды сборки

```cmd
build.bat assembleDebug          :: Debug APK
build.bat assembleRelease        :: Release APK
build.bat test                   :: Запустить все тесты
build.bat test --tests "...::SomeTest"  :: Конкретный тест
build.bat clean                  :: Очистить сборку
```

---

## Путь к Android SDK

Реальный путь (из `local.properties`):
```
C:\Users\user\AppData\Local\Android\Sdk
```

Если `local.properties` отсутствует, создай его в корне проекта:
```properties
sdk.dir=C\\:\\Users\\user\\AppData\\Local\\Android\\Sdk
```

---

## Установка на телефон

**ВАЖНО:** `adb` не в PATH. Используй полный путь:
```cmd
set ADB=C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe
```

### Вариант 1: Через USB-кабель (рекомендуется)

1. **Включи Developer Options** на телефоне:
   - Настройки → О телефоне → нажми "Номер сборки" 7 раз
2. **Включи USB Debugging** в Developer Options
3. Подключи телефон по USB-кабелю
4. Проверь, что устройство видно:
   ```cmd
   %ADB% devices
   ```
5. Установи APK:
   ```cmd
   %ADB% install -r app\build\outputs\apk\debug\grammermate.apk
   ```

### Вариант 2: По Wi-Fi (без USB)

1. Телефон и ПК в одной Wi-Fi сети
2. Подключи USB один раз и запусти:
   ```cmd
   %ADB% tcpip 5555
   %ADB% connect <IP_ТЕЛЕФОНА>:5555
   ```
3. Отключи USB-кабель
4. Установи APK:
   ```cmd
   %ADB% install -r app\build\outputs\apk\debug\grammermate.apk
   ```

### Вариант 3: Просто скопировать файл (без adb)

1. Скопируй `app\build\outputs\apk\debug\grammermate.apk` на телефон (USB, Google Drive, Telegram и т.д.)
2. Открой APK-файл в приложении "Файлы" на телефоне
3. Разреши "Установку из неизвестных источников" если появится запрос
4. Нажми **Установить**

---

## Запуск приложения на подключённом телефоне

```cmd
:: Запустить приложение
%ADB% shell am start -n com.alexpo.grammermate/.MainActivity

:: Логи в реальном времени
%ADB% logcat -s "GrammerMate" "AndroidRuntime"

:: Удалить приложение
%ADB% uninstall com.alexpo.grammermate
```

---

## Почему такая сложная команда?

Gradle 8.9 на Windows требует все 3 JAR файла в classpath. Стандартный `gradlew.bat` не работает, поэтому используем прямой вызов Java с полным classpath.

---

## Проблемы и решения

| Проблема | Решение |
|----------|---------|
| `NoClassDefFoundError` | Убедись, что все 3 JAR указаны в classpath (см. примеры) |
| `JAVA_HOME is not set` | Укажи полный путь к java.exe |
| `sdk.dir not found` | Создай `local.properties`: `sdk.dir=C\\:\\Users\\user\\AppData\\Local\\Android\\Sdk` |
| `adb` не найден | Полный путь: `C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe` |
| `FileAlreadyExistsException` при пересборке | Удали старый APK перед сборкой: `rm app\build\outputs\apk\debug\grammermate.apk` |
| Устройство не найдено | Проверь USB Debugging; выполни `%ADB% devices` |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Используй `%ADB% install -r` или сначала `%ADB% uninstall com.alexpo.grammermate` |

---

## Подробная документация

См. `docs/BUILD_INSTRUCTIONS.md` для детальной инструкции по установке Java, Android SDK и настройке окружения.
