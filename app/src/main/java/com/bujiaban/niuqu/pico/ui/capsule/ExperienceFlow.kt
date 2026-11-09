package com.bujiaban.niuqu.pico.ui.capsule

/** The welcome menu pauses the current round; tutorial progress follows committed game actions. */
enum class ExperienceScreen { HOME, PLAY, TUTORIAL, COMPLETE }
enum class TutorialStep { POSITION, COIN, TURN, TAKE, OPEN, STORE }

data class ExperienceFlow(
    val screen:ExperienceScreen=ExperienceScreen.HOME,
    val step:TutorialStep=TutorialStep.POSITION,
    val tutorialCompleted:Boolean=false,
    val tutorialToyId:Long?=null,
) {
    val gameplayActive get()=screen==ExperienceScreen.PLAY ||
        (screen==ExperienceScreen.TUTORIAL && step!=TutorialStep.POSITION)
}
