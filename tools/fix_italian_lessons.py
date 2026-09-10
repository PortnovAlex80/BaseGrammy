# -*- coding: utf-8 -*-
"""
Одноразовый патч итальянских уроков (Fast Track + Full Course).

Инвариант: число строк и их порядок в каждом файле НЕ меняются.
CsvParser выдаёт id карточки как "card_<номер строки>", поэтому вставка или
удаление строки переклеила бы накопленный прогресс на чужие карточки.
Правится только содержимое строк.

Разделы соответствуют отчёту по ревью методологии:
  §2 коллизии промптов, §3 обрывки придаточных, §5 грамматика,
  §6 рассинхрон подсказки и ответа, §7 мета-строки.
"""
import sys
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent / "docs/lesson-methodology/italian"
PACKS = ("short-engine-lessons", "full-course-63")

# (файл, старое, новое) — применяется к обоим пакам, если строка там есть.
PATCHES = [

    # ---- §5 грамматика -------------------------------------------------
    ("A04", "Vado in una casa bella", "Vado in una bella casa"),

    ("A13", "Кухня менее красивая, чем комната (meno, bello, che)",
            "Кухня менее красивая, чем комната (meno, bello, di)"),
    ("A13", "La mia stanza è più piccola della tua stanza",
            "La mia stanza è più piccola della tua"),

    # "a lavoro" -> "al lavoro": в паке сосуществовали оба варианта
    ("B07", "Andrai a lavoro", "Andrai al lavoro"),
    ("B08", "sarà già andata a lavoro", "sarà già andata al lavoro"),
    ("B09", "Andresti a lavoro", "Andresti al lavoro"),

    # participio passato: несуществующие по-итальянски сочетания
    ("B02", "Тот самый желанный подарок — красивый (voluto);Il regalo voluto è bello",
            "Та самая прочитанная книга — новая (letto);Il libro letto è nuovo"),
    ("B02", "Тот самый отработанный день — длинный (lavorato);Il giorno lavorato è lungo",
            "Та самая купленная книга — новая (comprato);Il libro comprato è nuovo"),

    # B12: "Gli arrivo a casa" не по-итальянски
    ("B12", "Я прихожу к нему — домой (gli, arrivare, casa);Gli arrivo a casa",
            "Я несу ему подарок (gli, portare, regalo);Gli porto un regalo"),

    # B13: pulirsi = "мыться", а не "убираться"
    ("B13", "Я убираюсь (mi, pulire);Mi pulisco",
            "Я умываюсь (mi, lavarsi);Mi lavo"),
    ("B13", "Ты убираешься (ti, pulire);Ti pulisci",
            "Ты умываешься (ti, lavarsi);Ti lavi"),
    ("B13", "Мы убираемся (ci, pulire);Ci puliamo",
            "Мы умываемся (ci, lavarsi);Ci laviamo"),
    ("B13", "Она убирается в той самой кухне (si, pulire, cucina);Si pulisce in cucina",
            "Она умывается на той самой кухне (si, lavarsi, cucina);Si lava in cucina"),

    # B14: оборванные "anche"
    ("B14", "Ты тоже думаешь об этом? (ci, pensare);Ci pensi anche",
            "Ты тоже думаешь об этом? (ci, pensare, anche, tu);Ci pensi anche tu?"),
    ("B14", "Они тоже живут там (ci, abitare);Ci abitano anche",
            "Они тоже живут там (ci, abitare, anche, loro);Ci abitano anche loro"),

    # B21: порядок слов при nemmeno / affatto / nessuno
    ("B21", "Non va a casa nemmeno", "Non va nemmeno a casa"),
    ("B21", "Та самая книга не красивая вовсе (non, libro, bello, affatto);Il libro non è bello affatto",
            "Та самая книга вовсе не красивая (non, libro, bello, affatto);Il libro non è affatto bello"),
    ("B21", "Никто не делает подарок (non, fare, regalo, nessuno);Non fa un regalo nessuno",
            "Никто не делает подарок (nessuno, fare, regalo);Nessuno fa un regalo"),

    # B23: пассив процесса строится через venire (это же и учит C11)
    ("B23", "Тот самый дом покупался (casa, comprato, imperfetto passivo);La casa era comprata",
            "Тот самый дом покупался (casa, comprare, venire, imperfetto passivo);La casa veniva comprata"),

    # C10: согласование si passivante + необъявленное "quella"
    ("C10", "Ci si compra i libri", "Ci si comprano i libri"),
    ("C10", "Si è cucinata la cena in quella cucina", "Si è cucinata la cena in cucina"),

    # C17: русский промпт в настоящем, ответ в будущем
    ("C17", "Совершаю возврат в свою резиденцию — формально",
            "Вернусь в свою резиденцию — формально"),

    # C18: dare per scontato che требует congiuntivo (этому учат B25-C15)
    ("C18", "do per scontato che è nuova", "do per scontato che sia nuova"),
    ("C18", "Diamo per scontato che la famiglia lavora",
            "Diamo per scontato che la famiglia lavori"),

    # C19: pensare a -> a cui (и это урок ПРО управление)
    ("C19", "книга, о которой думаем (libro, di cui, pensare);Il libro di cui pensiamo",
            "книга, о которой думаем (libro, a cui, pensare);Il libro a cui pensiamo"),
    ("C19", "книга, о которой мы думаем — di cui (libro, di cui, pensare);Il libro di cui pensiamo",
            "книга, о которой мы думаем — a cui (libro, a cui, pensare);Il libro a cui pensiamo"),
    # pensare управляет предлогом a, значит и местоимение ci, а не ne
    ("C19", "думаю о доме (pensare, di, casa);Penso alla casa",
            "думаю о доме (pensare, a, casa);Penso alla casa"),
    ("C19", "ne: думаю о ней, о книге (pensare, ne);Ne penso",
            "ci: думаю об этом, о книге (pensare, ci);Ci penso"),
    ("C19", "ДАТ.П.: готовлю ужин для семьи", "ДАТ.П.: готовлю ужин семье"),

    # ---- §6 подсказка противоречит ответу -------------------------------
    ("B04", "Он сделал подарок и пошёл домой (è, fare, regalo, e, andare, casa)",
            "Он сделал подарок и пошёл домой (ha, fare, regalo, e, è, andare, casa)"),
    ("C16", "Я покупаю домик (comprare, casetta, -ino)",
            "Я покупаю домик (comprare, casa, -etta)"),

    # ---- §2 коллизии промптов -------------------------------------------
    # B09 vs B07: "должно быть" = futuro epistemico; condizionale = "говорят"
    ("B09", "Должно быть, та самая кухня маленькая (cucina, sarebbe, piccolo)",
            "Говорят, та самая кухня маленькая (cucina, sarebbe, piccolo)"),

    # B11 vs A02: местоимение против полного дополнения
    ("B11", "Я покупаю тот самый дом (la, comprare);La compro",
            "Я покупаю его — тот самый дом (la, comprare);La compro"),
    ("B11", "Я покупаю ту самую машину (la, comprare);La compro",
            "Я покупаю её — ту самую машину (la, comprare);La compro"),
    ("B11", "Я иду туда — домой (vado a casa);Vado a casa",
            "Я иду домой (vado, a casa);Vado a casa"),

    # B16 vs B11: там же настоящее продолженное
    ("B16", "Я иду туда — домой (ci, stare, andare)",
            "Я сейчас иду туда — домой (ci, stare, andare)"),
    ("B16", "Я готовлю тот самый ужин (la, stare, cucinare)",
            "Я сейчас готовлю тот самый ужин (la, stare, cucinare)"),
    ("B16", "Я убираю ту самую кухню (la, stare, pulire)",
            "Я сейчас убираю ту самую кухню (la, stare, pulire)"),
    # и подсказка "Я должен" при ответе "Dobbiamo"
    # (запускается уже после перевода ответов на разделитель "+")
    ("B16", "Я должен вам это сказать (velo, dovere, dire);Dobbiamo dirvelo + Ve lo dobbiamo dire",
            "Мы должны вам это сказать (velo, dovere, dire);Dobbiamo dirvelo + Ve lo dobbiamo dire"),

    # C16: "книжечка" дважды с разными ответами
    ("C16", "Маленькая книжечка для тебя (libretto, essere, per, tu)",
            "Тот самый блокнот для тебя (libretto, essere, per, tu)"),

    # C20: чинит и коллизии, и грамматику
    ("C20", "Дом — туда я иду (casa, ci, andare);Ci vado a casa",
            "Туда я и иду (ci, andare);Ci vado"),
    ("C20", "Подарок делается (regalo, essere fatto);Il regalo è fatto",
            "Подарок сделан (regalo, essere fatto);Il regalo è fatto"),
    ("C20", "Пошёл домой (andare, casa, PP);Sono andato a casa",
            "Я пошёл домой (andare, casa, PP);Sono andato a casa"),
    ("C20", "Дом покупается мной (casa, essere comprato, da, io);La casa è comprata da me",
            "Дом куплен мной (casa, essere comprato, da, io);La casa è comprata da me"),

    # ---- §7 мета-строки, которые нельзя произнести -----------------------
    ("B12", "Я его вижу (прямое) vs я ему говорю (косвенное) (lo/gli);Lo vedo / Gli dico",
            "Я его вижу и ему говорю (lo, vedere, gli, dire);Lo vedo e gli dico"),
    ("B14", "Туда = ci / из них = ne (ci, ne);Ci vado / Ne voglio due",
            "Я иду туда и говорю об этом (ci, andare, ne, parlare);Ci vado e ne parlo"),
]

