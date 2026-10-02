/*
 * L51-L55 dependencies: Actuator, Prometheus e tracing usam o BOM do Boot; a ponte de métricas do circuito tem
 *     versão explícita porque não é gerenciada, sem exportador de traces (add-observability design D11).
 * L57-L58 dependencies: os módulos de teste habilitam métricas e tracing reais nos testes de observabilidade.
 * L103 localAwsCredentials: o DynamoDB Local aceita qualquer par não vazio; credenciais de teste ficam no ambiente
 *     para exercitar a cadeia padrão do SDK, sem credenciais no código de produção (add-observability design D9).
 * L105-L107 tasks.bootRun: o desenvolvimento local usa a mesma cadeia de credenciais da imagem.
 * L109-L151 tasks.withType<Test>: os dois source sets recebem as credenciais locais, sem depender do perfil AWS
 *     pessoal de quem executa os testes.
 * L61 dependencies: expõe no compile de teste o parser já transitivo do Konsist para verificar referências
 *     qualificadas pela API pública, sem um parser próprio (enforce-hexagonal-architecture design D2).
 * L154 tasks.test.systemProperty: o worker Gradle não expõe os jars em java.class.path; a política usa o
 *     classpath real para reconhecer funções qualificadas de frameworks, sem whitelist manual de namespaces.
 * L153-L161 tasks.test: os testes estáticos leem fontes e documentos fora do classpath; declarar as árvores
 *     preserva a invalidação por arquivo novo/renomeado/removido e o reaproveitamento sem mudanças.
 *
 * Enunciado: O que será avaliado → Production readiness
 */
plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
	jacoco
}

