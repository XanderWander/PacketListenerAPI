package nl.xanderwander.packetlistenerapi.reflection

import org.bukkit.Bukkit
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * A utility class that simplifies reflection in Bukkit plugins.
 *
 *
 * Modified by fren_gor to support 1.17+ servers
 * Modified by XanderWander to use Kotlin
 *
 * @author Kristian
 */
object Reflection {
    private val OBC_PREFIX = Bukkit.getServer().javaClass.getPackage().name
    private val VERSION = OBC_PREFIX.replace("org.bukkit.craftbukkit", "").replace(".", "")
    private val version = VERSION.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1].toInt()
    private val NMS_PREFIX = if (version < 17) "net.minecraft.server.$VERSION" else "net.minecraft"
    private val MATCH_VARIABLE = Pattern.compile("\\{([^\\}]+)\\}")
    private val MATCH_NMS = Pattern.compile("nms((:\\.[^\\.\\s]+)*)")
    fun <T> getField(target: Class<*>, name: String?, fieldType: Class<T>): FieldAccessor<T> {
        return getField(target, name, fieldType, 0)
    }

    fun <T> getField(className: String, name: String?, fieldType: Class<T>): FieldAccessor<T> {
        return getField(getClass(className), name, fieldType, 0)
    }

    fun <T> getField(target: Class<*>, fieldType: Class<T>, index: Int): FieldAccessor<T> {
        return getField(target, null, fieldType, index)
    }

    fun <T> getField(className: String, fieldType: Class<T>, index: Int): FieldAccessor<T> {
        return getField(getClass(className), fieldType, index)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getField(target: Class<*>, name: String?, fieldType: Class<T>, index: Int): FieldAccessor<T> {
        var idx = index
        for (field in target.declaredFields) {
            if ((name == null) || field.name == name && fieldType.isAssignableFrom(field.type) && idx-- <= 0) {
                field.isAccessible = true
                return object : FieldAccessor<T> {
                    override operator fun get(target: Any): T {
                        return try {
                            field[target] as T
                        } catch (e: IllegalAccessException) {
                            throw RuntimeException("Cannot access reflection.", e)
                        }
                    }

                    override operator fun set(target: Any, value: Any) {
                        try {
                            field[target] = value
                        } catch (e: IllegalAccessException) {
                            throw RuntimeException("Cannot access reflection.", e)
                        }
                    }

                    override fun hasField(target: Any): Boolean {
                        return field.declaringClass.isAssignableFrom(target.javaClass)
                    }
                }
            }
        }
        if (target.superclass != null) return getField(target.superclass, name, fieldType, idx)
        throw IllegalArgumentException("Cannot find field with type $fieldType")
    }

    fun getMethod(className: String, methodName: String, vararg params: Class<*>): MethodInvoker {
        return getTypedMethod(getClass(className), methodName, null, *params)
    }

    fun getMethod(clazz: Class<*>, methodName: String?, vararg params: Class<*>): MethodInvoker {
        return getTypedMethod(clazz, methodName, null, *params)
    }

    fun getHandle(instance: Any): Any {
        val clazz: Class<*> = instance.javaClass
        val method = getMethod(clazz, "getHandle")
        return method.invoke(instance)
    }

    fun getTypedMethod(
        clazz: Class<*>,
        methodName: String?,
        returnType: Class<*>?,
        vararg params: Class<*>
    ): MethodInvoker {
        for (method in clazz.declaredMethods) {
            if ((methodName == null || method.name == methodName)
                && (returnType == null || method.returnType == returnType)
                && Arrays.equals(method.parameterTypes, params)
            ) {
                method.isAccessible = true
                return object : MethodInvoker {
                    override fun invoke(target: Any, arguments: Array<out Any>): Any {
                        try {
                            return method.invoke(target, *arguments)
                        } catch (e: Exception) {
                            throw RuntimeException("Cannot invoke method $method", e)
                        }
                    }
                }
            }
        }
        if (clazz.superclass != null) return getMethod(clazz.superclass, methodName, *params)
        throw IllegalStateException(String.format("Unable to find method %s (%s).", methodName, listOf(*params)))
    }

    fun getConstructor(className: String, vararg params: Class<*>): ConstructorInvoker {
        return getConstructor(getClass(className), *params)
    }

    fun getConstructor(clazz: Class<*>, vararg params: Class<*>): ConstructorInvoker {
        for (constructor in clazz.declaredConstructors) {
            if (Arrays.equals(constructor.parameterTypes, params)) {
                constructor.isAccessible = true
                return object : ConstructorInvoker {
                    override operator fun invoke(vararg arguments: Any): Any {
                        return try {
                            constructor.newInstance(*arguments)
                        } catch (e: Exception) {
                            throw RuntimeException("Cannot invoke constructor $constructor", e)
                        }
                    }
                }
            }
        }
        throw IllegalStateException(
            String.format(
                "Unable to find constructor for %s (%s).",
                clazz,
                Arrays.asList(*params)
            )
        )
    }

    fun getUntypedClass(lookupName: String): Class<*> {
        return getClass(lookupName)
    }

    fun getClass(lookupName: String): Class<*> {
        return getCanonicalClass(expandVariables(lookupName))
    }

    fun getMinecraftClass(name: String, subpackage: String): Class<*> {
        var clazz = "$NMS_PREFIX."
        if (version >= 17) {
            clazz += "$subpackage."
        }
        return getCanonicalClass(clazz + name)
    }

    fun getCraftBukkitClass(name: String): Class<*> {
        return getCanonicalClass("$OBC_PREFIX.$name")
    }

    private fun getCanonicalClass(canonicalName: String): Class<*> {
        return try {
            Class.forName(canonicalName)
        } catch (e: ClassNotFoundException) {
            throw IllegalArgumentException("Cannot find $canonicalName", e)
        }
    }

    private fun expandVariables(name: String): String {
        val output = StringBuffer()
        val matcher = MATCH_VARIABLE.matcher(name)
        while (matcher.find()) {
            val variable = matcher.group(1)
            var replacement = ""
            if (variable.lowercase().startsWith("nms")) {
                val m = MATCH_NMS.matcher(variable)
                require(m.matches()) { "Illegal variable: $variable" }
                replacement = NMS_PREFIX
                if (version >= 17) replacement += m.group(1)
            } else if ("obc".equals(variable, ignoreCase = true)) replacement = OBC_PREFIX else if ("version".equals(
                    variable,
                    ignoreCase = true
                )
            ) replacement = VERSION else throw IllegalArgumentException(
                "Unknown variable: $variable"
            )
            if (replacement.isNotEmpty() && matcher.end() < name.length && name[matcher.end()] != '.') replacement += "."
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement))
        }
        matcher.appendTail(output)
        return output.toString()
    }

    interface ConstructorInvoker {
        operator fun invoke(vararg arguments: Any): Any
    }

    interface MethodInvoker {
        operator fun invoke(target: Any, vararg arguments: Any): Any
    }

    interface FieldAccessor<T> {
        operator fun get(target: Any): T
        operator fun set(target: Any, value: Any)
        fun hasField(target: Any): Boolean
    }
}