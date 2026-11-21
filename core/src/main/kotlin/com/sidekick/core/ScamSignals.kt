package com.sidekick.core

/**
 * Scam warning signs, in the app's seven languages. A sign on its own is rarely a scam ("gift card", "right now"); scams
 * combine a story ([pretext]: a caller claiming to be the bank, a grandchild in trouble) with an ask ([ask]: pay by gift
 * card, read out a code). [ScamMonitor] scores the signs of the last minutes of a conversation; [ScamCheck] checks one
 * message, including its links. Rules only: no audio or text is kept, only which signs were seen and when.
 */
enum class ScamSignal(val label: String, val pretext: Boolean = false, val ask: Boolean = false, val strong: Boolean = false) {
    AUTHORITY("claimed to be a government office, the police or your bank", pretext = true),
    TECH_SUPPORT("claimed to be tech support", pretext = true),
    FAMILY_EMERGENCY("a family member in trouble", pretext = true),
    NEW_NUMBER("a relative writing from a new number", pretext = true),
    PRIZE("a prize you have to pay for", pretext = true),
    INVESTMENT("guaranteed or quick investment returns", pretext = true),
    JOB("a job you have to pay for", pretext = true),
    OVERPAYMENT("an overpayment to send back", pretext = true),
    DELIVERY_FEE("an unpaid delivery or toll fee", pretext = true),
    ACCOUNT_PROBLEM("your account or card is blocked", pretext = true),
    URGENCY("pressure to act right now"),
    SECRECY("told to keep it secret or stay on the line"),
    GIFT_CARD("payment by gift card", ask = true, strong = true),
    CRYPTO("payment in cryptocurrency", ask = true, strong = true),
    WIRE("a money transfer", ask = true),
    CASH_COURIER("cash handed to a courier", ask = true, strong = true),
    CODE("asked for a code, password or PIN", ask = true, strong = true),
    REMOTE("asked for remote access to your device", ask = true, strong = true),
    SAFE_ACCOUNT("moving money to a “safe” account", ask = true, strong = true),
    LINK("a suspicious link", ask = true),
}

object ScamSignals {
    private fun rx(p: String) = Regex("(?U)$p", setOf(RegexOption.IGNORE_CASE))
    private class Rules(val defensive: Regex?, val signs: List<Pair<ScamSignal, Regex>>)