# §2: trapassato prossimo обязан иметь опору в прошлом — без неё промпт
# неотличим от passato prossimo в B03.
FOR_B06 = [
    "Я уже купил дом", "Я уже пошёл домой", "Я уже сделал подарок",
    "Мы уже жили в том самом большом доме", "Та самая семья уже приготовила ужин",
    "Она уже купила ту самую новую машину",
]
# §2: condizionale composto — прошедшее нереальное, маркируем "Тогда".
FOR_B10 = [
    "Я купил бы дом", "Я пошёл бы домой", "Я сделал бы подарок",
    "Ты купил бы ту самую машину", "Мы убрали бы кухню", "Они приготовили бы ужин",
    "Вы сделали бы тот самый подарок", "Она пошла бы домой",
    "Мы пришли бы на работу", "Та самая семья прибыла бы",
]
# §2: passato remoto против passato prossimo — маркируем повествовательным
# "Однажды" и явным лицом.
FOR_C07 = ["Купил дом", "Пошёл домой", "Сделал подарок"]
FOR_C08 = ["Купил дом", "Пошёл домой", "Сделал подарок"]

for ru in FOR_B06:
    PATCHES.append(("B06", ru + " (", "К тому времени " + ru[0].lower() + ru[1:] + " ("))
for ru in FOR_B10:
    PATCHES.append(("B10", "\n" + ru + " (", "\nТогда " + ru[0].lower() + ru[1:] + " ("))
