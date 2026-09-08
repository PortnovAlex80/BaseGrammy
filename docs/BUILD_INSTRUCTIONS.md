# Инструкция по сборке GrammarMate (BaseGrammy)

## Single runtime (2026-09-08)

There is exactly one runtime — the former `legacy` product, now the default
source set (v2 preview was removed, see `docs/architecture/decisions/006`).
Plain flavorless tasks are the product commands:

```cmd
gradlew.bat :app:assembleDebug
```

APK: `app\build\outputs\apk\debug\grammermate.apk`

## ⚠️ ВАЖНО: ИСПОЛЬЗУЙТЕ ПОЛНЫЙ ПУТЬ К INTELLIJ JBR (ОСНОВНОЕ РЕШЕНИЕ)

Если команда `java` не найдена в PATH, используйте полный путь к java.exe из IntelliJ JBR.
Это работает даже когда `java -version` не выполняется или Java отсутствует в PATH.

**РАБОЧАЯ КОМАНДА (проверено и подтверждено):**
```cmd
"C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin\java.exe" -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug
```

**Ключевые моменты:**
- Полный путь к java.exe из IntelliJ JBR работает даже когда команда `java` не доступна
- Это ОСНОВНОЕ решение для окружений Windows без Java в PATH
- Путь может отличаться в зависимости от версии IntelliJ
- См. раздел troubleshooting ниже, если этот путь не работает

---

## Предварительные требования

| Компонент | Версия | Примечание |
|-----------|--------|------------|
| Java JDK | 17 | Обязательно. Другие версии не поддерживаются. |
| Android SDK | API 34 | Платформа, build-tools и platform-tools. |
| Gradle | 8.9 | Загружается автоматически через wrapper. |

---

## 1. Установка Java 17

### Вариант A: Использовать Java из IntelliJ IDEA (рекомендуется)

Если у вас установлена IntelliJ IDEA, Java уже есть в комплекте:

```cmd
"C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin\java.exe" -version
```

Путь может отличаться в зависимости от версии IntelliJ.

### Вариант B: Установить отдельный JDK

