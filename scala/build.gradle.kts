plugins {
    id("buildlogic.scala-library-conventions")
}

sourceSets {
    main {
        scala {
            setSrcDirs(listOf("src/main/scala", "src/main/scala-2.13"))
        }
    }
}

dependencies {
    val scalaBinVersion = "2.13"
    implementation(project(":nlp"))
    implementation(project(":plot"))
    implementation(project(":json"))
    implementation("org.scala-lang.modules:scala-xml_$scalaBinVersion:2.3.0")
}
