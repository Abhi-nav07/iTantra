# iTantra Emergency Phrase Review Matrix

This matrix documents the 10 predefined safety-critical emergency codes across all 10 supported languages, and specifies the human review status for operational safety certification.

> **Review Status Definitions**:
> - **`HUMAN_VERIFIED`**: Validated and signed off by a certified fluent native speaker for emergency operational use.
> - **`SOURCE_DEFINED`**: Canonical source phrase formulated by the engineering team (English & Hindi base phrases).
> - **`REVIEW_REQUIRED`**: Programmatically provided or draft translation requiring fluent native speaker verification before safety-critical deployment.

> **CRITICAL PROTOCOL NOTICE**:
> - No translation is automatically marked `HUMAN_VERIFIED`.
> - Overall review status for the current vernacular emergency phrase pack is **`REVIEW_REQUIRED`**.
> - Emergency codes bypass dynamic Machine Translation (MT) at runtime and resolve deterministically to these vetted local strings to eliminate model hallucination risks during distress situations.

---

## 1. Complete 10x10 Emergency Phrase Matrix

| Emergency Code | English (`en`) | Hindi (`hi`) | Bengali (`bn`) | Gujarati (`gu`) | Marathi (`mr`) | Kannada (`kn`) | Malayalam (`ml`) | Tamil (`ta`) | Telugu (`te`) | Odia (`or`) | Review Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`HELP_REQUIRED`** | Help required. | सहायता की आवश्यकता है। | সাহায্য প্রয়োজন। | મદદની જરૂર છે. | मदतीची गरज आहे. | ಸಹಾಯ ಬೇಕಿದೆ. | സഹായം ആവശ്യമാണ്. | உதவி தேவை. | సహాయం కావాలి. | ସାହାଯ୍ୟ ଆବଶ୍ୟକ। | **REVIEW_REQUIRED** |
| **`MEDICAL_EMERGENCY`** | Immediate medical assistance required. | तुरंत चिकित्सा सहायता की आवश्यकता है। | জরুরী চিকিৎসা সহায়তা প্রয়োজন। | તાત્કાલિક તબીબી સહાયની જરૂર છે. | तातडीने वैद्यकीय मदतीची आवश्यकता आहे. | ತುರ್ತು ವೈದ್ಯಕೀಯ ನೆರವು ಬೇಕಿದೆ. | അടിയന്തര വൈദ്യസഹായം ആവശ്യമാണ്. | அவசர மருத்துவ உதவி தேவை. | తక్షణ వైద్య సహాయం కావాలి. | ତୁରନ୍ତ ଡାକ୍ତରୀ ସହାୟତା ଆବଶ୍ୟକ। | **REVIEW_REQUIRED** |
| **`FIRE`** | Fire emergency. | आग लगी है। | আগুন লেগেছে। | આગ લાગી છે. | आग लागली आहे. | ಬೆಂಕಿ ಅನಾಹುತ. | തീപിടുത്തം. | தீ விபத்து. | అగ్ని ప్రమాదం. | ନିଆଁ ଲାଗିଛି। | **REVIEW_REQUIRED** |
| **`FLOOD`** | Flood danger. | बाढ़ का खतरा है। | বন্যার বিপদ। | પૂરનો ખતરો છે. | पुराचा धोका आहे. | ಪ್ರವಾಹದ ಅಪಾಯ. | വെള്ളപ്പൊക്ക അപകടം. | வெள்ள அபாயம். | వరద ప్రమాదం. | ବନ୍ୟା ବିପଦ। | **REVIEW_REQUIRED** |
| **`LANDSLIDE`** | Landslide has occurred. | भूस्खलन हुआ है। | ভূমিধস হয়েছে। | ભૂસ્ખલન થયું છે. | भूस्खलन झाले आहे. | ಭೂಕುಸಿತ ಸಂಭವಿಸಿದೆ. | ഉരുൾപൊട്ടൽ ഉണ്ടായിട്ടുണ്ട്. | நிலச்சரிவு ஏற்பட்டுள்ளது. | కొండచరియలు విరిగిపడ్డాయి. | ଭୂସ୍ଖଳନ ହୋଇଛି। | **REVIEW_REQUIRED** |
| **`EVACUATE`** | Evacuate immediately to a safe place. | तुरंत सुरक्षित स्थान पर जाएँ। | অবিলম্বে নিরাপদ স্থানে সরে যান। | તાત્કાલિક સુરક્ષિત સ્થળે જાઓ. | तातडीने सुरक्षित ठिकाणी जा. | ತಕ್ಷಣ ಸುರಕ್ಷಿತ ಸ್ಥಳಕ್ಕೆ ತೆರಳಿ. | ഉടൻ സുരക്ഷിതമായ സ്ഥലത്തേക്ക് മാറുക. | உடனடியாக பாதுகாப்பான இடத்திற்கு செல்லவும். | వెంటనే సురక్షిత ప్రదేశానికి వెళ్ళండి. | ତୁରନ୍ତ ଏକ ସୁରକ୍ଷିତ ସ୍ଥାନକୁ ଯାଆନ୍ତୁ। | **REVIEW_REQUIRED** |
| **`ROAD_BLOCKED`** | Main road is blocked. | मुख्य रास्ता बंद है। | প্রধান রাস্তা বন্ধ। | મુખ્ય રસ્તો બંધ છે. | मुख्य रस्ता बंद आहे. | ಮುಖ್ಯ ರಸ್ತೆ ಬಂದ್ ಆಗಿದೆ. | പ്രധാന റോഡ് അടഞ്ഞു. | முக்கிய சாலை மூடப்பட்டுள்ளது. | ప్రధాన రహదారి మూసివేయబడింది. | ମୁଖ୍ୟ ରାସ୍ତା ବନ୍ଦ ଅଛି। | **REVIEW_REQUIRED** |
| **`SEND_RESCUE_TEAM`** | Please send a rescue team immediately. | कृपया तुरंत बचाव दल भेजें। | দয়া করে অবিলম্বে একটি উদ্ধারকারী দল পাঠান। | કૃપા કરીને તાત્કાલિક બચાવ ટીમ મોકલો. | कृपया तातडीने बचाव पथक पाठवा. | ದಯವಿಟ್ಟು ತಕ್ಷಣ ರಕ್ಷಣಾ ತಂಡವನ್ನು ಕಳುಹಿಸಿ. | ദയവായി ഉടൻ ഒരു രക്ഷാപ്രവർത്തക സംഘത്തെ അയക്കുക. | தயவுசெய்து உடனடியாக மீட்பு குழுவை அனுப்பவும். | దయచేసి వెంటనే రెస్క్యూ బృందాన్ని పంపండి. | ଦୟାକରି ତୁରନ୍ତ ଏକ ଉଦ୍ଧାରକାରୀ ଦଳ ପଠାନ୍ତୁ। | **REVIEW_REQUIRED** |
| **`DANGER`** | Danger ahead. | आगे खतरा है। | সামনে বিপদ। | આગળ ખતરો છે. | पुढे धोका आहे. | ಮುಂದೆ ಅಪಾಯವಿದೆ. | മുന്നിൽ അപകടം. | முன்னால் ஆபத்து. | ముందు ప్రమాదం ఉంది. | ଆଗରେ ବିପଦ। | **REVIEW_REQUIRED** |
| **`ALL_CLEAR`** | All clear. Area is safe. | सब ठीक है। क्षेत्र सुरक्षित है। | সব ঠিক আছে। এলাকা নিরাপদ। | બધું બરાબર છે. વિસ્તાર સુરક્ષિત છે. | सर्व ठीक आहे. परिसर सुरक्षित आहे. | ಎಲ್ಲವೂ ಸರಿ ಇದೆ. ಪ್ರದೇಶ ಸುರಕ್ಷಿತವಾಗಿದೆ. | എല്ലാം സുരക്ഷിതമാണ്. പ്രദേശം സുരക്ഷിതമാണ്. | எல்லாம் சரி. பகுதி பாதுகாப்பானது. | అంతా సురక్షితం. ప్రాంతం సురಕ್ಷితం. | ସବୁ ଠିକ୍ ଅଛି। ଅଞ୍ଚଳ ସୁରକ୍ଷିତ ଅଛି। | **REVIEW_REQUIRED** |

---

## 2. Review Coverage Summary

- **Total Emergency Semantic Codes**: 10
- **Total Supported Languages**: 10
- **Total Script Combinations**: 100
- **Technical Completeness**: 100/100 (100% complete, non-empty, vernacular-specific strings present)
- **Human Verification Status**: **`REVIEW_REQUIRED`** (Field deployment requires certified native speaker sign-off for languages beyond English and Hindi).
