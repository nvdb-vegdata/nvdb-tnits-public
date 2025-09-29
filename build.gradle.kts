// Git hooks setup
tasks.register("installGitHooks") {
    description = "Install git hooks for code formatting"
    group = "build setup"
    notCompatibleWithConfigurationCache("Task manipulates files outside of project directory")

    doLast {
        val hooksDir = File(rootDir, ".git/hooks")
        val preCommitHook = File(hooksDir, "pre-commit")
        val preCommitTemplate = File(rootDir, "git-hooks/pre-commit")

        if (!preCommitTemplate.exists()) {
            throw GradleException("Pre-commit hook template not found at: ${preCommitTemplate.absolutePath}")
        }

        preCommitHook.writeText(preCommitTemplate.readText())
        preCommitHook.setExecutable(true)

        println("✅ Git pre-commit hook installed successfully")
        println("The hook will automatically run ktlint formatting on commits")
    }
}
