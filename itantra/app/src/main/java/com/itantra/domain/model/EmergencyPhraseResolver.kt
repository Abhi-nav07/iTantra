package com.itantra.domain.model

object EmergencyPhraseResolver {

    fun resolve(code: EmergencyCode, language: LanguageCode?): String {
        return when (language) {
            LanguageCode.HINDI -> resolveHindi(code)
            LanguageCode.ENGLISH -> resolveEnglish(code)
            LanguageCode.BENGALI -> resolveBengali(code)
            LanguageCode.GUJARATI -> resolveGujarati(code)
            LanguageCode.MARATHI -> resolveMarathi(code)
            LanguageCode.KANNADA -> resolveKannada(code)
            LanguageCode.MALAYALAM -> resolveMalayalam(code)
            LanguageCode.TAMIL -> resolveTamil(code)
            LanguageCode.TELUGU -> resolveTelugu(code)
            LanguageCode.ODIA -> resolveOdia(code)
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

    // [REVIEW PENDING] Non-human verified translations for 8F compliance

    private fun resolveBengali(code: EmergencyCode): String = when (code) {
        EmergencyCode.HELP_REQUIRED -> "সাহায্য প্রয়োজন।"
        EmergencyCode.MEDICAL_EMERGENCY -> "জরুরী চিকিৎসা সহায়তা প্রয়োজন।"
        EmergencyCode.FIRE -> "আগুন লেগেছে।"
        EmergencyCode.FLOOD -> "বন্যার বিপদ।"
        EmergencyCode.LANDSLIDE -> "ভূমিধস হয়েছে।"
        EmergencyCode.EVACUATE -> "অবিলম্বে নিরাপদ স্থানে সরে যান।"
        EmergencyCode.ROAD_BLOCKED -> "প্রধান রাস্তা বন্ধ।"
        EmergencyCode.SEND_RESCUE_TEAM -> "দয়া করে অবিলম্বে একটি উদ্ধারকারী দল পাঠান।"
        EmergencyCode.DANGER -> "সামনে বিপদ।"
        EmergencyCode.ALL_CLEAR -> "সব ঠিক আছে। এলাকা নিরাপদ।"
    }

    private fun resolveGujarati(code: EmergencyCode): String = when (code) {
        EmergencyCode.HELP_REQUIRED -> "મદદની જરૂર છે."
        EmergencyCode.MEDICAL_EMERGENCY -> "તાત્કાલિક તબીબી સહાયની જરૂર છે."
        EmergencyCode.FIRE -> "આગ લાગી છે."
        EmergencyCode.FLOOD -> "પૂરનો ખતરો છે."
        EmergencyCode.LANDSLIDE -> "ભૂસ્ખલન થયું છે."
        EmergencyCode.EVACUATE -> "તાત્કાલિક સુરક્ષિત સ્થળે જાઓ."
        EmergencyCode.ROAD_BLOCKED -> "મુખ્ય રસ્તો બંધ છે."
        EmergencyCode.SEND_RESCUE_TEAM -> "કૃપા કરીને તાત્કાલિક બચાવ ટીમ મોકલો."
        EmergencyCode.DANGER -> "આગળ ખતરો છે."
        EmergencyCode.ALL_CLEAR -> "બધું બરાબર છે. વિસ્તાર સુરક્ષિત છે."
    }

    private fun resolveMarathi(code: EmergencyCode): String = when (code) {
        EmergencyCode.HELP_REQUIRED -> "मदतीची गरज आहे."
        EmergencyCode.MEDICAL_EMERGENCY -> "तातडीने वैद्यकीय मदतीची आवश्यकता आहे."
        EmergencyCode.FIRE -> "आग लागली आहे."
        EmergencyCode.FLOOD -> "पुराचा धोका आहे."
        EmergencyCode.LANDSLIDE -> "भूस्खलन झाले आहे."
        EmergencyCode.EVACUATE -> "तातडीने सुरक्षित ठिकाणी जा."
        EmergencyCode.ROAD_BLOCKED -> "मुख्य रस्ता बंद आहे."
        EmergencyCode.SEND_RESCUE_TEAM -> "कृपया तातडीने बचाव पथक पाठवा."
        EmergencyCode.DANGER -> "पुढे धोका आहे."
        EmergencyCode.ALL_CLEAR -> "सर्व ठीक आहे. परिसर सुरक्षित आहे."
    }

    private fun resolveKannada(code: EmergencyCode): String = when (code) {
        EmergencyCode.HELP_REQUIRED -> "ಸಹಾಯ ಬೇಕಿದೆ."
        EmergencyCode.MEDICAL_EMERGENCY -> "ತುರ್ತು ವೈದ್ಯಕೀಯ ನೆರವು ಬೇಕಿದೆ."
        EmergencyCode.FIRE -> "ಬೆಂಕಿ ಅನಾಹುತ."
        EmergencyCode.FLOOD -> "ಪ್ರವಾಹದ ಅಪಾಯ."
        EmergencyCode.LANDSLIDE -> "ಭೂಕುಸಿತ ಸಂಭವಿಸಿದೆ."
        EmergencyCode.EVACUATE -> "ತಕ್ಷಣ ಸುರಕ್ಷಿತ ಸ್ಥಳಕ್ಕೆ ತೆರಳಿ."
        EmergencyCode.ROAD_BLOCKED -> "ಮುಖ್ಯ ರಸ್ತೆ ಬಂದ್ ಆಗಿದೆ."
        EmergencyCode.SEND_RESCUE_TEAM -> "ದಯವಿಟ್ಟು ತಕ್ಷಣ ರಕ್ಷಣಾ ತಂಡವನ್ನು ಕಳುಹಿಸಿ."
        EmergencyCode.DANGER -> "ಮುಂದೆ ಅಪಾಯವಿದೆ."
        EmergencyCode.ALL_CLEAR -> "ಎಲ್ಲವೂ ಸರಿ ಇದೆ. ಪ್ರದೇಶ ಸುರಕ್ಷಿತವಾಗಿದೆ."
    }

    private fun resolveMalayalam(code: EmergencyCode): String = when (code) {
        EmergencyCode.HELP_REQUIRED -> "സഹായം ആവശ്യമാണ്."
        EmergencyCode.MEDICAL_EMERGENCY -> "അടിയന്തര വൈദ്യസഹായം ആവശ്യമാണ്."
        EmergencyCode.FIRE -> "തീപിടുത്തം."
        EmergencyCode.FLOOD -> "വെള്ളപ്പൊക്ക അപകടം."
        EmergencyCode.LANDSLIDE -> "ഉരുൾപൊട്ടൽ ഉണ്ടായിട്ടുണ്ട്."
        EmergencyCode.EVACUATE -> "ഉടൻ സുരക്ഷിതമായ സ്ഥലത്തേക്ക് മാറുക."
        EmergencyCode.ROAD_BLOCKED -> "പ്രധാന റോഡ് അടഞ്ഞു."
        EmergencyCode.SEND_RESCUE_TEAM -> "ദയവായി ഉടൻ ഒരു രക്ഷാപ്രവർത്തക സംഘത്തെ അയക്കുക."
        EmergencyCode.DANGER -> "മുന്നിൽ അപകടം."
        EmergencyCode.ALL_CLEAR -> "എല്ലാം സുരക്ഷിതമാണ്. പ്രദേശം സുരക്ഷിതമാണ്."
    }

    private fun resolveTamil(code: EmergencyCode): String = when (code) {
        EmergencyCode.HELP_REQUIRED -> "உதவி தேவை."
        EmergencyCode.MEDICAL_EMERGENCY -> "அவசர மருத்துவ உதவி தேவை."
        EmergencyCode.FIRE -> "தீ விபத்து."
        EmergencyCode.FLOOD -> "வெள்ள அபாயம்."
        EmergencyCode.LANDSLIDE -> "நிலச்சரிவு ஏற்பட்டுள்ளது."
        EmergencyCode.EVACUATE -> "உடனடியாக பாதுகாப்பான இடத்திற்கு செல்லவும்."
        EmergencyCode.ROAD_BLOCKED -> "முக்கிய சாலை மூடப்பட்டுள்ளது."
        EmergencyCode.SEND_RESCUE_TEAM -> "தயவுசெய்து உடனடியாக மீட்பு குழுவை அனுப்பவும்."
        EmergencyCode.DANGER -> "முன்னால் ஆபத்து."
        EmergencyCode.ALL_CLEAR -> "எல்லாம் சரி. பகுதி பாதுகாப்பானது."
    }

    private fun resolveTelugu(code: EmergencyCode): String = when (code) {
        EmergencyCode.HELP_REQUIRED -> "సహాయం కావాలి."
        EmergencyCode.MEDICAL_EMERGENCY -> "తక్షణ వైద్య సహాయం కావాలి."
        EmergencyCode.FIRE -> "అగ్ని ప్రమాదం."
        EmergencyCode.FLOOD -> "వరద ప్రమాదం."
        EmergencyCode.LANDSLIDE -> "కొండచరియలు విరిగిపడ్డాయి."
        EmergencyCode.EVACUATE -> "వెంటనే సురక్షిత ప్రదేశానికి వెళ్ళండి."
        EmergencyCode.ROAD_BLOCKED -> "ప్రధాన రహదారి మూసివేయబడింది."
        EmergencyCode.SEND_RESCUE_TEAM -> "దయచేసి వెంటనే రెస్క్యూ బృందాన్ని పంపండి."
        EmergencyCode.DANGER -> "ముందు ప్రమాదం ఉంది."
        EmergencyCode.ALL_CLEAR -> "అంతా సురక్షితం. ప్రాంతం సురక్షితం."
    }

    private fun resolveOdia(code: EmergencyCode): String = when (code) {
        EmergencyCode.HELP_REQUIRED -> "ସାହାଯ୍ୟ ଆବଶ୍ୟକ।"
        EmergencyCode.MEDICAL_EMERGENCY -> "ତୁରନ୍ତ ଡାକ୍ତରୀ ସହାୟତା ଆବଶ୍ୟକ।"
        EmergencyCode.FIRE -> "ନିଆଁ ଲାଗିଛି।"
        EmergencyCode.FLOOD -> "ବନ୍ୟା ବିପଦ।"
        EmergencyCode.LANDSLIDE -> "ଭୂସ୍ଖଳନ ହୋଇଛି।"
        EmergencyCode.EVACUATE -> "ତୁରନ୍ତ ଏକ ସୁରକ୍ଷିତ ସ୍ଥାନକୁ ଯାଆନ୍ତୁ।"
        EmergencyCode.ROAD_BLOCKED -> "ମୁଖ୍ୟ ରାସ୍ତା ବନ୍ଦ ଅଛି।"
        EmergencyCode.SEND_RESCUE_TEAM -> "ଦୟାକରି ତୁରନ୍ତ ଏକ ଉଦ୍ଧାରକାରୀ ଦଳ ପଠାନ୍ତୁ।"
        EmergencyCode.DANGER -> "ଆଗରେ ବିପଦ।"
        EmergencyCode.ALL_CLEAR -> "ସବୁ ଠିକ୍ ଅଛି। ଅଞ୍ଚଳ ସୁରକ୍ଷିତ ଅଛି।"
    }
}
