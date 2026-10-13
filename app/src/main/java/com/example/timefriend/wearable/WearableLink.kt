package com.example.timefriend.wearable

import android.content.Context
import android.util.Log
import com.example.timefriend.data.TimeRepository
import com.example.timefriend.data.db.PomodoroSessionEntity
import com.xiaomi.xms.wearable.Wearable
import com.xiaomi.xms.wearable.auth.AuthApi
import com.xiaomi.xms.wearable.auth.Permission
import com.xiaomi.xms.wearable.message.MessageApi
import com.xiaomi.xms.wearable.message.OnMessageReceivedListener
import com.xiaomi.xms.wearable.node.Node
import com.xiaomi.xms.wearable.node.NodeApi
import com.xiaomi.xms.wearable.service.OnServiceConnectionListener
import com.xiaomi.xms.wearable.service.ServiceApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

/**
 * 小米 Vela interconnect（小米互联 / LiFD）手机端接收器。
 *
 * 生命周期与唤起策略（对齐 Sportlink WatchBtSyncManager，2026-10 用户约定）：
 *  - **进程存活期间始终注册监听**（init 在 App 启动时调用）：蓝牙通道由小米运动健康
 *    按包名+签名路由，无 HTTP 那种无鉴权端口暴露，不适用「冷启动强制关闭」的安全策略。
 *  - **App 启动只注册监听、绝不 launchWearApp**；唤起表端建立会话仅发生在用户开启
 *    「设置 → 手表同步服务」时（见 [wakeWatch]）。该开关冷启动强制复位为关
 *    （[com.example.timefriend.TimeFriendApp]），因此打开 App 不会弹表端页面。
 *  - 「手表同步服务」开关关闭时不做任何反向操作（监听保持注册），仅不再唤起表端。
 *
 * 会话建立顺序（手机侧主导，跳步 = 全部报文失踪）：
 *   getConnectedNodes → checkPermission/requestPermission(DEVICE_MANAGER)
 *   → addListener → launchWearApp("/pages/sync")（仅开关开启时）
 *   → 应用层握手 hs(c0/c1) → 请求手表推送 req → 收 pomo 幂等写 Room → 回 ok
 *
 * 硬约束（vela-interconnect-sync 手册）：
 *  - 包名一致（com.example.timefriend）且与手表 rpk 同签名；
 *  - rpk 经运动健康开发者入口侧载；配套 App 进程存活（SDK 无 manifest 级唤入组件）。
 */