for ru in FOR_C07:
    PATCHES.append(("C07", "\n" + ru + " (", "\nОднажды он " + ru[0].lower() + ru[1:] + " ("))
for ru in FOR_C08:
    PATCHES.append(("C08", "\n" + ru + " (", "\nОднажды я " + ru[0].lower() + ru[1:] + " ("))

# §6: подсказки вида "ebbe comprare" — несуществующие формы, весь урок C09.
C09_HINTS = [("ebbe comprare", "ebbe comprato"), ("fu venire", "fu venuto"),
             ("ebbe fare", "ebbe fatto"), ("ebbe scrivere", "ebbe scritto"),
             ("furono venire", "furono venuti"), ("ebbero pulire", "ebbero pulito"),
             ("ebbe cucinare", "ebbe cucinato"), ("furono partire", "furono partiti"),
             ("ebbero comprare", "ebbero comprato")]
for a, b in C09_HINTS:
    PATCHES.append(("C09", a, b))

# §3: C01 — придаточное без главного. Добавляем аподосис, не пересекаясь с C02.
C01_FIX = [
    ("Если бы я купил дом (se, avessi, comprato, casa);Se avessi comprato una casa",
     "Если бы я купил дом, я был бы дома (se, avessi, comprato, casa, essere);Se avessi comprato una casa, sarei stato a casa"),
    ("Если бы я пошёл домой (se, fossi, andato, casa);Se fossi andato a casa",
     "Если бы я пошёл домой, я увидел бы ту самую семью (se, fossi, andato, casa, vedere);Se fossi andato a casa, avrei visto la famiglia"),
    ("Если бы я сделал подарок (se, avessi, fatto, regalo);Se avessi fatto un regalo",
     "Если бы я сделал подарок, я не купил бы машину (se, avessi, fatto, regalo, comprare);Se avessi fatto un regalo, non avrei comprato la macchina"),
    ("Если бы мы убрали кухню (se, avessimo, pulito, cucina);Se avessimo pulito la cucina",
     "Если бы мы убрали кухню, мы пошли бы домой (se, avessimo, pulito, cucina, andare);Se avessimo pulito la cucina, saremmo andati a casa"),
    ("Если бы та самая семья приготовила ужин (se, famiglia, avesse, cucinato);Se la famiglia avesse cucinato",
     "Если бы та самая семья приготовила ужин, мы пришли бы рано (se, famiglia, avesse, cucinato, venire);Se la famiglia avesse cucinato, saremmo venuti presto"),
    ("Если бы она пошла на работу (se, fosse, andata, lavoro);Se fosse andata al lavoro",
     "Если бы она пошла на работу, её не было бы дома (se, fosse, andata, lavoro, essere);Se fosse andata al lavoro, non sarebbe stata a casa"),
    ("Если бы они жили в том самом доме (se, avessero, abitato, casa);Se avessero abitato nella casa",
     "Если бы они жили в том самом доме, они убрали бы кухню (se, avessero, abitato, casa, pulire);Se avessero abitato nella casa, avrebbero pulito la cucina"),
    ("Если бы он купил ту самую новую машину (se, avesse, comprato, macchina, nuovo);Se avesse comprato la macchina nuova",
     "Если бы он купил ту самую новую машину, он уехал бы (se, avesse, comprato, macchina, nuovo, partire);Se avesse comprato la macchina nuova, sarebbe partito"),
    ("Если бы та самая кухня была чистая (se, cucina, fosse, pulita);Se la cucina fosse stata pulita",
     "Если бы та самая кухня была чистая, мы приготовили бы ужин (se, cucina, fosse, stata, pulita, cucinare);Se la cucina fosse stata pulita, avremmo cucinato la cena"),
    ("Если бы та самая книга была большая (se, libro, fosse, grande);Se il libro fosse stato grande",
     "Если бы та самая книга была большая, я не купил бы её (se, libro, fosse, stato, grande, comprare);Se il libro fosse stato grande, non l'avrei comprato"),
]
PATCHES += [("C01", a, b) for a, b in C01_FIX]

