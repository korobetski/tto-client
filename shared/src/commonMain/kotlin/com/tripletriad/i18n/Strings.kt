package com.tripletriad.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.intl.Locale
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import tripletriad.shared.generated.resources.Res

enum class AppLocale(val tag: String, val displayName: String) {
    EN_US("en_US", "English"),
    FR_FR("fr_FR", "Français"),
    DE_DE("de_DE", "Deutsch"),
    JA_JA("ja_JA", "日本語"),
    ;

    companion object {
        val Default: AppLocale = EN_US

        fun forTag(tag: String): AppLocale? = entries.firstOrNull { it.tag == tag }

        fun match(languageTag: String): AppLocale {
            val language = languageTag.replace('_', '-').substringBefore('-').lowercase()
            return entries.firstOrNull { it.tag.substringBefore('_') == language } ?: Default
        }
    }
}

class Strings internal constructor(
    val locale: AppLocale,
    private val values: Map<String, String>,
    private val fallback: Map<String, String>,
) {
    operator fun get(key: String): String = values[key] ?: fallback[key] ?: key

    fun has(key: String): Boolean = key in values || key in fallback

    fun isTranslated(key: String): Boolean = key in values

    val translatedKeys: Set<String> get() = values.keys

    val keys: Set<String> get() = values.keys + fallback.keys

    fun format(key: String, vararg args: String): String {
        var text = get(key)
        for ((index, arg) in args.withIndex()) {
            text = text.replace("{$index}", arg)
        }
        return text
    }
}

val LocalStrings: androidx.compose.runtime.ProvidableCompositionLocal<Strings> =
    staticCompositionLocalOf { Strings(AppLocale.Default, emptyMap(), emptyMap()) }

@Composable
fun rememberDeviceLocale(): AppLocale = AppLocale.match(Locale.current.toLanguageTag())

@Composable
fun rememberStrings(locale: AppLocale): Strings {
    val strings by produceState(EmptyStrings, locale) { value = loadStrings(locale) }
    return strings
}

suspend fun loadStrings(locale: AppLocale): Strings {
    val fallback = readLocale(AppLocale.Default)
    val values = if (locale == AppLocale.Default) fallback else readLocale(locale)
    return Strings(locale, values, fallback)
}

private suspend fun readLocale(locale: AppLocale): Map<String, String> =
    readBundle("tto-${locale.tag}") + readBundle("app-${locale.tag}")

@OptIn(ExperimentalResourceApi::class)
private suspend fun readBundle(name: String): Map<String, String> =
    Bundles.decodeFromString(Res.readBytes("$LOCALE_PATH/$name.json").decodeToString())

private val Bundles = Json

private val EmptyStrings = Strings(AppLocale.Default, emptyMap(), emptyMap())

private const val LOCALE_PATH = "files/locales"
