// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

// Chuyển toàn bộ thư mục build ra khỏi OneDrive (vào thư mục User/AndroidBuilds)
val buildDirOut = file("${System.getProperty("user.home")}/AndroidBuilds/${rootProject.name}")

allprojects {
    layout.buildDirectory.set(buildDirOut.resolve(project.name))
}
