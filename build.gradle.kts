// Git hooks setup
tasks.register<Exec>("installGitHooks") {
    description = "Configure Git to use .hooks folder for hooks"
    group = "build setup"
    notCompatibleWithConfigurationCache("Task manipulates Git configuration")

    val gitHooksDir = File(rootDir, ".hooks")

    doFirst {
        if (!gitHooksDir.exists()) {
            throw GradleException("Git hooks directory not found at: ${gitHooksDir.absolutePath}")
        }

        // Ensure hooks are executable
        gitHooksDir.listFiles()?.forEach { it.setExecutable(true) }
    }

    commandLine("git", "config", "core.hooksPath", ".hooks")

    doLast {
        println("✅ Git configured to use .hooks/ folder")
        println("All hooks in .hooks/ will be automatically used")
    }
}
