package com.bujiaban.niuqu.pico.scene

import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3

/** A single metre-based composition: catalogue, machine, optional album, then tools. */
object CapsuleLayout {
    const val SCENE_SCALE=1.45f
    val volumeCenter=Vector3(.10f,1.20f,.12f)
    val volumeSize=Vector3(2.1f,1.45f,1.3f)
    const val MACHINE_SCALE=1.65f
    val machinePosition=Vector3(0f,.86f,0f)
    // All three motion.json coinSlot anchors use y=.188m; align the pickup coin centre.
    val coinBoxPosition=Vector3(-.34f,machinePosition.y+.188f*MACHINE_SCALE-.056f,.24f)
    val coinHome=coinBoxPosition+Vector3(.035f,.056f,.023f)
    val capsuleRest=machinePosition+Vector3(.072f,.0255f,.156f)*MACHINE_SCALE

    const val TITLE_LOGO_PIXELS_W=600
    const val TITLE_LOGO_PIXELS_H=170
    const val TITLE_LOGO_WIDTH=.42f
    val titleLogoPosition=Vector3(0f,1.68f,.12f)
    const val TITLE_ACTIONS_PIXELS_W=480
    const val TITLE_ACTIONS_PIXELS_H=220
    const val TITLE_ACTIONS_WIDTH=.36f
    val titleActionsPosition=Vector3(0f,1.10f,.18f)
    const val TITLE_OPTIONS_PIXELS_W=480
    const val TITLE_OPTIONS_PIXELS_H=500
    const val TITLE_OPTIONS_WIDTH=.40f
    val titleOptionsPosition=Vector3(0f,1.32f,.18f)
    const val TUTORIAL_PIXELS_W=480
    const val TUTORIAL_PIXELS_H=540
    const val TUTORIAL_WIDTH=.38f
    val tutorialPosition=Vector3(-.45f,1.46f,.02f)

    const val SERIES_PIXELS_W=400
    const val SERIES_PIXELS_H=360
    const val SERIES_WIDTH=.34f
    val seriesCardPosition=Vector3(-.57f,1.38f,.10f)
    // Native miniatures sit in the card's clear centre, in front of its paper.
    val seriesPreviewPosition=seriesCardPosition+Vector3(0f,.0255f,.05f)

    const val CLAW_PANEL_PIXELS_W=640
    const val CLAW_PANEL_PIXELS_H=380
    const val CLAW_PANEL_WIDTH=.48f
    // A desk-like surface in front of the outlet, tilted up towards the player.
    val clawPanelPosition=Vector3(0f,.69f,.15f)
    val clawPanelRotation=EulerAngles(-40f,0f,0f)

    const val BUBBLE_WIDTH=.50f
    const val BUBBLE_PIXELS_W=900
    const val BUBBLE_PIXELS_H=240
    const val BUBBLE_TIP_X=.50f
    const val BUBBLE_TIP_Y=.99f
    val bubbleSource get()=machinePosition+Vector3(0f,.437f,.012f)*MACHINE_SCALE
    val bubblePosition get()=bubbleSource+Vector3(0f,BUBBLE_WIDTH*BUBBLE_PIXELS_H/BUBBLE_PIXELS_W*(BUBBLE_TIP_Y-.5f),0f)

    const val STATUS_PIXELS_W=640
    const val STATUS_PIXELS_H=64
    const val STATUS_WIDTH=.50f
    val statusPosition=Vector3(.55f,1.51f,0f)
    const val TOOLBAR_PIXELS_W=740
    const val TOOLBAR_PIXELS_H=240
    const val TOOLBAR_WIDTH=.50f
    val toolbarPosition=Vector3(.55f,seriesCardPosition.y,0f)

    const val STUDY_PIXELS_W=360
    const val STUDY_PIXELS_H=850
    const val STUDY_WIDTH=.31f
    val studyGuidePosition=Vector3(-.57f,1.22f,.10f)

    const val ALBUM_SCALE=.72f
    val albumPosition=Vector3(.67f,1.0f,.08f)
    val albumRotation=EulerAngles(-5f,-10f,0f)
}