    private val EN = Rules(
        rx("""\b(?:never|don'?t|do not|won'?t|will not|should not|shouldn'?t|must not|not going to)\s+(?:ever\s+)?(?:pay|send|share|give|read|install|download|allow|grant|buy|transfer|wire)\b"""),
        listOf(
            ScamSignal.AUTHORITY to rx("""\b(?:from|with|this is|calling from)\s+(?:the\s+)?(?:irs|internal revenue|social security|ssa|fbi|police|sheriff'?s?\s+(?:office|department)|court|medicare|immigration|customs|dea|federal (?:agent|agency)|your bank'?s?\s+(?:fraud|security))\b|\bfraud (?:department|team|prevention unit)\b|\bwarrant (?:out )?for your arrest\b|\byour (?:social security|ssn|bank) (?:number|account) (?:has been|is|was) (?:suspended|frozen|compromised|locked)"""),
            ScamSignal.TECH_SUPPORT to rx("""\b(?:microsoft|apple|windows|amazon|geek squad|norton|mcafee)\s+(?:support|security|technician|refund)\b|\byour (?:computer|phone|device|laptop) (?:has|is infected with|got) (?:a )?(?:virus|malware)|\byour (?:computer|account|phone) (?:has been|was) hacked"""),
            ScamSignal.FAMILY_EMERGENCY to rx("""\b(?:grandma|grandpa|grandson|granddaughter|nephew|niece|son|daughter|mom|dad)\b.{0,40}\b(?:jail|arrested|bail|accident|hospital|lawyer|kidnapped)\b|\bbail money\b|\bit'?s me,?\s+(?:grandma|grandpa)\b|\bdon'?t tell (?:mom|dad|my parents|grandpa|grandma)\b"""),
            ScamSignal.NEW_NUMBER to rx("""\b(?:mom|mum|dad|grandma|grandpa)\b.{0,30}\b(?:new (?:phone|number)|phone (?:broke|is broken)|lost my phone)\b|\bthis is my new number\b"""),
            ScamSignal.PRIZE to rx("""\b(?:you(?:'ve| have)? won|winner|lottery|sweepstakes|prize)\b.{0,60}\b(?:fee|tax|taxes|pay|processing|shipping)\b"""),
            ScamSignal.INVESTMENT to rx("""\bguaranteed (?:returns?|profits?)\b|\b(?:double|triple) your money\b|\b(?:trading|investment) platform\b|\brisk[- ]free (?:investment|returns?)\b"""),
            ScamSignal.JOB to rx("""\b(?:pay|buy)\b.{0,40}\b(?:equipment|training|starter kit)\b.{0,40}\b(?:job|position|work)\b|\b(?:job|position)\b.{0,60}\b(?:pay|buy|deposit)\b.{0,30}\b(?:equipment|training|check)\b"""),
            ScamSignal.OVERPAYMENT to rx("""\b(?:overpaid|sent (?:you )?too much|refunded too much)\b.{0,80}\b(?:send|pay|return|give)\b|\bsend (?:back )?the difference\b"""),
            ScamSignal.DELIVERY_FEE to rx("""\b(?:package|parcel|delivery|shipment)\b.{0,60}\b(?:fee|unpaid|on hold|held|failed|redeliver|customs)\b|\b(?:toll|e-?zpass|fastrak|sunpass)\b.{0,40}\b(?:unpaid|balance|due|fee)\b"""),
            ScamSignal.ACCOUNT_PROBLEM to rx("""\b(?:account|card)\b.{0,30}\b(?:suspended|locked|blocked|frozen|deactivated|on hold)\b|\bunusual (?:activity|sign-?in)\b"""),
            ScamSignal.URGENCY to rx("""\b(?:immediately|within (?:the next )?(?:\d+|an?|one|two) (?:minutes?|hours?)|before it'?s too late|act now|urgent(?:ly)?|right away)\b"""),
            ScamSignal.SECRECY to rx("""\bdon'?t tell (?:anyone|anybody|your (?:family|bank|kids|wife|husband))\b|\bkeep (?:this|it) (?:a )?secret\b|\bstay on the (?:line|phone)\b|\b(?:don'?t|do not) hang up\b|\bwhy can'?t i hang up\b"""),
            ScamSignal.GIFT_CARD to rx("""\bgift ?cards?\b.{0,60}\b(?:pay|fine|fee|tax|bill|debt|read|scratch|photo|picture|numbers? on|codes? on|the back)\b|\bgift ?cards?\s+(?:numbers?|codes?|pins?)\b|\b(?:pay|fine|fee|tax|bill|debt|scratch|picture of|read(?: me)?)\b.{0,60}\bgift ?cards?\b|\b(?:you want me to|i have to|i need to|why (?:do|would) i (?:have|need) to)\s+(?:buy|get|pick up)\b.{0,25}\bgift ?cards?\b"""),
            ScamSignal.CRYPTO to rx("""\b(?:bitcoin|crypto(?:currency)?)\s+(?:atm|machine|kiosk)\b|\b(?:pay|send|deposit|transfer)\b.{0,40}\b(?:bitcoin|crypto(?:currency)?|usdt|tether)\b"""),
            ScamSignal.WIRE to rx("""\b(?:wire|western union|moneygram|zelle|venmo|cash ?app)\b.{0,40}\b(?:money|\$|dollars|payment|\d)|\b(?:send|pay|transfer)\b.{0,30}\b(?:through|via|with|by|on)\s+(?:wire|western union|moneygram|zelle)\b|\b(?:you want me to|you're asking me to|why (?:do|would|should) i(?: have to| need to)?)\s+(?:withdraw|wire|send|transfer|move)\b.{0,30}\b(?:money|cash|savings|\$)|\b(?:send|lend|give|wire|transfer)\b.{0,15}\b(?:me|us)\b.{0,20}(?:\$?\d+|money|cash|dollars)\b|\bi need\b.{0,20}\bmoney\b"""),
            ScamSignal.CASH_COURIER to rx("""\b(?:courier|driver|agent|officer|someone)\b.{0,40}\b(?:pick up|collect|come (?:and )?get)\b.{0,40}\b(?:cash|money|gold)\b|\bput (?:the )?(?:cash|money) in (?:an? )?(?:envelope|box|shoebox)\b"""),
            ScamSignal.CODE to rx("""\b(?:send|read|tell|give|share|text)\b.{0,60}\b(?:(?:verification|one[ -]time|security|login|authentication|6[- ]digit)\s+codes?|passwords?|pins?)\b|\b(?:you want me to|why do you need)\b.{0,20}\b(?:the )?(?:code|password|pin)\b|\b(?:need|confirm|verify|provide|enter)\b.{0,25}\b(?:(?:one[ -]time|verification|security|6[- ]digit)\s+codes?|(?:your )?(?:pin|password))\b"""),
            ScamSignal.REMOTE to rx("""\b(?:install|download|open)\b.{0,70}\b(?:anydesk|teamviewer|quick ?support|ultraviewer|rustdesk|supremo|screenconnect|logmein)\b|\b(?:allow|grant|give|let)\b.{0,40}\bremote (?:access|control)\b|\byou want me to install\b"""),
            ScamSignal.SAFE_ACCOUNT to rx("""\b(?:safe|secure|protected|safety)\s+account\b"""),
        ))

