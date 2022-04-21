package svcs

import java.io.File
import java.security.MessageDigest

const val COMMAND_HELP = "--help"

enum class Command {
    CONFIG,
    ADD,
    LOG,
    COMMIT,
    CHECKOUT,
    ;

    companion object {
        fun valueOfOrNull(string: String): Command? =
            try {
                valueOf(string.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
    }
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printManPage()
        return
    }
    val commandArgument = args[0]
    if (commandArgument == COMMAND_HELP)
        printManPage()
    else when (val command = Command.valueOfOrNull(commandArgument)) {
        Command.CONFIG -> {
            val configFile = getVcsFile("config.txt")
            if (args.size > 1) {
                setUsername(args[1])
            } else {
                if (configFile.length() == 0L) {
                    println("Please, tell me who you are.")
                    val username = readLine()!!
                    setUsername(username)
                }
            }
            val username = configFile.readText()
            println("The username is $username.")
        }
        Command.ADD -> {
            val indexFile = getVcsFile("index.txt")
            if (args.size > 1) {
                val file = File(args[1])
                if (file.exists()) {
                    val lines = indexFile.readLines()
                    if (! lines.contains(file.name)) {
                        indexFile.appendText("${file.name}\n")
                        println("The file '${file.name}' is tracked.")
                    }
                } else {
                    println("Can't find '${file.name}'.")
                }
            } else {
                if (indexFile.length() == 0L) {
                    println("Add a file to the index.")
                } else {
                    println("Tracked files:")
                    indexFile.readLines()
                        .forEach(::println)
                }
            }
        }
        Command.COMMIT -> {
            if (args.size == 1) {
                println("Message was not passed.")
                return
            }
            val message = args[1]

            val configFile = getVcsFile("config.txt")
            if (configFile.length() == 0L) {
                println("No username given.")
                return
            }
            val username = configFile.readText()

            val indexFile = getVcsFile("index.txt")
            if (indexFile.length() == 0L) {
                println("No tracked files.")
                return
            }

            val messageDigest = MessageDigest.getInstance("SHA-256")
            indexFile.readLines()
                .forEach {
                    val file = File(it)
                    if (file.exists()) {
                        messageDigest.update(file.readBytes())
                    }
                }
            val hash = messageDigest.digest().toHex()

            val logFile = getVcsFile("log.txt")
            val changes = if (logFile.length() > 0) {
                val commitId = logFile.readLines()
                    .last()
                    .substring(7)
                !commitId.startsWith(hash)
            } else {
                true
            }

            if (changes) {
                val id = "$hash${System.nanoTime()}"
                logFile.appendText("\n")
                logFile.appendText("$message\n")
                logFile.appendText("Author: $username\n")
                logFile.appendText("commit $id\n")

                val commitDirectory = getCommitDirectory(id)
                indexFile.readLines()
                    .filter { it.isNotBlank() }
                    .forEach {
                        val file = File(it)
                        if (file.exists() && file.isFile) {
                            file.copyTo(File(commitDirectory, it))
                        }
                    }
                println("Changes are committed.")
            } else {
                println("Nothing to commit.")
            }
        }
        Command.LOG -> {
            val logFile = getVcsFile("log.txt")
            if (logFile.length() == 0L) {
                println("No commits yet.")
            } else {
                logFile.readLines()
                    .reversed()
                    .forEach(::println)
            }
        }
        Command.CHECKOUT -> {
            if (args.size == 1) {
                println("Commit id was not passed.")
                return
            }
            val id = args[1]

            val logFile = getVcsFile("log.txt")
            if (logFile.readLines()
                .filter { it == "commit $id" }
                .isEmpty()) {
                println("Commit does not exist.")
                return
            }

            val commitDirectory = getCommitDirectory(id)
            val indexFile = getVcsFile("index.txt")
            indexFile.readLines()
                .filter { it.isNotBlank() }
                .forEach {
                    val file = File(commitDirectory, it)
                    val targetFile = File(it)
                    if (targetFile.exists() && !file.exists()) {
                        targetFile.delete()
                    } else {
                        file.copyTo(targetFile, true)
                    }
                }
            println("Switched to commit $id.")
        }
        else -> println("'$commandArgument' is not a SVCS command.")
    }
}

fun setUsername(username: String) {
    val configFile = getVcsFile("config.txt")
    configFile.writeText(username)
}

fun getCommitDirectory(id: String): File {
    val commitsDirectory = File(getVcsDirectory(), "commits")
    val commitDirectory = File(commitsDirectory, id)
    if (!commitDirectory.exists()) {
        commitDirectory.mkdir()
    }
    return commitDirectory
}

fun getVcsFile(filename: String): File {
    val file = File(getVcsDirectory(), filename)
    if (!file.exists()) {
        file.createNewFile()
    }
    return file
}

fun getVcsDirectory(): File {
    val directory = File("vcs")
    if (!directory.exists()) {
        directory.mkdir()
    }
    return directory
}

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun printManPage() = println("""
    These are SVCS commands:
    config     Get and set a username.
    add        Add a file to the index.
    log        Show commit logs.
    commit     Save changes.
    checkout   Restore a file.
""".trimIndent())