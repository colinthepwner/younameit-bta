plugins {
	alias(libs.plugins.loom)
	java
}

val lwjglNatives = resolveLwjglNatives()

val modVersion = "${providers.gradleProperty("mod_version").get()}+${libs.versions.bta.get()}"
val modGroup: Provider<String> = providers.gradleProperty("mod_group")
val modName: Provider<String> = providers.gradleProperty("mod_name")

val javaVersion: Provider<Int> = libs.versions.java.map { it.toInt() }

base.archivesName = modName
group = modGroup.get()
version = modVersion

loom {
	val btaChannel = libs.versions.btaChannel.get()
	val btaVersion = (if (btaChannel == "nightly") "" else "v") + libs.versions.bta.get()
	customMinecraftMetadata.set("https://downloads.betterthanadventure.net/bta-client/${btaChannel}/$btaVersion/manifest.json")
}

repositories {
	mavenCentral()
	maven("https://maven.fabricmc.net/") { name = "Fabric" }
	maven("https://maven.thesignalumproject.net/infrastructure") { name = "SignalumMavenInfrastructure" }
	maven("https://maven.thesignalumproject.net/releases") { name = "SignalumMavenReleases" }
	maven("https://maven.thesignalumproject.net/nightly") { name = "SignalumMavenNightly" }
	ivy("https://piston-data.mojang.com") {
		patternLayout { artifact("v1/[organisation]/[revision]/[module].jar") }
		metadataSources { artifact() }
	}
}

dependencies {
	minecraft("::${libs.versions.bta.get()}")

	// HalpLibe is NOT bundled — it is a normal mod dependency, declared in fabric.mod.json.
	// Bundling it would shadow whatever (possibly newer) HalpLibe the user already has installed.
	implementation(libs.loader)
	implementation(libs.halplibe)

	compileOnly(libs.bundles.btaLwjgl)
	compileOnly(libs.joml)
	compileOnly(libs.joml.primitives)
	compileOnly(libs.slf4jApi)

	localRuntime(libs.modMenu)
	runtimeClasspath(libs.clientJar)
	val lwjglVer = libs.versions.lwjgl.get()
	localRuntime(platform("org.lwjgl:lwjgl-bom:${lwjglVer}"))
	localRuntime("org.lwjgl:lwjgl::$lwjglNatives")
	localRuntime("org.lwjgl:lwjgl-glfw::$lwjglNatives")
	localRuntime("org.lwjgl:lwjgl-openal::$lwjglNatives")
	localRuntime("org.lwjgl:lwjgl-opengl::$lwjglNatives")
	localRuntime("org.lwjgl:lwjgl-stb::$lwjglNatives")
}

java {
	toolchain {
		languageVersion = javaVersion.map { JavaLanguageVersion.of(it) }
		vendor = JvmVendorSpec.ADOPTIUM
	}
	sourceCompatibility = JavaVersion.toVersion(javaVersion.get())
	targetCompatibility = JavaVersion.toVersion(javaVersion.get())
	withSourcesJar()
}

tasks {
	withType<JavaCompile>().configureEach {
		options.encoding = "UTF-8"
		sourceCompatibility = javaVersion.get().toString()
		targetCompatibility = javaVersion.get().toString()
		if (javaVersion.get() > 8) options.release = javaVersion
	}
	named<UpdateDaemonJvm>("updateDaemonJvm") {
		languageVersion = libs.versions.gradleJava.map { JavaLanguageVersion.of(it.toInt()) }
		vendor = JvmVendorSpec.ADOPTIUM
	}
	withType<JavaExec>().configureEach { defaultCharacterEncoding = "UTF-8" }
	withType<Javadoc>().configureEach { options.encoding = "UTF-8" }
	withType<Test>().configureEach { defaultCharacterEncoding = "UTF-8" }
	processResources {
		val resourceMap = mapOf(
			"version" to modVersion,
			"fabricloader" to libs.versions.loader.get(),
			"halplibe" to libs.versions.halplibe.get(),
			"java" to libs.versions.java.get(),
			"modmenu" to libs.versions.modMenu.get()
		)
		inputs.properties(resourceMap)
		duplicatesStrategy = DuplicatesStrategy.INCLUDE
		with(copySpec {
			from("src/main/resources/") {
				include("fabric.mod.json")
				include("*.mixins.json")
				expand(resourceMap)
			}
		})
	}
}

