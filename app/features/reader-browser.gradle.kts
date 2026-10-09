check(file("features/networking.gradle.kts").isFile) { "Reader requires networking transport" }
dependencies { add("implementation", "androidx.lifecycle:lifecycle-runtime-compose:2.9.4") }