# §3: C15 — все 15 строк были придаточными без главного.
C15_FIX = [
    ("Прежде чем купить дом (prima che, comprare, casa);Prima che compri una casa",
     "Я работаю, прежде чем он купит дом (lavorare, prima che, comprare, casa);Lavoro prima che compri una casa"),
    ("Прежде чем пойти домой (prima che, andare, casa);Prima che vada a casa",
     "Я готовлю ужин, прежде чем он пойдёт домой (cucinare, cena, prima che, andare, casa);Cucino la cena prima che vada a casa"),
    ("Прежде чем сделать подарок (prima che, fare, regalo);Prima che faccia un regalo",
     "Я убираю кухню, прежде чем он сделает подарок (pulire, cucina, prima che, fare, regalo);Pulisco la cucina prima che faccia un regalo"),
    ("Без того чтобы мы работали вместе (senza che, lavorare, insieme);Senza che lavoriamo insieme",
     "Он покупает дом без того, чтобы мы работали вместе (comprare, casa, senza che, lavorare, insieme);Compra una casa senza che lavoriamo insieme"),
    ("Без того чтобы она убирала кухню (senza che, pulire, cucina);Senza che pulisca la cucina",
     "Мы готовим ужин без того, чтобы она убирала кухню (cucinare, cena, senza che, pulire, cucina);Cuciniamo la cena senza che pulisca la cucina"),
    ("Если только он не придёт домой (a meno che, non, venire, casa);A meno che non venga a casa",
     "Я работаю, если только он не придёт домой (lavorare, a meno che, non, venire, casa);Lavoro a meno che non venga a casa"),
    ("При условии что она живёт в том самом большом доме (purché, abitare, casa, grande);Purché abiti nella casa grande",
     "Я покупаю ту самую машину при условии, что она живёт в том самом большом доме (comprare, macchina, purché, abitare, casa, grande);Compro la macchina purché abiti nella casa grande"),
    ("Хотя тот самый дом и большой (benché, casa, essere, grande);Benché la casa sia grande",
     "Хотя тот самый дом и большой, я его не покупаю (benché, casa, essere, grande, non, comprare);Benché la casa sia grande, non la compro"),
    ("Хотя та самая машина и красивая (sebbene, macchina, essere, bello);Sebbene la macchina sia bella",
     "Хотя та самая машина и красивая, мы её не покупаем (sebbene, macchina, essere, bello, non, comprare);Sebbene la macchina sia bella, non la compriamo"),
    ("Чтобы он купил подарок (affinché, comprare, regalo);Affinché compri un regalo",
     "Я работаю, чтобы он купил подарок (lavorare, affinché, comprare, regalo);Lavoro affinché compri un regalo"),
    ("Несмотря на то что она уходит поздно (nonostante, partire, tardi);Nonostante parta tardi",
     "Несмотря на то что она уходит поздно, мы ужинаем вместе (nonostante, partire, tardi, cenare, insieme);Nonostante parta tardi, ceniamo insieme"),
    ("В случае если будет куплена новая машина (qualora, comprare, macchina, nuovo);Qualora compri una macchina nuova",
     "В случае если он купит новую машину, мы поедем вместе (qualora, comprare, macchina, nuovo, andare, insieme);Qualora compri una macchina nuova, andremo insieme"),
    ("Как если бы он покупал ту самую машину (come se, comprare, macchina, imperfetto cong.);Come se comprasse la macchina",
     "Он говорит так, как если бы покупал ту самую машину (parlare, come se, comprare, macchina, imperfetto cong.);Parla come se comprasse la macchina"),
    ("Как если бы она жила в том самом доме (come se, abitare, casa, imperfetto cong.);Come se abitasse nella casa",
     "Она говорит так, как если бы жила в том самом доме (parlare, come se, abitare, casa, imperfetto cong.);Parla come se abitasse nella casa"),
    ("При условии что кухня будет чистой (purché, cucina, essere, pulito);Purché la cucina sia pulita",
     "Мы работаем здесь при условии, что кухня будет чистой (lavorare, qui, purché, cucina, essere, pulito);Lavoriamo qui purché la cucina sia pulita"),
]
PATCHES += [("C15", a, b) for a, b in C15_FIX]


