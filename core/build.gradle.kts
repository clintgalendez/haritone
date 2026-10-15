// Pure Java navigation engine: no Hytale dependency, so it builds and tests anywhere.

tasks.named<JacocoReport>("jacocoTestReport") {
    reports { xml.required = true }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            limit { counter = "LINE"; minimum = "0.90".toBigDecimal() }
            limit { counter = "BRANCH"; minimum = "0.75".toBigDecimal() }
        }
    }
}

tasks.named("check") { dependsOn("jacocoTestCoverageVerification") }
