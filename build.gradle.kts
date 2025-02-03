subprojects {
    tasks.withType<Jar> {
        archiveBaseName.set("${rootProject.name}-${project.name}")
    }
}