    private val KO = Rules(
        rx("""(?:사기|보이스\s?피싱)(?:야|이야|입니다|조심|주의|일 수)|절대.{0,15}(?:알려\s?주지|보내지|송금하지|이체하지)\s?(?:마세요|마|말아)"""),
        listOf(
            ScamSignal.AUTHORITY to rx("""검찰|검사(?:님|입니다)|수사관|금융감독원|금감원|경찰청|사이버\s?수사대|국세청|금융위원회|(?:본인|명의)(?:가|이)?.{0,15}(?:도용|연루|사건)"""),
            ScamSignal.FAMILY_EMERGENCY to rx("""(?:아들|딸|손자|손녀|조카).{0,20}(?:사고|합의금|구속|납치|병원)|합의금|납치"""),
            ScamSignal.NEW_NUMBER to rx("""(?:엄마|아빠|어머니|아버지).{0,20}(?:폰|핸드폰|휴대폰).{0,10}(?:고장|깨|잃어|분실)|(?:폰|핸드폰|휴대폰).{0,10}(?:고장|깨졌|분실).{0,20}(?:이 번호|새 번호)"""),
            ScamSignal.INVESTMENT to rx("""대환\s?대출|저금리\s?대출|(?:원금|수익)\s?보장|고수익|리딩방|코인\s?(?:투자|리딩)"""),
            ScamSignal.PRIZE to rx("""당첨.{0,20}(?:수수료|세금|입금)"""),
            ScamSignal.DELIVERY_FEE to rx("""(?:택배|배송).{0,20}(?:주소\s?불일치|반송|보류|수수료|미납)"""),
            ScamSignal.ACCOUNT_PROBLEM to rx("""(?:계좌|카드|계정).{0,15}(?:정지|차단|동결)|해외\s?결제.{0,20}(?:승인|완료)"""),
            ScamSignal.URGENCY to rx("""지금\s?(?:바로|당장)|오늘\s?안에|즉시|급하게|급히"""),
            ScamSignal.SECRECY to rx("""(?:아무|누구)(?:한테|에게)도\s?(?:말하지|얘기하지)|비밀로|(?:전화\s?)?끊지\s?마"""),
            ScamSignal.GIFT_CARD to rx("""(?:문화\s?상품권|상품권|기프트\s?카드).{0,20}(?:구매|사서|사\s?와|핀\s?번호|번호|사진)"""),
            ScamSignal.CRYPTO to rx("""(?:비트코인|코인|가상\s?화폐|테더|usdt).{0,20}(?:송금하세요|보내세요|입금하세요|사서 보내)|코인\s?atm"""),
            ScamSignal.WIRE to rx("""(?:송금|이체|입금)\s?(?:해|하세요|해\s?주세요|해줘)"""),
            ScamSignal.CASH_COURIER to rx("""(?:현금|돈).{0,20}(?:전달|인출해|찾아서|뽑아서|건네|수거)|직원.{0,10}(?:만나서|에게)\s?(?:전달|건네)"""),
            // A request ("알려 주세요", "불러 줘"), not the speaker offering to tell later ("알려 줄게").
            ScamSignal.CODE to rx("""(?:인증\s?번호|비밀\s?번호|otp|보안\s?카드).{0,20}(?:(?:알려|불러|말해|보내)\s?(?:주세요|줘|주실|달라|주시면)|입력하세요|입력해\s?주세요)"""),
            ScamSignal.REMOTE to rx("""팀뷰어|애니데스크|원격\s?(?:제어|조종|지원)|(?:보안|백신|금융감독원|검찰)\s?(?:앱|어플)"""),
            ScamSignal.SAFE_ACCOUNT to rx("""(?:안전|보호|국가\s?안전)\s?계좌"""),
        ))

    private val ZH = Rules(
        rx("""(?:不要|别|千万别|千万不要).{0,6}(?:转账|汇款|给.{0,4}(?:验证码|密码))|是骗(?:子|局)"""),
        listOf(
            ScamSignal.AUTHORITY to rx("""公安|警察|检察院|法院|银行(?:客服|工作人员)|医保局|社保局|海关"""),
            ScamSignal.FAMILY_EMERGENCY to rx("""(?:儿子|女儿|孙子|孙女).{0,20}(?:出车祸|被抓|拘留|住院)"""),
            ScamSignal.INVESTMENT to rx("""稳赚|保本|高回报|带你投资|返利"""),
            ScamSignal.JOB to rx("""刷单|兼职.{0,10}(?:垫付|押金)"""),
            ScamSignal.PRIZE to rx("""中奖.{0,10}(?:手续费|税)"""),
            ScamSignal.ACCOUNT_PROBLEM to rx("""(?:账户|银行卡|账号).{0,10}(?:冻结|异常|封)"""),
            ScamSignal.URGENCY to rx("""马上|立刻|立即|今天之内"""),
            ScamSignal.SECRECY to rx("""不要告诉(?:任何人|家人)|保密|不要挂电话"""),
            ScamSignal.GIFT_CARD to rx("""(?:礼品卡|购物卡|充值卡).{0,10}(?:购买|买|卡号|密码)"""),
            ScamSignal.CRYPTO to rx("""(?:比特币|usdt|虚拟币).{0,10}(?:转|付|充值|购买)|(?:转|付|充值)(?:成|为)?.{0,6}(?:比特币|usdt|虚拟币)"""),
            ScamSignal.WIRE to rx("""转账|汇款|打款|垫付|押金|保证金"""),
            ScamSignal.CASH_COURIER to rx("""(?:现金|取钱).{0,10}(?:交给|当面)"""),
            ScamSignal.CODE to rx("""(?:验证码|密码).{0,10}(?:告诉|发给|报给|说给)"""),
            ScamSignal.REMOTE to rx("""屏幕共享|共享屏幕|远程(?:控制|协助)"""),
            ScamSignal.SAFE_ACCOUNT to rx("""安全账户"""),
        ))

