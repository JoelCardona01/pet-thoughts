import java.io.DataInputStream
import java.io.File
import java.util.zip.ZipFile

// Applied by the mod modules: adds "verifyMixins" to "check".
//
// A mixin that points at a method or field that does not exist compiles fine and only fails in the
// game, when the target class is loaded. This task reads the target classes from the compile
// classpath and fails the build if a name used by @Inject, @Invoker, @Accessor or @Shadow is not
// declared there. It checks names only, not parameter types.

fun declaredMembers(input: DataInputStream): Set<String> {
    input.readInt() // magic
    input.readInt() // minor + major version
    val poolSize = input.readUnsignedShort()
    val utf8 = HashMap<Int, String>()
    var index = 1
    while (index < poolSize) {
        when (val tag = input.readUnsignedByte()) {
            1 -> utf8[index] = input.readUTF()
            3, 4, 9, 10, 11, 12, 17, 18 -> input.skipBytes(4)
            5, 6 -> { input.skipBytes(8); index++ } // long and double take two slots
            7, 8, 16, 19, 20 -> input.skipBytes(2)
            15 -> input.skipBytes(3)
            else -> error("unknown constant pool tag $tag")
        }
        index++
    }
    input.skipBytes(6) // access flags, this class, super class
    input.skipBytes(2 * input.readUnsignedShort()) // interfaces
    val names = HashSet<String>()
    repeat(2) { // fields, then methods
        repeat(input.readUnsignedShort()) {
            input.skipBytes(2)
            names.add(utf8.getValue(input.readUnsignedShort()))
            input.skipBytes(2)
            repeat(input.readUnsignedShort()) {
                input.skipBytes(2)
                input.skipBytes(input.readInt())
            }
        }
    }
    return names
}

fun readClass(classpath: Collection<File>, internalName: String): Set<String>? {
    val entry = "$internalName.class"
    for (file in classpath) {
        if (file.isDirectory) {
            val candidate = File(file, entry)
            if (candidate.isFile) {
                return candidate.inputStream().buffered().use { declaredMembers(DataInputStream(it)) }
            }
        } else if (file.isFile && file.name.endsWith(".jar")) {
            ZipFile(file).use { zip ->
                val found = zip.getEntry(entry)
                if (found != null) {
                    return zip.getInputStream(found).buffered().use { declaredMembers(DataInputStream(it)) }
                }
            }
        }
    }
    return null
}

val verifyMixins = tasks.register("verifyMixins") {
    group = "verification"
    description = "Checks that every mixin target (class, method, field) exists in this Minecraft version."
    val main = project.extensions.getByType(SourceSetContainer::class.java).getByName("main")
    val sourceDirs = main.java.sourceDirectories
    val classpath = main.compileClasspath
    inputs.files(sourceDirs)
    inputs.files(classpath)

    doLast {
        val jars = classpath.files
        val problems = ArrayList<String>()
        var checked = 0
        val mixinFiles = sourceDirs.files.flatMap { dir ->
            dir.walkTopDown().filter { it.isFile && it.extension == "java" && it.readText().contains("@Mixin(") }.toList()
        }
        for (file in mixinFiles) {
            val source = file.readText()
            val target = Regex("""@Mixin\(([\w.]+)\.class\)""").find(source)?.groupValues?.get(1)
            if (target == null) {
                problems.add("${file.name}: could not read the @Mixin target")
                continue
            }
            // "Display.TextDisplay" + "import net.minecraft.world.entity.Display;" -> net/minecraft/world/entity/Display$TextDisplay
            val parts = target.split('.')
            val imported = Regex("""import ([\w.]+\.${Regex.escape(parts[0])});""").find(source)?.groupValues?.get(1)
            if (imported == null) {
                problems.add("${file.name}: no import found for ${parts[0]}")
                continue
            }
            val internalName = imported.replace('.', '/') + parts.drop(1).joinToString("") { "\$" + it }
            val members = readClass(jars, internalName)
            if (members == null) {
                problems.add("${file.name}: target class ${internalName.replace('/', '.')} does not exist")
                continue
            }
            val wanted = LinkedHashSet<String>()
            Regex("""method\s*=\s*"(\w+)"""").findAll(source).forEach { wanted.add(it.groupValues[1]) }
            Regex("""@(?:Invoker|Accessor)\("(\w+)"\)""").findAll(source).forEach { wanted.add(it.groupValues[1]) }
            Regex("""@Shadow\s+[^;{(]*?(\w+)\s*[;(]""").findAll(source).forEach { wanted.add(it.groupValues[1]) }
            for (name in wanted) {
                checked++
                if (name !in members) {
                    problems.add("${file.name}: ${internalName.substringAfterLast('/')} has no member named '$name'")
                }
            }
        }
        if (mixinFiles.isEmpty()) {
            problems.add("no mixin sources found")
        }
        if (problems.isNotEmpty()) {
            throw GradleException("Mixin targets missing in this Minecraft version:\n  " + problems.joinToString("\n  "))
        }
        logger.lifecycle("verifyMixins: $checked members in ${mixinFiles.size} mixins exist")
    }
}

tasks.named("check") {
    dependsOn(verifyMixins)
}