group = "br.com.itau"
version = "0.0.1-SNAPSHOT"
description = "itau-code-challange-starter-kit"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation(platform("software.amazon.awssdk:bom:2.46.7"))
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	implementation("software.amazon.awssdk:dynamodb")
	implementation("software.amazon.awssdk:apache5-client")
	implementation("org.springframework.boot:spring-boot-starter-kafka")
	implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.4.0")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")
	implementation("io.micrometer:micrometer-tracing-bridge-otel")
	implementation("io.github.resilience4j:resilience4j-micrometer:2.4.0")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-micrometer-metrics-test")
	testImplementation("org.springframework.boot:spring-boot-micrometer-tracing-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("com.lemonappdev:konsist:0.17.3")
	testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

sourceSets {
	create("integrationTest") {
		kotlin.srcDir("src/integrationTest/kotlin")
		resources.srcDir("src/integrationTest/resources")
		compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
		runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
	}
}

configurations["integrationTestImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

val integrationTest =
	tasks.register<Test>("integrationTest") {
		description = "Runs integration tests against live infrastructure (start it first with `make db-up`)."
		group = "verification"
		testClassesDirs = sourceSets["integrationTest"].output.classesDirs
		classpath = sourceSets["integrationTest"].runtimeClasspath
		useJUnitPlatform()
		shouldRunAfter(tasks.test)

		testLogging {
			events("passed", "skipped", "failed")
			exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
		}
	}

jacoco {
	toolVersion = "0.8.12"
}

val localAwsCredentials = mapOf("AWS_ACCESS_KEY_ID" to "local", "AWS_SECRET_ACCESS_KEY" to "local")

tasks.bootRun {
	environment(localAwsCredentials)
}

tasks.withType<Test> {
	environment(localAwsCredentials)
	useJUnitPlatform()
	finalizedBy(tasks.jacocoTestReport)

	testLogging {
		events("passed", "skipped", "failed")
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
		showStandardStreams = false
	}

	addTestListener(
		object : org.gradle.api.tasks.testing.TestListener {
			override fun beforeSuite(suite: org.gradle.api.tasks.testing.TestDescriptor) = Unit

			override fun beforeTest(testDescriptor: org.gradle.api.tasks.testing.TestDescriptor) = Unit

			override fun afterTest(
				testDescriptor: org.gradle.api.tasks.testing.TestDescriptor,
				result: org.gradle.api.tasks.testing.TestResult,
			) = Unit

			override fun afterSuite(
				suite: org.gradle.api.tasks.testing.TestDescriptor,
				result: org.gradle.api.tasks.testing.TestResult,
			) {
				if (suite.className != null) {
					val outcome = if (result.failedTestCount == 0L) "PASSED" else "FAILED"
					println(
						"  %-70s %-6s (%d tests, %d passed, %d failed, %d skipped)".format(
							suite.className,
							outcome,
							result.testCount,
							result.successfulTestCount,
							result.failedTestCount,
							result.skippedTestCount,
						),
					)
				}
			}
		},
	)
}

tasks.test {
	systemProperty("architecture.test.classpath", configurations.testRuntimeClasspath.get().asPath)
	inputs.files("README.md", "CLAUDE.md", "Makefile", "Dockerfile", ".dockerignore", "docker-compose.yml")
		.withPropertyName("repositoryDocuments")
		.withPathSensitivity(PathSensitivity.RELATIVE)
	inputs.files(fileTree("openspec"), fileTree("infra"), fileTree("http"), fileTree("src"))
		.withPropertyName("repositoryInventories")
		.withPathSensitivity(PathSensitivity.RELATIVE)
}

val jacocoCoverageExclusions =
	listOf(
		"br/com/itau/challenge/ApplicationKt.class",
		"br/com/itau/challenge/Application.class",
	)

val coverageMinimum = 0.90

tasks.jacocoTestReport {
	dependsOn(tasks.test)

	classDirectories.setFrom(
		classDirectories.files.map {
			fileTree(it) { exclude(jacocoCoverageExclusions) }
		},
	)

	reports {
		xml.required = true
		html.required = true
	}

	doLast {
		printCoverageSummary(reports.xml.outputLocation.asFile.get(), coverageMinimum)
	}
}

tasks.jacocoTestCoverageVerification {
	dependsOn(tasks.jacocoTestReport)

	classDirectories.setFrom(
		classDirectories.files.map {
			fileTree(it) { exclude(jacocoCoverageExclusions) }
		},
	)

	violationRules {
		rule {
			limit {
				minimum = coverageMinimum.toBigDecimal()
			}
		}
	}
}

tasks.check {
	dependsOn(tasks.jacocoTestCoverageVerification)
}

fun printCoverageSummary(
	reportFile: File,
	minimum: Double,
) {
	if (!reportFile.exists()) return

	val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
	factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
	factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
	val document = factory.newDocumentBuilder().parse(reportFile)
	val root = document.documentElement

	fun directCounters(element: org.w3c.dom.Element): Map<String, Pair<Int, Int>> {
		val counters = mutableMapOf<String, Pair<Int, Int>>()
		val children = element.childNodes
		for (i in 0 until children.length) {
			val node = children.item(i)
			if (node is org.w3c.dom.Element && node.tagName == "counter") {
				val covered = node.getAttribute("covered").toInt()
				val missed = node.getAttribute("missed").toInt()
				counters[node.getAttribute("type")] = covered to missed
			}
		}
		return counters
	}

	fun percentage(
		counters: Map<String, Pair<Int, Int>>,
		type: String,
	): Double {
		val (covered, missed) = counters[type] ?: return 100.0
		val total = covered + missed
		return if (total == 0) 100.0 else covered.toDouble() / total * 100.0
	}

	val overall = directCounters(root)
	val types = listOf("INSTRUCTION", "BRANCH", "LINE", "COMPLEXITY", "METHOD", "CLASS")

	val header = "%-12s %8s %8s %8s %8s".format("Type", "Covered", "Missed", "Total", "Coverage")
	val bar = "-".repeat(header.length)

	println()
	println("Coverage summary")
	println(bar)
	println(header)
	println(bar)

	for (type in types) {
		val (covered, missed) = overall[type] ?: continue
		val total = covered + missed
		println("%-12s %8d %8d %8d %7.1f%%".format(type.lowercase().replaceFirstChar { it.uppercase() }, covered, missed, total, percentage(overall, type)))
	}

	println(bar)

	val instructionPct = percentage(overall, "INSTRUCTION")
	val gate = if (instructionPct >= minimum * 100) "PASS" else "FAIL"
	println("Gate: minimum %.0f%% instruction coverage -> %s (%.1f%%)".format(minimum * 100, gate, instructionPct))
	println()
}
