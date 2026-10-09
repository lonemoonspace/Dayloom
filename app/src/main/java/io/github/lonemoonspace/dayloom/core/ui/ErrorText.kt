package io.github.lonemoonspace.dayloom.core.ui

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.i18n.uiText

/**
 * All user-facing error text comes from here; the data layer only throws [AppError].
 * 所有面向用户的错误文字都来自这里；数据层只抛 [AppError]。
 */
fun AppError.userMessage(): UiText = when (this) {
    is AppError.Offline -> uiText(R.string.error_offline)
    is AppError.Network -> uiText(R.string.error_network, detailOf(cause))
    is AppError.Http -> when (code) {
        401, 403 -> uiText(R.string.error_http_auth, service, code)
        else -> uiText(R.string.error_http, service, code)
    }
    is AppError.NotConfigured -> uiText(R.string.error_not_configured, what)
    is AppError.SecretUnreadable -> uiText(R.string.error_secret_unreadable, what)
    is AppError.BadData -> uiText(R.string.error_bad_data, service)
    is AppError.Unexpected -> uiText(R.string.error_unexpected, detailOf(cause))
}

private fun detailOf(cause: Throwable?): String = cause?.message ?: cause?.javaClass?.simpleName.orEmpty()
