val modName: Provider<String> = providers.gradleProperty("mod_name")
rootProject.name = modName.get()
pluginManagement {
	fun isRepoHealthy(url: String): Boolean {
		var connection: javax.net.ssl.HttpsURLConnection? = null
		return try {
			connection = java.net.URI(url).toURL().openConnection() as javax.net.ssl.HttpsURLConnection
			connection.requestMethod = "HEAD"
			connection.connectTimeout = 2000
			connection.readTimeout = 2000
			connection.instanceFollowRedirects = true
			connection.connect()
			connection.responseCode in 200..399
		} catch (_: Exception) {
			false
		} finally {
			connection?.disconnect()
		}
	}
	fun repoUrlWithFallbacks(candidates: List<String>): String {
		val chosen = candidates.firstOrNull { isRepoHealthy(it) } ?: candidates.first()
		logger.lifecycle("Using \"{}\" as the Fabric repository.", chosen)
		return chosen
	}
	repositories {
		maven(
			repoUrlWithFallbacks(
				listOf(
					"https://maven.fabricmc.net",
					"https://maven2.fabricmc.net",
					"https://maven3.fabricmc.net"
				)
			)
		) { name = "Fabric" }
		maven("https://maven.thesignalumproject.net/infrastructure") { name = "SignalumMavenInfrastructure" }
		mavenCentral()
		gradlePluginPortal()
	}
	val foojayResolverVersion = providers.gradleProperty("foojay_resolver_version")
	plugins {
		id("org.gradle.toolchains.foojay-resolver-convention").version(foojayResolverVersion.get())
	}
}
plugins {
	id("org.gradle.toolchains.foojay-resolver-convention")
}
