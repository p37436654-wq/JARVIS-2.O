JARVIS (Android) - voice assistant that controls your phone.
Say "Jarvis, <command>" e.g. "Jarvis open WhatsApp", "Jarvis call Mom", "Jarvis set alarm 6 am",
"Jarvis turn on flashlight", "Jarvis scroll down", "Jarvis go home".

Setup inside the app (do in order): 1 key+permissions, 2 overlay, 3 phone control (Accessibility), 4 battery, then START JARVIS.
If step 3 is greyed out: Settings > Apps > Jarvis > top-right menu > "Allow restricted settings", then retry.
Brain: Groq (model set in Brain.kt -> MODEL). Boss answer is in the SYSTEM prompt in Brain.kt.
Build: open in Android Studio, or push to GitHub with the build.yml workflow (see chat instructions).