    private val RU = Rules(
        rx("""(?:никогда не|ни в коем случае не)\s+(?:переводите|сообщайте|называйте|отправляйте)"""),
        listOf(
            ScamSignal.AUTHORITY to rx("""служб\w*\s+безопасности|сотрудник\w*\s+банка|центробанк|полици|следовател|фсб|прокуратур|госуслуг"""),
            ScamSignal.FAMILY_EMERGENCY to rx("""(?:сын|внук|внучка|дочь)\w*.{0,30}(?:авари|дтп|задержан|больниц)|(?:мама|папа|бабушка|дедушка).{0,30}(?:я попал|беда)"""),
            ScamSignal.NEW_NUMBER to rx("""(?:мама|папа).{0,20}(?:новый номер|телефон сломался)|это мой новый номер"""),
            ScamSignal.INVESTMENT to rx("""гарантированн\w*\s+(?:доход|прибыл)|(?:инвестиц|биржев)\w*\s+платформ"""),
            ScamSignal.PRIZE to rx("""выигр\w*.{0,30}(?:комисси|налог|оплат)"""),
            ScamSignal.DELIVERY_FEE to rx("""(?:посылк|доставк)\w*.{0,30}(?:оплат|пошлин)"""),
            ScamSignal.ACCOUNT_PROBLEM to rx("""(?:карт|сч[её]т)\w*.{0,15}(?:заблокирован|блокирован)"""),
            ScamSignal.URGENCY to rx("""срочно|немедленно|прямо сейчас"""),
            ScamSignal.SECRECY to rx("""никому не (?:говорите|рассказывайте)|не кладите трубку"""),
            ScamSignal.GIFT_CARD to rx("""подарочн\w*\s+карт"""),
            ScamSignal.CRYPTO to rx("""(?:переведите|оплатите|купите|внесите).{0,30}(?:биткоин|криптовалют|usdt)|(?:биткоин|крипто)\w*[- ]?банкомат"""),
            ScamSignal.WIRE to rx("""переведите.{0,20}(?:деньги|средства|сумм)|перевод\w*.{0,20}(?:денег|средств)"""),
            ScamSignal.CASH_COURIER to rx("""курьер\w*.{0,30}(?:деньги|наличн)|наличн\w*.{0,20}курьер"""),
            ScamSignal.CODE to rx("""(?:код|пароль|пин)\w*.{0,20}(?:из смс|скажите|назовите|продиктуйте|сообщите)|(?:скажите|назовите|продиктуйте|сообщите).{0,20}(?:код|пароль)"""),
            ScamSignal.REMOTE to rx("""anydesk|teamviewer|rustdesk|удал[её]нн\w*\s+доступ"""),
            ScamSignal.SAFE_ACCOUNT to rx("""(?:безопасн|резервн)\w*\s+сч[её]т"""),
        ))

    private val AR = Rules(
        rx("""لا\s+(?:ترسل|تعطي|تحول|تشارك)"""),
        listOf(
            ScamSignal.AUTHORITY to rx("""الشرطة|البنك المركزي|النيابة|المحكمة|موظف البنك|إدارة الاحتيال"""),
            ScamSignal.FAMILY_EMERGENCY to rx("""(?:ابنك|ابنتك|حفيدك|حفيدتك).{0,30}(?:حادث|مستشفى|القبض|السجن)"""),
            ScamSignal.INVESTMENT to rx("""أرباح مضمونة|عائد مضمون"""),
            ScamSignal.PRIZE to rx("""ربحت.{0,30}(?:رسوم|دفع)"""),
            ScamSignal.DELIVERY_FEE to rx("""(?:شحنة|طرد|الشحنة|الطرد).{0,30}(?:رسوم|دفع)"""),
            ScamSignal.ACCOUNT_PROBLEM to rx("""(?:حسابك|بطاقتك).{0,20}(?:موقوف|محظور|مجمد|معلق)"""),
            ScamSignal.URGENCY to rx("""فورا|فوراً|حالا|حالاً|خلال ساعة"""),
            ScamSignal.SECRECY to rx("""لا تخبر أحد|لا تقل لأحد|لا تغلق الخط"""),
            ScamSignal.GIFT_CARD to rx("""بطاقات?\s+(?:هدايا|هدية|شحن|جوجل بلاي|آيتونز)"""),
            ScamSignal.CRYPTO to rx("""(?:ادفع|حول|حوّل|أرسل|اشتر).{0,30}(?:بيتكوين|عملات?\s+رقمية|usdt)"""),
            ScamSignal.WIRE to rx("""(?:حول|حوّل|تحويل|أرسل).{0,20}(?:المبلغ|مال|المال|فلوس)"""),
            ScamSignal.CODE to rx("""(?:رمز|كود|كلمة السر|الرقم السري|رمز التحقق).{0,20}(?:أرسل|أعطني|قل|أخبرني|اخبرني)|(?:أرسل|أعطني|أخبرني|اخبرني).{0,20}(?:رمز|كود|كلمة السر|الرقم السري)"""),
            ScamSignal.REMOTE to rx("""anydesk|teamviewer|التحكم عن بعد|الوصول عن بعد"""),
            ScamSignal.SAFE_ACCOUNT to rx("""حساب\s+آمن"""),
        ))

