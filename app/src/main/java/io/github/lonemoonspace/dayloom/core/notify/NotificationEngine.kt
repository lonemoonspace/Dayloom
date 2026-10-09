package io.github.lonemoonspace.dayloom.core.notify

import kotlinx.coroutines.CancellationException

/**
 * A rule together with its namespaced state key.
 * 规则及其带命名空间的状态键。
 */
data class ScopedRule(val stateKey: String, val rule: NotificationRule<*>)

class NotificationEngine(
    private val stateStore: NotificationStateStore,
    private val notifier: Notifier,
    private val onError: (ScopedRule, Exception) -> Unit = { _, _ -> },
) {
    /**
     * Each rule is isolated: one throwing only drops its own result. A disabled rule neither reads nor writes state, so events
     * that happened while it was off are not marked as already notified. State is written before sending: a failed send may lose
     * one notification, but never duplicates it.
     * 每条规则互相隔离：一条抛异常只丢弃它自己的结果。未启用的规则不读也不写状态，关闭期间发生的事件不会被当成「已通知」。
     * 先写状态再发送：发送失败宁可漏一条，也不重复推送。
     */
    suspend fun run(rules: List<ScopedRule>, input: RuleInput) {
        for (scoped in rules) {
            try {
                runRule(scoped.stateKey, scoped.rule, input)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(scoped, e)
            }
        }
    }

    private suspend fun <S> runRule(stateKey: String, rule: NotificationRule<S>, input: RuleInput) {
        if (!rule.isEnabled()) return
        val previous = rule.codec.decode(stateStore.read(stateKey))
        val decision = rule.evaluate(input, previous)
        if (decision.newState != previous) stateStore.write(stateKey, rule.codec.encode(decision.newState))
        decision.notifications.forEach(notifier::send)
    }
}
