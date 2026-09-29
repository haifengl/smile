/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.kernel

import java.io.File
import java.lang.reflect.Modifier
import java.net.URL
import java.util.Collections
import java.util.Enumeration
import java.util.Locale
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.reflect.KClass
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.displayName
import kotlin.script.experimental.api.repl
import kotlin.script.experimental.api.resultFieldPrefix
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContext
import kotlin.script.experimental.jvmhost.repl.JvmReplCompiler
import kotlin.script.experimental.jvmhost.repl.JvmReplEvaluator
import org.jetbrains.kotlin.cli.common.repl.IReplStageState
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import org.jetbrains.kotlin.cli.common.repl.ReplCompileResult
import org.jetbrains.kotlin.cli.common.repl.ReplEvalResult

/**
 * The outcome of evaluating one snippet of Kotlin code.
 *
 * @property success whether the snippet compiled and evaluated without errors.
 * @property name the name of the generated result field. It is null when the
 *           snippet has no value, such as a declaration or a Unit expression.
 * @property value the value of the last expression, or null.
 * @property typeName the declared type of [value], or null.
 * @property error the diagnostics produced on failure, or null.
 */
@JvmRecord
data class ScriptResult(
    val success: Boolean,
    val name: String?,
    val value: Any?,
    val typeName: String?,
    val error: String?
)

/**
 * A variable defined by a previously evaluated snippet.
 *
 * @property name the variable name.
 * @property typeName the simple name of the declared type.
 */
@JvmRecord
data class ScriptVariable(val name: String, val typeName: String)

/**
 * A ClassLoader that delegates to a parent ClassLoader but hides any class
 * or resource belonging to smile-scala, allowing the Kotlin scripting engine
 * to resolve smile-kotlin declarations instead.
 */
class ScriptBaseClassLoader(
    parent: ClassLoader,
    private val filter: (String) -> Boolean = {
        ScriptRunnerBridge.isScalaClasspathEntry(it)
    }
) : ClassLoader(parent) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (!name.startsWith("java.") && !name.startsWith("javax.") && !name.startsWith("kotlin.")) {
            val resourceName = name.replace('.', '/') + ".class"
            val url = parent?.getResource(resourceName)
            if (url != null && filter(url.toString())) {
                throw ClassNotFoundException(name)
            }
        }
        return super.loadClass(name, resolve)
    }

    override fun getResource(name: String): URL? {
        val url = super.getResource(name)
        if (url != null && filter(url.toString())) {
            return null
        }
        return url
    }

    override fun getResources(name: String): Enumeration<URL> {
        val resources = super.getResources(name)
        val filtered = ArrayList<URL>()
        while (resources.hasMoreElements()) {
            val url = resources.nextElement()
            if (!filter(url.toString())) {
                filtered.add(url)
            }
        }
        return Collections.enumeration(filtered)
    }
}

/**
 * Evaluates Kotlin scripts with the Kotlin scripting host, keeping the state
 * of a session across calls so that successive snippets share declarations.
 *
 * <p>Unlike JSR-223 (deprecated in Kotlin 2.2.0), this drives the REPL API of
 * the scripting host ([JvmReplCompiler] and [JvmReplEvaluator]). Every [eval]
 * compiles a snippet against the declarations of the previous snippets and
 * evaluates it on top of them. The script classpath is derived from the current
 * context, so snippets can reference the SMILE libraries and any other
 * dependency of the hosting application.
 *
 * <p>This class is not thread safe; callers must serialize access.
 *
 * @author Haifeng Li
 */
