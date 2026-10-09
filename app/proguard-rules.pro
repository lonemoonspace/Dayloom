# Release R8 rules. Only add a rule that a dependency does not already ship and that this app's code
# actually needs, and explain why next to it; an unexplained rule is one nobody dares to delete.
# Release 构建的 R8 规则。只补依赖没有自带、且本项目确实需要的规则，并在旁边写明理由；
# 没有理由的规则以后没人敢删。
#
# Not needed (shipped by the libraries or no reflection involved):
# kotlinx.serialization, OkHttp, WorkManager, Navigation (type-safe routes are @Serializable), DataStore.
# 不需要（库自带或不涉及反射）：kotlinx.serialization、OkHttp、WorkManager、Navigation（类型安全路由是 @Serializable）、DataStore。