# ---------------------------------------------------------------------------
# Второй проход: дефекты, найденные только в Full Course (он в 8 раз больше
# Fast Track, и часть коллизий существует лишь там).
# ---------------------------------------------------------------------------

PATCHES_FULL = [
    # -- порча текста прямо в исходниках (тот же дефект, что раздул zip) ----
    ("A01", "Мы едим pasta (mangiamo, pasta)", "Мы едим пасту (mangiamo, pasta)"),
    ("A02", "Мы едим pasta (mangiamo, la pasta)", "Мы едим ту самую пасту (mangiamo, la pasta)"),
    ("A06", "У нас есть certezza", "У нас есть уверенность"),
    ("B22", "Учитель, который преподаёт Italian", "Учитель, который преподаёт итальянский"),
    ("B25", "Я знаю, что они едят pasta", "Я знаю, что они едят пасту"),
    ("B25", "Я думаю, что они едят pasta", "Я думаю, что они едят пасту"),
    ("C12", "Вот-вот Film начнётся", "Вот-вот фильм начнётся"),

    # -- артикль: A01 даёт голое имя, A02/A03/A05 — определённое ------------
    # Конвенция пака: "тот самый X" = определённый артикль.
    ("A02", "Вы едите сыр (mangiate, il formaggio)", "Вы едите тот самый сыр (mangiate, il formaggio)"),
    ("A02", "Вы пьёте молоко (bevete, il latte)", "Вы пьёте то самое молоко (bevete, il latte)"),
    ("A03", "Мы пьём чай (beviamo, il tè)", "Мы пьём тот самый чай (beviamo, il tè)"),
    ("A02", "Мы смотрим картину (guardiamo, il quadro)", "Мы смотрим ту самую картину (guardiamo, il quadro)"),
    ("A02", "Он ест мясо (mangia, la carne)", "Он ест то самое мясо (mangia, la carne)"),
    ("A03", "Он ест мясо (mangia, la carne)", "Он ест то самое мясо (mangia, la carne)"),
    ("A02", "Он смотрит фото (guarda, la foto)", "Он смотрит то самое фото (guarda, la foto)"),
    ("A02", "Он читает письмо (legge, la lettera)", "Он читает то самое письмо (legge, la lettera)"),
    ("A02", "Они дают надежду (danno, la speranza)", "Они дают ту самую надежду (danno, la speranza)"),
    ("A02", "Они пьют пиво (bevono, la birra)", "Они пьют то самое пиво (bevono, la birra)"),
    ("A02", "Ты ешь рыбу (mangi, il pesce)", "Ты ешь ту самую рыбу (mangi, il pesce)"),
    ("A02", "Ты ищешь работу (cerchi, il lavoro)", "Ты ищешь ту самую работу (cerchi, il lavoro)"),
    ("A05", "Ты ищешь работу (cerchi, il lavoro)", "Ты ищешь ту самую работу (cerchi, il lavoro)"),
    ("A03", "Ты пьёшь вино (bevi, il vino)", "Ты пьёшь то самое вино (bevi, il vino)"),
    ("A02", "Я ем хлеб (mangio, il pane)", "Я ем тот самый хлеб (mangio, il pane)"),
    ("A05", "Ты пишешь письмо (scrivi, la lettera)", "Ты пишешь то самое письмо (scrivi, la lettera)"),

    # -- A08 учит c'è/ci sono и берёт указательное quel/quella; "тот самый"
    #    в этом паке уже занято под артикль, поэтому "вон тот".
    ("A08", "В той самой комнате нет окна", "Вон в той комнате нет окна"),
    ("A08", "В том самом саду есть красивый цветок", "Вон в том саду есть красивый цветок"),
    ("A08", "На том самом столе нет книги", "Вон на том столе нет книги"),

    # -- местоименные уроки: промпт должен требовать местоимение ------------
    ("B11", "Я делаю подарок (lo, fare);Lo faccio",
            "Я делаю его — тот самый подарок (lo, fare);Lo faccio"),
    ("B11", "Я покупаю дом (la, comprare);La compro",
            "Я покупаю его — тот самый дом (la, comprare);La compro"),
    ("B11", "Я покупаю подарок (lo, comprare);Lo compro",
            "Я покупаю его — тот самый подарок (lo, comprare);Lo compro"),
    ("B11", "Я знаю ответ (la, sapere);La so",
            "Я знаю его — тот самый ответ (la, sapere);La so"),
    ("B11", "Ты читаешь книгу (lo, leggere);Lo leggi",
            "Ты читаешь её — ту самую книгу (lo, leggere);Lo leggi"),
    # заодно элизия: перед гласной lo/la дают l'
    ("B11", "Ты открываешь дверь (la, aprire);La apri",
            "Ты открываешь её — ту самую дверь (la, aprire);L'apri"),
    ("B11", "Я купил дом (la, comprare, PP);L'ho comprata",
            "Я купил его — тот самый дом (la, comprare, PP);L'ho comprata"),
    ("B11", "Она написала письмо (la, scrivere, PP);L'ha scritta",
            "Она написала его — то самое письмо (la, scrivere, PP);L'ha scritta"),
    ("B11", "Она приготовила ужин (la, cucinare, PP);L'ha cucinata",
            "Она приготовила его — тот самый ужин (la, cucinare, PP);L'ha cucinata"),
    ("B16", "Ты покупаешь ту самую машину (la, stare, comprare)",
            "Ты сейчас покупаешь ту самую машину (la, stare, comprare)"),
    ("B16", "Она возвращается туда — домой (ci, stare, tornare)",
            "Она сейчас возвращается туда — домой (ci, stare, tornare)"),

    # -- passato remoto против passato prossimo ----------------------------
    ("C07", "Он пошёл домой (andò, casa)", "Однажды он пошёл домой (andò, casa)"),
    ("C08", "Они пошли домой (andarono, casa)", "Однажды они пошли домой (andarono, casa)"),
    ("C07", "Она написала письмо (scrisse, lettera)", "Однажды она написала письмо (scrisse, lettera)"),
    ("C08", "Она написала письмо (scrisse, lettera)", "Однажды она написала письмо (scrisse, lettera)"),
    ("C07", "Она приготовила ужин (cucinò, cena)", "Однажды она приготовила ужин (cucinò, cena)"),
    ("C08", "Ты увидел тот самый фильм (vedesti, film)", "Однажды ты увидел тот самый фильм (vedesti, film)"),
    ("C08", "Я купил дом (comprai, casa)", "Однажды я купил дом (comprai, casa)"),
    ("C07", "Тот самый концерт был отменён (concerto, fu, cancellato)",
            "Однажды тот самый концерт был отменён (concerto, fu, cancellato)"),
    ("C07", "Тот самый мост был построен (ponte, fu, costruito)",
            "Однажды тот самый мост был построен (ponte, fu, costruito)"),

    # -- прочее -------------------------------------------------------------
    ("B06", "Они уже закончили работу (avevano, finito, lavoro)",
            "К тому времени они уже закончили работу (avevano, finito, lavoro)"),
    ("C11", "Билеты покупаются онлайн (venire, comprare, biglietti, online)",
            "Билеты покупаются онлайн — процесс (venire, comprare, biglietti, online)"),
    ("C02", "Должно быть, та самая кухня маленькая (cucina, sarebbe, piccola)",
            "Говорят, та самая кухня маленькая (cucina, sarebbe, piccola)"),
    ("C14", "Если бы я знал правду, я бы сказал (qualora, sapere, verità, dire)",
            "В случае если бы я знал правду, я бы сказал (qualora, sapere, verità, dire)"),
    ("A06", "Она хочет пить (avere, sete);Ha sete",
            "Она испытывает жажду (avere, sete);Ha sete"),
    ("A16", "Дай мне книгу! (dai, libro);Dammi il libro!",
            "Дай мне ту самую книгу! (dai, libro);Dammi il libro!"),

    # §7: строка-шпаргалка требовала произнести семь фраз через "/". Смысл
    # урока — что все семь равнозначны, поэтому делаем их равноправными
    # ответами: засчитывается любой.
    ('C20',
     '"Синтез: Я покупаю дом; дом покупается; я его покупаю; хочу купить; купил бы; дом нужно купить; дом покупается прямо сейчас — всё означает одно и то же (comprare, casa)";'
     '"Compro una casa / La casa viene comprata / La compro / Voglio comprarla / La comprerei / La casa va comprata / Sto comprando una casa"',
     '"Синтез: скажи это любым из семи способов — я покупаю дом (comprare, casa)";'
     '"Compro una casa + La casa viene comprata + La compro + Voglio comprarla + La comprerei + La casa va comprata + Sto comprando una casa"'),

    # -- обе формы верны: делаем их равноправными ответами, а не коллизией --
    ("B25", "Я хочу, чтобы ты остался (voglio che, rimanere);Voglio che tu rimanga",
            "Я хочу, чтобы ты остался (voglio che, rimanere);Voglio che tu rimanga + Voglio che tu stia"),
    ("B26", "Я хочу, чтобы ты остался (voglio che, stare);Voglio che tu stia",
            "Я хочу, чтобы ты остался (voglio che, stare);Voglio che tu stia + Voglio che tu rimanga"),
    ("A06", "Вы голодны? — вежливо (avere, fame, Lei);Lei ha fame?",
            "Вы голодны? — вежливо (avere, fame, Lei);Lei ha fame? + Ha fame Lei?"),
    ("A07", "Вы голодны? — вежливо (ha, fame, Lei);Ha fame Lei?",
            "Вы голодны? — вежливо (ha, fame, Lei);Ha fame Lei? + Lei ha fame?"),
    ("A06", "У вас есть проблема? — вежливо (avere, problema, Lei);Lei ha un problema?",
            "У вас есть проблема? — вежливо (avere, problema, Lei);Lei ha un problema? + Ha un problema Lei?"),
    ("A07", "У вас есть проблема? — вежливо (ha, problema, Lei);Ha un problema Lei?",
            "У вас есть проблема? — вежливо (ha, problema, Lei);Ha un problema Lei? + Lei ha un problema?"),
]