    private val FR = Rules(
        rx("""(?:ne|jamais).{0,12}(?:donnez|communiquez|envoyez|virez|payez)"""),
        listOf(
            ScamSignal.AUTHORITY to rx("""service des fraudes|conseiller bancaire|gendarmerie|police|impôts|banque de france|douane"""),
            ScamSignal.FAMILY_EMERGENCY to rx("""(?:fils|fille|petit-fils|petite-fille).{0,30}(?:accident|hôpital|garde à vue|arrêté)"""),
            ScamSignal.NEW_NUMBER to rx("""(?:maman|papa|mamie|papy).{0,30}nouveau numéro|c'est mon nouveau numéro"""),
            ScamSignal.INVESTMENT to rx("""rendement garanti|gains garantis|placement (?:sans risque|garanti)"""),
            ScamSignal.PRIZE to rx("""gagné.{0,30}(?:frais|payer)"""),
            ScamSignal.DELIVERY_FEE to rx("""(?:colis|livraison).{0,40}(?:frais|payer|bloqué|en attente)"""),
            ScamSignal.ACCOUNT_PROBLEM to rx("""(?:compte|carte).{0,20}(?:bloqué|suspendu|piraté)"""),
            ScamSignal.URGENCY to rx("""immédiatement|tout de suite|urgent|dans l'heure"""),
            ScamSignal.SECRECY to rx("""n'en parlez à personne|ne dites rien à|ne raccrochez pas|restez en ligne"""),
            ScamSignal.GIFT_CARD to rx("""cartes?\s+(?:cadeau|pcs|transcash|neosurf|google play)"""),
            ScamSignal.CRYPTO to rx("""(?:payez|envoyez|virez|achetez|déposez).{0,30}(?:bitcoin|crypto|usdt)|distributeur de bitcoin"""),
            ScamSignal.WIRE to rx("""(?:virement|virer|western union).{0,30}(?:argent|somme|€|euros)"""),
            ScamSignal.CASH_COURIER to rx("""(?:coursier|livreur).{0,30}(?:espèces|argent|carte bancaire)"""),
            ScamSignal.CODE to rx("""(?:code|mot de passe).{0,30}(?:donnez|dites|communiquez|lisez|envoyez)|(?:donnez|dites|communiquez|lisez)(?:-moi)?.{0,30}(?:le code|mot de passe)"""),
            ScamSignal.REMOTE to rx("""anydesk|teamviewer|prise en main à distance|accès à distance"""),
            ScamSignal.SAFE_ACCOUNT to rx("""compte\s+(?:sécurisé|de sécurité|sûr|protégé)"""),
        ))

