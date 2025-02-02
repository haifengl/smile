plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    api(project(":core"))
}

tasks.jar {
    manifest {
        attributes["Automatic-Module-Name"] = "smile.nlp"
    }
}
