package io.github.lonemoonspace.dayloom.app.home

/**
 * Order of the home cards.
 * 首页卡片的顺序。
 */
object CardOrderPolicy {

    data class Card(val key: String, val defaultOrder: Int, val pinnedToTop: Boolean = false)

    /**
     * The user's order: saved keys that still exist first, then the remaining cards by default order. Cards of modules
     * added later therefore appear at the end instead of disappearing.
     * 用户的顺序：仍然存在的已保存键在前，其余卡片按默认顺序排在后面。以后新增模块的卡片因此出现在末尾，而不是消失。
     */
    fun userOrder(cards: List<Card>, saved: List<String>): List<String> {
        val known = cards.mapTo(mutableSetOf()) { it.key }
        val savedKnown = saved.filter { it in known }.distinct()
        val rest = cards.filter { it.key !in savedKnown }
            .sortedWith(compareBy<Card>({ it.defaultOrder }, { it.key }))
            .map { it.key }
        return savedKnown + rest
    }

    /**
     * What the home screen shows: [userOrder] with pinned cards (e.g. something about to expire) moved to the top.
     * Pinning is temporary and never written back to the saved order.
     * 首页实际显示的顺序：在 [userOrder] 基础上把置顶的卡片（如快到期的条目）挪到最前。置顶是临时的，不会写回已保存的顺序。
     */
    fun displayOrder(cards: List<Card>, saved: List<String>): List<String> {
        val pinned = cards.filter { it.pinnedToTop }.mapTo(mutableSetOf()) { it.key }
        val (top, normal) = userOrder(cards, saved).partition { it in pinned }
        return top + normal
    }

    /** Moves the item at [from] to [to]; out-of-range indices leave the list unchanged. / 把 [from] 处的项移到 [to]；下标越界时不变。 */
    fun move(order: List<String>, from: Int, to: Int): List<String> {
        if (from !in order.indices || to !in order.indices || from == to) return order
        return order.toMutableList().apply { add(to, removeAt(from)) }
    }
}
