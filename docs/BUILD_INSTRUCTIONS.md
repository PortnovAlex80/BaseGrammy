# Инструкция по сборке GrammarMate (BaseGrammy)

## Single runtime (2026-09-08)

There is exactly one runtime — the former `legacy` product, now the default
source set (v2 preview was removed, see `docs/architecture/decisions/006`).
Plain flavorless tasks are the product commands:

```cmd
gradlew.bat :app:assembleDebug
```

APK: `app\build\outputs\apk\debug\grammermate.apk`

## Полный JDK и стандартный wrapper (2026-10-05)

Установите `JAVA_HOME` на полный JDK с `jlink`. Локально проверен Corretto 21.0.8,
в CI используется JDK 17; целевой bytecode остаётся Java 17. Установленный IntelliJ
JBR не содержит `jlink` и для этой сборки не подходит.

```cmd
set JAVA_HOME=C:\Users\user\.jdks\corretto-21.0.8
gradlew.bat :app:assembleDebug
gradlew.bat :app:testDebugUnitTest :app:lintDebug --no-parallel --max-workers=1
```

В Git Bash и Linux используется `./gradlew`. Оба скрипта подключают три wrapper JAR;
запуск через полный Java classpath сохранён ниже как диагностический резервный вариант.

---

## Предварительные требования

| Компонент | Версия | Примечание |
|-----------|--------|------------|
| Java JDK | 17 / 21 | Полный JDK с jlink; локально Corretto 21.0.8, CI 17. |
| Android SDK | API 35 | Платформа для compileSdk, build-tools и platform-tools. |
| Gradle | 8.9 | Загружается автоматически через wrapper. |

---

## 1. Установка Java 17

### Вариант A: Проверенный локальный JDK

```cmd
"C:\Users\user\.jdks\corretto-21.0.8\bin\java.exe" -version
"C:\Users\user\.jdks\corretto-21.0.8\bin\jlink.exe" --version
```

Путь может отличаться в зависимости от установленного JDK.

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
sdkmanager --install "platforms;android-35" "build-tools;34.0.0" "platform-tools"
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

## 3. Стандартный wrapper и резервный запуск Java

Используйте `gradlew.bat` в Windows или `./gradlew` в Git Bash/Linux. Скрипты
подключают `gradle-wrapper.jar`, `gradle-wrapper-shared.jar` и `gradle-cli.jar` из
`gradle/wrapper`. Ошибка `NoClassDefFoundError: org/gradle/wrapper/IDownload` в старом
checkout означает неполный classpath; обновите скрипты wrapper.

Эквивалентный резервный запуск в Windows с настроенным полным JDK:
```cmd
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug
```

Разделитель classpath — `;` для Windows JVM и `:` для Unix JVM. Создавать отдельный
`build.bat` не требуется: достаточно `JAVA_HOME` и стандартного wrapper.

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
gradlew.bat :app:assembleDebug
```

Готовый APK: `app\build\outputs\apk\debug\grammermate.apk`

### Release APK

```cmd
gradlew.bat :app:assembleRelease
```

### Запуск тестов

```cmd
gradlew.bat :app:testDebugUnitTest --no-parallel --max-workers=1
```

Запуск конкретного тестового класса:

```cmd
gradlew.bat :app:testDebugUnitTest --tests "com.alexpo.grammermate.data.FlowerCalculatorTest" --no-parallel --max-workers=1
```

Запуск конкретного тестового метода:

```cmd
gradlew.bat :app:testDebugUnitTest --tests "com.alexpo.grammermate.data.FlowerCalculatorTest.calculate_nullMastery_returnsSeedState" --no-parallel --max-workers=1
```

### Очистка

```cmd
gradlew.bat clean
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
:: 1. Проверить Java (полный JDK)
"C:\Users\user\.jdks\corretto-21.0.8\bin\java.exe" -version
:: или просто: java -version

:: 2. Проверить Android SDK (если не настроен local.properties)
echo %ANDROID_HOME%

:: 3. Собрать debug APK (одна строка, без переноса)
gradlew.bat :app:assembleDebug

:: 4. Забрать APK
:: app\build\outputs\apk\debug\grammermate.apk
```

---

## Решение проблем

### Проблема: команда `java` не найдена или `java -version` не выполняется
**Решение:** Укажите полный JDK с `jlink` в `JAVA_HOME`:
```cmd
set JAVA_HOME=C:\Users\user\.jdks\corretto-21.0.8
gradlew.bat :app:assembleDebug
```

### Проблема: отсутствует jlink
**Решение:** Используйте полный Corretto JDK из примера выше. Проверьте наличие
`JAVA_HOME/bin/jlink.exe`; установленный IntelliJ JBR для этой сборки недостаточен.

### Проблема: NoClassDefFoundError: org/gradle/wrapper/IDownload
**Решение:** Убедитесь, что все три JAR указаны в classpath (см. раздел 3 выше)

### Проблема: Сборка не выполняется несмотря на правильную настройку Java
**Решение:** Проверьте путь к Android SDK в local.properties и убедитесь, что установлен API 35

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
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Установите APK с той же подписью. Удаление приложения позволит сменить подпись, но удалит его данные и внутренний backup; предварительно сохраните нужные данные вне приложения. |

### Проблема: FileAlreadyExistsException при пересборке

Если при повторной сборке Gradle выдаёт:
```
kotlin.io.FileAlreadyExistsException: app-debug.apk -> grammermate.apk: Tried to overwrite the destination, but failed to delete it.
```

**Решение:** Удалите старый APK перед сборкой:
```cmd
del app\build\outputs\apk\debug\grammermate.apk
gradlew.bat :app:assembleDebug
```
