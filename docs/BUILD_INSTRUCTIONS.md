# Инструкция по сборке GrammarMate (BaseGrammy)

## Предварительные требования

| Компонент | Версия | Примечание |
|-----------|--------|------------|
| Java JDK | 17 | Обязательно. Другие версии не поддерживаются. |
| Android SDK | API 34 | Платформа, build-tools и platform-tools. |
| Gradle | 8.9 | Загружается автоматически через wrapper. |

---

## 1. Установка Java 17

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
sdk.dir=C:\\Users\\user\\Android\\Sdk
```

Укажите свой реальный путь к SDK. Этот файл не коммитится в репозиторий.

Альтернативно — установите переменную окружения `ANDROID_HOME`:

```cmd
set ANDROID_HOME=C:\Users\user\Android\Sdk
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

```cmd
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug
```

Для удобства можно создать файл `build.bat` в корне проекта:

```bat
@echo off
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain %*
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
:: 1. Проверить Java
java -version

:: 2. Проверить Android SDK
echo %ANDROID_HOME%

:: 3. Собрать debug APK
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug

:: 4. Забрать APK
:: app\build\outputs\apk\debug\grammermate.apk
```