# §3: придаточное без главного — не предложение, а обрывок; человек заучивает
# его как самостоятельную единицу и потом не может построить целую фразу.
# Главное предложение для каждого союза одно и то же: переменной остаётся
# только придаточное — ровно та "неподвижная рама", на которой построен пак.
FRAGMENT_TEMPLATES = {
    # союз: (ru_приставка, ru_суффикс, it_приставка, it_суффикс, лемма_в_подсказку)
    "Se ":         ("", ", всё было бы иначе", "", ", sarebbe stato diverso", "essere"),
    "Purché":      ("Всё в порядке, ", "", "Va bene ", "", "andare, bene"),
    "Affinché":    ("Я делаю это, ", "", "Lo faccio ", "", "lo, fare"),
    "Nonostante":  ("Мы работаем, ", "", "Lavoriamo ", "", "lavorare"),
    "Qualora":     ("Я напишу тебе, ", "", "Ti scrivo ", "", "ti, scrivere"),
    "Come se":     ("Он говорит так, ", "", "Parla ", "", "parlare"),
    "Prima che":   ("Я работаю, ", "", "Lavoro ", "", "lavorare"),
    "Senza che":   ("Я делаю это ", "", "Lo faccio ", "", "lo, fare"),
    "A meno che":  ("Я приду, ", "", "Vengo ", "", "venire"),
    "Benché":      ("Мы работаем, ", "", "Lavoriamo ", "", "lavorare"),
    "Sebbene":     ("Мы работаем, ", "", "Lavoriamo ", "", "lavorare"),
    "Malgrado":    ("Мы работаем, ", "", "Lavoriamo ", "", "lavorare"),
}


