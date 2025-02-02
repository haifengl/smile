plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    api(project(":base"))
}

tasks.jar {
    manifest {
        attributes["Automatic-Module-Name"] = "smile.core"
    }
}
