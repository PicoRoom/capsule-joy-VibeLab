package com.bujiaban.niuqu.pico.data.repository

import android.content.Context

interface OnboardingPreferences {
    var tutorialCompleted:Boolean
}

class LocalOnboardingPreferences(context:Context):OnboardingPreferences {
    private val prefs=context.getSharedPreferences("paper_capsule_onboarding",Context.MODE_PRIVATE)
    override var tutorialCompleted:Boolean
        get()=prefs.getBoolean("tutorial_completed",false)
        set(value){prefs.edit().putBoolean("tutorial_completed",value).apply()}
}
