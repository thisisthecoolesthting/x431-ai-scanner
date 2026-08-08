# 017 JDK 17 bootstrap (CU1 - Windows)

Date: **2026-05-20**.

## Find JDK 17

Search under Program Files folders named jdk-17* plus Java\\*17* (Adoptium, Microsoft JDK, Oracle-style Java root).

On this workstation: **Temurin 17 present** at:

C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.19.10-hotspot

Winget fallback if absent:

winget install EclipseAdoptium.Temurin.17.JDK -e --accept-package-agreements --accept-source-agreements

## Gradle must run on JDK 17

AGP 8 reported the daemon was still on JDK 11 (jdk-11.0.16.101-hotspot) until Gradle JVM pinned.

Pinned in gradle.properties:

 - org.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot
 - optional shell: PowerShell env var JAVA_HOME set to same folder.

Also set org.gradle.parallel=false + org.gradle.workers.max=2 to reduce flaky mergeDebugResources races on deep Documents paths (cross-ref 018).

## Wrapper

gradlew.bat exists under CU1.

## G0 command

gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon

Captured log:

c:/Users/reasn/Documents/Claude/Projects/DEv1/cu1_gradle_g0.txt

## Failure excerpt (merge resources)

`	ext
> Task :app:mergeDebugResources FAILED

ResourceCompilationRunnable ... values-am.xml ... (The system cannot find the path specified). Cause: null

BUILD FAILED in about 4m 19s
`

No Kotlin diagnostics reached; therefore **no planb/programming Tier4 Kotlin fixes** applied in this pass.