def complete_fragments():
    import re
    hint_re = re.compile(r"^(.*?)(\s*\(([^)]*)\))$")
    total = 0
    for pack in PACKS:
        for name in ("C01", "C15"):
            p = BASE / pack / f"{name}.csv"
            if not p.exists():
                continue
            out, txt, n = [], p.read_text(encoding="utf-8", newline=""), 0
            for i, raw in enumerate(txt.split("\n")):
                eol = "\r" if raw.endswith("\r") else ""
                s = raw.rstrip("\r")
                if i and s.strip() and ";" in s:
                    ru, it = s.rsplit(";", 1)
                    key = next((k for k in FRAGMENT_TEMPLATES if it.startswith(k)), None)
                    if key and "," not in it:
                        rp, rs, ip, isuf, lemma = FRAGMENT_TEMPLATES[key]
                        m = hint_re.match(ru)
                        core, hint = (m.group(1), m.group(3)) if m else (ru, "")
                        core = (rp + core[0].lower() + core[1:] if rp else core) + rs
                        hint = f"{hint}, {lemma}" if hint else lemma
                        s = f"{core} ({hint});{ip}{it}{isuf}"
                        n += 1
                out.append(s + eol)
            p.write_text("\n".join(out), encoding="utf-8", newline="")
            if n:
                print(f"  {pack}/{name}.csv: дописано главных предложений: {n}")
            total += n
    return total