configurations.configureEach {
	exclude(group = "org.lwjgl.lwjgl")
	exclude(group = "net.java.jutils")
	exclude(group = "net.java.jinput")
	exclude(group = "net.sf.jopt-simple")
	exclude(group = "net.minecraft", module = "launchwrapper")
}

fun resolveLwjglNatives(): String {
	return Pair(
		System.getProperty("os.name")!!,
		System.getProperty("os.arch")!!
	).let { (name, arch) ->
		when {
			arrayOf("Linux", "SunOS", "Unit").any { name.startsWith(it) } ->
				if (arrayOf("arm", "aarch64").any { arch.startsWith(it) })
					"natives-linux${if (arch.contains("64") || arch.startsWith("armv8")) "-arm64" else "-arm32"}"
				else
					"natives-linux"
			arrayOf("Mac OS X", "Darwin").any { name.startsWith(it) } ->
				"natives-macos${if (arch.startsWith("aarch64")) "-arm64" else ""}"
			arrayOf("Windows").any { name.startsWith(it) } ->
				if (arch.contains("64"))
					"natives-windows${if (arch.startsWith("aarch64")) "-arm64" else ""}"
				else
					"natives-windows-x86"
			else -> throw Error("Unrecognized or unsupported platform.")
		}
	}
}

// ---------------------------------------------------------------------------
//  The obfuscated build
//
//  `younameit-BTA8.0.X-obf.jar`: the same mod with its internal class and
//  member names replaced. Produced beside the normal jar by `build`.
//
//  Two things are deliberately left readable, because the game resolves them
//  by name and a renamed name is simply a name that no longer resolves:
//
//    * the four classes listed in fabric.mod.json's entrypoints. Keeping them
//      means fabric.mod.json does not have to be rewritten to match, which is
//      the part of this kind of pipeline that usually breaks.
//    * everything inside the mixin package. Mixin matches @Shadow members to
//      the target class BY NAME, so a renamed @Shadow field is not a link
//      error -- it is a silent no-match that drops the injection.
// ---------------------------------------------------------------------------

val proguardTool: Configuration by configurations.creating {
	isCanBeConsumed = false
	isCanBeResolved = true
}

dependencies {
	proguardTool("com.guardsquare:proguard-base:7.7.0")
}

/** Named for the BTA line it targets rather than the exact patch it was built against. */
val obfJarFile: Provider<RegularFile> =
	layout.buildDirectory.file("libs/${modName.get()}-BTA8.0.X-obf.jar")

val obfWorkDir: Provider<Directory> = layout.buildDirectory.dir("obfuscation")

/** Classes the loader looks up by name. Renaming any of these makes the mod fail to start. */
val obfKeptClasses = listOf(
	"net.colin.younameit.YouNameIt",
	"net.colin.younameit.recipe.YniRecipes",
	"net.colin.younameit.client.YniClient"
)
val obfMixinPackage = "net.colin.younameit.mixin"

