plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// Headless verification: ./gradlew runServer21 -Pbellow.selftest=true
if (project.findProperty("bellow.selftest") == "true") {
    tasks.withType<JavaExec>().configureEach {
        if (name.startsWith("runServer")) {
            systemProperty("akashicbellow.selftest", "true")
            systemProperty("akashicbellow.selftest.exit", "true")
            systemProperty("fml.queryResult", "confirm")
            systemProperty("akashicbellow.selftest.phase", project.findProperty("bellow.phase") ?: "full")
        }
    }
}

// Headless client verification (needs a display, e.g. xvfb-run): ./gradlew runClient21 -Pbellow.clienttest=true
if (project.findProperty("bellow.clienttest") == "true") {
    tasks.withType<JavaExec>().configureEach {
        if (name.startsWith("runClient")) {
            systemProperty("akashicbellow.clienttest", "true")
            systemProperty("fml.queryResult", "confirm")
        }
    }
}
