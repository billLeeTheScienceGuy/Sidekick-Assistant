package com.sidekick.app.speaker

import com.sidekick.core.VoiceMatch

/**
 * [standard] and [beforeNotice] are this model's tuned owner rules when its stranger cohort ([cohortAsset]) is installed;
 * without the cohort the original VoiceMatch.Thresholds sets apply. Values and evidence: docs/evaluation/SPEAKER_TUNING.md.
 */
enum class SpeakerModel(val label: String, val fileName: String, val hash: String, val dimension: Int, val shortThreshold: Float,
    val cohortAsset: String, val standard: VoiceMatch.Thresholds, val beforeNotice: VoiceMatch.Thresholds,
    /** False: kept only so old profiles can be read and deleted; never selected for matching in the app. */
    val offered: Boolean = true) {
    RESNET("Standard · WeSpeaker", "voxceleb_resnet34_LM.onnx", "e9848563da86f263117134dfd7ad63c92355b37de492b55e325400c9d9c39012", 256, .80f,
        "speaker/cohort-resnet.bin", Tuned.RESNET_STANDARD, Tuned.RESNET_BEFORE_NOTICE, offered = false),
    CAMPPLUS("Short phrases · CAM++", "campplus-advanced.onnx", "aa3cfc16963a10586a9393f5035d6d6b57e98d358b347f80c2a30bf4f00ceba2", 192, .60f,
        "speaker/cohort-campplus.bin", Tuned.CAMPPLUS_STANDARD, Tuned.CAMPPLUS_BEFORE_NOTICE);
}

/**
 * Tuned on the laptop corpus (scripts/laptop-eval/; docs/evaluation/SPEAKER_TUNING.md): 381 stranger voices and about
 * 160,000 stranger turns in quiet, noisy, far and echo rooms. Rule: every bar sits at least .08 above the closest
 * stranger seen in any room; before notice in all-party places nothing changes.
 */
internal object Tuned {
    /** ResNet is not offered any more: strangers scored as high as the owner in the evaluation. Old rules kept as is. */
    val RESNET_STANDARD = VoiceMatch.Thresholds.STANDARD
    val RESNET_BEFORE_NOTICE = VoiceMatch.Thresholds.BEFORE_NOTICE
    /**
     * CAM++: quiet-room profile .64 (closest stranger .56), other settings .71 (closest .63), session voices .74
     * (closest .58). The cohort check (2 deviations above strangers) never rejected an owner turn and guards outliers.
     * Evaluation: owner 77% of turns (was 54%), strangers 0 of about 160,000.
     */
    val CAMPPLUS_STANDARD = VoiceMatch.Thresholds(both = .71f, balancedMin = .66f, balancedMean = .73f, consistency = .65f,
        cohortZ = 2f, quietOffset = .07f, session = VoiceMatch.SessionCarry(minimum = .74f, mean = .74f, profileFloor = .40f))
    val CAMPPLUS_BEFORE_NOTICE = VoiceMatch.Thresholds.BEFORE_NOTICE.copy(cohortZ = 2f)
}
