package com.sidekick.app

import com.sidekick.core.*
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class IncomingText(val text: String, val timestamp: Long)
data class MessageEnvelope(val app: String, val conversation: String, val messages: List<IncomingText>)

/** New notifications only. Separate bounded context per conversation; no inbox database. */
class MessageInbox(private val settings: GuidanceSettings, private val temporary: TemporaryMemory, scope: CoroutineScope) {
    private data class Context(var text: String, val capturedAt: Instant, var lastSeen: Long, val seen: MutableSet<String> = linkedSetOf())
    private val contexts = linkedMapOf<String, Context>()
    private val gate = Mutex()
    private val queue = Channel<MessageEnvelope>(32, BufferOverflow.DROP_OLDEST)
    init {
        scope.launch {
            for (envelope in queue) {
                try { withContext(Dispatchers.Default) { ingest(envelope) } }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Never log message contents. Proposals remain bounded. */ }
            }
        }
        scope.launch { settings.messageNotifications.collect { if (!it) clear() } }
    }
    fun enqueue(envelope: MessageEnvelope) { queue.trySend(envelope) }
    suspend fun clear() = gate.withLock { contexts.clear() }

    suspend fun ingest(envelope: MessageEnvelope) = gate.withLock {
        if (!settings.messageNotifications.value || envelope.app !in settings.messageApps.value) return@withLock
        val now = System.currentTimeMillis()
        contexts.entries.removeAll { now - it.value.lastSeen > 2 * 3_600_000L }
        val key = hash(envelope.app + "|" + envelope.conversation)
        val incoming = envelope.messages.filter { it.timestamp >= settings.messagesEnabledSince && it.timestamp <= now + 60_000 }.takeLast(25)
        if (incoming.isEmpty()) return@withLock
        val context = contexts.getOrPut(key) { Context("", Instant.ofEpochMilli(incoming.first().timestamp), now) }
        for (message in incoming) {
            val text = message.text.trim().take(4000)
            if (text.isBlank()) continue
            val identity = hash(message.timestamp.toString() + text)
            if (!context.seen.add(identity)) continue
            while (context.seen.size > 256) context.seen.remove(context.seen.first())
            context.text = LiveText.append(context.text, text).takeLast(6000)
        }
        context.lastSeen = now
        while (contexts.size > 20) contexts.remove(contexts.keys.first())
        val source = Transcript(context.text, context.capturedAt, ZoneId.systemDefault(), Speaker.OTHER, true)
        val proposals = ActivityTiming.extract(source, informationalOnly = true).map {
            it.copy(sourceKey = key, needsReview = "Incoming message: sender attendance does not imply yours. " + it.needsReview)
        }
        temporary.addProposals(proposals)
    }
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
