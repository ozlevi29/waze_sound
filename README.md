# Waze Speaker 🔊

<div dir="rtl">

**הדיבור של Waze תמיד נשמע ברמקול של הטלפון, בעוצמה שאתם בוחרים - גם כשהטלפון על רטט, וגם כשהוא מחובר לבלוטוס של הרכב.**

נוצר ע"י [Oz Levi](https://www.linkedin.com/in/ozlevi1/) · חינם וקוד פתוח

## מה זה פותר?
- הטלפון על רטט ואי אפשר לשמוע את Waze
- הטלפון מחובר לבלוטוס, אבל אתם רוצים לשמוע את ההנחיות ברמקול הטלפון
- ההנחיות של Waze שקטות מדי ואתם צריכים להגביר ידנית

## התקנה
1. הורידו את קובץ ה-APK מדף ה-[Releases](../../releases) והתקינו (יש לאשר "התקנה ממקורות לא ידועים").
2. פתחו את **Waze Speaker** ואשרו התראות.
3. לחצו על **"אפשר גישה ל'נא לא להפריע'"** והפעילו את האפליקציה ברשימה (נדרש רק אם רוצים שזה יעבוד כשהטלפון על רטט).
4. הדליקו את **"הפעל"**.
5. בהגדרות הטלפון, הוציאו את האפליקציה מחיסכון בסוללה, כדי שהמערכת לא תכבה אותה.

זהו! פתחו את Waze והתחילו לנווט.

## האפשרויות
| אפשרות | מה היא עושה |
|---|---|
| **הפעל** | מדליק או מכבה את האפליקציה |
| **עוצמת דיבור** | העוצמה של הדיבור ב-Waze, בין 10% ל-100% |
| **לנתב לרמקול הטלפון** | גם אם מחוברים לבלוטוס או לאוזניות, הדיבור יישמע ברמקול הטלפון |
| **להשהות מוזיקה בזמן הדיבור** | שיר או יוטיוב עוצרים כשהדיבור מתחיל וממשיכים אחריו |
| **להוציא מרטט/שקט בזמן הדיבור** | הטלפון עובר לרגע למצב רגיל בזמן הדיבור, ואז חוזר לרטט |
| **להחזיר עוצמות אחרי הדיבור** | מחזיר את העוצמות הקודמות של הטלפון (כבוי כברירת מחדל) |
| **בדיקה** | מנגן צליל ניווט כדי לבדוק שהכול עובד |

## טיפים
- אם הדיבור לא נשמע ברטט, בדקו שנתתם גישה ל"נא לא להפריע".
- שורת "סוג השמע האחרון שזוהה" בתחתית המסך צריכה להראות **"ניווט"** אחרי שמשמיעים צליל בדיקה או מנווטים. אם כתוב משהו אחר, פתחו Issue.
- בדוק על Samsung Galaxy S26 Ultra. במכשירים אחרים ייתכנו הבדלים.

## בנייה מהקוד
```
./gradlew assembleDebug
```
הקובץ ייווצר ב-`app/build/outputs/apk/debug/`. דרוש Android SDK ו-JDK 17.

</div>

---

## English

**Waze Speaker** plays Waze's voice guidance on the phone speaker at a volume you choose, even when the phone is on vibrate or connected to car Bluetooth. Free and open source, created by [Oz Levi](https://www.linkedin.com/in/ozlevi1/).

**Setup:** install the APK from [Releases](../../releases), open the app, allow notifications, grant *Do Not Disturb access* (needed for vibrate mode), turn on **Enable**, and exclude the app from battery optimization.

**Options:** volume level (10-100%), force phone speaker with Bluetooth, pause music during prompts, leave vibrate during prompts, restore volumes afterwards, and a test button.

Android 8.0+. Licensed under [MIT](LICENSE).