    private val DE = Rules(
        rx("""(?:niemals|nie|keine?n?)\s.{0,12}(?:geben|nennen|überweisen|schicken|herausgeben)"""),
        listOf(
            ScamSignal.AUTHORITY to rx("""polizei|staatsanwalt|bankmitarbeiter|bank-mitarbeiter|sicherheitsabteilung|finanzamt|europol"""),
            ScamSignal.FAMILY_EMERGENCY to rx("""(?:enkel|sohn|tochter).{0,30}(?:unfall|verhaftet|kaution|krankenhaus)"""),
            ScamSignal.NEW_NUMBER to rx("""(?:mama|papa|oma|opa).{0,30}neue (?:handy)?nummer|das ist meine neue nummer"""),
            ScamSignal.INVESTMENT to rx("""garantierte?\s+(?:rendite|gewinne?)|risikolose? (?:anlage|rendite)"""),
            ScamSignal.PRIZE to rx("""gewonnen.{0,30}(?:gebühr|zahlen)"""),
            ScamSignal.DELIVERY_FEE to rx("""(?:paket|sendung).{0,30}(?:gebühr|zahlen|zoll)"""),
            ScamSignal.ACCOUNT_PROBLEM to rx("""(?:konto|karte).{0,20}(?:gesperrt|blockiert)"""),
            ScamSignal.URGENCY to rx("""sofort|dringend|heute noch"""),
            ScamSignal.SECRECY to rx("""(?:niemandem|keinem)\s.{0,10}(?:sagen|erzählen)|legen sie nicht auf|bleiben sie dran"""),
            ScamSignal.GIFT_CARD to rx("""(?:gutschein|paysafe|google[- ]play[- ]karte)\w*.{0,20}(?:kaufen|code|nummer)|(?:kaufen|code).{0,20}(?:paysafe|google[- ]play[- ]karte)"""),
            ScamSignal.CRYPTO to rx("""(?:zahlen|überweisen|senden|kaufen|einzahlen).{0,30}(?:bitcoin|krypto|usdt)|bitcoin-?automat"""),
            ScamSignal.WIRE to rx("""(?:überweis|western union).{0,30}(?:geld|betrag|euro|€)|(?:\d+\s?(?:euro|€)|geld).{0,30}überweis"""),
            ScamSignal.CASH_COURIER to rx("""(?:abholer|bote|kurier).{0,30}(?:bargeld|geld|schmuck)|bargeld.{0,30}(?:abholen|übergeben)"""),
            ScamSignal.CODE to rx("""(?:tan|code|passwort|pin|bestätigungscode)\b.{0,30}(?:nennen|sagen|durchgeben|vorlesen|schicken)|(?:nennen|sagen|geben|lesen)\w*\s.{0,30}\b(?:tan|code|passwort|pin|bestätigungscode)\b"""),
            ScamSignal.REMOTE to rx("""anydesk|teamviewer|fernzugriff|fernwartung"""),
            ScamSignal.SAFE_ACCOUNT to rx("""sicherheitskonto|sicheres konto"""),
        ))

    private val ALL = listOf(EN, KO, ZH, RU, AR, FR, DE)

    /** Signs in [text], every language at once; sentences that warn against the scam ("never send gift cards") don't count. */
    fun find(text: String): Set<ScamSignal> {
        val found = linkedSetOf<ScamSignal>()
        for (sentence in Sentences.split(text.takeLast(4000))) {
            if (ALL.any { it.defensive?.containsMatchIn(sentence) == true }) continue
            for (rules in ALL) for ((signal, pattern) in rules.signs) if (pattern.containsMatchIn(sentence)) found += signal
        }
        if (LinkCheck.suspicious(text).isNotEmpty()) found += ScamSignal.LINK
        return found
    }
}

/** How worried to be, from the signs seen together. */
enum class ScamLevel { NONE, CAUTION, STOP }

data class ScamAssessment(val level: ScamLevel, val signals: List<ScamSignal>) {
    val title: String get() = when (level) {
        ScamLevel.STOP -> "Possible scam: stop now"
        ScamLevel.CAUTION -> "Be careful: scam warning signs"
        ScamLevel.NONE -> "No scam warning signs"
    }
    /** The one thing to do now, chosen by what is being asked for. */
    val step: String get() = when {
        ScamSignal.CODE in signals -> "Don't share the code. Hang up and call the company on the number on your card or their website."
        ScamSignal.REMOTE in signals -> "Don't install anything or give access. Hang up."
        signals.any { it in MONEY } -> "Don't pay or move money. Hang up and call back on a number you already know."
        ScamSignal.LINK in signals || ScamSignal.DELIVERY_FEE in signals || ScamSignal.ACCOUNT_PROBLEM in signals ->
            "Don't tap the link. Open the company's app or website yourself."
        ScamSignal.FAMILY_EMERGENCY in signals || ScamSignal.NEW_NUMBER in signals -> "Call your family member on their usual number first."
        else -> "Pause. Check with someone you trust before doing anything."
    }
    val reasons: String get() = signals.joinToString("; ") { it.label }

    companion object {
        private val MONEY = setOf(ScamSignal.GIFT_CARD, ScamSignal.CRYPTO, ScamSignal.WIRE, ScamSignal.CASH_COURIER, ScamSignal.SAFE_ACCOUNT)
        /**
         * STOP: a story plus an ask (the shape of nearly every scam), a "safe account", or three signs including an ask.
         * CAUTION: one strong ask (gift-card payment, a code, remote access...), an ask with another sign, or a story told in secret.
         * Urgency and secrecy alone never warn: they are common in everyday talk.
         */
        fun of(signals: Collection<ScamSignal>): ScamAssessment {
            val s = signals.toSet()
            val ordered = ScamSignal.entries.filter { it in s }
            val story = s.any { it.pretext }; val ask = s.any { it.ask }
            val level = when {
                story && ask -> ScamLevel.STOP
                ScamSignal.SAFE_ACCOUNT in s -> ScamLevel.STOP
                s.size >= 3 && ask -> ScamLevel.STOP
                s.any { it.strong } -> ScamLevel.CAUTION
                ask && s.size >= 2 -> ScamLevel.CAUTION
                story && ScamSignal.SECRECY in s -> ScamLevel.CAUTION
                else -> ScamLevel.NONE
            }
            return ScamAssessment(level, ordered)
        }
    }
}