def defuse_polite_marker():
    """§2: '(вежл.)' — это скобка, а HintCalculator стирает ВСЕ скобки.

    То есть маркер вежливой формы исчезает ровно на третьей встрече, когда он
    только и нужен: 'Вы (вежл.) идёте домой' превращается в 'Вы идёте домой' и
    становится неотличимо от voi. Выносим маркер за скобки в стиле, который в
    паке уже принят для регистров (C17: '— формально' / '— разговорно').
    """
    import re
    moved = 0
    for pack in PACKS:
        for p in sorted((BASE / pack).glob("[ABC][0-9][0-9].csv")):
            out, txt = [], p.read_text(encoding="utf-8", newline="")
            for i, raw in enumerate(txt.split("\n")):
                eol = "\r" if raw.endswith("\r") else ""
                s = raw.rstrip("\r")
                if i and "(вежл.)" in s and ";" in s:
                    ru, ans = s.rsplit(";", 1)
                    ru = ru.replace(" (вежл.)", "").replace("(вежл.) ", "")
                    m = re.match(r"^(.*?)(\s*\([^)]*\))$", ru)
                    ru = f"{m.group(1)} — вежливо{m.group(2)}" if m else ru + " — вежливо"
                    s = f"{ru};{ans}"
                    moved += 1
                out.append(s + eol)
            p.write_text("\n".join(out), encoding="utf-8", newline="")
    print(f"  маркер '(вежл.)' вынесен за скобки в {moved} строках")


def requote_semicolon_rows():
    """§1: ';' — разделитель колонок, но в C14/C20 он же стоит внутри ответа.

    Fast Track из-за этого терял строки (3 колонки -> MalformedLine), а в Full
    Course кавычки стояли не на месте, и строка молча парсилась как 2 колонки с
    мусором: promptRu = весь текст вместе с половиной ответа. Разделитель —
    ';' сразу за закрывающей скобкой подсказки; обе части берём в кавычки,
    CsvLineParser их понимает.
    """
    import re
    for pack in PACKS:
        for name in ("C14", "C20"):
            p = BASE / pack / f"{name}.csv"
            if not p.exists():
                continue
            out, changed = [], 0
            txt = p.read_text(encoding="utf-8", newline="")
            for i, raw in enumerate(txt.split("\n")):
                eol = "\r" if raw.endswith("\r") else ""
                s = raw.rstrip("\r")
                if i and s.strip():
                    core = s.replace('"', "")
                    if core.count(";") > 1:
                        m = re.match(r"^(.*\));(.*)$", core)
                        s = f'"{m.group(1).strip()}";"{m.group(2).strip()}"'
                        changed += 1
                out.append(s + eol)
            p.write_text("\n".join(out), encoding="utf-8", newline="")
            if changed:
                print(f"  {pack}/{name}.csv: перекавычено строк с ';' внутри ответа: {changed}")


def main():
    applied, missed = 0, []
    requote_semicolon_rows()
    defuse_polite_marker()
    # B16: " / " — не альтернативы для парсера, а один непроизносимый ответ.
    for pack in PACKS:
        p = BASE / pack / "B16.csv"
        if not p.exists():
            continue
        txt = p.read_text(encoding="utf-8")
        n = txt.count(" / ")
        p.write_text(txt.replace(" / ", " + "), encoding="utf-8")
        print(f"  {pack}/B16.csv: {n} ответов переведено с ' / ' на ' + '")

    for lesson, old, new in PATCHES + PATCHES_FULL:
        hit = 0
        for pack in PACKS:
            p = BASE / pack / f"{lesson}.csv"
            if not p.exists():
                continue
            txt = p.read_text(encoding="utf-8")
            if old in txt:
                p.write_text(txt.replace(old, new), encoding="utf-8")
                hit += txt.count(old)
        if hit:
            applied += hit
        else:
            missed.append((lesson, old))

    # после точечных правок: всё, что осталось придаточным без главного
    complete_fragments()

    print(f"\nприменено замен: {applied}")
    if missed:
        print(f"НЕ НАЙДЕНО ({len(missed)}):")
        for lesson, old in missed:
            print(f"  {lesson}: {old[:78]}")
    return 1 if missed else 0


if __name__ == "__main__":
    sys.exit(main())
