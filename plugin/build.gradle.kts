plugins {
	id("dev.arbjerg.lavalink.gradle-plugin") version "1.0.15"
}

base {
	archivesName = "lavasrc-plugin"
}

lavalinkPlugin {
	name = "lavasrc-plugin"
	apiVersion = "4.0.0"
	serverVersion = "4.0.5"
	configurePublishing = false
}



dependencies {
	implementation(project(":main"))
	implementation(project(":protocol"))
	compileOnly("dev.lavalink.youtube:common:1.1.0")
	compileOnly("com.github.topi314.lavasearch:lavasearch:1.0.0")
	implementation("com.github.topi314.lavasearch:lavasearch-plugin-api:1.0.0")
	implementation("com.github.topi314.lavalyrics:lavalyrics-plugin-api:1.0.0")
    implementation("me.xdrop:fuzzywuzzy:1.4.0")


    // Copy lyrics.kt from main
	project.project(":main").configurations["implementation"].dependencies.forEach {
		if (it.group == "dev.schlaubi.lyrics") {
			add("implementation", it)
		}
	}
}

tasks {
	jar {
		exclude("dev/schlaubi/lyrics/LyricsClient*")
		exclude("dev/schlaubi/lyrics/Lyrics_jvmKt.class")
	}
}

publishing {
	publications {
		create<MavenPublication>("maven") {
			from(components["java"])
			artifactId = base.archivesName.get()
		}
	}
}