class ScriptRunnerBridge @JvmOverloads constructor(
    classpathFilter: ((File) -> Boolean)? = null
) {
    private val scriptBaseClassLoader = ScriptBaseClassLoader(
        Thread.currentThread().contextClassLoader ?: ScriptRunnerBridge::class.java.classLoader
    )

    /** The compilation configuration shared by all snippets of the session. */
    private val compilationConfiguration: ScriptCompilationConfiguration = ScriptCompilationConfiguration {
        displayName("SMILE Kotlin")
        // Let snippets resolve the classes of the hosting application,
        // including the SMILE libraries and their dependencies, but
        // excluding smile-scala to avoid package-level shadowing.
        jvm {
            val filter = classpathFilter ?: { !isScalaClasspathEntry(it) }
            val classpath = scriptCompilationClasspathFromContext(
                classLoader = scriptBaseClassLoader,
                wholeClasspath = true
            ).filter(filter)
            updateClasspath(classpath)
        }
        repl {
            // The REPL stores the value of an expression in a synthetic
            // field of the snippet class, named "<prefix><n>". Keep the
            // default prefix; variables() filters those fields out.
            resultFieldPrefix(RESULT_FIELD_PREFIX)
        }
    }

    /** The evaluation configuration shared by all snippets of the session. */
    private val evaluationConfiguration = ScriptEvaluationConfiguration {
        jvm {
            this[baseClassLoader] = scriptBaseClassLoader
        }
    }

    /** The compiler of the current session, or null when not running. */
    private var compiler: JvmReplCompiler? = null

    /** The evaluator of the current session, or null when not running. */
    private var evaluator: JvmReplEvaluator? = null

    /** The compiler stage state of the current session. */
    private var compilerState: IReplStageState<*>? = null

    /** The evaluator stage state of the current session. */
    private var evaluatorState: IReplStageState<*>? = null

    /** The sequence number of the next snippet. */
    private var lineNumber = FIRST_LINE_NUMBER

    init {
        restart()
    }

    /**
     * (Re)starts the session, discarding all previously defined variables.
     */
    fun restart() {
        close()
        val hostConfiguration = defaultJvmScriptingHostConfiguration
        val replCompiler = JvmReplCompiler(compilationConfiguration, hostConfiguration)
        val replEvaluator = JvmReplEvaluator(evaluationConfiguration)
        val lock = ReentrantReadWriteLock()
        compilerState = replCompiler.createState(lock)
        evaluatorState = replEvaluator.createState(lock)
        compiler = replCompiler
        evaluator = replEvaluator
        lineNumber = FIRST_LINE_NUMBER
    }

    /**
     * Discards all previously defined variables, keeping the session usable.
     * It is equivalent to [restart].
     */
    fun reset() = restart()

    /**
     * Shuts down the session and frees the compiler resources.
     */
    fun close() {
        try {
            compilerState?.dispose()
        } catch (_: Throwable) {
            // The state may already be disposed; nothing to do.
        }
        compilerState = null
        evaluatorState = null
        compiler = null
        evaluator = null
    }

    /**
     * Evaluates a snippet of Kotlin code.
     *
     * @param code the snippet to evaluate. It may span several lines.
     * @return the result of the evaluation, never null.
     */
    fun eval(code: String): ScriptResult {
        if (code.isBlank()) {
            return ScriptResult(true, null, null, null, null)
        }

        val replCompiler = compiler ?: return notRunning()
        val replEvaluator = evaluator ?: return notRunning()
        val cState = compilerState ?: return notRunning()
        val eState = evaluatorState ?: return notRunning()

        return try {
            val snippet = ReplCodeLine(lineNumber++, FIRST_GENERATION, code)
            when (val compiled = replCompiler.compile(cState, snippet)) {
                is ReplCompileResult.CompiledClasses -> evaluate(replEvaluator, eState, compiled)
                is ReplCompileResult.Incomplete -> failure(compiled.message)
                is ReplCompileResult.Error -> failure(compiled.message)
            }
        } catch (ex: Throwable) {
            failure(ex.message ?: ex.toString())
        }
    }

    /** Evaluates a compiled snippet. */
    private fun evaluate(
        replEvaluator: JvmReplEvaluator,
        state: IReplStageState<*>,
        compiled: ReplCompileResult.CompiledClasses
    ): ScriptResult = when (val evaluated = replEvaluator.eval(state, compiled, null, null)) {
        is ReplEvalResult.ValueResult ->
            ScriptResult(true, evaluated.name, evaluated.value, evaluated.type, null)
        is ReplEvalResult.UnitResult -> ScriptResult(true, null, null, null, null)
        is ReplEvalResult.Error -> failure(evaluated.message)
        is ReplEvalResult.Incomplete -> failure(evaluated.message)
        else -> failure("Evaluation failed: $evaluated")
    }

    /** Builds a failed result from the given diagnostics. */
    private fun failure(message: String?): ScriptResult =
        ScriptResult(false, null, null, null, message?.trim()?.takeIf { it.isNotEmpty() } ?: "Unknown error")

    /** Builds a failed result for a call on a stopped session. */
    private fun notRunning(): ScriptResult =
        ScriptResult(false, null, null, null, "The Kotlin scripting engine is not running.")

    /**
     * Returns the variables defined by the evaluated snippets so far.
     *
     * @return the variables in declaration order, where a later
     *         redeclaration overrides an earlier one.
     */
    fun variables(): List<ScriptVariable> {
        val state = evaluatorState ?: return emptyList()
        val variables = LinkedHashMap<String, String>()
        for (record in state.history) {
            val pair = record.item as? Pair<*, *> ?: continue
            val clazz = pair.first as? KClass<*> ?: continue
            for (field in clazz.java.declaredFields) {
                if (field.isSynthetic || Modifier.isStatic(field.modifiers)) continue
                val name = field.name
                // Skip the compiler-generated fields: $$earlierScripts holds
                // the instances of the previous snippets and res<N> the value
                // of the snippet's last expression.
                if (name.startsWith("\$")) continue
                if (RESULT_FIELD.matches(name)) continue
                variables[name] = field.type.simpleName
            }
        }
        return variables.map { ScriptVariable(it.key, it.value) }
    }

    companion object {
        /** The prefix of the synthetic result fields of the REPL. */
        private const val RESULT_FIELD_PREFIX = "res"
        /** The sequence number of the first snippet. */
        private const val FIRST_LINE_NUMBER = 1
        /** The generation of the first snippet. */
        private const val FIRST_GENERATION = 1
        /** Matches the synthetic result fields of the REPL, e.g. "res3". */
        private val RESULT_FIELD = Regex("$RESULT_FIELD_PREFIX\\d+")

        /**
         * Returns true if the given file or directory belongs to smile-scala
         * or Scala compiler tooling jars.
         */
        @JvmStatic
        fun isScalaClasspathEntry(file: File): Boolean = isScalaClasspathEntry(file.path)

        /**
         * Returns true if the given classpath entry path belongs to smile-scala
         * or Scala compiler tooling jars.
         */
        @JvmStatic
        fun isScalaClasspathEntry(path: String?): Boolean {
            if (path.isNullOrBlank()) return false
            var normalized = path.replace('\\', '/')
            if (normalized.contains("!")) {
                normalized = normalized.substringBefore("!")
            }
            val lower = normalized.lowercase(Locale.ROOT)
            if (lower.contains("smile-scala") ||
                lower.contains("scala3-compiler") ||
                lower.contains("scala3-repl") ||
                lower.contains("scala3-directives-parser") ||
                lower.contains("scala3-interfaces") ||
                lower.contains("compiler-interface") ||
                lower.contains("util-interface") ||
                lower.contains("tasty-core") ||
                lower.contains("scala-asm")) {
                return true
            }
            if (normalized.matches(Regex("(?i).*/scala/(build/classes|bin|target)(/.*)?"))) {
            //    return true
            }
            return false
        }
    }
}