Скачайте и установите JDK 17 (например, [Eclipse Temurin](https://adoptium.net/)). Убедитесь, что команда `java -version` выводит версию 17:

```cmd
java -version
```

Переменная `JAVA_HOME` должна указывать на каталог установки JDK.

---

## 2. Установка Android SDK (без Android Studio)

Если Android Studio не установлена, выполните установку SDK из командной строки.

### 2.1. Загрузка command-line tools

Скачайте архив с https://developer.android.com/studio#command-tools (раздел "Command line tools only").

### 2.2. Установка

Распакуйте в удобный каталог, например `C:\Users\user\Android\Sdk\cmdline-tools\latest\`.

Установите необходимые компоненты:

```cmd
sdkmanager --install "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

Принимите лицензии:

```cmd
sdkmanager --licenses
```

### 2.3. Настройка local.properties

Создайте файл `local.properties` в корне проекта со следующим содержимым:

```properties
sdk.dir=C\\:\\Users\\user\\AppData\\Local\\Android\\Sdk
```

Укажите свой реальный путь к SDK. Этот файл не коммитится в репозиторий.

Альтернативно — установите переменную окружения `ANDROID_HOME`:

```cmd
set ANDROID_HOME=C:\Users\user\AppData\Local\Android\Sdk
```

---

## 3. Проблема с Gradle wrapper на Windows

Проект использует Gradle 8.9, поставляемый в формате multi-JAR wrapper. Стандартный скрипт `gradlew.bat` может завершаться с ошибкой:

```
NoClassDefFoundError: org/gradle/wrapper/IDownload
```

### Причина

Начиная с Gradle 8.9, wrapper состоит из нескольких JAR-файлов:

```
gradle/wrapper/
  gradle-wrapper.jar
  gradle-wrapper-shared.jar
  gradle-cli.jar
```

Стандартный скрипт `gradlew` загружает только `gradle-wrapper.jar`, что приводит к ошибке.

### Решение

Запускайте сборку напрямую через Java, указав все три JAR в classpath:

**С Java из IntelliJ IDEA (рекомендуется):**
```cmd
"C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin\java.exe" -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug
```

**С системным Java:**
```cmd
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug
```

> **Примечание:** Путь к IntelliJ JBR может отличаться в зависимости от установленной версии. Проверьте наличие java.exe в каталоге `jbr/bin/` вашей установки IntelliJ.

Для удобства можно создать файл `build.bat` в корне проекта (укажите свой путь к Java):

```bat
@echo off
set JAVA_EXE=C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin\java.exe
"%JAVA_EXE%" -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain %*
```

После этого можно использовать `build.bat assembleDebug` вместо `gradlew assembleDebug`.

---

## 4. Некириллический путь проекта

Если каталог проекта содержит нелатинские символы (например, `Разработка`), Gradle может выдать ошибку проверки пути. В файле `gradle.properties` уже добавлено:

```properties
android.overridePathCheck=true
```

Если этой строки нет — добавьте её. Это отключает проверку на не-ASCII символы в пути проекта.

---

## 5. Сборка

### Debug APK

```cmd
build.bat assembleDebug
```

Готовый APK: `app\build\outputs\apk\debug\grammermate.apk`

### Release APK

```cmd
build.bat assembleRelease
```

### Запуск тестов

```cmd
build.bat test
```

Запуск конкретного тестового класса:

```cmd
build.bat test --tests "com.alexpo.grammermate.data.FlowerCalculatorTest"
```

Запуск конкретного тестового метода:

```cmd
build.bat test --tests "com.alexpo.grammermate.data.FlowerCalculatorTest.testBloomState"
```

### Очистка

```cmd
build.bat clean
```

---

## 6. Валидация учебных пакетов

Для проверки lesson pack ZIP-архивов:

```cmd
python tools/pack_validator/pack_validator.py path/to/pack.zip
```

---

## Краткая шпаргалка

```cmd
:: 1. Проверить Java (из IntelliJ или системная)
"C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin\java.exe" -version
:: или просто: java -version

:: 2. Проверить Android SDK (если не настроен local.properties)
echo %ANDROID_HOME%

:: 3. Собрать debug APK (одна строка, без переноса)
"C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin\java.exe" -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug

:: 4. Забрать APK
:: app\build\outputs\apk\debug\grammermate.apk
```

---

## Решение проблем

### Проблема: команда `java` не найдена или `java -version` не выполняется
**Решение:** Используйте полный путь к IntelliJ JBR вместо команды `java`
```cmd
"C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin\java.exe" -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug
```

### Проблема: путь к IntelliJ JBR отличается от документированного
**Решение:** Найдите установку IntelliJ и locate java.exe в каталоге jbr/bin/
- Обычные расположения:
  - `C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.1\jbr\bin\java.exe`
  - `C:\Program Files\JetBrains\IntelliJ IDEA Ultimate\jbr\bin\java.exe`
  - `C:\Program Files (x86)\JetBrains\IntelliJ IDEA *\jbr\bin\java.exe`

### Проблема: NoClassDefFoundError: org/gradle/wrapper/IDownload
**Решение:** Убедитесь, что все три JAR указаны в classpath (см. раздел 3 выше)

### Проблема: Сборка не выполняется несмотря на правильную настройку Java
**Решение:** Проверьте путь к Android SDK в local.properties и убедитесь, что установлен API 34

---

## 7. Установка APK на телефон

### Вариант A: Через USB-кабель (рекомендуется)

1. **Включите Developer Options** на телефоне:
   - Настройки → О телефоне → нажать "Номер сборки" 7 раз
2. **Включите USB Debugging** в Developer Options
3. Подключите телефон по USB-кабелю
4. Установите APK:
   ```cmd
   adb install app\build\outputs\apk\debug\grammermate.apk
   ```
5. Если приложение уже установлено — обновление с заменой:
   ```cmd
   adb install -r app\build\outputs\apk\debug\grammermate.apk
   ```

### Вариант B: По Wi-Fi (без USB-кабеля)

1. Телефон и ПК должны быть в одной Wi-Fi сети
2. Подключите USB один раз и выполните:
   ```cmd
   adb tcpip 5555
   adb connect <IP_ТЕЛЕФОНА>:5555
   ```
3. Отключите USB-кабель, затем установите:
   ```cmd
   adb install app\build\outputs\apk\debug\grammermate.apk
   ```

### Вариант C: Прямой перенос файла (без adb)

1. Скопируйте `app\build\outputs\apk\debug\grammermate.apk` на телефон (USB-накопитель, Google Drive, Telegram и т.д.)
2. На телефоне откройте APK-файл через приложение "Файлы"
3. Разрешите "Установку из неизвестных источников" при запросе
4. Нажмите **Установить**

---

## 8. Запуск приложения на подключённом телефоне

```cmd
:: Запустить приложение
adb shell am start -n com.alexpo.grammermate/.MainActivity

:: Логи в реальном времени
adb logcat -s "GrammerMate" "AndroidRuntime"

:: Удалить приложение
adb uninstall com.alexpo.grammermate
```

### Проблемы с adb

**ВАЖНО:** На данной машине `adb` не находится в PATH. Используйте полный путь:
```cmd
set ADB=C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe
```
Затем заменяйте все `adb` в командах ниже на `%ADB%`.

| Проблема | Решение |
|----------|---------|
| `adb` не найден | Используйте полный путь: `C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe` |
| Устройство не отображается в `adb devices` | Включите USB Debugging; проверьте драйверы; попробуйте другой USB-кабель |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Используйте `adb install -r` или сначала `adb uninstall com.alexpo.grammermate` |

### Проблема: FileAlreadyExistsException при пересборке

Если при повторной сборке Gradle выдаёт:
```
kotlin.io.FileAlreadyExistsException: app-debug.apk -> grammermate.apk: Tried to overwrite the destination, but failed to delete it.
```

**Решение:** Удалите старый APK перед сборкой:
```cmd
del app\build\outputs\apk\debug\grammermate.apk
build.bat assembleDebug
```
