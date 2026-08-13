plugins {
    java
}

group = "id.speedplayer"
version = "1.0.0"

repositories {
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }

    jar {
        archiveBaseName.set("SpeedPlayer")
        archiveVersion.set("1.0.0")
    }
}
