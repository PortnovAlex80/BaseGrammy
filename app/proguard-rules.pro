# Sherpa-ONNX JNI bindings
-keep class com.k2fsa.sherpa.onnx.** { *; }

# Kotlin coroutines
-keep class kotlin.coroutines.** { *; }

# SnakeYAML
-keep class org.yaml.snakeyaml.** { *; }
-dontwarn org.yaml.snakeyaml.**

# Keep data models (serialized to YAML)
-keep class com.alexpo.grammermate.data.Models** { *; }
-keep class com.alexpo.grammermate.data.LessonPackManifest** { *; }
