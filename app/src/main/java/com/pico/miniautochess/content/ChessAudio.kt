package com.pico.miniautochess.content

import android.util.Log
import com.pico.miniautochess.rules.CombatEventKind
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ObjectAudioComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.audio.AudioPlayerController
import com.pico.spatial.core.ecs.resource.AudioResource
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Four bounded emitters for the watched battle. No background battle audio. */
class ChessAudio {
    private val resources = mutableMapOf<CombatEventKind, AudioResource>()
    private val emitters = mutableListOf<Entity>()
    private val players = mutableListOf<Map<CombatEventKind, AudioPlayerController>>()
    private var lastSound = -1f
    private var loggedPlayback = false

    suspend fun initialize(parent: Entity) {
        for ((kind, name) in listOf(CombatEventKind.HIT to "hit", CombatEventKind.SHOT to "shot",
            CombatEventKind.CAST to "cast", CombatEventKind.HEAL to "heal")) {
            val resource = withContext(Dispatchers.IO) {
                AudioResource.load(name, "asset://audio/$name.wav")
            }
            check(resource.valid) { "Invalid audio asset: $name" }
            resource.toGlobal()
            resources[kind] = resource
        }
        repeat(4) { index ->
            val entity = Entity()
            entity.setName("watchedAudio_$index")
            parent.addChild(entity)
            entity.components.set(ObjectAudioComponent(volume = .32f, soundRadiusLevel = .5f))
            emitters.add(entity)
            players.add(resources.mapValues { (_, resource) -> entity.prepareAudio(resource) })
        }
        Log.i("JadeChessAudio", "AUDIO_READY resources=4 emitters=4 mono=24000Hz")
    }

    fun play(kind: CombatEventKind, position: Vector3, battleTime: Float) {
        if (battleTime - lastSound < .08f && battleTime >= lastSound) return
        val index = players.indexOfFirst { map -> map.values.none { it.isPlaying() } }
        if (index < 0) return
        val player = players[index][kind] ?: return
        emitters[index].components[TransformComponent::class.java]!!.position = position
        player.seekTo(0)
        if (player.play()) {
            lastSound = battleTime
            if (!loggedPlayback) {
                loggedPlayback = true
                Log.i("JadeChessAudio", "WATCHED_EVENT_PLAY accepted=true kind=$kind")
            }
        }
    }

    fun silence() {
        players.forEach { map -> map.values.forEach { it.stop() } }
        lastSound = -1f
    }

    /** Called before scene entities are destroyed, followed by closing resources. */
    fun destroyPlayers() {
        silence()
        players.forEach { map -> map.values.forEach { it.close() } }
        players.clear()
        emitters.clear()
    }
    fun closeResources() { resources.values.forEach { it.close() }; resources.clear() }
}
