package com.pico.miniautochess.content

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import com.pico.miniautochess.rules.*
import com.pico.spatial.core.math.Vector3
import java.util.concurrent.ConcurrentHashMap

/** Short bounded SoundPool voices avoid the native seek/replay -38 path. */
class ChessAudio(private val context: Context) {
    private val clips = mutableMapOf<String, Int>()
    private val loaded = ConcurrentHashMap.newKeySet<Int>()
    private val activeStreams = ArrayDeque<Int>()
    private var pool: SoundPool? = null
    private var music: MediaPlayer? = null
    private var musicPlaying = false
    private var lastAttackSound = -1f
    private var loggedPlayback = false

    fun initialize() {
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attributes).build().also { sounds ->
            sounds.setOnLoadCompleteListener { _, id, status ->
                if (status == 0) {
                    loaded.add(id)
                    if (loaded.size == 20) Log.i("JadeChessAudio", "SOUND_BANK_READY decoded=20 status=0")
                } else Log.e("JadeChessAudio", "SOUND_LOAD_FAILED id=$id status=$status")
            }
            val names = listOf("hit", "shot", "magic_attack", "fall") + Catalog.heroList.map { "skill_${it.id}" }
            names.forEach { name ->
                context.assets.openFd("audio/$name.wav").use { clips[name] = sounds.load(it, 1) }
            }
        }
        music = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            context.assets.openFd("audio/garden_music.wav").use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            isLooping = true
            setVolume(.18f, .18f)
            setOnErrorListener { _, what, extra ->
                musicPlaying = false
                Log.e("JadeChessAudio", "MUSIC_ERROR what=$what extra=$extra"); true
            }
            prepare()
        }
        Log.i("JadeChessAudio", "AUDIO_READY uniqueSkills=16 sharedBasics=3 fall=1 voices=8 music=original_32bar_loop")
    }

    fun play(event: CombatEvent, hero: HeroDef, position: Vector3, battleTime: Float) {
        val skill = event.kind in listOf(CombatEventKind.CAST, CombatEventKind.HEAL, CombatEventKind.BLOCK)
        if (!skill && battleTime - lastAttackSound in 0f.. .065f) return
        val name = if (skill) "skill_${hero.id}" else when (event.kind) {
            CombatEventKind.SHOT -> if (hero.job == Job.ARCHER) "shot" else "magic_attack"
            CombatEventKind.FALL -> "fall"
            else -> "hit"
        }
        val id = clips[name]?.takeIf { it in loaded } ?: return
        val pan = (position.x / .95f).coerceIn(-1f, 1f)
        val volume = if (skill) .66f else .38f
        val stream = pool?.play(id, volume * (1f - pan.coerceAtLeast(0f) * .48f),
            volume * (1f + pan.coerceAtMost(0f) * .48f), if (skill) 2 else 1, 0, 1f) ?: 0
        if (stream > 0) {
            activeStreams.addLast(stream)
            // SoundPool stops completed voices; retain only a bounded recent list for pause.
            while (activeStreams.size > 32) activeStreams.removeFirst()
            if (!skill) lastAttackSound = battleTime
            if (!loggedPlayback) { loggedPlayback = true; Log.i("JadeChessAudio", "WATCHED_EVENT_PLAY accepted=true clip=$name") }
        }
    }
    fun setMusic(enabled: Boolean) {
        val player = music ?: return
        if (enabled == musicPlaying) return
        if (enabled) player.start() else player.pause()
        musicPlaying = enabled
        Log.i("JadeChessAudio", "MUSIC_PLAY playing=$enabled looping=true volume=.18")
    }
    fun silence() {
        activeStreams.forEach { pool?.stop(it) }; activeStreams.clear()
        lastAttackSound = -1f
    }
    fun destroyPlayers() {
        silence(); setMusic(false)
        music?.release(); music = null
        pool?.release(); pool = null
        clips.clear(); loaded.clear()
    }
    fun closeResources() = Unit
}
