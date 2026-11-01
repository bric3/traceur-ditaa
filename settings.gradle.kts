// SPDX-License-Identifier: MPL-2.0
// Copyright (c) 2025 Brice Dutheil
pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("traceur.versioning")
    id("traceur.central-publication")
}

rootProject.name = "traceur-ditaa"