/** A conversation: the signs of the last [windowMs] (a scam call unfolds over minutes), scored together. */
class ScamMonitor(private val windowMs: Long = 10 * 60_000L) {
    private val seen = ArrayDeque<Pair<ScamSignal, Long>>()
    private fun prune(now: Long) { while (seen.isNotEmpty() && now - seen.first().second > windowMs) seen.removeFirst() }
    /** Adds the signs in [text] and returns the conversation's assessment now. */
    @Synchronized fun add(text: String, now: Long): ScamAssessment {
        prune(now)
        ScamSignals.find(text).forEach { seen.addLast(it to now) }
        return assessment(now)
    }
    @Synchronized fun assessment(now: Long): ScamAssessment { prune(now); return ScamAssessment.of(seen.map { it.first }) }
    /** Signs seen since [startMs] (for example, during a call), whatever the window. */
    @Synchronized fun since(startMs: Long): ScamAssessment = ScamAssessment.of(seen.filter { it.second >= startMs }.map { it.first })
    @Synchronized fun clear() = seen.clear()
}

/** One message on its own (texts, shared text): its signs and links. */
object ScamCheck {
    fun message(text: String): ScamAssessment = ScamAssessment.of(ScamSignals.find(text))
}

/**
 * Links that are often used in scam texts, checked on the phone (nothing is looked up online): a lookalike of a
 * well-known name ("paypa1", "amaz0n"), a known name in front of someone else's domain ("paypal.com.secure-login.top"),
 * a link shortener, an IP address, a disguised (punycode) name, or a domain ending often used for throwaway sites.
 */
object LinkCheck {
    private val url = Regex("""(?i)\b(?:https?://)?((?:[a-z0-9\-]+\.)+[a-z]{2,}|\d{1,3}(?:\.\d{1,3}){3})(?::\d+)?(?:/[^\s]*)?""")
    private val brands = listOf("paypal", "amazon", "apple", "icloud", "google", "microsoft", "netflix", "usps", "fedex", "ups", "dhl",
        "irs", "chase", "wellsfargo", "bankofamerica", "citibank", "coinbase", "binance", "naver", "kakao", "coupang", "toss", "facebook", "instagram", "whatsapp")
    private val shorteners = setOf("bit.ly", "tinyurl.com", "t.co", "is.gd", "cutt.ly", "rb.gy", "han.gl", "me2.kr", "goo.gl", "ow.ly", "shorturl.at", "tiny.cc", "s.id", "t.ly")
    private val riskyEndings = setOf("top", "xyz", "click", "icu", "buzz", "vip", "live", "shop", "rest", "cfd", "sbs", "cyou", "mom", "lol", "zip", "mov")
    private fun deLeet(s: String) = s.replace('0', 'o').replace('1', 'l').replace('3', 'e').replace('5', 's').replace("rn", "m").replace("vv", "w")

    /** The suspicious hosts in [text] (empty when none). Bare words with dots such as "e.g." are ignored. */
    fun suspicious(text: String): List<String> = url.findAll(text).mapNotNull { m ->
        val host = m.groupValues[1].lowercase()
        val labels = host.split('.')
        if (labels.size < 2 || labels.last().length < 2) return@mapNotNull null
        val registrable = labels.takeLast(2).joinToString(".")
        val name = labels[labels.size - 2]
        val flagged = when {
            host.matches(Regex("""\d{1,3}(?:\.\d{1,3}){3}""")) -> true
            labels.any { it.startsWith("xn--") } -> true
            registrable in shorteners || host in shorteners -> true
            labels.last() in riskyEndings -> true
            // a lookalike: not the brand itself, but the same after undoing digit swaps, or the brand with extra words
            brands.any { b -> name != b && (deLeet(name) == b || (name.contains('-') && name.split('-').any { it == b || deLeet(it) == b })) } -> true
            // a brand in front of someone else's domain: paypal.com.example.net
            labels.dropLast(2).any { it in brands } && name !in brands -> true
            else -> false
        }
        if (flagged) host else null
    }.distinct().toList()
}

