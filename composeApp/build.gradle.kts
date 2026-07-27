import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(project(":core"))
            implementation(project(":infra"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.hrns_now.app.MainKt"

        nativeDistributions {
            // Phase 6A는 외부 Kit MSI MVP만 다룬다 — DMG/DEB는 build하거나 검증하지 않는다
            // (doc/claude_prompts/phase6-msi-distribution.md).
            targetFormats(TargetFormat.Msi)
            // JvmProcessExecutor는 Windows native console charset(예: MS949)을 해석한다.
            // jlink가 이를 제공하는 jdk.charsets를 제거하지 않게 명시한다.
            modules("jdk.charsets")
            packageName = "HRNS-NOW"
            // 단일 version source of truth다 — gradle/libs.versions.toml의 hrnsNowApp만 갱신한다.
            packageVersion = libs.versions.hrnsNowApp.get()
            vendor = "HRNS-NOW"
            // WiX 3.11(jpackage가 내려받아 쓰는 toolchain)의 non-ASCII 인자 처리가 이 Windows
            // host의 native encoding(MS949)과 충돌해 candle/light가 "Input length = 1"로
            // 실패하는 것을 실측으로 확인했다 — installer 메타데이터는 ASCII로만 채운다
            // (`doc/phase_reports/phase6-report.md` 참고).
            description = "HRNS-NOW: Windows desktop control panel for harness-kit"
            copyright = "Copyright (C) 2026 HRNS-NOW"

            windows {
                // 고정 UpgradeCode다 — 이후 버전에서 바뀌면 Windows가 업그레이드가 아니라
                // 별도 제품 설치로 취급한다. 절대 재생성하지 않는다.
                upgradeUuid = "31f02588-34f5-454a-a4f8-3a3071ef9aa4"
                menuGroup = "HRNS-NOW"
                perUserInstall = false
                shortcut = true
                menu = true
                console = false
                iconFile.set(project.file("src/jvmMain/resources/hrns-now.ico"))
            }
        }
    }
}
