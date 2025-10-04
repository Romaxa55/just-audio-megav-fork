package com.ryanheise.just_audio;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Custom AudioProcessor for real-time FFT visualization
 * 
 * Портировано из Yandex Music (InterfaceC30448Ih0 в GB2.java)
 * Перехватывает PCM данные между декодером и AudioTrack
 */
@UnstableApi
public class VisualizerAudioProcessor implements AudioProcessor {
    
    // Callback для PCM данных
    public interface OnPcmDataListener {
        void onPcmData(byte[] pcmData);
    }
    
    private OnPcmDataListener listener;
    
    // Конфигурация аудио
    private AudioFormat inputAudioFormat = AudioFormat.NOT_SET;
    private AudioFormat outputAudioFormat = AudioFormat.NOT_SET;
    private ByteBuffer buffer = EMPTY_BUFFER;
    private boolean inputEnded = false;
    
    // Throttling для снижения нагрузки
    private long lastSendTime = 0;
    private static final long MIN_SEND_INTERVAL = 16; // ~60 FPS
    
    private static final ByteBuffer EMPTY_BUFFER = 
        ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
    
    public void setListener(OnPcmDataListener listener) {
        this.listener = listener;
    }
    
    // Alias для совместимости
    public void setOnAudioDataListener(OnPcmDataListener listener) {
        setListener(listener);
    }
    
    /**
     * Конфигурация процессора (вызывается ExoPlayer при инициализации)
     */
    @Override
    public AudioFormat configure(AudioFormat inputAudioFormat) throws UnhandledAudioFormatException {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            android.util.Log.w("VisualizerAudioProcessor", 
                "Unsupported encoding: " + inputAudioFormat.encoding + ", expected PCM_16BIT");
            throw new UnhandledAudioFormatException(inputAudioFormat);
        }
        
        this.inputAudioFormat = inputAudioFormat;
        this.outputAudioFormat = inputAudioFormat;
        
        android.util.Log.d("VisualizerAudioProcessor", 
            "Configured: " + inputAudioFormat.sampleRate + "Hz, " + inputAudioFormat.channelCount + "ch");
        
        return outputAudioFormat;
    }
    
    /**
     * Проверка активности
     */
    @Override
    public boolean isActive() {
        return inputAudioFormat != AudioFormat.NOT_SET;
    }
    
    /**
     * Обработка входящего буфера (ЗДЕСЬ перехватываем PCM!)
     */
    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        if (!isActive() || inputBuffer.remaining() == 0) {
            return;
        }
        
        int remaining = inputBuffer.remaining();
        
        // ✅ Подготавливаем output buffer (как в Yandex Music)
        if (buffer.capacity() < remaining) {
            buffer = ByteBuffer.allocateDirect(remaining)
                .order(ByteOrder.nativeOrder());
        }
        
        buffer.clear();
        
        // ✅ Копируем данные для отправки в Flutter (БЕЗ изменения позиции inputBuffer)
        long currentTime = System.currentTimeMillis();
        if (listener != null && (currentTime - lastSendTime) >= MIN_SEND_INTERVAL) {
            try {
                byte[] pcmData = new byte[remaining];
                
                // Дублируем буфер чтобы не менять позицию оригинала
                ByteBuffer duplicate = inputBuffer.duplicate();
                duplicate.get(pcmData);
                
                // Отправляем в Flutter (асинхронно, не блокируем)
                listener.onPcmData(pcmData);
                lastSendTime = currentTime;
                
                // Логируем каждую секунду
                if ((currentTime / 1000) % 1 == 0) {
                    android.util.Log.d("VisualizerAudioProcessor", "✅ Sending PCM: " + remaining + " bytes");
                }
                
            } catch (Exception e) {
                android.util.Log.e("VisualizerAudioProcessor", "❌ Error copying PCM data", e);
            }
        }
        
        // ✅ Pass-through: копируем inputBuffer → buffer для AudioTrack
        buffer.put(inputBuffer);
        buffer.flip();
    }
    
    /**
     * Возврат обработанного буфера
     */
    @Override
    public ByteBuffer getOutput() {
        ByteBuffer output = buffer;
        buffer = EMPTY_BUFFER;
        return output;
    }
    
    /**
     * Сигнал окончания потока
     */
    @Override
    public void queueEndOfStream() {
        inputEnded = true;
    }
    
    /**
     * Проверка окончания
     */
    @Override
    public boolean isEnded() {
        return inputEnded && buffer == EMPTY_BUFFER;
    }
    
    /**
     * Сброс состояния
     */
    @Override
    public void flush() {
        buffer = EMPTY_BUFFER;
        inputEnded = false;
    }
    
    /**
     * Полный сброс
     */
    @Override
    public void reset() {
        flush();
        inputAudioFormat = AudioFormat.NOT_SET;
        outputAudioFormat = AudioFormat.NOT_SET;
        listener = null;
        android.util.Log.d("VisualizerAudioProcessor", "Reset");
    }
}
