package com.xex.filemanager.fs

import org.benf.cfr.reader.api.CfrDriver
import org.benf.cfr.reader.api.OutputSinkFactory
import org.jf.baksmali.Adaptors.ClassDefinition
import org.jf.baksmali.BaksmaliOptions
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.util.IndentingWriter
import java.io.File
import java.io.StringWriter
import java.util.regex.Pattern
import java.util.zip.ZipFile

/** mode: "jar" — классы Java (CFR), "dex" — классы Dalvik (smali). */
class DecResult(
    val mode: String,
    val classes: List<String>,
    val dexOf: Map<String, Int>,
    val dexFiles: List<File>
)

object Decompiler {
    fun supported(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".jar") || n.endsWith(".apk") || n.endsWith(".dex") || n.endsWith(".class") ||
            n.endsWith(".aar") || n.endsWith(".xapk") || n.endsWith(".apks") || n.endsWith(".apkm")
    }

    fun load(file: File, tmpDir: File): DecResult {
        val lower = file.name.lowercase()
        if (lower.endsWith(".class")) {
            return DecResult("jar", listOf(file.name.removeSuffix(".class")), emptyMap(), emptyList())
        }
        if (lower.endsWith(".dex")) return dexResult(listOf(file))
        val dexes = ArrayList<File>()
        val names = ArrayList<String>()
        ZipFile(file).use { z ->
            for (e in z.entries().asSequence()) {
                val n = e.name
                if (n.endsWith(".class")) {
                    names.add(n.removeSuffix(".class").replace('/', '.'))
                } else if (n.startsWith("classes") && n.endsWith(".dex") && !n.contains('/')) {
                    val t = File(tmpDir, "dex_" + System.nanoTime() + "_" + n)
                    z.getInputStream(e).use { ins -> t.outputStream().use { o -> ins.copyTo(o) } }
                    dexes.add(t)
                }
            }
        }
        if (dexes.isNotEmpty()) return dexResult(dexes)
        return DecResult("jar", names.sorted(), emptyMap(), emptyList())
    }

    private fun dexResult(dexes: List<File>): DecResult {
        val names = ArrayList<String>()
        val dexOf = HashMap<String, Int>()
        for ((i, f) in dexes.withIndex()) {
            val dex = DexFileFactory.loadDexFile(f, Opcodes.getDefault())
            for (c in dex.classes) {
                val n = c.type.removePrefix("L").removeSuffix(";").replace('/', '.')
                names.add(n)
                dexOf[n] = i
            }
        }
        return DecResult("dex", names.sorted(), dexOf, dexes)
    }

    fun smali(dexFile: File, className: String): String {
        val dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault())
        val type = "L" + className.replace('.', '/') + ";"
        for (c in dex.classes) {
            if (c.type == type) {
                val sw = StringWriter()
                ClassDefinition(BaksmaliOptions(), c).writeTo(IndentingWriter(sw))
                return sw.toString()
            }
        }
        return "// Класс не найден"
    }

    /** Java-код класса из JAR (className) или отдельного .class (className = null). */
    fun java(source: File, className: String?): String {
        val sb = StringBuilder()
        val sink = object : OutputSinkFactory {
            override fun getSupportedSinks(
                sinkType: OutputSinkFactory.SinkType,
                collection: MutableCollection<OutputSinkFactory.SinkClass>
            ): MutableList<OutputSinkFactory.SinkClass> = mutableListOf(OutputSinkFactory.SinkClass.STRING)

            override fun <T> getSink(
                sinkType: OutputSinkFactory.SinkType,
                sinkClass: OutputSinkFactory.SinkClass
            ): OutputSinkFactory.Sink<T> {
                return OutputSinkFactory.Sink<T> { t ->
                    if (sinkType == OutputSinkFactory.SinkType.JAVA || sinkType == OutputSinkFactory.SinkType.EXCEPTION) {
                        sb.append(t.toString()).append('\n')
                    }
                }
            }
        }
        val opts = HashMap<String, String>()
        if (className != null) {
            opts["jarfilter"] = "^" + Pattern.quote(className) + "(" + Pattern.quote("$") + ".*)?\$"
        }
        val driver = CfrDriver.Builder().withOptions(opts).withOutputSink(sink).build()
        driver.analyse(listOf(source.absolutePath))
        val text = sb.toString()
        return if (text.isBlank()) "// CFR не смог декомпилировать этот класс" else text
    }
}
