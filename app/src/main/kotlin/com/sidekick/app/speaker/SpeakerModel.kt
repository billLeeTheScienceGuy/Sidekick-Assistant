package com.sidekick.app.speaker

enum class SpeakerModel(val label: String, val fileName: String, val hash: String, val dimension: Int, val shortThreshold: Float) {
    RESNET("Standard · WeSpeaker", "voxceleb_resnet34_LM.onnx", "e9848563da86f263117134dfd7ad63c92355b37de492b55e325400c9d9c39012", 256, .80f),
    CAMPPLUS("Short phrases · CAM++", "campplus-advanced.onnx", "aa3cfc16963a10586a9393f5035d6d6b57e98d358b347f80c2a30bf4f00ceba2", 192, .60f);
}
