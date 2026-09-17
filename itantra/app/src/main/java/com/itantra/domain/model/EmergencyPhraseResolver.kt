package com.itantra.domain.model

object EmergencyPhraseResolver {

    fun resolve(code: EmergencyCode, language: LanguageCode?): String {
        return when (language) {
            LanguageCode.HINDI -> resolveHindi(code)
            LanguageCode.ENGLISH -> resolveEnglish(code)
            else -> resolveEnglish(code) // Fallback to English
        }
    }

    private fun resolveEnglish(code: EmergencyCode): String = when (code) {
        EmergencyCode.HELP_REQUIRED -> "Help required."
        EmergencyCode.MEDICAL_EMERGENCY -> "Immediate medical assistance required."
        EmergencyCode.FIRE -> "Fire emergency."
        EmergencyCode.FLOOD -> "Flood danger."
        EmergencyCode.LANDSLIDE -> "Landslide has occurred."
        EmergencyCode.EVACUATE -> "Evacuate immediately to a safe place."
        EmergencyCode.ROAD_BLOCKED -> "Main road is blocked."
        EmergencyCode.SEND_RESCUE_TEAM -> "Please send a rescue team immediately."
        EmergencyCode.DANGER -> "Danger ahead."
        EmergencyCode.ALL_CLEAR -> "All clear. Area is safe."
    }

    private fun resolveHindi(code: EmergencyCode): String = when (code) {
        EmergencyCode.HELP_REQUIRED -> "सहायता की आवश्यकता है।"
        EmergencyCode.MEDICAL_EMERGENCY -> "तुरंत चिकित्सा सहायता की आवश्यकता है।"
        EmergencyCode.FIRE -> "आग लगी है।"
        EmergencyCode.FLOOD -> "बाढ़ का खतरा है।"
        EmergencyCode.LANDSLIDE -> "भूस्खलन हुआ है।"
        EmergencyCode.EVACUATE -> "तुरंत सुरक्षित स्थान पर जाएँ।"
        EmergencyCode.ROAD_BLOCKED -> "मुख्य रास्ता बंद है।"
        EmergencyCode.SEND_RESCUE_TEAM -> "कृपया तुरंत बचाव दल भेजें।"
        EmergencyCode.DANGER -> "आगे खतरा है।"
        EmergencyCode.ALL_CLEAR -> "सब ठीक है। क्षेत्र सुरक्षित है।"
    }
}
