// SPDX-License-Identifier: MPL-2.0
tasks.register<SelectTestSlot>("selectTestSlot") {
    group = "verification"
    description = "Selects a deterministic slot of the test input manifest."
    slot.convention(providers.gradleProperty("testSlot").orElse("0/1"))
    testInput.convention(providers.gradleProperty("testInput").orElse(""))
    selectedManifest.convention(layout.buildDirectory.file("test-slots/manifest.tsv"))
}