class WearableLink private constructor(
    private val context: Context,
    private val repository: TimeRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 单线程执行器：收包解析 + Room 写入全部串行，避免并发 */
    private val ioExecutor = Executors.newSingleThreadExecutor()

    /** 手表晚连 / 穿戴服务晚起时的兜底重查；任一节点注册成功后停表 */
    private val rebindExecutor = Executors.newSingleThreadScheduledExecutor()
    private val rebindScheduled = AtomicBoolean(false)

    /** 用户显式唤起请求（wakeWatch）尚未兑现的标记：监听注册成功那一刻据此拉起表端 */
    private val pendingWake = AtomicBoolean(false)

    /** start() 是否已成功初始化（幂等防护；未装运动健康时 API 抛错则保持 false） */
    private val started = AtomicBoolean(false)

    /** 已注册消息监听的 node 集合（binder 线程回调，需并发安全） */
    private val boundNodes: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap())

    private val datePattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2}")

    private var nodeApi: NodeApi? = null
    private var messageApi: MessageApi? = null
    private var authApi: AuthApi? = null
    private var serviceApi: ServiceApi? = null

    private val serviceListener = object : OnServiceConnectionListener {
        override fun onServiceConnected() {
            Log.i(TAG, "Xiaomi Wear service connected, bind nodes")
            bindNodes()
        }

        override fun onServiceDisconnected() {
            Log.w(TAG, "Xiaomi Wear service disconnected")
            boundNodes.clear()
            scheduleRebind()
        }
    }

    private val messageListener = OnMessageReceivedListener { nodeId, message ->
        ioExecutor.execute { handleMessage(nodeId, message) }
    }

    /** 进程存活期间初始化一次（AppContainer 初始化时调用；重复调用幂等）。
     *  只注册监听，绝不 launchWearApp —— 唤起仅由设置页开关触发。 */
    @Synchronized
    fun start() {
        if (started.get()) {
            Log.d(TAG, "start: already initialized")
            return
        }
        try {
            nodeApi = Wearable.getNodeApi(context)
            messageApi = Wearable.getMessageApi(context)
            authApi = Wearable.getAuthApi(context)
            serviceApi = Wearable.getServiceApi(context)
            serviceApi?.registerServiceConnectionListener(serviceListener)
        } catch (e: Throwable) {
            // 未装小米运动健康/穿戴时 API 可能直接抛错——手表同步静默降级，不影响 App 其余功能
            Log.e(TAG, "start failed (Xiaomi Wear app missing?)", e)
            return
        }
        started.set(true)
        Log.i(TAG, "start ok (listener-only; watch NOT woken — use wakeWatch())")
        bindNodes()
        scheduleRebind()
    }

    /** 查询已连接手表并逐个注册消息监听（幂等） */
    private fun bindNodes() {
        val api = nodeApi ?: return
        try {
            api.connectedNodes
                .addOnSuccessListener { nodes ->
                    if (nodes.isEmpty()) {
                        Log.i(TAG, "no connected watch node")
                        scheduleRebind()
                    } else {
                        nodes.forEach { ensureListener(it) }
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "getConnectedNodes failed: ${e.message}")
                    scheduleRebind()
                }
        } catch (e: Throwable) {
            Log.w(TAG, "bindNodes threw: ${e.message}")
            scheduleRebind()
        }
    }

    private fun scheduleRebind() {
        if (boundNodes.isNotEmpty()) return
        if (rebindScheduled.compareAndSet(false, true)) {
            rebindExecutor.scheduleWithFixedDelay({
                try {
                    if (boundNodes.isEmpty()) {
                        Log.d(TAG, "rebind tick")
                        bindNodes()
                    } else {
                        rebindScheduled.set(false)
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "rebind threw: ${e.message}")
                }
            }, REBIND_DELAY_S, REBIND_DELAY_S, TimeUnit.SECONDS)
        }
    }

    /** 单节点授权 + 注册监听（首次调用授权默认授予 DEVICE_MANAGER） */
    private fun ensureListener(node: Node) {
        val id = node.id
        // 先占位再注册：start 直查与 onServiceConnected 回调会并发到这，若等
        // addListener 成功才登记，两路都会通过检查 → 同一 node 双注册 → 消息双份投递
        if (!boundNodes.add(id)) return
        val auth = authApi ?: return
        try {
            auth.checkPermission(id, Permission.DEVICE_MANAGER)
                .addOnSuccessListener { granted ->
                    if (granted) registerListener(id)
                    else auth.requestPermission(id, Permission.DEVICE_MANAGER)
                        .addOnSuccessListener { registerListener(id) }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "requestPermission failed: ${e.message}")
                            boundNodes.remove(id)
                        }
                }
                .addOnFailureListener {
                    // 授权状态查询失败不拦注册——直接试，失败由 addListener 的回调报告
                    registerListener(id)
                }
        } catch (e: Throwable) {
            Log.w(TAG, "ensureListener threw: ${e.message}")
            boundNodes.remove(id)
        }
    }

    private fun registerListener(id: String) {
        val msg = messageApi ?: return
        try {
            msg.addListener(id, messageListener)
                .addOnSuccessListener {
                    Log.i(TAG, "message listener registered for node $id")
                    // 启动路径到此为止（不唤起表端）；仅当用户已通过 wakeWatch
                    // 显式请求唤起（手表晚连场景）时才拉起
                    if (pendingWake.get()) launchWearApp(id)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "addListener failed: ${e.message}")
                    boundNodes.remove(id) // 退占位，允许下一轮 rebind 重试
                }
        } catch (e: Throwable) {
            Log.w(TAG, "addListener threw: ${e.message}")
            boundNodes.remove(id)
        }
    }

    /** 显式唤起表端并建立会话（设置页「手表同步服务」开启时调用，App 启动绝不调用）：
     *  监听已注册的直接逐节点拉起；还没绑定上就先挂起请求，
     *  监听注册成功那一刻自动拉起（手表晚连 / 穿戴服务晚起都覆盖）。 */
    @Synchronized
    fun wakeWatch() {
        val nodes = boundNodes.toList()
        if (nodes.isEmpty()) {
            pendingWake.set(true)
            bindNodes()
        } else {
            nodes.forEach { launchWearApp(it) }
        }
    }

    /** 经系统 API 拉起手表端同步页（interconnect 会话由手机侧发起建立，
     *  手表端 onopen / 应用层握手依赖它），随后主动发握手 + 请求手表推送。 */
    private fun launchWearApp(id: String) {
        val node = nodeApi ?: return
        try {
            node.launchWearApp(id, WATCH_SYNC_PAGE)
                .addOnSuccessListener {
                    Log.i(TAG, "launchWearApp ok")
                    pendingWake.set(false)
                    // 手机侧主动发握手（表端回 c=1 即双向可用），随后请求手表推送数据
                    sendJson(id, """{"t":"hs","c":0}""")
                    sendJson(id, """{"t":"req"}""")
                }
                .addOnFailureListener { e -> Log.w(TAG, "launchWearApp failed: ${e.message}") }
        } catch (e: Throwable) {
            Log.w(TAG, "launchWearApp threw: ${e.message}")
        }
    }

    // ── 消息处理（始终在 ioExecutor 串行执行）──────────────────────────────

    private fun handleMessage(nodeId: String, raw: ByteArray?) {
        if (raw == null || raw.isEmpty()) return
        val obj = try {
            JSONObject(String(raw, StandardCharsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "non-JSON message (${raw.size}B), ignored")
            return
        }
        when (obj.optString("t")) {
            // 应用层握手：手机主动发 c=0，表端回 c=1 = watch→phone 方向确认可用；
            // 表端也可能先发 c=0（如 resume 场景），回 c=1 告知双向走通
            "hs" -> {
                val c = obj.optInt("c", -1)
                if (c == 0) sendJson(nodeId, """{"t":"hs","c":1}""")
                if (c == 1) Log.i(TAG, "hs handshake confirmed (watch c=1)")
            }
            "pomo" -> {
                val arr = obj.optJSONArray("d")
                if (arr != null) {
                    val sessions = parseSessions(arr)
                    if (sessions.isNotEmpty()) {
                        upsertSessions(sessions)
                        Log.i(TAG, "received ${sessions.size} pomodoro session(s) from watch")
                    } else {
                        Log.d(TAG, "pomo payload empty / all invalid")
                    }
                }
                // 无论有无新数据都回执，表端据此把 UI 置为「已同步」
                sendJson(nodeId, """{"t":"ok"}""")
            }
            else -> Log.d(TAG, "unknown msg t=${obj.optString("t")}")
        }
    }

    /** 表端逐条番茄钟：{date, focusMinutes, restMinutes, createdAt}。脏数据（缺字段/日期格式错）跳过。 */
    private fun parseSessions(arr: JSONArray): List<PomodoroSessionEntity> {
        val out = mutableListOf<PomodoroSessionEntity>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val date = o.optString("date")
            if (!datePattern.matcher(date).matches()) continue
            val focus = o.optInt("focusMinutes", -1)
            val rest = o.optInt("restMinutes", -1)
            val createdAt = o.optLong("createdAt", -1L)
            if (focus < 0 || rest < 0 || createdAt < 0) continue
            out += PomodoroSessionEntity(
                date = date,
                focusMinutes = focus,
                restMinutes = rest,
                createdAt = createdAt
            )
        }
        return out
    }

    /** 按 (date|createdAt|focusMinutes|restMinutes) 幂等写入（repository 内去重），重复同步不产生重复行。 */
    private fun upsertSessions(sessions: List<PomodoroSessionEntity>) {
        scope.launch {
            try {
                repository.upsertWatchPomodoroSessions(sessions)
            } catch (e: Exception) {
                Log.e(TAG, "upsertSessions failed", e)
            }
        }
    }

    private fun sendJson(nodeId: String, json: String) {
        val msg = messageApi ?: return
        try {
            msg.sendMessage(nodeId, json.toByteArray(StandardCharsets.UTF_8))
                .addOnFailureListener { e -> Log.w(TAG, "sendJson failed: ${e.message}") }
        } catch (e: Throwable) {
            Log.w(TAG, "sendJson threw: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "TimefriendWearable"
        private const val WATCH_SYNC_PAGE = "/pages/sync"
        private const val REBIND_DELAY_S = 30L

        /** 「手表同步服务」开关持久化（设置页读写；冷启动由 TimeFriendApp 强制复位为关闭） */
        const val PREFS_NAME = "timefriend_sync"
        const val KEY_WATCH_SYNC_ENABLED = "watch_sync_enabled"

        @Volatile
        private var INSTANCE: WearableLink? = null

        fun get(context: Context, repository: TimeRepository): WearableLink =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: WearableLink(context.applicationContext, repository).also { INSTANCE = it }
            }
    }
}
