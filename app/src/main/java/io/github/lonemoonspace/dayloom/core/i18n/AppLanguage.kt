package io.github.lonemoonspace.dayloom.core.i18n

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

/**
 * The language the user picked inside the app. [tag] is the BCP 47 tag stored by the system; [SYSTEM] has none.
 * 用户在 App 内选择的语言。[tag] 是系统保存的 BCP 47 标签；[SYSTEM] 没有标签。
 */
enum class LanguageChoice(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    CHINESE("zh"),
    ;

    companion object {
        fun fromTag(tag: String?): LanguageChoice {
            val language = tag?.substringBefore('-')?.lowercase()
            return entries.firstOrNull { it.tag != null && it.tag == language } ?: SYSTEM
        }
    }
}

/**
 * Per-app language, behind an interface so ViewModels can be tested without Android.
 * 按应用语言；抽成接口，ViewModel 测试时不需要 Android 环境。
 */
interface AppLanguage {
    fun current(): LanguageChoice

    /**
     * The system recreates running activities after a change, so callers do not need to.
     * 修改后系统会自动重建正在运行的 Activity，调用方不需要自己处理。
     */
    fun set(choice: LanguageChoice)
}

/**
 * Uses the platform per-app language API (Android 13+), so the choice also shows up in system settings.
 * 使用系统的按应用语言接口（Android 13 起），所选语言在系统设置里也能看到。
 */
class SystemAppLanguage(context: Context) : AppLanguage {
    private val localeManager = context.applicationContext.getSystemService(LocaleManager::class.java)

    override fun current(): LanguageChoice {
        val locales = localeManager?.applicationLocales ?: return LanguageChoice.SYSTEM
        return if (locales.isEmpty) LanguageChoice.SYSTEM else LanguageChoice.fromTag(locales[0].toLanguageTag())
    }

    override fun set(choice: LanguageChoice) {
        localeManager?.applicationLocales = choice.tag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
    }
}