/** "SideKick, is this a scam?" in the app's languages, and the spoken answer. */
object ScamQuestion {
    private val ASK = mapOf(
        "en" to Regex("""(?i)\b(?:is (?:this|that|it|he|she|this call) (?:a )?(?:scam|legit|real|fraud)|am i (?:being )?scammed|scam\?)"""),
        "ko" to Regex("""사기(?:야|인가|일까|맞아|아니야|같아)|보이스\s?피싱(?:이야|인가|일까|맞아|같아)"""),
        "zh" to Regex("""是不是(?:骗|诈骗)|是骗局吗|是诈骗吗|骗子吗"""),
        "ru" to Regex("""(?i)это (?:мошенни|развод)|меня обманывают"""),
        "ar" to Regex("""هل (?:هذا|هذه) (?:احتيال|نصب)"""),
        "fr" to Regex("""(?i)(?:c'est|est-ce) (?:une )?arnaque"""),
        "de" to Regex("""(?i)ist (?:das|es) (?:ein )?betrug|ist das abzocke"""),
    )
    /** The language code when [rest] (the words after "SideKick") asks whether something is a scam. */
    fun asked(rest: String): String? = ASK.entries.firstOrNull { it.value.containsMatchIn(rest) }?.key

    private val NONE = mapOf("en" to "I haven't heard any scam warning signs in the last few minutes. If unsure, hang up and call back on a number you know.",
        "ko" to "최근 몇 분 동안 사기 의심 신호는 없었어요. 확실하지 않으면 전화를 끊고 알고 있는 번호로 다시 걸어 보세요.",
        "zh" to "最近几分钟没有听到诈骗迹象。如果不确定，请挂断电话，用你知道的号码回拨。",
        "ru" to "За последние минуты признаков мошенничества не было. Если сомневаетесь, положите трубку и перезвоните по известному номеру.",
        "ar" to "لم ألاحظ علامات احتيال في الدقائق الأخيرة. إذا لم تكن متأكدًا، أغلق الخط واتصل برقم تعرفه.",
        "fr" to "Je n'ai entendu aucun signe d'arnaque ces dernières minutes. En cas de doute, raccrochez et rappelez un numéro que vous connaissez.",
        "de" to "In den letzten Minuten gab es keine Betrugsanzeichen. Im Zweifel auflegen und eine bekannte Nummer zurückrufen.")
    private val CAUTION = mapOf("en" to "Be careful. I heard %d warning signs. Don't pay or share codes; hang up and call back on a number you know.",
        "ko" to "조심하세요. 사기 의심 신호가 %d개 있었어요. 돈을 보내거나 인증번호를 알려 주지 말고, 전화를 끊고 알고 있는 번호로 다시 거세요.",
        "zh" to "请小心。我听到了%d个诈骗迹象。不要付款或透露验证码；挂断后用你知道的号码回拨。",
        "ru" to "Осторожно. Я заметил признаков мошенничества: %d. Не платите и не называйте коды; положите трубку и перезвоните по известному номеру.",
        "ar" to "انتبه. لاحظت %d من علامات الاحتيال. لا تدفع ولا تشارك أي رمز؛ أغلق الخط واتصل برقم تعرفه.",
        "fr" to "Attention. J'ai relevé %d signes d'arnaque. Ne payez pas et ne donnez aucun code ; raccrochez et rappelez un numéro connu.",
        "de" to "Vorsicht. Ich habe %d Betrugsanzeichen bemerkt. Nichts zahlen, keine Codes nennen; auflegen und eine bekannte Nummer zurückrufen.")
    private val STOP = mapOf("en" to "This looks like a scam: %d warning signs. Stop now. Hang up and call back on a number you know.",
        "ko" to "사기일 가능성이 높아요. 의심 신호가 %d개예요. 지금 멈추고 전화를 끊은 뒤 알고 있는 번호로 다시 거세요.",
        "zh" to "这很像诈骗：有%d个迹象。请立即停止，挂断电话，用你知道的号码回拨。",
        "ru" to "Похоже на мошенничество: признаков %d. Остановитесь, положите трубку и перезвоните по известному номеру.",
        "ar" to "يبدو هذا احتيالًا: %d علامات. توقف الآن، أغلق الخط واتصل برقم تعرفه.",
        "fr" to "Cela ressemble à une arnaque : %d signes. Arrêtez maintenant, raccrochez et rappelez un numéro connu.",
        "de" to "Das sieht nach Betrug aus: %d Anzeichen. Jetzt aufhören, auflegen und eine bekannte Nummer zurückrufen.")
    private val OFF = mapOf("en" to "Scam warnings are turned off in Settings.", "ko" to "사기 경고가 설정에서 꺼져 있어요.", "zh" to "诈骗提醒已在设置中关闭。",
        "ru" to "Предупреждения о мошенничестве отключены в настройках.", "ar" to "تحذيرات الاحتيال متوقفة في الإعدادات.",
        "fr" to "Les alertes d'arnaque sont désactivées dans les réglages.", "de" to "Betrugswarnungen sind in den Einstellungen ausgeschaltet.")

    fun answer(assessment: ScamAssessment?, language: String): String {
        val code = if (language in NONE) language else "en"
        if (assessment == null) return OFF.getValue(code)
        return when (assessment.level) {
            ScamLevel.NONE -> NONE.getValue(code)
            ScamLevel.CAUTION -> CAUTION.getValue(code).format(assessment.signals.size)
            ScamLevel.STOP -> STOP.getValue(code).format(assessment.signals.size)
        }
    }
}
