plugins {
    // Apply the common convention plugin for shared build configuration between library and application projects.
    id("buildlogic.java-common-conventions")
    id("buildlogic.maven-publish-conventions")

    // Apply the java-library plugin for API and implementation separation.
    `java-library`
}

tasks.jar {
    manifest {
        attributes["Automatic-Module-Name"] = "${rootProject.name}.${project.name}"
    }
}
