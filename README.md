# Gopal Jarvis (v1)

## GitHub पर डालकर APK बनाना
1. GitHub पर नया खाली repo बनाओ।
2. इस फोल्डर की सारी फाइलें (छुपे फोल्डर `.github` समेत) उसमें push करो:
   git init && git add . && git commit -m "v1" && git branch -M main
   git remote add origin <repo-link> && git push -u origin main
3. repo के **Actions** टैब में "Build APK" चलेगा (5-10 मिनट)।
4. पूरा होने पर उसी रन में नीचे **Artifacts → GopalJarvis-APK** डाउनलोड करो, ज़िप खोलो, APK इंस्टॉल करो।
5. एरर आए तो रन का लॉग (लाल वाला step) मुझे भेज दो।

## ऐप चलाने का क्रम
माइक की इजाज़त → ऐप info में ⋮ → "Allow restricted settings" → Accessibility चालू → Overlay इजाज़त →
बैटरी ऑप्टिमाइज़ेशन बंद → Settings में कम से कम एक API key डालकर "Test key" → "सर्विस चालू करो"।
