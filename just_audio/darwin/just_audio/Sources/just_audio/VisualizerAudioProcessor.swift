//
//  VisualizerAudioProcessor.swift
//  just_audio
//
//  Custom audio processor для real-time FFT визуализации (macOS/iOS)
//  Портировано с Android ExoPlayer AudioProcessor
//

import Foundation
import AVFoundation

@objc public class VisualizerAudioProcessor: NSObject {
    
    // Callback для PCM данных
    @objc public typealias PcmDataCallback = ([Int16]) -> Void
    
    private var audioEngine: AVAudioEngine?
    private var playerNode: AVAudioPlayerNode?
    private var pcmCallback: PcmDataCallback?
    
    // Throttling для снижения нагрузки
    private var lastSendTime: TimeInterval = 0
    private let minSendInterval: TimeInterval = 0.016 // ~60 FPS
    
    @objc public override init() {
        super.init()
        NSLog("🎛️ VisualizerAudioProcessor initialized")
    }
    
    /**
     * Установить callback для PCM данных
     */
    @objc public func setOnAudioDataListener(_ callback: @escaping PcmDataCallback) {
        pcmCallback = callback
        NSLog("✅ PCM callback set")
    }
    
    /**
     * Подключиться к AVPlayer для перехвата аудио
     */
    @objc public func attachToPlayer(_ player: AVPlayer) {
        NSLog("🔗 Attaching audio processor to player...")
        
        // Создаём AVAudioEngine
        audioEngine = AVAudioEngine()
        playerNode = AVAudioPlayerNode()
        
        guard let engine = audioEngine, let node = playerNode else {
            NSLog("❌ Failed to create audio engine")
            return
        }
        
        engine.attach(node)
        
        // Подключаем к mixer
        let format = engine.mainMixerNode.outputFormat(forBus: 0)
        engine.connect(node, to: engine.mainMixerNode, format: format)
        
        // Устанавливаем tap для перехвата PCM данных
        engine.mainMixerNode.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, time in
            self?.processPcmData(buffer: buffer)
        }
        
        // Запускаем engine
        do {
            try engine.start()
            NSLog("✅ Audio engine started for PCM capture")
        } catch {
            NSLog("❌ Failed to start audio engine: \(error)")
        }
    }
    
    /**
     * Отключиться от плеера
     */
    @objc public func detachFromPlayer() {
        NSLog("🔌 Detaching audio processor...")
        audioEngine?.stop()
        audioEngine?.mainMixerNode.removeTap(onBus: 0)
        audioEngine = nil
        playerNode = nil
    }
    
    /**
     * Обработка PCM данных (как в Android версии)
     */
    private func processPcmData(buffer: AVAudioPCMBuffer) {
        // Throttling - не чаще 60 FPS
        let currentTime = Date().timeIntervalSince1970
        guard currentTime - lastSendTime >= minSendInterval else {
            return
        }
        lastSendTime = currentTime
        
        guard let channelData = buffer.floatChannelData else {
            return
        }
        
        let frameLength = Int(buffer.frameLength)
        let channelCount = Int(buffer.format.channelCount)
        
        // Берём первый канал (mono)
        var pcmData: [Int16] = []
        pcmData.reserveCapacity(min(frameLength, 512))
        
        for frame in 0..<min(frameLength, 512) { // Ограничиваем 512 сэмплами для FFT
            let sample = channelData[0][frame]
            // Конвертируем Float (-1.0...1.0) в Int16 (-32768...32767)
            let intSample = Int16(max(-32768, min(32767, sample * 32767.0)))
            pcmData.append(intSample)
        }
        
        // Отправляем в callback
        pcmCallback?(pcmData)
    }
    
    deinit {
        detachFromPlayer()
    }
}

