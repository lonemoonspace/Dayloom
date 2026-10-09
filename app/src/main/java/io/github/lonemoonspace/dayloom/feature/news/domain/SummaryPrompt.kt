package io.github.lonemoonspace.dayloom.feature.news.domain

/**
 * The instructions sent to the summarizer, one per app language so the summary comes back in the language the user reads
 * the app in. They are model input, not UI text, so they live here rather than in resources.
 * 发给摘要模型的指令，每种 App 语言一份，摘要就会以用户使用 App 的语言返回。它们是模型的输入，不是界面文案，所以放在这里而不是资源里。
 */
object SummaryPrompt {
    fun system(language: String): String = if (language.startsWith("zh")) ZH else EN

    fun user(title: String, text: String): String = "$title\n\n$text"

    private const val EN =
        "You summarize news articles for a busy reader. Reply in English with three to five short bullet points covering " +
            "the key facts, then one line starting with \"Why it matters:\". No introduction, no markdown headings."

    private const val ZH =
        "你为忙碌的读者总结新闻文章。请用简体中文回答：先用三到五条简短的要点列出关键事实，再用一行以「为什么重要：」开头的话说明意义。" +
            "不要开场白，不要使用 Markdown 标题。"
}
