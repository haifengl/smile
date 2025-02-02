plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    api(project(":base"))
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.18.2")
    implementation("org.swinglabs:swingx:1.6.1")
}

tasks.jar {
    manifest {
        attributes["Automatic-Module-Name"] = "smile.plot"
    }
}
