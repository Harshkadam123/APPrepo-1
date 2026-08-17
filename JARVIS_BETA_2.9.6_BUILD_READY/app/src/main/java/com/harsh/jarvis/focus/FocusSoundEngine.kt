package com.harsh.jarvis.focus

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.sin
import kotlin.random.Random

/** Local ambient generator. No audio is downloaded, recorded, or uploaded. */
class FocusSoundEngine {
    enum class Sound { WHITE, BROWN, RAIN }
    private var track: AudioTrack? = null
    private var worker: Thread? = null
    fun play(sound:Sound=Sound.WHITE):Boolean { stop(); val rate=44100; val min=AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT); val t=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes((min*2).coerceAtLeast(rate/2)).setTransferMode(AudioTrack.MODE_STREAM).build();track=t;t.play();worker=Thread{val buf=ShortArray(rate/10);var brown=0.0;var phase=0.0;while(track===t){for(i in buf.indices){val x=when(sound){Sound.WHITE->Random.nextInt(-2500,2501).toDouble();Sound.BROWN->{brown=(brown+Random.nextDouble(-500.0,500.0)).coerceIn(-5000.0,5000.0);brown};Sound.RAIN->{phase+=0.15;Random.nextDouble(-1800.0,1800.0)+sin(phase)*900.0}};buf[i]=x.toInt().coerceIn(-32768,32767).toShort()};runCatching{t.write(buf,0,buf.size)}}}.apply{isDaemon=true;start()};return true}
    fun toggle():Boolean=if(track!=null){stop();false}else{play();true}
    fun stop(){runCatching{track?.stop();track?.release()};track=null;worker?.interrupt();worker=null}
}
