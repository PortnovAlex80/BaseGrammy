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

## Другие команды

```cmd
:: Release APK
build.bat assembleRelease

:: Запустить тесты
build.bat test

:: Очистить
build.bat clean

:: Конкретный тест
build.bat test --tests "com.alexpo.grammermate.ui.VerbDrillSessionCardRegressionTest"
```

---

## Почему такая сложная команда?

Gradle 8.9 на Windows требует все 3 JAR файла в classpath. Стандартный `gradlew.bat` не работает, поэтому используем прямой вызов Java с полным classpath.

---

## Проблемы?

### "NoClassDefFoundError"
→ Используй полную команду с указанием всех 3 JAR файлов

### "JAVA_HOME is not set"
→ Укажи полный путь к java.exe, как в примерах выше

### "sdk.dir not found"
→ Создай файл `local.properties` в корне:
```properties
sdk.dir=C\\:\\Users\\user\\Android\\Sdk
```
(укажи свой путь к Android SDK)

---

## Подробная документация

См. `docs/BUILD_INSTRUCTIONS.md` для детальной инструкции по установке Java, Android SDK и настройке окружения.