val obfuscatedJar = tasks.register<JavaExec>("obfuscatedJar") {
	group = "build"
	description = "Builds ${modName.get()}-BTA8.0.X-obf.jar beside the normal jar."

	val jarTask = tasks.named<Jar>("jar")
	dependsOn(jarTask)

	val inputJar = jarTask.flatMap { it.archiveFile }
	// The compile classpath carries BTA, the loader (and with it Mixin) and HalpLibe.
	// ProGuard needs their class hierarchy to tell which of our methods override a
	// library method and therefore must keep their name -- onInitialize() above all.
	val libraryJars = sourceSets.main.map { it.compileClasspath }
	val configFile = obfWorkDir.map { it.file("proguard.conf") }
	val mappingFile = obfWorkDir.map { it.file("mapping.txt") }

	inputs.file(inputJar)
	inputs.files(libraryJars)
	outputs.file(obfJarFile)
	outputs.file(mappingFile)

	classpath = proguardTool
	mainClass = "proguard.ProGuard"

	val toolchainHome = javaToolchains.launcherFor(java.toolchain).map { it.metadata.installationPath.asFile }

	doFirst {
		obfWorkDir.get().asFile.mkdirs()
		fun path(file: File) = file.absolutePath.replace('\\', '/')

		val lines = mutableListOf<String>()
		lines += "# Generated by the obfuscatedJar task. Edit build.gradle.kts, not this file."
		lines += "-injars '${path(inputJar.get().asFile)}'"
		lines += "-outjars '${path(obfJarFile.get().asFile)}'"

		// Java 9+ has no rt.jar, so the platform classes come from the toolchain's jmods.
		// java.desktop is not optional: BufferedImage and ImageIO are in the signature of
		// every texture class in this mod.
		val jmods = File(toolchainHome.get(), "jmods").listFiles()
			?.filter { it.name.endsWith(".jmod") }?.sortedBy { it.name }.orEmpty()
		check(jmods.isNotEmpty()) { "No jmods under ${toolchainHome.get()}; ProGuard has no platform classes." }
		jmods.forEach { lines += "-libraryjars '${path(it)}'(!**.jar;!module-info.class)" }
		libraryJars.get().files
			.filter { it.exists() && it.name.endsWith(".jar") }
			.sortedBy { it.name }
			.forEach { lines += "-libraryjars '${path(it)}'" }

		lines += ""
		lines += "-printmapping '${path(mappingFile.get().asFile)}'"
		// A renamer, not an optimiser. Shrinking would decide the item subclasses are
		// unreachable (nothing constructs them by name that ProGuard can see), and
		// optimising would move code a mixin injects into.
		lines += "-dontshrink"
		lines += "-dontoptimize"
		lines += "-dontnote"
		lines += "-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions"

		lines += ""
		lines += "# Entrypoints named in fabric.mod.json."
		obfKeptClasses.forEach { lines += "-keep class $it { *; }" }

		lines += ""
		// Both the class name and its members have to survive. younameit.mixins.json names the
		// mixin class textually, so a renamed class is a config entry that resolves to nothing;
		// and Mixin matches @Shadow members to the target BY NAME, so a renamed @Shadow field is
		// not a link error but a silent no-match that drops the injection entirely.
		lines += "# Mixin classes and their members are matched by name at load time."
		lines += "-keeppackagenames $obfMixinPackage"
		lines += "-keep class $obfMixinPackage.** { *; }"

		lines += ""
		lines += "# Enum identity survives values()/valueOf, which HalpLibe's builders rely on."
		lines += "-keepclassmembers enum * {"
		lines += "    public static **[] values();"
		lines += "    public static ** valueOf(java.lang.String);"
		lines += "}"

		configFile.get().asFile.writeText(lines.joinToString(System.lineSeparator()))
		args = listOf("@${path(configFile.get().asFile)}")
	}
}

tasks.named("assemble") { dependsOn(obfuscatedJar) }

// ---------------------------------------------------------------------------
// Auto-deploy into the local MultiMC BTA 8.0.1 instance after `build`.
// Skips silently when the instance folder is absent (e.g. another machine).
// ---------------------------------------------------------------------------
val multiMcModsDir = file("C:/Users/colin/OneDrive/Desktop/Games/MultiMC/instances/bta_fabric_instance_8.0.1/.minecraft/mods")

val deployToMultiMC = tasks.register("deployToMultiMC") {
	description = "Copies the built jar into the MultiMC BTA 8.0.1 mods folder."
	doLast {
		if (!multiMcModsDir.isDirectory) {
			logger.lifecycle("deployToMultiMC: instance folder not found, skipping: $multiMcModsDir")
			return@doLast
		}
		val jar = file("${layout.buildDirectory.get()}/libs/${base.archivesName.get()}-$version.jar")
		if (!jar.exists()) {
			logger.error("deployToMultiMC: built jar not found: $jar")
			return@doLast
		}
		try {
			multiMcModsDir.listFiles { f -> f.name.startsWith("younameit") && f.name.endsWith(".jar") }
				?.forEach { it.delete() }
			jar.copyTo(File(multiMcModsDir, jar.name), overwrite = true)
			logger.lifecycle("deployToMultiMC: deployed ${jar.name} -> $multiMcModsDir")
		} catch (e: Exception) {
			logger.warn("deployToMultiMC: could not update (is BTA running?). ${e.message}")
		}
	}
}

tasks.named("build") { finalizedBy(deployToMultiMC) }
