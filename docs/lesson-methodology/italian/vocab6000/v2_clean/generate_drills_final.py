#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generate drill CSV files from v2_new_lemmas.txt (top 2760 entries).
Two-layer translation: exact-match dictionary + smart fallback.
"""
import csv, os, sys, re

if sys.stdout.encoding != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8')

BASE = os.path.dirname(os.path.abspath(__file__))
INPUT = os.path.join(BASE, "v2_new_lemmas.txt")

entries = []
with open(INPUT, encoding="utf-8") as f:
    next(f)
    for line in f:
        parts = line.rstrip("\n").split("\t")
        if len(parts) < 4:
            continue
        rank, original, lemma, pos = int(parts[0]), parts[1], parts[2], parts[3]
        entries.append((rank, original, lemma, pos))
        if len(entries) >= 2760:
            break

# ═══════════════════════════════════════════════════════════════════════════════
# EXACT MATCH DICTIONARY — keyed by (original, POS)
# Covers entries where the spaCy original/lemma is garbled or non-standard.
# ═══════════════════════════════════════════════════════════════════════════════
EXACT = {}

def _n(orig, ru): EXACT[(orig, "NOUN")] = ru
def _v(orig, ru): EXACT[(orig, "VERB")] = ru
def _a(orig, ru): EXACT[(orig, "ADJ")]  = ru
def _d(orig, ru): EXACT[(orig, "ADV")]  = ru

# ─── NOUNS: proper names (transliterated) + special ──────────────────────────
# Names from subtitles
for orig, ru in [
    ("jack","Джек"), ("frank","Фрэнк"), ("danny","Дэнни"), ("nick","Ник"),
    ("bill","Билл"), ("tony","Тони"), ("ray","Рэй"), ("will","Уилл"),
    ("bob","Боб"), ("billy","Билли"), ("richard","Ричард"), ("amy","Эми"),
    ("andy","Энди"), ("josh","Джош"), ("matt","Мэтт"), ("grace","Грейс"),
    ("annie","Энни"), ("jeff","Джефф"), ("roger","Роджер"), ("jenny","Дженни"),
    ("barry","Барри"), ("maggie","Мэгги"), ("patrick","Патрик"),
    ("oliver","Оливер"), ("terry","Терри"), ("penny","Пенни"),
    ("rebecca","Ребекка"), ("casey","Кейси"), ("rick","Рик"),
    ("ricky","Рики"), ("ethan","Итан"), ("jackie","Джеки"),
    ("cooper","Купер"), ("fred","Фред"), ("betty","Бетти"), ("mac","Мак"),
    ("cole","Коул"), ("harvey","Харви"), ("catherine","Кэтрин"),
    ("owen","Оуэн"), ("sally","Салли"), ("miller","Миллер"),
    ("shawn","Шон"), ("steven","Стивен"), ("justin","Джастин"),
    ("nina","Нина"), ("ron","Рон"), ("sophie","Софи"), ("nancy","Нэнси"),
    ("dennis","Деннис"), ("davis","Дэвис"), ("grant","Грант"),
    ("brown","Браун"), ("spencer","Спенсер"), ("carol","Кэрол"),
    ("wayne","Уэйн"), ("jules","Джулс"), ("bonnie","Бонни"),
    ("walker","Уокер"), ("booth","Бут"), ("jenna","Дженна"),
    ("edward","Эдвард"), ("ken","Кен"), ("leslie","Лесли"),
    ("miles","Майлз"), ("carrie","Кэрри"), ("earl","Эрл"),
    ("matthew","Мэттью"), ("janet","Джанет"), ("alicia","Алисия"),
    ("ellie","Элли"), ("maya","Майя"), ("erica","Эрика"),
    ("green","Грин"), ("mitchell","Митчелл"), ("mickey","Микки"),
    ("wade","Уэйд"), ("jill","Джилл"), ("malcolm","Малькольм"),
    ("tracy","Трейси"), ("ronnie","Ронни"), ("nora","Нора"),
    ("barney","Барни"), ("amber","Эмбер"), ("colin","Колин"),
    ("cameron","Камерон"), ("pam","Пэм"), ("norman","Норман"),
    ("drew","Дрю"), ("hunter","Хантер"), ("stefan","Стефан"),
    ("daisy","Дейзи"), ("klaus","Клаус"), ("stone","Стоун"),
    ("ruth","Рут"), ("melissa","Мелисса"), ("katherine","Кэтрин"),
    ("tara","Тара"), ("christine","Кристин"), ("salvatore","Сальваторе"),
    ("bishop","Бишоп"), ("andre","Андре"), ("stuart","Стюарт"),
    ("brooke","Брук"), ("angel","Ангел"), ("chase","Чейз"),
    ("matt","Мэтт"), ("dawson","Доусон"), ("bobby","Бобби"),
    ("richie","Ричи"), ("mikey","Майки"), ("willie","Вилли"),
    ("rocky","Рокки"), ("percy","Перси"), ("bud","Бад"),
    ("shepherd","Шепард"), ("kirk","Кирк"), ("woody","Вуди"),
    ("baxter","Бакстер"), ("fitz","Фиц"), ("shannon","Шеннон"),
    ("evans","Эванс"), ("cynthia","Синтия"), ("stewart","Стюарт"),
    ("bennet","Беннет"), ("esther","Эстер"), ("thor","Тор"),
    ("damien","Дэмиен"), ("joshua","Джошуа"), ("sidney","Сидней"),
    ("shelly","Шелли"), ("kathy","Кэти"), ("sonny","Сонни"),
    ("mandy","Мэнди"), ("eli","Илай"), ("felix","Феликс"),
    ("stevie","Стиви"), ("stark","Старк"), ("jared","Джаред"),
    ("artie","Арти"), ("douglas","Дуглас"), ("laurel","Лорел"),
    ("homer","Гомер"), ("sophia","София"), ("rudy","Руди"),
    ("sasha","Саша"), ("foster","Фостер"), ("porter","Портер"),
    ("stephanie","Стефани"), ("matty","Мэтти"), ("lenny","Ленни"),
    ("murray","Мюррей"), ("kara","Кара"), ("karma","Карма"),
    ("reynolds","Рейнольдс"), ("juliette","Джульетта"),
    ("axl","Аксел"), ("val","Вэл"), ("ivy","Айви"), ("piper","Пайпер"),
    ("rex","Рекс"), ("silver","Сильвер"), ("clarke","Кларк"),
    ("ward","Уорд"), ("crane","Крейн"), ("moore","Мур"),
    ("palmer","Палмер"), ("jasper","Джаспер"), ("mindy","Минди"),
    ("donnie","Донни"), ("agnes","Агнес"), ("nash","Нэш"),
    ("brett","Бретт"), ("hunt","Хант"), ("tessa","Тесса"),
    ("nicholas","Николас"), ("vivian","Вивиан"), ("sid","Сид"),
    ("eve","Ив"), ("joy","Джой"), ("ned","Нед"), ("patty","Пэтти"),
    ("denise","Дениз"), ("dawn","Доун"), ("nicky","Ники"),
    ("jeffrey","Джеффри"), ("fisher","Фишер"), ("dave","Дэйв"),
    ("todd","Тодд"), ("connor","Коннор"), ("riley","Райли"),
    ("martha","Марта"), ("travis","Трэвис"), ("andrew","Эндрю"),
    ("frankie","Фрэнки"), ("linda","Линда"), ("gibbs","Гиббс"),
    ("dylan","Дилан"), ("evan","Эван"), ("neal","Нил"),
    ("harold","Гарольд"), ("mitch","Митч"), ("debbie","Дебби"),
    ("dwight","Дуайт"), ("robbie","Робби"), ("doyle","Дойл"),
    ("brandon","Брэндон"), ("pablo","Пабло"), ("valentino","Валентино"),
    ("dee","Ди"), ("stanley","Стэнли"), ("donald","Дональд"),
    ("lex","Лекс"), ("miguel","Мигель"), ("haley","Хейли"),
    ("nolan","Нолан"), ("beverly","Беверли"), ("brody","Броди"),
    ("peggy","Пегги"), ("reggie","Реджи"), ("gavin","Гэвин"),
    ("donovan","Донован"), ("sadie","Сейди"), ("eleanor","Элеонора"),
    ("edgar","Эдгар"), ("garrett","Гарретт"), ("bridget","Бриджит"),
    ("peyton","Пейтон"), ("darren","Даррен"), ("mina","Мина"),
    ("carmen","Кармен"), ("roman","Роман"), ("alec","Алек"),
    ("isaac","Айзек"), ("freddie","Фредди"), ("callie","Кэлли"),
    ("abigail","Эбигейл"), ("theo","Тео"), ("cathy","Кэти"),
    ("june","Джун"), ("reese","Рис"), ("valerie","Валери"),
    ("daphne","Дафна"), ("chelsea","Челси"), ("shirley","Ширли"),
    ("brendan","Брендан"), ("rafael","Рафаэль"), ("dante","Данте"),
    ("trish","Триш"), ("gabrielle","Габриэль"), ("mills","Миллс"),
    ("rodney","Родни"), ("price","Прайс"), ("kane","Кейн"),
    ("choi","Чой"), ("chang","Чанг"), ("chen","Чен"), ("dong","Дон"),
    ("chan","Чан"), ("sung","Сан"), ("jun","Джун"), ("jin","Джин"),
    ("shin","Шин"), ("omar","Омар"), ("lawrence","Лоуренс"),
    ("logan","Логан"), ("shaw","Шо"), ("avery","Эвери"),
    ("clyde","Клайд"), ("calvin","Кальвин"),
    ("hugh","Хью"), ("lance","Ланс"), ("hammond","Хаммонд"),
    ("marie","Мария"), ("lois","Лоис"), ("pope","Папа Римский"),
    ("elvis","Элвис"), ("batman","Бэтмен"), ("sherlock","Шерлок"),
    ("xena","Зена"), ("lupin","Люпен"), ("vega","Вега"),
    ("sam","Сэм"), ("alex","Алекс"), ("mark","Марк"),
    ("john","Джон"), ("james","Джеймс"), ("david","Дэвид"),
    ("michael","Майкл"), ("paul","Пол"), ("george","Джордж"),
    ("thomas","Томас"), ("chris","Крис"), ("daniel","Дэниел"),
    ("lucas","Лукас"), ("adam","Адам"), ("ben","Бен"),
    ("sammy","Сэмми"), ("tommy","Томми"), ("kate","Кейт"),
    ("mary","Мэри"), ("sara","Сара"), ("lisa","Лиза"),
    ("emma","Эмма"), ("julia","Юлия"), ("helen","Хелен"),
    ("rachel","Рэйчел"), ("susan","Сьюзен"), ("laura","Лора"),
    ("sandy","Сэнди"), ("mike","Майк"), ("steve","Стив"),
    ("brian","Брайан"), ("kevin","Кевин"), ("joe","Джо"),
    ("eric","Эрик"), ("derek","Дерек"), ("sean","Шон"),
    ("phil","Фил"), ("aaron","Аарон"), ("craig","Крейг"),
    ("bruce","Брюс"), ("peter","Питер"), ("ian","Иан"),
    ("simon","Саймон"), ("max","Макс"), ("lee","Ли"),
    ("marco","Марко"), ("luca","Лука"), ("giulia","Джулия"),
    ("alessandro","Алессандро"), ("roberto","Роберто"),
    ("federico","Федерико"), ("lorenzo","Лоренцо"),
    ("matteo","Маттео"), ("andrea","Андреа"),
    ("stefania","Стефания"), ("elena","Елена"),
    ("francesca","Франческа"), ("patrizia","Патриция"),
    ("giuseppe","Джузеппе"), ("antonio","Антонио"),
    ("benny","Бенни"), ("sheila","Шейла"), ("lori","Лори"),
    ("jen","Джен"), ("jess","Джесс"), ("angie","Энджи"),
    ("tess","Тесс"), ("chas","Чарли"), ("coop","Куп"),
]:
    _n(orig, ru)

# Names tagged as VERB or ADJ by spaCy
for orig, ru in [
    ("scott","Скотт"), ("dave","Дэйв"), ("todd","Тодд"),
    ("angeles","Анджелес"), ("linda","Линда"), ("connor","Коннор"),
    ("riley","Райли"), ("martha","Марта"), ("travis","Трэвис"),
    ("andrew","Эндрю"), ("frankie","Фрэнки"), ("gibbs","Гиббс"),
    ("dylan","Дилан"), ("evan","Эван"), ("neal","Нил"),
    ("harold","Гарольд"), ("mitch","Митч"), ("debbie","Дебби"),
    ("dwight","Дуайт"), ("robbie","Робби"), ("doyle","Дойл"),
    ("brandon","Брэндон"), ("pablo","Пабло"), ("valentino","Валентино"),
    ("dee","Ди"), ("stanley","Стэнли"), ("donald","Дональд"),
    ("lex","Лекс"), ("miguel","Мигель"), ("haley","Хейли"),
    ("nolan","Нолан"), ("beverly","Беверли"), ("brody","Броди"),
    ("peggy","Пегги"), ("reggie","Реджи"), ("gavin","Гэвин"),
    ("donovan","Донован"), ("sadie","Сейди"), ("eleanor","Элеонора"),
    ("edgar","Эдгар"), ("garrett","Гарретт"), ("bridget","Бриджит"),
    ("peyton","Пейтон"), ("darren","Даррен"), ("mina","Мина"),
    ("carmen","Кармен"), ("roman","Роман"), ("alec","Алек"),
    ("isaac","Айзек"), ("freddie","Фредди"), ("callie","Кэлли"),
    ("abigail","Эбигейл"), ("theo","Тео"), ("cathy","Кэти"),
    ("june","Джун"), ("reese","Рис"), ("valerie","Валери"),
    ("daphne","Дафна"), ("chelsea","Челси"), ("shirley","Ширли"),
    ("brendan","Брендан"), ("rafael","Рафаэль"), ("dante","Данте"),
    ("trish","Триш"), ("gabrielle","Габриэль"), ("mills","Миллс"),
    ("rodney","Родни"), ("price","Прайс"), ("kane","Кейн"),
    ("logan","Логан"), ("shaw","Шо"), ("avery","Эвери"),
    ("clyde","Клайд"), ("calvin","Кальвин"),
]:
    _v(orig, ru)

# Names tagged as ADJ by spaCy
for orig, ru in [
    ("alice","Алиса"), ("charlotte","Шарлотта"), ("jamie","Джейми"),
    ("jesse","Джесси"), ("doug","Дуг"), ("caroline","Каролина"),
    ("margaret","Маргарет"), ("quinn","Куинн"), ("natalie","Натали"),
    ("diane","Диана"), ("megan","Меган"), ("shane","Шейн"),
    ("neil","Нил"), ("dick","Дик"), ("vanessa","Ванесса"),
    ("damon","Деймон"), ("anthony","Энтони"), ("raymond","Рэймонд"),
    ("nicole","Николь"), ("lydia","Лидия"), ("carmen","Кармен"),
    ("miranda","Миранда"), ("veronica","Вероника"), ("cat","Кэт"),
    ("ari","Ари"), ("randall","Рэндалл"), ("freddie","Фредди"),
    ("abigail","Эбигейл"), ("theo","Тео"), ("cathy","Кэти"),
    ("june","Джун"), ("reese","Рис"), ("valerie","Валери"),
    ("daphne","Дафна"), ("chelsea","Челси"), ("shirley","Ширли"),
    ("brendan","Брендан"), ("dallas","Даллас"), ("mick","Мик"),
    ("alec","Алек"), ("isaac","Айзек"),
]:
    _a(orig, ru)

# ─── Special NOUNs ────────────────────────────────────────────────────────────
_n("cosa","вещь/дело"); _n("grazie","спасибо"); _n("dio","Бог")
_n("okay","окей"); _n("morte","смерть"); _n("piacere","удовольствие")
_n("marito","муж"); _n("auto","машина"); _n("genere","род/жанр/вид")
_n("signorina","синьорина"); _n("be","междометие 'бе'")
_n("mente","разум/ум"); _n("cioe","то есть")
_n("fammi","дай-ка мне"); _n("smettila","прекрати!")
_n("wow","вау"); _n("giornata","день/сутки")
_n("verita","правда/истина"); _n("giu","вниз")
_n("scusami","извини меня"); _n("cristo","Христос")
_n("pero","но/однако"); _n("lista","список")
_n("lasciami","оставь меня"); _n("zitto","тихо/молчи")
_n("occhiata","взгляд"); _n("hey","эй")
_n("fame","голод"); _n("proposito","намерение/цель")
_n("uscita","выход"); _n("spalle","плечи/спина")
_n("serata","вечер (мероприятие)"); _n("esercito","армия")
_n("fbi","ФБР"); _n("mare","море"); _n("lato","сторона/бок")
_n("posti","места"); _n("accidenti","чёрт возьми!")
_n("caffe","кофе"); _n("benvenuto","добро пожаловать")
_n("banca","банк"); _n("stronzate","чушь/хрень")
_n("animali","животные"); _n("dati","данные")
_n("college","колледж"); _n("chiamate","звонки")
_n("guardami","посмотри на меня"); _n("show","шоу/спектакль")
_n("vaffanculo","иди к чёрту/отвали"); _n("sottotitoli","субтитры")
_n("latte","молоко"); _n("mercato","рынок")
_n("drink","напиток"); _n("ponte","мост")
_n("perdita","потеря"); _n("scuse","извинения")
_n("scusatemi","извините меня"); _n("ie","то есть")
_n("fidanzata","невеста/подруга"); _n("dita","пальцы")
_n("copertura","покрытие"); _n("porte","двери")
_n("fan","фанат"); _n("quartiere","квартал/район")
_n("mr","мистер"); _n("tette","груди")
_n("street","улица"); _n("sali","ступени/соль")
_n("est","восток"); _n("caduta","падение")
_n("lady","леди"); _n("entrate","входы/доходы")
_n("park","парк"); _n("polvere","пыль/порошок")
_n("scherzi","шутки"); _n("lord","лорд")
_n("credito","кредит"); _n("fatemi","дайте мне")
_n("sissignore","да, сэр"); _n("baby","малыш/ребёнок")
_n("accuse","обвинения"); _n("scrivania","письменный стол")
_n("desideri","желания"); _n("prestito","заём/ссуда")
_n("lago","озеро"); _n("occhiali","очки")
_n("sito","сайт"); _n("volto","лицо")
_n("colleghi","коллеги"); _n("band","группа/банда")
_n("champagne","шампанское"); _n("vendita","продажа")
_n("carcere","тюрьма"); _n("rock","рок")
_n("passeggiata","прогулка"); _n("football","футбол")
_n("opportunita","возможность"); _n("attivita","деятельность")
_n("calci","пинки/удары"); _n("spari","выстрелы")
_n("corda","верёвка/канат"); _n("benzina","бензин")
_n("king","Король"); _n("venerdi","пятница")
_n("nascita","рождение"); _n("universita","университет")
_n("man","человек/мужчина"); _n("cioccolato","шоколад")
_n("torre","башня"); _n("fegato","печень")
_n("vegas","Вегас"); _n("chiacchiere","болтовня")
_n("giovanotto","юноша"); _n("gang","банда")
_n("extra","дополнительно"); _n("responsabilita","ответственность")
_n("morso","укус"); _n("contea","графство")
_n("set","набор"); _n("procedura","процедура")
_n("party","вечеринка"); _n("madame","мадам")
_n("fiato","дыхание"); _n("nozze","свадьба")
_n("novita","новость/новинка"); _n("identita","личность")
_n("bici","велосипед"); _n("cavaliere","рыцарь")
_n("velocita","скорость"); _n("deposito","склад/депозит")
_n("whoa","ого"); _n("comitato","комитет")
_n("cameriere","официант"); _n("istituto","институт")
_n("staff","персонал"); _n("brindisi","тост")
_n("frigo","холодильник"); _n("insalata","салат")
_n("baci","поцелуи"); _n("filmato","видеозапись")
_n("serial","сериал"); _n("autorita","власть/органы")
_n("carattere","характер"); _n("prodotti","продукты")
_n("mestiere","профессия/ремесло"); _n("stivali","сапоги")
_n("feci","я сделал/фекалии"); _n("grilletto","курок")
_n("capacita","способность"); _n("cotta","влюблённость")
_n("dea","богиня"); _n("qualita","качество")
_n("pubblicita","реклама"); _n("comunita","сообщество")
_n("figata","круто/класс"); _n("pipi","пи-пи")
_n("consigliere","советник"); _n("feriti","раненые")
_n("facce","лица"); _n("sfortuna","неудача")
_n("ehila","эй/привет"); _n("candidato","кандидат")
_n("dozzina","дюжина"); _n("chiacchierata","беседа")
_n("appetito","аппетит"); _n("partite","матчи/партии")
_n("manette","наручники"); _n("preghiera","молитва")
_n("tuta","комбинезон"); _n("minacce","угрозы")
_n("database","база данных"); _n("ambasciata","посольство")
_n("pattuglia","патруль"); _n("giovedi","четверг")
_n("gita","экскурсия/поход"); _n("carini","милые")
_n("cellulari","мобильные"); _n("brooklyn","Бруклин")
_n("seminterrato","подвал"); _n("chiappe","ягодицы")
_n("monsieur","месье"); _n("felicita","счастье")
_n("lutto","траур"); _n("cocktail","коктейль")
_n("lire","лиры"); _n("supermercato","супермаркет")
_n("evvai","давай/ура"); _n("cassie","Кэсси")
_n("senzatetto","бездомный"); _n("banche","банки")
_n("garcia","Гарсия"); _n("ingegnere","инженер")
_n("bicchieri","стаканы"); _n("caleb","Калеб")
_n("tucker","Такер"); _n("curtis","Кёртис")
_n("sorprese","сюрпризы"); _n("preghiere","молитвы")
_n("statua","статуя"); _n("vomito","рвота")
_n("corsi","курсы"); _n("anderson","Андерсон")
_n("forme","формы"); _n("lloyd","Ллойд")
_n("lemon","Лимон"); _n("favola","сказка")
_n("server","сервер"); _n("risparmi","сбережения")
_n("gemma","драгоценный камень"); _n("diploma","диплом")
_n("contenuto","содержание"); _n("riga","строка/линия")
_n("goccia","капля"); _n("lampada","лампа")
_n("sconfitta","поражение"); _n("durata","длительность")
_n("salvataggio","спасение"); _n("lavanderia","прачечная")
_n("competizione","соревнование"); _n("vernice","лак/краска")
_n("freccia","стрела"); _n("gentilezza","доброта")
_n("commercio","торговля"); _n("assassinio","убийство")
_n("suite","люкс"); _n("vendite","продажи")
_n("riabilitazione","реабилитация"); _n("rancore","обида/злоба")
_n("flusso","поток"); _n("quantita","количество")
_n("foglio","лист (бумаги)"); _n("note","заметки/ноты")
_n("diagnosi","диагноз"); _n("codici","коды")
_n("satana","сатана"); _n("astronave","космический корабль")
_n("saggezza","мудрость"); _n("approvazione","одобрение")
_n("nuvole","облака"); _n("candele","свечи")
_n("bistecca","бифштекс"); _n("riscontro","подтверждение")
_n("cristallo","кристалл"); _n("applauso","аплодисменты")
_n("cicatrice","шрам"); _n("riferimento","ссылка/указание")
_n("infermeria","лазарет"); _n("riconoscimento","признание")
_n("molo","причал"); _n("erede","наследник")
_n("protagonista","главный герой"); _n("animo","дух/душа")
_n("impulso","импульс"); _n("oscurita","темнота")
_n("commenti","комментарии"); _n("curriculum","резюме")
_n("budget","бюджет"); _n("priorita","приоритет")
_n("sindacato","профсоюз"); _n("dimissioni","отставка")
_n("spedizione","отправка/экспедиция"); _n("multa","штраф")
_n("solitudine","одиночество"); _n("mento","подбородок")
_n("professione","профессия"); _n("commedia","комедия")
_n("scacchi","шахматы"); _n("calzini","носки")
_n("detenuti","заключённые"); _n("campionato","чемпионат")
_n("volontario","волонтёр"); _n("ginnastica","гимнастика")
_n("yoga","йога"); _n("ranger","рейнджер")
_n("colonna","колонна"); _n("conforto","утешение")
_n("ninja","ниндзя"); _n("cereali","хлопья/злаки")
_n("crescita","рост"); _n("nebbia","туман")
_n("sconto","скидка"); _n("etichetta","этикетка")
_n("entusiasmo","энтузиазм"); _n("regione","регион")
_n("grano","зерно/пшеница"); _n("archivio","архив")
_n("sequenza","последовательность"); _n("colonia","колония")
_n("alleanza","альянс"); _n("fenomeno","феномен")
_n("fetta","ломтик"); _n("obbligo","обязанность")
_n("cuoco","повар"); _n("testo","текст")
_n("panni","одежды"); _n("circuito","цепь/контур")
_n("pianoforte","фортепиано"); _n("orbita","орбита")
_n("preavviso","предупреждение"); _n("costruzione","строительство")
_n("casco","шлем/каск"); _n("pirata","пират")
_n("scudo","щит"); _n("riva","берег")
_n("prato","луг/газон"); _n("assalto","штурм/атака")
_n("guardiano","страж"); _n("tesi","тезис/диссертация")
_n("orrore","ужас"); _n("urlo","крик")
_n("bowling","боулинг"); _n("cecchino","снайпер")
_n("barone","барон"); _n("pallone","мяч")
_n("reggiseno","лифчик"); _n("hobby","хобби")
_n("ranch","ранчо"); _n("asso","туз")
_n("bottone","кнопка/пуговица"); _n("dramma","драма")
_n("serratura","замок"); _n("dannati","проклятые")
_n("attentato","покушение"); _n("zampe","лапы")
_n("umanita","человечество"); _n("bancone","стойка/бар")
_n("innocenza","невинность"); _n("rilascio","выпуск/освобождение")
_n("destinazione","назначение/пункт"); _n("soffitto","потолок")
_n("brividi","мурашки"); _n("banana","банан")
_n("venditore","продавец"); _n("mattinata","утро")
_n("riscatto","выкуп/искупление"); _n("menzogna","ложь")
_n("fotografo","фотограф"); _n("fulmine","молния")
_n("marijuana","марихуана"); _n("manicomio","психбольница")
_n("pompieri","пожарные"); _n("illusione","иллюзия")
_n("demonio","демон"); _n("asciugamano","полотенце")
_n("frattura","перелом"); _n("campana","колокол")
_n("sciopero","забастовка"); _n("toast","тост")
_n("operai","рабочие"); _n("agenda","повестка")
_n("blog","блог"); _n("ossessione","одержимость")
_n("sirena","сирена"); _n("gangster","гангстер")
_n("palo","столб/шест"); _n("marinaio","моряк")
_n("cavalieri","рыцари"); _n("fata","фея")
_n("sacchetto","пакетик"); _n("garanzia","гарантия")
_n("approccio","подход"); _n("ragno","паук")
_n("salone","салон"); _n("omicida","убийца")
_n("massacro","резня"); _n("ballerina","балерина")
_n("semi","семена"); _n("gestione","управление")
_n("torcia","факел/фонарь"); _n("marea","прилив")
_n("costole","рёбра"); _n("disgrazia","беда/несчастье")
_n("hacker","хакер"); _n("funghi","грибы")
_n("antenati","предки"); _n("carita","милосердие/благотворительность")
_n("software","ПО"); _n("elefante","слон")
_n("scheda","карта/анкета"); _n("epidemia","эпидемия")
_n("rospo","жаба"); _n("pompa","насос")
_n("carbone","уголь"); _n("pugnale","кинжал")
_n("sapone","мыло"); _n("posa","поза")
_n("dibattito","дебаты"); _n("spaghetti","спагетти")
_n("sceneggiatura","сценарий"); _n("delusione","разочарование")
_n("disperazione","отчаяние"); _n("raffreddore","простуда")
_n("categoria","категория"); _n("filosofia","философия")
_n("tacchi","каблуки"); _n("invasione","вторжение")
_n("democrazia","демократия"); _n("trapianto","трансплантация")
_n("volpe","лиса"); _n("peste","чума/зараза")
_n("inchiesta","расследование"); _n("soprannome","прозвище")
_n("cemento","цемент"); _n("frode","мошенничество")
_n("radici","корни"); _n("acquisto","покупка")
_n("ciambelle","пончики"); _n("prosciutto","ветчина")
_n("edizione","издание"); _n("cenere","пепел/зола")
_n("iniziativa","инициатива"); _n("mancia","чаевые")
_n("nascondiglio","укрытие"); _n("limone","лимон")
_n("barriera","барьер"); _n("finestrino","окно (в машине)")
_n("gratitudine","благодарность"); _n("panchina","скамейка")
_n("contadino","крестьянин"); _n("fondazione","основание/фонд")
_n("mito","миф"); _n("adozione","усыновление")
_n("seta","шёлк"); _n("ditta","фирма")
_n("allerta","тревога"); _n("pollice","большой палец")
_n("ostacolo","препятствие"); _n("poeta","поэт")
_n("squillo","звонок"); _n("bunker","бункер")
_n("alloggio","жильё"); _n("poltrona","кресло")
_n("residenza","резиденция"); _n("aspettative","ожидания")
_n("pecore","овцы"); _n("cervo","олень")
_n("maglione","свитер"); _n("sponsor","спонсор")
_n("stima","оценка"); _n("bacon","бекон")
_n("tana","нора"); _n("punteggio","счёт/очки")
_n("sasso","камень"); _n("vescovo","епископ")
_n("libretto","буклет"); _n("farmacia","аптека")
_n("cognato","зять/шурин"); _n("discrezione","усмотрение")
_n("scrittura","письменность"); _n("notiziario","новости")
_n("altare","алтарь"); _n("esposizione","выставка")
_n("guarigione","исцеление"); _n("ascia","топор")
_n("olive","маслины"); _n("cestino","корзина")
_n("calendario","календарь"); _n("soddisfazione","удовлетворение")
_n("caverna","пещера"); _n("sorso","глоток")
_n("frontiera","граница"); _n("profitti","прибыли")
_n("semestre","семестр"); _n("razzo","ракета")
_n("scadenza","срок годности"); _n("ricchezza","богатство")
_n("turisti","туристы"); _n("macellaio","мясник")
_n("capanno","сарай"); _n("donatore","донор")
_n("cospirazione","заговор"); _n("picnic","пикник")
_n("barattolo","банка"); _n("monitor","монитор")
_n("privilegio","привилегия"); _n("ironia","ирония")
_n("smoking","смокинг"); _n("manzo","говядина")
_n("abuso","злоупотребление"); _n("ferie","отпуск")
_n("cagnolino","собачка"); _n("cantiere","стройка")
_n("secchio","ведро"); _n("forbici","ножницы")
_n("vaccino","вакцина"); _n("zucca","тыква")
_n("carrello","тележка"); _n("esibizione","выступление")
_n("mais","кукуруза"); _n("creatore","создатель")
_n("benessere","благополучие"); _n("arancia","апельсин")
_n("coppa","кубок"); _n("marca","бренд/марка")
_n("dessert","десерт"); _n("quarantena","карантин")
_n("terapista","терапевт"); _n("visitatori","посетители")
_n("concentrazione","концентрация"); _n("incrocio","перекрёсток")
_n("caserma","казарма"); _n("ahia","ай!")
_n("attrazione","притяжение/аттракцион"); _n("tessera","билет/карточка")
_n("marciapiede","тротуар"); _n("furia","ярость")
_n("ferrovia","железная дорога"); _n("tartaruga","черепаха")
_n("sushi","суши"); _n("casino","казино")
_n("apocalisse","апокалипсис"); _n("curiosita","любопытство")
_n("lotto","лотерея"); _n("parata","парад")
_n("letteratura","литература"); _n("addetto","ответственный")
_n("fazzoletto","носовой платок"); _n("maresciallo","маршал")
_n("verdure","овощи"); _n("cera","воск")
_n("piattaforma","платформа"); _n("pullman","автобус")
_n("sostituto","заместитель"); _n("donazione","пожертвование")
_n("griglia","решётка"); _n("percentuale","процент")
_n("suggerimento","подсказка"); _n("fienile","сарай/амбар")
_n("litigio","ссора"); _n("preparazione","подготовка")
_n("difetti","недостатки"); _n("editore","издатель")
_n("musicista","музыкант"); _n("crollo","крах")
_n("evoluzione","эволюция"); _n("libreria","книжный магазин")
_n("perla","жемчужина"); _n("manutenzione","обслуживание")
_n("armatura","броня/доспехи"); _n("agnello","ягнёнок")
_n("bordello","бордель"); _n("farsa","фарс")
_n("maggioranza","большинство"); _n("sedute","заседания")
_n("baracca","лачуга"); _n("panna","сливки")
_n("anatra","утка"); _n("terremoto","землетрясение")
_n("samurai","самурай"); _n("ubriacone","пьяница")
_n("clima","климат"); _n("pepe","перец")
_n("cugini","двоюродные братья"); _n("inchiostro","чернила")
_n("vicinanze","окрестности"); _n("bush","Буш")
_n("trofeo","трофей"); _n("calze","чулки/носки")
_n("gossip","сплетни"); _n("calendario","календарь")
_n("profondita","глубина"); _n("probabilita","вероятность")
_n("personalita","личность"); _n("carita","милосердие")
_n("eternita","вечность"); _n("casino","казино")
_n("quantita","количество"); _n("f","эфф")
_n("cha","Ча")

# ─── Special VERBs (interjections, greetings, garbled lemmas) ─────────────────
_v("sai","знать"); _v("cosi","таким образом"); _v("ciao","здороваться/прощаться")
_v("dispiace","сожалеть"); _v("vai","идти"); _v("piace","нравиться")
_v("viene","приходить"); _v("vieni","приходить"); _v("puo","мочь")
_v("vado","идти"); _v("sara","быть (будущ.)"); _v("dici","говорить")
_v("eri","быть (прош.)"); _v("riesco","мочь/справляться")
_v("dimmi","скажи мне"); _v("avevi","иметь (прош.)")
_v("potresti","мочь (усл.)"); _v("avresti","иметь (усл.)")
_v("sarei","быть (усл.)"); _v("buongiorno","доброе утро")
_v("dammi","дай мне"); _v("dica","сказать (сосл.)")
_v("capisci","понимать"); _v("immagino","представлять")
_v("tieni","держать"); _v("vorresti","хотеть (усл.)")
_v("posso","мочь"); _v("vuoi","хотеть"); _v("fallo","делай это")
_v("stai","быть/находиться"); _v("devo","должен"); _v("voglio","хотеть")
_v("siamo","быть (мы)"); _v("fatto","сделать"); _v("ho","иметь")
_v("so","знать"); _v("vuole","хотеть"); _v("sta","быть/находиться")
_v("dai","давать"); _v("credi","верить"); _v("hai","иметь")
_v("era","быть (прош.)"); _v("visto","видеть"); _v("hanno","иметь (они)")
_v("devi","должен"); _v("sono","быть"); _v("stato","быть (прош.)")
_v("sa","знать"); _v("va","идти"); _v("credo","верить/думать")
_v("penso","думать"); _v("fanno","делать (они)"); _v("metti","класть")
_v("resto","оставаться"); _v("andiamo","идти (мы)"); _v("prendi","брать")
_v("vede","видеть"); _v("dicono","говорить (они)"); _v("conosco","знать/быть знакомым")
_v("abbiamo","иметь (мы)"); _v("torno","возвращаться"); _v("guarda","смотреть")
_v("sempra","казаться"); _v("volevi","хотеть (прош.)")
_v("lasciami","оставь меня"); _v("riesci","мочь/справляться")
_v("bisogna","нужно"); _v("vattene","уходи"); _v("saremo","быть (будущ. мн.)")
_v("stavi","быть (прош.)"); _v("averlo","иметь это")
_v("intendi","понимать/иметь в виду"); _v("ascoltami","послушай меня")
_v("prometto","обещать"); _v("siediti","садись"); _v("scusa","извини")
_v("vorrei","хотеть (усл.)"); _v("e","быть"); _v("crede","верить")
_v("lascia","оставлять"); _v("ascolta","слушать")
_v("dimentica","забывать"); _v("faccio","делать"); _v("siete","быть (вы)")
_v("metto","класть"); _v("abbia","иметь (сосл.)")
_v("venite","приходить (мн.)"); _v("vuol","хотеть")
_v("possono","мочь (они)"); _v("stanno","быть (они)")
_v("vengono","приходить (они)"); _v("vogliono","хотеть (они)")
_v("devono","должны (они)"); _v("danno","давать (они)")
_v("sanno","знать (они)"); _v("vanno","идти (они)")
_v("siediti","садись"); _v("farlo","делать это")
_v("farti","делать тебе"); _v("dirmi","сказать мне")
_v("farmi","сделать мне"); _v("dirlo","сказать это")
_v("saperlo","знать это"); _v("dirti","сказать тебе")
_v("vederti","видеть тебя"); _v("lasci","оставлять")
_v("chiamo","называть/звать"); _v("averci","иметь")
_v("farai","сделать (будущ.)"); _v("sarai","быть (будущ.)")
_v("preoccuparti","беспокоиться"); _v("farci","сделать (нам)")
_v("esserci","быть здесь"); _v("andra","он пойдёт")
_v("aiutarti","помочь тебе"); _v("dovrai","будешь должен")
_v("saresti","ты был бы"); _v("scusami","извини меня")
_v("spiacere","сожалеть"); _v("sappia","знать (сосл.)")
_v("e'","быть"); _v("abbiate","иметь (сосл. мн.)")
_v("sentito","слышать/чувствовать"); _v("andato","пойти")
_v("chiesto","спросить"); _v("preso","взять"); _v("detto","сказать")
_v("venuto","прийти"); _v("saputo","узнать"); _v("scritto","написать")
_v("morto","умереть"); _v("vissuto","жить"); _v("perso","потерять")
_v("nato","родиться"); _v("tornato","вернуться"); _v("successo","случиться")
_v("potuto","смочь"); _v("dovuto","быть должным")
_v("voluto","хотеть"); _v("piaciuto","нравиться")
_v("rimasto","остаться"); _v("parlato","говорить"); _v("dormito","спать")
_v("corso","бежать"); _v("bevuto","пить"); _v("vinto","выиграть")
_v("aperto","открыть"); _v("chiuso","закрыть"); _v("cominciato","начать")
_v("finito","закончить"); _v("continuato","продолжить"); _v("provato","пробовать")
_v("deciso","решить"); _v("scelto","выбрать"); _v("capito","понять")
_v("creduto","верить"); _v("sembrato","казаться"); _v("diventato","стать")
_v("arrivato","прибыть"); _v("partito","уехать"); _v("entrato","войти")
_v("uscito","выйти"); _v("salito","подняться"); _v("sceso","спуститься")
_v("passato","пройти"); _v("avuto","иметь"); _v("messo","положить")
_v("mangiato","есть"); _v("letto","читать"); _v("trovato","найти")
_v("saremmo","мы были бы"); _v("sarete","вы будете")
_v("legge","читает"); _v("beve","пьёт"); _v("beviamo","пьём")
_v("scrive","пишет"); _v("chiama","зовёт"); _v("dorme","спит")
_v("corre","бежит"); _v("cammina","идёт пешком"); _v("mangia","ест")
_v("suona","играет/звенит"); _v("canta","поёт"); _v("balla","танцует")
_v("disegna","рисует"); _v("dipinge","красит/рисует")
_v("uh","ух/гм"); _v("ehm","эмм"); _v("ia","ия")
_v("parlarne","говорить об этом"); _v("sapevi","ты знал")
_v("scommetto","держу пари"); _v("averti","иметь тебя")
_v("arrivederci","до свидания"); _v("venuti","пришедшие")
_v("parlarti","поговорить с тобой"); _v("aiutarmi","помочь мне")
_v("ama","любит"); _v("potessi","мог (сосл.)"); _v("suppongo","предполагаю")
_v("avermi","иметь меня"); _v("saprei","я знал бы")
_v("aiutarla","помочь ей"); _v("dirò","я скажу")
_v("potrai","ты сможешь"); _v("aiutami","помоги мне")
_v("averla","иметь её"); _v("andartene","уходить")
_v("prendermi","взять меня"); _v("sii","будь")
_v("andarci","пойти туда"); _v("siate","будьте")
_v("muoviti","двигайся"); _v("andrò","я пойду")
_v("pensarci","подумать об этом"); _v("trovarlo","найти его")
_v("potevi","ты мог"); _v("volessi","хотел бы (сосл.)")
_v("andarmene","уйти отсюда"); _v("preoccupi","беспокоишь")
_v("piaci","ты нравишься"); _v("bicchiere","стакан (ошибка: сущ.)")
_v("andarsene","уходить"); _v("vorrà","он захочет")
_v("dritto","прямо"); _v("succedera","он произойдёт")
_v("son","они есть"); _v("pensavi","ты думал")
_v("andarcene","уйти отсюда"); _v("parlarle","поговорить с ней")
_v("fossimo","мы были (сосл.)"); _v("starai","ты будешь")
_v("sarete","вы будете"); _v("aiutarci","помочь нам")
_v("piaccia","нравиться (сосл.)"); _v("dovrò","я буду должен")
_v("preferirei","я предпочёл бы"); _v("muoversi","двигаться")
_v("darò","я дам"); _v("smetterla","прекратить")
_v("potreste","вы могли бы"); _v("prenderla","взять её")
_v("trovarmi","найти меня"); _v("dubito","сомневаюсь")
_v("sapessi","знал бы (сосл.)"); _v("averne","иметь из этого")
_v("trovarla","найти её"); _v("smetti","прекрати")
_v("amanda","Аманда"); _v("risolto","решил")
_v("nathan","Натан"); _v("muovetevi","двигайтесь")
_v("portami","принеси мне"); _v("stara","он будет")
_v("provarci","попробовать"); _v("occupo","я занимаюсь")
_v("rendi","ты делаешь/возвращаешь"); _v("parlarmi","поговорить со мной")
_v("muoverti","двигаться"); _v("stan","они стоят/находятся")
_v("troverai","ты найдёшь"); _v("togliti","сними")
_v("trovarti","найти тебя"); _v("aiutarvi","помочь вам")
_v("scendi","спускайся"); _v("vorremmo","мы хотели бы")
_v("ross","Росс"); _v("portarla","нести её")
_v("uscendo","выходя"); _v("dormendo","спя")
_v("prendero","я возьму"); _v("potrò","я смогу")
_v("tornerò","я вернусь"); _v("stiano","пусть будут")
_v("aiutatemi","помогите мне"); _v("verrai","ты придёшь")
_v("parlargli","поговорить с ним"); _v("accada","случится (сосл.)")
_v("aiutarlo","помочь ему"); _v("lascerò","я оставлю")
_v("scoprirlo","узнать/раскрыть его"); _v("staro","я буду")
_v("prenderò","я возьму"); _v("prendilo","возьми его")
_v("sentirlo","услышать/почувствовать его")
_v("prenditi","возьми себе"); _v("viviamo","мы живём")
_v("poterlo","мочь его"); _v("prendersi","брать себе")
_v("offro","я предлагаю"); _v("tornero","я вернусь")
_v("smettetela","прекратите это"); _v("potro","я смогу")
_v("rivederti","увидеть тебя снова"); _v("ditemi","скажите мне")
_v("sposarmi","жениться/выйти замуж"); _v("piantala","хватит!")
_v("mettilo","положи это"); _v("assicurarmi","убедиться")
_v("han","они имеют"); _v("odi","ненавидишь")
_v("troverò","я найду"); _v("passiamo","проходим/переходим")
_v("uccido","я убиваю"); _v("trovero","я найду")
_v("servira","он послужит"); _v("andranno","они пойдут")
_v("accorto","заметил"); _v("tornerai","ты вернёшься")
_v("evan","Эван"); _v("abbi","имей")
_v("riley","Райли"); _v("martha","Марта")
_v("spetta","полагается/следует"); _v("piaccio","я нравлюсь")
_v("spieghi","объясни"); _v("travis","Трэвис")
_v("reverendo","преподобный"); _v("scrivi","пиши")
_v("stavate","вы были"); _v("verrò","я приду")
_v("starò","я буду"); _v("parlami","поговори со мной")
_v("tenerla","держать её"); _v("andatevene","уходите")
_v("ahi","ай!"); _v("ucciderla","убить её")
_v("perdoni","прости (сосл.)"); _v("sentirsi","чувствовать себя")
_v("potrete","вы сможете"); _v("morirai","ты умрёшь")
_v("vedervi","увидеть вас"); _v("occupi","занимает")
_v("muoviamoci","давайте двигаться"); _v("riuscirai","ты справишься")
_v("finisci","заканчивай"); _v("tienilo","держи его")
_v("april","открой"); _v("portatelo","принесите его")
_v("spiego","я объясняю"); _v("trovarci","найти нас")
_v("sparisci","исчезни"); _v("piaciuta","понравившаяся")
_v("porterò","я принесу"); _v("ringraziarti","поблагодарить тебя")
_v("ammetterlo","признать это"); _v("resisti","держись")
_v("occuparmi","заниматься"); _v("spento","выключенный/погасший")
_v("proteggerti","защитить тебя"); _v("sederti","сесть")
_v("benedica","благослови"); _v("sapremo","мы будем знать")
_v("seguo","я следую"); _v("mettila","положи её")
_v("piacerà","понравится (будущ.)"); _v("portero","я принесу")
_v("sentirai","ты услышишь"); _v("volerlo","хотеть его")
_v("toccarmi","тронуть меня"); _v("piacera","он понравится")
_v("tenermi","держать меня"); _v("usarlo","использовать его")
_v("neal","Нил"); _v("pensassi","думал (сосл.)")
_v("apra","открою/открой (сосл.)"); _v("sareste","вы были бы")
_v("riconosco","я узнаю"); _v("trono","трон/я грохочу")
_v("sedetevi","садитесь"); _v("poterti","мочь тебе")
_v("prenderci","взять нас"); _v("raccomando","рекомендую")
_v("parlero","я поговорю"); _v("prendila","возьми её")
_v("aiutera","он поможет"); _v("sembrerebbe","казалось бы")
_v("accadra","это случится"); _v("rendo","я делаю/возвращаю")
_v("portera","он принесёт"); _v("mitch","Митч")
_v("averli","иметь их"); _v("servirebbe","послужило бы")
_v("avervi","иметь вас"); _v("bugiarda","лгунья")
_v("trevor","Тревор"); _v("troverete","вы найдёте")
_v("debbie","Дебби"); _v("risponda","ответь (сосл.)")
_v("passami","дай мне"); _v("maledetti","проклятые")
_v("mettero","я положу"); _v("funzionerà","заработает (будущ.)")
_v("venissi","пришёл (сосл.)"); _v("sposarti","жениться на тебе")
_v("salvarti","спасти тебя"); _v("perdiamo","мы теряем")
_v("metterò","я положу"); _v("seguitemi","следуйте за мной")
_v("dwight","Дуайт"); _v("scuso","извиняюсь")
_v("sorridi","улыбнись"); _v("sposarsi","жениться")
_v("vedro","я увижу"); _v("sapra","он узнает")
_v("passera","он пройдёт"); _v("uscirne","выйти оттуда")
_v("robbie","Робби"); _v("sconvolta","потрясённая")
_v("perdo","я теряю"); _v("seguimi","следуй за мной")
_v("nascosti","спрятанные"); _v("doyle","Дойл")
_v("ascoltatemi","послушайте меня"); _v("valentino","Валентино")
_v("svelta","быстрая"); _v("brandon","Брэндон")
_v("pablo","Пабло"); _v("prometti","обещай")
_v("permetti","позволяешь"); _v("sbrigatevi","поторопитесь")
_v("svelto","быстрый"); _v("prenderli","взять их")
_v("provarlo","попробовать его"); _v("preoccupatevi","беспокойтесь")
_v("vederli","увидеть их"); _v("arriveremo","мы прибудем")
_v("mostrarti","показать тебе"); _v("permettermi","позволить мне")
_v("dee","Ди"); _v("ando","я иду")
_v("stanley","Стэнли"); _v("bada","следи")
_v("lindsay","Линдсей"); _v("muovi","двигай")
_v("carla","Карла"); _v("prenderai","ты возьмёшь")
_v("riconosci","узнаёшь"); _v("usarla","использовать её")
_v("phoebe","Фиби"); _v("riuscirci","справиться с этим")
_v("tieniti","держись"); _v("segua","пусть следует")
_v("donald","Дональд"); _v("mettetevi","положите себе")
_v("sappi","знай"); _v("parlarci","поговорить с нами")
_v("saperne","знать об этом"); _v("nascondersi","прятаться")
_v("vorrebbero","они хотели бы"); _v("pigiama","пижама")
_v("sedermi","сесть"); _v("sire","сеньор")
_v("portarci","нести нам"); _v("trovera","он найдёт")
_v("lex","Лекс"); _v("cercherò","я поищу")
_v("preoccupo","я беспокоюсь"); _v("chiederò","я спрошу")
_v("porteremo","мы принесём"); _v("vadano","пусть идут")
_v("presentarti","представить тебя"); _v("provaci","попробуй")
_v("diranno","они скажут"); _v("salvarlo","спасти его")
_v("togliermi","убрать от меня"); _v("prendetelo","возьмите его")
_v("prenderanno","они возьмут"); _v("preparatevi","готовьтесь")
_v("vedra","он увидит"); _v("miguel","Мигель")
_v("mancherai","ты будешь скучать"); _v("cercarla","искать её")
_v("sedersi","сесть"); _v("sappiate","знайте (сосл.)")
_v("muoverci","двигаться нам"); _v("ucciderci","убить нас")
_v("verdetto","вердикт"); _v("stiate","будьте (сосл.)")
_v("scomparsi","исчезнувшие"); _v("unirsi","объединиться")
_v("ascoltarmi","послушать меня"); _v("perderai","ты потеряешь")
_v("lena","Лена"); _v("salvarla","спасти её")
_v("volevate","вы хотели"); _v("seguirmi","следовать за мной")
_v("svenuto","упавший в обморок"); _v("andarvene","уходить вам")
_v("haley","Хейли"); _v("nolan","Нолан")
_v("permetterò","я позволю"); _v("scordatelo","забудь это")
_v("presentarvi","представить вам"); _v("accettarlo","принять это")
_v("prepari","готовь"); _v("raccontami","расскажи мне")
_v("terrò","я буду держать"); _v("vergogno","стыжусь")
_v("riuscissi","справился (сосл.)"); _v("beverly","Беверли")
_v("trovarsi","находиться"); _v("assicurarsi","убедиться")
_v("cercarti","искать тебя"); _v("ehilà","эй!")
_v("moriranno","они умрут"); _v("perdonatemi","простите меня")
_v("sappiano","знают (сосл.)"); _v("riuscirò","я справлюсь")
_v("brody","Броди"); _v("salvarmi","спасти меня")
_v("butto","я бросаю"); _v("accompagni","сопровождаешь")
_v("prenderne","взять из этого"); _v("peggy","Пегги")
_v("accendi","зажги/включи"); _v("starei","я был бы")
_v("pensai","я подумал"); _v("scomparse","исчезнувшие")
_v("aiuterai","ты поможешь"); _v("sapevate","вы знали")
_v("penseranno","они подумают"); _v("vogliano","пусть хотят")
_v("unirti","объединиться с тобой"); _v("verrebbe","он пришёл бы")
_v("tienila","держи её"); _v("penserai","ты подумаешь")
_v("proteggerla","защитить её"); _v("prepararsi","готовиться")
_v("reggie","Реджи"); _v("rivedremo","мы увидимся")
_v("aspettarti","дождаться тебя"); _v("gavin","Гэвин")
_v("prenderle","взять их (ж.р.)"); _v("preghiamo","мы молимся")
_v("trovarli","найти их"); _v("proteggermi","защитить меня")
_v("vendo","я продаю"); _v("vogliate","хотите (сосл.)")
_v("aiuterò","я помогу"); _v("brenda","Бренда")
_v("spiegarmi","объяснить мне"); _v("pierre","Пьер")
_v("cercarmi","искать меня"); _v("piangi","плачь")
_v("toglierti","снять с тебя"); _v("renderlo","сделать его")
_v("riusciro","я справлюсь"); _v("leggerlo","прочитать его")
_v("arrivarci","добраться"); _v("mostrami","покажи мне")
_v("donovan","Донован"); _v("sadie","Сейди")
_v("trent","тридцать"); _v("aiutero","я помогу")
_v("rimarrà","он останется"); _v("parlavi","ты говорил")
_v("terremo","мы будем держать"); _v("rivederla","увидеть её снова")
_v("cerchero","я поищу"); _v("offrendo","предлагая")
_v("mettiamoci","давайте положим"); _v("scossa","потрясение")
_v("scappi","убегаешь"); _v("sopportarlo","терпеть его")
_v("scopriamo","мы обнаружим"); _v("bernie","Берни")
_v("renderti","сделать тебя"); _v("permetterci","позволить нам")
_v("sposarci","пожениться"); _v("eleanor","Элеонора")
_v("porterai","ты принесёшь"); _v("useremo","мы используем")
_v("perderti","потерять тебя"); _v("avvicinarsi","приближаться")
_v("incontreremo","мы встретим"); _v("edgar","Эдгар")
_v("garrett","Гарретт"); _v("accomodatevi","размещайтесь")
_v("parlarvi","поговорить с вами"); _v("spiegarti","объяснить тебе")
_v("finche","пока"); _v("malinteso","недоразумение")
_v("resterai","ты останешься"); _v("tenerli","держать их")
_v("alzarsi","вставать"); _v("rimarra","он останется")
_v("pensero","я подумаю"); _v("rivisto","увиденный снова")
_v("toccarlo","тронуть его"); _v("portarli","нести их")
_v("bridget","Бриджит"); _v("propongo","я предлагаю")
_v("nasconderti","спрятать тебя"); _v("amarti","любить тебя")
_v("ucciderai","ты убьёшь"); _v("tirarti","тянуть тебя")
_v("rilassarti","расслабиться"); _v("depresso","подавленный")
_v("vorreste","вы хотели бы"); _v("occuperò","я займусь")
_v("aiutarli","помочь им"); _v("valsa","вальс")
_v("perdonarmi","простить меня"); _v("rompe","ломает")
_v("accadde","случилось"); _v("avvicinarti","приблизиться к тебе")
_v("permettersi","позволить себе"); _v("ricominciamo","начнём снова")
_v("mettimi","положи мне"); _v("verremo","мы придём")
_v("benedetto","благословенный"); _v("prepararmi","подготовиться")
_v("sembravi","ты казался"); _v("usciremo","мы выйдем")
_v("arrendo","сдаюсь"); _v("toglimi","убери от меня")
_v("occuparti","заниматься"); _v("affinche","чтобы")
_v("toglietevi","снимите с себя"); _v("ucciderli","убить их")
_v("ringraziarmi","поблагодарить меня"); _v("sposarlo","женить его")
_v("evitarlo","избежать его"); _v("mina","мина/Мина")
_v("peyton","Пейтон"); _v("darren","Даррен")
_v("vederle","увидеть их (ж.р.)"); _v("portarle","принести их (ж.р.)")
_v("tenersi","держаться"); _v("proteggerlo","защитить его")
_v("assicurarci","убедиться нам"); _v("doverlo","должен его")
_v("riuscira","он справится"); _v("pagherai","ты заплатишь")
_v("muovermi","двигаться мне"); _v("spiegherebbe","объяснил бы")
_v("succederebbe","случилось бы"); _v("avergli","иметь ему")
_v("prendertela","принять близко к сердцу"); _v("cambiarmi","изменить меня")
_v("portarvi","нести вам"); _v("sott","под")
_v("tirarlo","тянуть его"); _v("scommetterci","держать пари на это")
_v("permettimi","позволь мне"); _v("ricordarmi","вспомнить")
_v("tenetevi","держитесь"); _v("occupiamo","мы занимаем")
_v("ringraziarla","поблагодарить её"); _v("alzarti","встать")
_v("scopro","я обнаруживаю"); _v("metterli","положить их")
_v("pentirai","ты раскаешься"); _v("spiegarlo","объяснить его")
_v("florrick","Флоррик"); _v("spararmi","застрелить меня")
_v("rimorso","угрызение/раскаяние"); _v("tolta","снятая")
_v("gia'","уже"); _v("volonta","воля/желание")
_v("abilita","способность"); _v("chiese","спросил/церкви")
_v("vidi","я видел"); _v("cercarlo","искать его")
_v("parlerò","я скажу"); _v("aiuterà","он поможет")

# ─── Special ADJs ─────────────────────────────────────────────────────────────
_a("vero","настоящий/верный"); _a("nuovo","новый"); _a("giusto","правильный")
_a("grande","большой"); _a("sicuro","уверенный/безопасный")
_a("stesso","тот же/самый"); _a("bella","красивая"); _a("buona","хорошая")
_a("pronto","готовый"); _a("bel","красивый"); _a("felice","счастливый")
_a("migliore","лучший"); _a("importante","важный"); _a("possibile","возможный")
_a("piccolo","маленький"); _a("difficile","трудный/сложный")
_a("fantastico","фантастический"); _a("serio","серьёзный")
_a("forte","сильный/крепкий"); _a("strano","странный")
_a("bravo","хороший/умелый"); _a("unica","единственная")
_a("vecchio","старый"); _a("divertente","забавный")
_a("esatto","точный"); _a("perfetto","идеальный")
_a("facile","лёгкий"); _a("vivo","живой"); _a("ottimo","отличный")
_a("lungo","длинный/долгий"); _a("stupido","глупый"); _a("alto","высокий")
_a("futuro","будущий"); _a("cara","дорогая"); _a("speciale","особенный")
_a("semplice","простой"); _a("chiaro","ясный"); _a("fermo","неподвижный")
_a("libero","свободный"); _a("carino","милый"); _a("brutto","уродливый/плохой")
_a("incredibile","невероятный"); _a("gentile","любезный")
_a("personale","личный"); _a("pieno","полный"); _a("diverso","разный")
_a("normale","нормальный"); _a("terribile","ужасный")
_a("attento","внимательный"); _a("impossibile","невозможный")
_a("dolce","сладкий/нежный"); _a("interessante","интересный")
_a("nero","чёрный"); _a("ex","бывший"); _a("tranquillo","спокойный")
_a("simile","подобный"); _a("pubblico","общественный")
_a("freddo","холодный"); _a("triste","грустный"); _a("duro","твёрдый/жёсткий")
_a("necessario","необходимый"); _a("reale","реальный")
_a("intelligente","умный"); _a("bianco","белый")
_a("contento","довольный"); _a("maggiore","больший/старший")
_a("umano","человеческий"); _a("caldo","горячий/тёплый")
_a("interno","внутренний"); _a("pericoloso","опасный")
_a("presente","настоящий"); _a("intero","целый"); _a("comune","общий/обычный")
_a("responsabile","ответственный"); _a("rosso","красный")
_a("arrabbiato","злой/рассерженный"); _a("enorme","огромный")
_a("colpevole","виновный"); _a("fortunato","удачливый")
_a("contrario","противоположный"); _a("locale","местный")
_a("massimo","максимальный"); _a("minimo","минимальный")
_a("prossimo","ближайший"); _a("ricco","богатый"); _a("povero","бедный")
_a("giovane","молодой"); _a("segreto","секретный"); _a("morto","мёртвый")
_a("proprio","собственный"); _a("cattivo","плохой/злой")
_a("silenzioso","тихий"); _a("pulito","чистый"); _a("sano","здоровый")
_a("scuro","тёмный"); _a("stanco","усталый"); _a("unico","единственный")
_a("esterno","внешний"); _a("pazzo","сумасшедший")
_a("complesso","сложный"); _a("certo","уверенный/некоторый")
_a("sincero","искренний"); _a("coraggioso","храбрый")
_a("nobile","благородный"); _a("idiotta","идиотский")
_a("spaventato","испуганный"); _a("curioso","любопытный")
_a("caro","дорогой"); _a("amato","любимый"); _a("santo","святой")
_a("maledetto","проклятый"); _a("stupendo","потрясающий")
_a("meraviglioso","удивительный"); _a("brillante","блестящий")
_a("elegante","элегантный"); _a("moderno","современный")
_a("antico","древний"); _a("classico","классический")
_a("romantico","романтический"); _a("drammatico","драматический")
_a("comico","комический"); _a("tragico","трагический")
_a("creativo","творческий"); _a("originale","оригинальный")
_a("geniale","гениальный"); _a("corretto","правильный")
_a("sbagliato","неправильный"); _a("falso","ложный")
_a("oscuro","тёмный/неясный"); _a("evidente","очевидный")
_a("preciso","точный"); _a("vago","неопределённый")
_a("probabile","вероятный"); _a("utile","полезный")
_a("inutile","бесполезный"); _a("pratico","практичный")
_a("ideale","идеальный"); _a("concreto","конкретный")
_a("generale","общий"); _a("particolare","особый")
_a("raro","редкий"); _a("frequente","частый")
_a("costante","постоянный"); _a("naturale","естественный")
_a("artificiale","искусственный"); _a("primitivo","примитивный")
_a("fondamentale","основной"); _a("principale","главный")
_a("politico","политический"); _a("economico","экономический")
_a("sociale","социальный"); _a("scientifico","научный")
_a("militare","военный"); _a("civile","гражданский")
_a("tradizionale","традиционный"); _a("democratico","демократический")
_a("liberale","либеральный"); _a("cattolico","католический")
_a("cristiano","христианский"); _a("spirituale","духовный")
_a("materiale","материальный"); _a("fisico","физический")
_a("mentale","умственный"); _a("emotivo","эмоциональный")
_a("razionale","рациональный"); _a("logico","логический")
_a("medico","медицинский"); _a("psicologico","психологический")
_a("legale","законный"); _a("penale","уголовный")
_a("amministrativo","административный"); _a("giudiziario","судебный")
_a("costituzionale","конституционный"); _a("statale","государственный")
_a("federale","федеральный"); _a("autonomo","автономный")
_a("indipendente","независимый"); _a("dipendente","зависимый")
_a("pesante","тяжёлый"); _a("leggero","лёгкий")
_a("grosso","большой/крупный"); _a("largo","широкий")
_a("stretto","узкий"); _a("corto","короткий"); _a("basso","низкий")
_a("profondo","глубокий"); _a("vuoto","пустой")
_a("aperto","открытый"); _a("chiuso","закрытый")
_a("rotto","сломанный"); _a("fresco","свежий")
_a("maturo","зрелый"); _a("verde","зелёный")
_a("caldo","горячий"); _a("freddo","холодный")
_a("bollente","кипящий"); _a("amaro","горький")
_a("acido","кислый"); _a("salato","солёный")
_a("piccante","острый"); _a("delizioso","вкусный")
_a("peggiore","худший"); _a("uguale","равный")
_a("identico","идентичный"); _a("opposto","противоположный")
_a("rotondo","круглый"); _a("continuo","непрерывный")
_a("regolare","регулярный"); _a("simmetrico","симметричный")
_a("equilibrato","сбалансированный"); _a("stabile","стабильный")
_a("fisso","фиксированный"); _a("duraturo","долговечный")
_a("perenne","вечный"); _a("temporaneo","временный")
_a("definitivo","окончательный"); _a("esclusivo","исключительный")
_a("specifico","конкретный"); _a("assoluto","абсолютный")
_a("totale","полный"); _a("parziale","частичный")
_a("completo","полный"); _a("trasparente","прозрачный")
_a("opaco","непрозрачный"); _a("luminoso","светлый")
_a("buio","тёмный"); _a("pallido","бледный")
_a("colorato","цветной"); _a("vivo","яркий")
_a("morbido","мягкий"); _a("elastico","эластичный")
_a("flessibile","гибкий"); _a("solido","твёрдый/прочный")
_a("sonoro","звучный"); _a("allegro","весёлый")
_a("lento","медленный"); _a("veloce","быстрый")
_a("rapido","быстрый"); _a("immediato","немедленный")
_a("sicuro","безопасный"); _a("nocivo","вредный")
_a("letale","смертельный"); _a("doloroso","болезненный")
_a("comodo","удобный"); _a("scomodo","неудобный")
_a("costoso","дорогой"); _a("gratuito","бесплатный")
_a("abbondante","обильный"); _a("scarso","скудный")
_a("eccellente","превосходный"); _a("scadente","низкокачественный")
_a("notevole","заметный"); _a("indispensabile","незаменимый")
_a("superfluo","излишний"); _a("vietato","запрещённый")
_a("permesso","разрешённый"); _a("valido","действительный")
_a("efficace","эффективный"); _a("efficiente","эффективный")
_a("onesto","честный"); _a("leale","верный")
_a("fedele","верный"); _a("sincero","искренний")
_a("timido","робкий"); _a("audace","смелый")
_a("prudente","осторожный"); _a("saggio","мудрый")
_a("furbo","хитрый"); _a("ingenuo","наивный")
_a("noioso","скучный"); _a("eccitante","возбуждающий")
_a("calmante","успокаивающий"); _a("accogliente","уютный")
_a("cordiale","сердечный"); _a("affettuoso","ласковый")
_a("tenero","нежный"); _a("educato","вежливый")
_a("ambizioso","честолюбивый"); _a("modesto","скромный")
_a("orgoglioso","гордый"); _a("vanitoso","тщеславный")
_a("geloso","ревнивый"); _a("indifferente","безразличный")
_a("spietato","безжалостный"); _a("gratitudine","благодарный")
_a("innamorato","влюблённый"); _a("viziato","избалованный")
# Additional ADJ from missing list
_a("ovvio","очевидный"); _a("preoccupato","обеспокоенный")
_a("breve","короткий/краткий"); _a("innocente","невинный")
_a("centrale","центральный"); _a("grandioso","величественный")
_a("americano","американский"); _a("francese","французский")
_a("capace","способный"); _a("convinto","убеждённый")
_a("finale","финальный"); _a("super","супер")
_a("potente","могущественный"); _a("assurdo","абсурдный")
_a("calmo","спокойный"); _a("nervoso","нервный")
_a("sessuale","сексуальный"); _a("superiore","верхний/высший")
_a("recente","недавний"); _a("complicato","сложный")
_a("famoso","знаменитый"); _a("folle","безумный")
_a("diretta","прямая"); _a("adorabile","очаровательный")
_a("interessato","заинтересованный"); _a("positivo","положительный")
_a("morale","моральный/нравственный"); _a("lieto","радостный")
_a("confuso","смущённый/запутанный"); _a("disgustoso","омерзительный")
_a("tedesco","немецкий"); _a("piacevole","приятный")
_a("pago","удовлетворённый"); _a("mondiale","мировой")
_a("disponibile","доступный"); _a("pessima","очень плохая")
_a("professionale","профессиональный"); _a("adatto","подходящий")
_a("deserto","пустынный/пустой"); _a("crudele","жестокий")
_a("negativo","отрицательный"); _a("urgente","срочный")
_a("deluso","разочарованный"); _a("finto","поддельный/ненастоящий")
_a("buffo","смешной"); _a("simpatico","симпатичный/милый")
_a("attraente","привлекательный"); _a("vergine","девственный")
_a("spagnolo","испанский"); _a("eccezionale","исключительный")
_a("femminile","женский"); _a("grato","благодарный")
_a("singolo","одиночный/единственный"); _a("italiano","итальянский")
_a("sinistro","зловещий/левый"); _a("inquietante","тревожный")
_a("mortale","смертельный"); _a("potenziale","потенциальный")
_a("violento","насильственный"); _a("isolato","изолированный")
_a("popolare","популярный"); _a("spiacente","сожалеющий")
_a("ragionevole","разумный"); _a("patetico","жалкий")
_a("attuale","текущий/настоящий"); _a("bionda","белокурая")
_a("infelice","несчастный"); _a("prezioso","драгоценный")
_a("desperato","отчаянный"); _a("entusiasta","восторженный")
_a("tipico","типичный"); _a("schifoso","отвратительный")
_a("sensibile","чувствительный"); _a("invisibile","невидимый")
_a("desiderato","желанный"); _a("perdente","проигравший")
_a("solare","солнечный"); _a("ribelli","мятежные")
_a("domestico","домашний"); _a("misterioso","таинственный")
_a("psicopatico","психопатический"); _a("consapevole","осведомлённый")
_a("eterno","вечный"); _a("corrotto","коррумпированный")
_a("rischioso","рискованный"); _a("sorprendente","удивительный")
_a("certificato","сертифицированный"); _a("attivo","активный")
_a("irlandese","ирландский"); _a("vivente","живущий")
_a("proibito","запрещённый"); _a("vitale","жизненный")
_a("degno","достойный"); _a("solitario","одинокий")
_a("insolito","необычный"); _a("tremendo","ужасный")
_a("emozionante","волнующий"); _a("delicato","деликатный")
_a("affamato","голодный"); _a("infinito","бесконечный")
_a("maschile","мужской"); _a("scolastico","школьный")
_a("vulnerabile","уязвимый"); _a("incapace","неспособный")
_a("affidabile","надёжный"); _a("favoloso","сказочный/потрясающий")
_a("inferiore","нижний"); _a("decente","приличный")
_a("grigio","серый"); _a("inevitabile","неизбежный")
_a("convincente","убедительный"); _a("intimo","интимный")
_a("marrone","коричневый"); _a("greco","греческий")
_a("fragile","хрупкий"); _a("occidentale","западный")
_a("distante","далёкий"); _a("permanente","постоянный")
_a("finanziario","финансовый"); _a("globale","глобальный")
_a("alimentare","пищевой"); _a("volgare","вульгарный")
_a("basato","основанный"); _a("incantevole","очаровательный")
_a("infantile","детский"); _a("pacifico","мирный")
_a("sentimentale","сентиментальный"); _a("spettacolare","зрелищный")
_a("intenso","интенсивный"); _a("paranoico","параноидальный")
_a("estremo","крайний"); _a("operativo","оперативный")
_a("terrificante","устрашающий"); _a("onnipotente","всемогущий")
_a("insopportabile","невыносимый"); _a("essenziale","существенный")
_a("cosciente","сознающий"); _a("aggressivo","агрессивный")
_a("brutale","жестокий/брутальный"); _a("orientale","восточный")
_a("comprensibile","понятный"); _a("privo","лишённый")
_a("celeste","небесный/голубой"); _a("ipocrita","лицемерный")
_a("disponibile","доступный/располагающий"); _a("messicano","мексиканский")
_a("messico","Мексика"); _a("inglese","английский")
_a("giapponese","японский"); _a("porca","чёртов (ж.р.)")
_a("oddio","боже мой"); _a("puttana","чёртов")
_a("cellulare","мобильный"); _a("scorsa","прошлая")
_a("incinta","беременная"); _a("destra","правый")
_a("blu","синий"); _a("gay","гей"); _a("sexy","сексуальный")
_a("sole","солнечный"); _a("amico","дружеский")
_a("nemico","враждебный"); _a("divino","божественный")
_a("magnifico","великолепный"); _a("splendido","великолепный")
_a("sofisticato","изощрённый"); _a("poetico","поэтический")
_a("musicale","музыкальный"); _a("artistico","художественный")
_a("epico","эпический"); _a("lirico","лирический")
_a("storicamente","исторический"); _a("digitale","цифровой")
_a("industriale","промышленный"); _a("commerciale","коммерческий")
_a("internazionale","международный"); _a("nazionale","национальный")
_a("regionale","региональный"); _a("religioso","религиозный")
_a("laico","светский"); _a("conservativo","консервативный")
_a("progressista","прогрессивный"); _a("socialista","социалистический")
_a("comunista","коммунистический"); _a("fascista","фашистский")
_a("protestante","протестантский"); _a("ebraico","еврейский")
_a("islamico","исламский"); _a("pagano","языческий")
_a("sacro","священный"); _a("profano","светский/мирской")
_a("psichico","психический"); _a("intellettuale","интеллектуальный")
_a("irrazionale","иррациональный"); _a("matematico","математический")
_a("biologico","биологический"); _a("chirurgico","хирургический")
_a("clinico","клинический"); _a("terapeutico","терапевтический")
_a("giuridico","юридический"); _a("burocratico","бюрократический")
_a("esecutivo","исполнительный"); _a("legislativo","законодательный")
_a("presidenziale","президентский"); _a("governativo","правительственный")
_a("municipale","муниципальный"); _a("provinciale","провинциальный")
_a("comunale","коммунальный"); _a("coloniale","колониальный")
_a("imperiale","имперский"); _a("feudale","феодальный")
_a("borghese","буржуазный"); _a("aristocratico","аристократический")
_a("oligarchico","олигархический"); _a("totalitario","тоталитарный")
_a("autoritario","авторитарный"); _a("anarchico","анархический")
_a("rivoluzionario","революционный"); _a("reazionario","реакционный")
_a("ecologico","экологический"); _a("ambientale","окружающей среды")
_a("sostenibile","устойчивый"); _a("tossico","токсичный")
_a("radioattivo","радиоактивный"); _a("nucleare","ядерный")
_a("sperimentale","экспериментальный"); _a("marino","морской")
_a("montano","горный"); _a("costiero","прибрежный")
_a("tropicale","тропический"); _a("umido","влажный")
_a("secco","сухой"); _a("nebbioso","туманный")
_a("soleggiato","солнечный"); _a("nuvoloso","облачный")
_a("serale","вечерний"); _a("notturno","ночной")
_a("giornaliero","ежедневный"); _a("primaverile","весенний")
_a("estivo","летний"); _a("autunnale","осенний")
_a("invernale","зимний"); _a("mobile","подвижный")
_a("denso","плотный"); _a("compatto","компактный")
_a("crudo","сырой"); _a("cotto","варёный")
_a("tiepido","тёплый"); _a("gelato","замороженный")
_a("congelato","замороженный"); _a("rigido","жёсткий")
_a("mite","мягкий"); _a("insipido","пресный")
_a("saporito","вкусный"); _a("squisito","изысканный")
_a("continuo","непрерывный"); _a("discontinuo","прерывистый")
_a("rotondo","круглый"); _a("variabile","переменный")
_a("retto","прямой"); _a("curvo","кривой")
_a("quadrato","квадратный"); _a("lineare","линейный")
_a("duraturo","долговечный"); _a("provvisorio","временный")
_a("eccellente","превосходный"); _a("scadente","низкокачественный")
_a("sporco","грязный"); _a("bagnato","мокрый")
_a("asciutto","сухой"); _a("ferito","раненый")
_a("malato","больной"); _a("debole","слабый")
_a("robusto","крепкий"); _a("agile","проворный")
_a("improvviso","внезапный"); _a("graduale","постепенный")
_a("comodo","удобный"); _a("scomodo","неудобный")
_a("costoso","дорогой"); _a("gratuito","бесплатный")
_a("indispensabile","незаменимый"); _a("superfluo","излишний")
_a("necessario","необходимый"); _a("vietato","запрещённый")
_a("permesso","разрешённый"); _a("illegale","незаконный")
_a("legittimo","законный"); _a("valido","действительный")
_a("efficace","эффективный"); _a("curato","ухоженный")
_a("trascurato","запущенный"); _a("ordinato","аккуратный")
_a("metodico","методичный"); _a("casuale","случайный")
_a("severo","строгий"); _a("generoso","щедрый")
_a("egoista","эгоистичный"); _a("altruista","альтруистичный")
_a("infedele","неверный"); _a("bugiardo","лживый")
_a("ipocrito","лицемерный"); _a("franco","откровенный")
_a("riservato","сдержанный"); _a("valoroso","доблестный")
_a("codardo","трусливый"); _a("incauto","неосторожный")
_a("sciocco","глупый"); _a("spiritoso","остроумный")
_a("tedioso","утомительный"); _a("appassionante","увлекательный")
_a("rilassante","расслабляющий"); _a("irritante","раздражающий")
_a("ospitale","гостеприимный"); _a("ruvido","грубый")
_a("brusco","резкий"); _a("scortese","невежливый")
_a("maleducato","невоспитанный"); _a("umile","скромный")
_a("superbo","надменный"); _a("avidamente","алчный")
_a("passionale","страстный"); _a("solidale","солидарный")
_a("compassionevole","сострадательный"); _a("clemente","милосердный")
_a("riconoscente","признательный"); _a("adirato","разгневанный")
_a("furente","яростный"); _a("indignato","негодующий")
_a("adorante","обожающий"); _a("devoto","преданный")
_a("affezionato","привязанный"); _a("odiato","ненавистный")
_a("rispettato","уважаемый"); _a("ammirato","восхищённый")
_a("temuto","страшимый"); _a("addestrato","обученный")
_a("privata","частный (ж.р.)"); _a("passati","прошлые")
_a("media","средний (ж.р.)"); _a("animale","животный")
_a("corrente","текущий"); _a("santa","святая")
_a("inglese","английский"); _a("brevetto","патентованный")
# More from missing list
_a("srt","SRT (субтитры)"); _a("credimi","поверь мне")
_a("dimmelo","скажи мне это"); _a("famoso","знаменитый")
_a("maiale","свинский"); _a("idioti","идиотские")
_a("coso","штука/хрень"); _a("pari","равный")
_a("single","одинокий"); _a("vice","заместительский")
_a("fiero","гордый"); _a("spalla","плечевой")
_a("capite","понятный"); _a("agio","удобный")
_a("pessima","худшая"); _a("ebrei","еврейские")
_a("pura","чистая"); _a("concentrati","сосредоточенные")
_a("preparati","приготовленные"); _a("persi","потерянные")
_a("top","лучший/топ"); _a("vent","двадцать")
_a("ann","год"); _a("subspedia","субтитры")
_a("mancata","несостоявшаяся"); _a("magico","волшебный")
_a("temporale","гроза/временный"); _a("storto","кривой")
_a("spaventoso","пугающий"); _a("dammelo","дай мне это")
_a("fottuti","чёртовы"); _a("peggior","худший")
_a("eccoli","вот они"); _a("facci","делай")
_a("bell","красивый"); _a("accomodi","располагайся")
_a("shh","тсс"); _a("dale","Дейл")
_a("hot","горячий"); _a("tessuto","тканевый")
_a("bravissimo","очень хороший"); _a("impressionante","впечатляющий")
_a("sedile","сиденье"); _a("cerebrale","мозговой")
_a("medio","средний"); _a("varie","различные")
_a("successivo","последующий"); _a("improbabile","маловероятный")
_a("ulteriori","дальнейшие"); _a("caporale","капральский")
_a("distrettuale","районный"); _a("sgualdrina","шлюха")
_a("overdose","передозировка"); _a("adolescenti","подростковые")
_a("delicata","деликатная"); _a("stellare","звёздный")
_a("postale","почтовый"); _a("italian","итальянский")
_a("permettero","они позволят"); _a("hard","жёсткий")
_a("estranei","чужие"); _a("bellissime","прекраснейшие")
_a("sobrio","трезвый"); _a("daphne","Дафна")
_a("suprema","верховная"); _a("stradale","дорожный")
_a("quassu","наверху"); _a("high","высокий")
_a("piccolina","маленькая"); _a("ritrovo","место встречи")
_a("frocio","пидор (оск.)"); _a("rossetto","помада")
_a("mini","мини"); _a("spettacolare","зрелищный")
_a("intenso","интенсивный"); _a("elettorale","избирательный")
_a("gold","золотой"); _a("terrestre","земной")
_a("dimentico","забывчивый"); _a("sopravvissuta","выжившая")
_a("chiamalo","назови его"); _a("storico","исторический")
_a("audio","аудио"); _a("fammelo","дай мне это")
_a("gialla","жёлтая"); _a("now","сейчас")
_a("fumetti","комиксовый"); _a("anonima","анонимная")
_a("art","художественный"); _a("ispirato","вдохновлённый")
_a("romani","римские"); _a("happy","счастливый")
_a("poiche","поскольку"); _a("hei","эй")
_a("lenta","медленная"); _a("neanch","даже не")
_a("assassino","убийственный"); _a("salve","целый/привет")
_a("natale","рождественский"); _a("precedenti","предыдущие")
_a("b","бе"); _a("j","жи"); _a("g","жи")
_a("ii","второй"); _a("comune","обычный/общий")
_a("ex","бывший"); _a("oddio","боже мой")
_a("stu","этот (диал.)"); _a("contabile","счётный")
_a("irrilevante","несущественный"); _a("immortale","бессмертный")
_a("puttana","чёртов (ж.р.)"); _a("negro","негритянский")
_a("precedenti","предыдущие"); _a("scorsa","прошлая")
_a("k","ка"); _a("fi","фи")
_a("johnny","Джонни"); _a("tommy","Томми")

# ─── ADV ──────────────────────────────────────────────────────────────────────
_d("come","как"); _d("oh","о"); _d("perche","почему/потому что")
_d("beh","ну/в общем"); _d("allora","тогда/итак"); _d("piu","более/больше")
_d("quindi","поэтому"); _d("gia","уже"); _d("via","прочь/долой")
_d("ecco","вот"); _d("sotto","под/внизу"); _d("fino","до/вплоть до")
_d("stasera","сегодня вечером"); _d("scusi","извините")
_d("dietro","позади/сзади"); _d("pure","также/даже")
_d("probabilmente","вероятно"); _d("sopra","над/сверху")
_d("davanti","перед/впереди"); _d("piuttosto","довольно/скорее")
_d("insomma","в общем/впрочем"); _d("idiota","по-идиотски")
_d("finalmente","наконец"); _d("oltre","кроме/за")
_d("circa","около/приблизительно"); _d("ovviamente","очевидно")
_d("soltanto","только"); _d("veloce","быстро")
_d("semplicemente","просто"); _d("rispetto","относительно")
_d("peggio","хуже"); _d("addosso","на себе")
_d("intorno","вокруг"); _d("sicuramente","непременно")
_d("altrimenti","иначе"); _d("stamattina","сегодня утром")
_d("dunque","итак/следовательно"); _d("infatti","на самом деле")
_d("certamente","конечно"); _d("ovunque","везде")
_d("affatto","вовсе"); _d("mica","вовсе не")
_d("percio","поэтому"); _d("naturalmente","естественно")
_d("immediatamente","немедленно"); _d("praticamente","практически")
_d("accanto","рядом"); _d("persino","даже")
_d("stavolta","на этот раз"); _d("ultimamente","в последнее время")
_d("chiaramente","ясно"); _d("decisamente","определённо")
_d("purtroppo","к сожалению"); _d("direttamente","напрямую")
_d("seriamente","серьёзно"); _d("perfettamente","превосходно")
_d("velocemente","быстро"); _d("gratis","бесплатно")
_d("dappertutto","везде"); _d("finora","до сих пор")
_d("assieme","вместе"); _d("facilmente","легко")
_d("solamente","только"); _d("chissa","кто знает")
_d("specialmente","особенно"); _d("tuttavia","однако/тем не менее")
_d("lentamente","медленно"); _d("personalmente","лично")
_d("totalmente","полностью"); _d("amen","аминь")
_d("sinceramente","искренне"); _d("sfortunatamente","к несчастью")
_d("onestamente","честно"); _d("letteralmente","буквально")
_d("talmente","настолько"); _d("ufficialmente","официально")
_d("estremamente","в высшей степени"); _d("porno","порно")
_d("intanto","тем временем"); _d("improvvisamente","внезапно")
_d("tecnicamente","технически"); _d("realmente","действительно")
_d("incredibilmente","невероятно"); _d("profondamente","глубоко")
_d("continuamente","непрерывно"); _d("attentamente","внимательно")
_d("normalmente","обычно"); _d("altrove","в другом месте")
_d("particolarmente","в частности"); _d("duramente","сурово")
_d("dannatamente","чёртовски"); _d("terribilmente","ужасно")
_d("francamente","откровенно"); _d("infine","наконец")
_d("effettivamente","фактически"); _d("recentemente","недавно")
_d("fortunatamente","к счастью"); _d("rapidamente","быстро")
_d("evidentemente","очевидно"); _d("nuovamente","снова")
_d("leggermente","слегка"); _d("fisicamente","физически")
_d("alquanto","довольно"); _d("gentilmente","любезно")
_d("innanzitutto","прежде всего"); _d("stranamente","странно")
_d("apparentemente","по-видимому"); _d("eccetera","и так далее")
_d("legalmente","законно"); _d("attualmente","в настоящее время")
_d("altamente","весьма"); _d("necessariamente","необходимо")
_d("fottutamente","чёртовски"); _d("disperatamente","отчаянно")
_d("costantemente","постоянно"); _d("tranquillamente","спокойно")
_d("gravemente","серьёзно/тяжело"); _d("contemporaneamente","одновременно")
_d("correttamente","правильно")
# Missing ADV
_d("perciò","поэтому"); _d("quì","здесь"); _d("bè","ну/в общем")

# ═══════════════════════════════════════════════════════════════════════════════
# REMAINING 609 ENTRIES — bulk add for everything still missing
# ═══════════════════════════════════════════════════════════════════════════════

# ─── Remaining NOUNs ─────────────────────────────────────────────────────────
for orig, ru in [
    ("fossi","я был (сосл.)"), ("perche'","почему/потому что"),
    ("saresti","ты был бы"), ("verra","он придёт"),
    ("digli","скажи ему"), ("andro","я пойду"),
    ("faresti","ты сделал бы"), ("kyle","Кайл"), ("lily","Лили"),
    ("resynch","ресинхронизация"), ("elizabeth","Элизабет"),
    ("molly","Молли"), ("chloe","Хлоя"),
    ("andiamocene","пойдём отсюда"), ("diamine","чёрт возьми!"),
    ("babbo","папа"), ("stammi","будь со мной"),
    ("jones","Джонс"), ("arthur","Артур"),
    ("episode","эпизод"), ("sembro","я кажусь"),
    ("red","Рэд"), ("oscar","Оскар"), ("ione","ион"),
    ("big","большой"), ("online","онлайн"), ("doc","док"),
    ("house","дом"), ("synch","синхронизация"),
    ("dacci","дай нам"), ("arrivera","он прибудет"),
    ("giappone","Япония"), ("versi","стихи"),
    ("chance","шанс"), ("hill","Хилл"), ("one","один"),
    ("presumo","предполагаю"), ("iscrew","исклю́чение"),
    ("morira","он умрёт"), ("subsfactory","субтитры"),
    ("out","выход"), ("eun","Евн"), ("cam","Кам"),
    ("dog","собака"), ("time","время"), ("girl","девушка"),
    ("martedi","вторник"), ("aspettami","подожди меня"),
    ("boy","мальчик"), ("stufo","уставший"),
    ("grata","решётка"), ("blue","синий"),
    ("quartier","квартал"), ("east","восток"),
    ("post","пост"), ("won","ван"), ("muoio","я умираю"),
    ("turner","Тёрнер"), ("pat","Пэт"), ("boschi","леса"),
    ("gina","Джина"), ("flynn","Флинн"), ("beach","пляж"),
    ("graham","Грэм"), ("tac","так"), ("mcgee","Макги"),
    ("collins","Коллинз"), ("mori","Мори"),
    ("seattle","Сиэтл"), ("vacca","корова"),
    ("cal","Кэл"), ("kitty","Китти"), ("harper","Харпер"),
    ("connie","Конни"), ("wallace","Уоллес"),
    ("manny","Мэнни"), ("violet","Вайолет"),
    ("troy","Троя"), ("rory","Рори"), ("sharon","Шэрон"),
    ("spie","шпионы"), ("night","ночь"), ("world","мир"),
    ("detesto","отвращение"), ("parliamone","поговорим об этом"),
    ("long","Лонг"), ("terro","страх"), ("clan","клан"),
    ("louise","Луиза"), ("avenue","авеню"),
    ("rivoglio","хочу обратно"), ("maestra","учительница"),
    ("brick","кирпич"), ("becky","Бекки"), ("color","цвет"),
    ("orleans","Орлеан"), ("round","раунд"), ("spreco","отходы/расточительство"),
    ("paraggi","окрестности"), ("vinci","ты побеждаешь"),
    ("queen","королева"), ("vie","улицы/пути"),
    ("web","веб"), ("alexander","Александр"),
    ("letti","кровати"), ("force","сила"),
    ("sensori","датчики"), ("ego","эго"), ("judy","Джуди"),
    ("nerd","гик/ботаник"), ("andrete","вы пойдёте"),
    ("yang","Ян"), ("business","бизнес"),
    ("salite","подъёмы"), ("pipi","пи-пи"),
    ("vicepresidente","вице-президент"),
    ("supervisore","супервайзер"), ("gwen","Гвен"),
    ("missili","ракеты"), ("cambiera","она поменяет"),
    ("radar","радар"), ("portale","портал"),
    ("deposizione","показание/осадок"), ("galassia","галактика"),
    ("barbecue","барбекю"), ("intesi","понял"),
    ("esplosivo","взрывчатка"), ("south","юг"),
    ("pop","поп"), ("gabinetto","кабинет/туалет"),
    ("provero","я попробую"), ("scarico","сток/разряд"),
    ("mercoledi","среда"), ("calcoli","расчёты"),
    ("jazz","джаз"), ("omaggio","дань/подарок"),
    ("lotteria","лотерея"), ("look","вид/внешность"),
    ("pro","за/про"), ("insegno","знак/вывеска"),
    ("tecniche","техники"), ("news","новости"),
    ("lenti","линзы"), ("pan","пан"), ("spot","ролик/спот"),
    ("scotty","Скотти"), ("air","эйр/воздух"),
    ("circolazione","обращение"), ("jedi","джедай"),
    ("defunto","покойный"), ("confraternita","братство"),
    ("portatemi","отнесите меня"),
    ("psicologo","психолог"), ("bong","бонг"),
    ("what","что"), ("wall","стена"), ("nsa","АНБ"),
    ("manica","рукав"), ("nucleo","ядро"), ("app","приложение"),
    ("freno","тормоз"), ("side","сторона"),
    ("inviti","приглашения"), ("morfina","морфин"),
    ("crociera","круиз"), ("arabo","араб"),
    ("immondizia","мусор"), ("corrispondenza","переписка"),
    ("falco","сокол"), ("bonus","бонус"), ("legna","дрова"),
    ("feccia","накипь/отбросы"), ("baciami","поцелуй меня"),
    ("song","песня"), ("ricevimento","приём"),
    ("cocco","кокос"), ("batti","удар"),
    ("individuo","индивид"), ("bum","бум"),
    ("palude","болото"), ("strip","стрип"),
    ("life","жизнь"), ("interruzione","перерыв"),
    ("particelle","частицы"), ("terrorismo","терроризм"),
    ("premuto","нажатый"),
    ("federazione","федерация"), ("merlino","Мерлин"),
    ("domicilio","местожительство"), ("risonanza","резонанс"),
    ("pos","терминал"), ("uva","виноград"),
    ("immigrazione","иммиграция"), ("brandy","коньяк"),
    ("bond","Бонд"), ("crack","крэк/трещина"),
    ("fritto","жареное"), ("perquisizione","обыск"),
    ("good","хорошо"), ("water","туалет"),
    ("stronzetto","подонок"), ("graffio","царапина"),
    ("strappo","рывок"), ("ops","ой"),
    ("camille","Камиль"), ("arizona","Аризона"),
    ("granche","ошибки"), ("pasticcio","путаница/беспорядок"),
    ("whiskey","виски"), ("arteria","артерия"),
    ("capitan","капитан"), ("arachidi","арахис"),
    ("vip","VIP"), ("sballo","кайф"),
    ("brien","Брайен"), ("artificio","уловка"),
    ("clacson","клаксон"), ("suv","внедорожник"),
    ("gorilla","горилла"), ("deputato","депутат"),
    ("lavoretto","подработка"), ("puttanella","шлюшка"),
    ("reporter","репортёр"), ("architetto","архитектор"),
    ("dance","танец"), ("ramo","ветка"),
    ("detroit","Детройт"), ("scartoffie","бумажная волокита"),
    ("klingon","клингон"), ("telegramma","телеграмма"),
    ("fumi","дымы"), ("muffin","маффин"),
    ("spogliarellista","стриптизёр"), ("account","аккаунт"),
    ("navetta","шаттл/маршрутка"), ("dolcetto","конфетка"),
    ("yogurt","йогурт"), ("lampo","молния"),
    ("aggressore","нападающий"), ("grotta","пещера"),
    ("black","чёрный"), ("racconti","рассказы"),
    ("assassini","убийцы"), ("white","Уайт"),
    ("villa","вилла"), ("clay","Клэй"),
    ("vacci","иди (в преп.)"), ("reato","преступление"),
    ("funzionera","заработает"), ("prostituta","проститутка"),
    ("halloween","Хэллоуин"), ("little","маленький"),
    ("chiamero","я позвоню"), ("finira","он закончит"),
    ("lassu","наверху"), ("sparate","выстрелы"),
    ("uccidera","он убьёт"), ("operatoria","операционная"),
    ("negro","негр"), ("yeah","да/ага"),
    ("morira","он умрёт"), ("lunedi","понедельник"),
    ("battito","биение/удар"), ("giappone","Япония"),
    ("fang","Фэнг"), ("ion","ион"),
    ("ione","ион"), ("alf","альф"), ("alfa","альфа"),
    ("vena","вена/жила"), ("foglie","листья"),
    ("spreco","отходы"), ("paraggi","окрестности"),
    ("latino","латынь"), ("hockey","хоккей"),
    ("senato","сенат"), ("schmidt","Шмидт"),
    ("iris","Ирис"), ("baker","Бейкер"),
    ("maniaco","маньяк"), ("cannone","пушка"),
    ("sosta","остановка"), ("riscaldamento","обогрев/нагрев"),
    ("roulotte","фургон/прицеп"), ("negri","негры"),
    ("allucinazioni","галлюцинации"), ("personalita","личность"),
    ("dipendenza","зависимость"), ("uccellino","птичка"),
    ("min","мин"), ("jonah","Джона"),
    ("occupero","я займу"), ("salita","подъём"),
    ("provino","прослушивание"), ("femminuccia","девчонка"),
    ("precisione","точность"), ("melanie","Мелани"),
    ("central","центр"), ("vermi","черви"),
    ("comprensione","понимание"), ("dimostrazione","демонстрация"),
    ("tasso","ставка/уровень"), ("tattica","тактика"),
    ("api","пчёлы"), ("esperta","эксперт"),
    ("mona","Мона"), ("papi","папочки"),
    ("portiere","привратник/дверник"), ("audizione","прослушивание"),
    ("georgia","Джорджия"), ("intelligence","разведка"),
    ("ash","Эш"), ("giochetti","игрушки"),
    ("disordine","беспорядок"), ("priorita","приоритет"),
    ("limousine","лимузин"), ("cheerleader","чирлидер"),
    ("gin","джин"), ("ring","ринг/кольцо"),
    ("creazione","создание"), ("ohio","Огайо"),
    ("schianto","крах/грохот"), ("dominio","владение/домен"),
    ("dossier","досье"), ("musical","мюзикл"),
    ("inganno","обман"), ("yen","иена"),
    ("fusion","слияние"), ("generatore","генератор"),
    ("tequila","текила"), ("squali","акулы"),
    ("stupidaggini","глупости"), ("contessa","графиня"),
    ("presentimento","предчувствие"), ("apparecchio","аппарат"),
    ("sbarre","прутья"), ("end","конец"),
    ("polo","поло"), ("mutandine","трусики"),
    ("know","знание"), ("can","банка"),
    ("country","кантри"), ("sufficienza","достаточность"),
    ("gray","Грей"), ("american","американец"),
    ("grandezza","величие"), ("definizione","определение"),
    ("sudore","пот"), ("pancake","блинчик"),
    ("gelosia","ревность"), ("gallina","курица"),
    ("aquila","орёл"), ("bestiame","скот"),
    ("lite","ссора"), ("ometto","маленький человек"),
    ("muso","морда"), ("benone","отлично"),
    ("broadway","Бродвей"), ("enterprise","предприятие"),
    ("giubbotto","куртка"), ("fica","фига (вульг.)"),
    ("seo","СЕО"), ("soo","Су"),
    ("vigilia","канун"), ("sottomarino","подводная лодка"),
    ("litri","литры"), ("politiche","политики"),
    ("piega","складка"), ("tonnellate","тонны"),
    ("senno","рассудок"), ("detroit","Детройт"),
    ("fagli","сделай ему"), ("partiti","партии"),
    ("terr","терр"), ("linea","линия"),
    ("balle","чушь"), ("bianco","белый"),
    ("piaga","язва/беда"), ("brutta","некрасивая"),
    ("fetta","ломтик"), ("bici","велосипед"),
    ("metri","метры"), ("frigo","холодильник"),
    ("cugino","двоюродный брат"), ("bicchiere","стакан"),
    ("compagno","товарищ"), ("vicina","соседка"),
    ("nonna","бабушка"), ("nonno","дедушка"),
    ("nipote","внук/племянник"), ("zia","тётя"),
    ("zio","дядя"), ("sorella","сестра"),
    ("fratello","брат"), ("figlia","дочь"),
    ("figlio","сын"), ("madre","мать"),
    ("padre","отец"), ("figli","дети/сыновья"),
    ("figlie","дочери"), ("mariti","мужья"),
    ("mogli","жёны"), ("amiche","подруги"),
    ("amici","друзья"), ("vicini","соседи"),
    ("vicino","сосед/близко"), ("lontano","далеко"),
    ("dentro","внутри"), ("fuori","снаружи"),
    ("sopra","над/сверху"), ("sotto","под/внизу"),
    ("accordo","согласие"), ("aiuto","помощь"),
    ("bisogno","нужда/потребность"), ("coraggio","храбрость"),
    ("danno","ущерб/вред"), ("diritto","право/прямой"),
    ("dubbio","сомнение"), ("errore","ошибка"),
    ("giudizio","суждение/суд"), ("guadagno","заработок"),
    ("incarico","задание/поручение"), ("motivo","причина/мотив"),
    ("movimento","движение"), ("permesso","разрешение"),
    ("peso","вес"), ("piacere","удовольствие"),
    ("rispetto","уважение"), ("riferimento","ссылка"),
    ("senso","смысл/чувство"), ("sguardo","взгляд"),
    ("silenzio","тишина"), ("sonno","сон"),
    ("spazio","пространство"), ("speranza","надежда"),
    ("successo","успех/происшествие"), ("talento","талант"),
    ("valore","ценность/стоимость"), ("viaggio","путешествие"),
    ("vittoria","победа"), ("zucchero","сахар"),
]:
    _n(orig, ru)

# ─── Remaining VERBs ─────────────────────────────────────────────────────────
for orig, ru in [
    ("é","быть"), ("dirtelo","сказать тебе это"),
    ("fargli","сделать ему"), ("dargli","дать ему"),
    ("lasciatemi","оставьте меня"), ("scusarmi","извиниться"),
    ("dirmelo","сказать мне это"), ("dirglielo","сказать ему это"),
    ("dillo","скажи это"), ("facciamolo","сделаем это"),
    ("conoscete","знаете"), ("portarlo","нести его"),
    ("portarti","нести тебе"), ("iniziamo","начнём"),
    ("tenerlo","держать его"), ("lasciala","оставь её"),
    ("calmi","успокойся"), ("esca","выходи"),
    ("farete","сделаете"), ("cominciamo","начнём"),
    ("eccomi","вот я"), ("esco","выхожу"),
    ("portarmi","нести мне"), ("lascero","я оставлю"),
    ("perdonami","прости меня"), ("conviene","стоит/целесообразно"),
    ("mangi","ешь"), ("divertendo","развлекая"),
    ("dille","скажи ей"), ("mando","посылаю"),
    ("gliene","ему об этом"), ("datemi","дайте мне"),
    ("fidarti","доверять"), ("bevo","пью"),
    ("fermarlo","остановить его"), ("preoccuparsi","беспокоиться"),
    ("fatelo","сделайте это"), ("fidarmi","доверять"),
    ("fermarmi","остановиться"), ("fermarti","остановиться"),
    ("eccoti","вот ты"), ("eccola","вот она"),
    ("compro","покупаю"), ("rimango","остаюсь"),
    ("bevendo","пить (гер.)"), ("divertirsi","развлекаться"),
    ("dicci","скажи нам"), ("cominciamo","начнём"),
    ("finisco","заканчиваю"), ("crederai","поверишь"),
    ("coperte","покрыл"), ("fermarci","остановиться"),
    ("aspettavi","ждал"), ("fidarsi","доверять"),
    ("fermarsi","остановиться"), ("scusarti","извиниться"),
    ("compri","купи"), ("fidanzati","обручиться"),
    ("insistito","настаивал"), ("comportarti","вести себя"),
    ("fidarci","доверять"), ("concentrarmi","сосредоточиться"),
    ("lasciatelo","оставьте это"), ("preoccuparmi","беспокоиться"),
    ("divertirci","развлечься"), ("fermarla","остановить её"),
    ("liberarsi","освободиться"), ("credetemi","поверьте мне"),
    ("finch","пока"), ("chiederglielo","спросить его об этом"),
    ("convincerlo","убедить его"), ("divertirti","развлечься"),
    ("divertirmi","развлечься"), ("liberarmi","освободиться"),
    ("divertitevi","развлекайтесь"), ("divertirmi","развлечься"),
    ("dispiaccia","сожалеть (сосл.)"), ("fidanzati","обручиться"),
    ("distratto","отвлечённый"), ("metterai","положишь"),
    ("facessimo","делали бы"), ("facciate","делайте"),
    ("inseguendo","преследуя"), ("chiedertelo","спросить тебя об этом"),
    ("spiacevole","неприятный (как гл.)"),
    ("mandero","я пришлю"), ("mandi","пошли"),
    ("aspettero","я подожду"), ("mantieni","держи/поддерживай"),
    ("convincermi","убедить себя"), ("lasciaci","оставь нам"),
    ("facciamola","сделаем это"), ("lavorarci","работать с этим"),
    ("aspettero","я подожду"), ("legga","пусть читает"),
    ("assunta","принятая"), ("lasceranno","оставят (они)"),
    ("mandarmi","прислать мне"), ("possiate","можете (сосл.)"),
    ("goditi","наслаждайся"), ("comportarsi","вести себя"),
    ("calmatevi","успокойтесь"), ("fermarli","остановить их"),
    ("stessero","были (сосл.)"), ("chiamami","позови меня"),
    ("lascialo","оставь его"), ("guardalo","посмотри на это"),
    ("indossi","надень"), ("scelgo","выбираю"),
    ("dammela","дай мне её"), ("colpisci","ударь"),
    ("cattura","ловит"), ("guardala","посмотри на неё"),
    ("decisi","решил"), ("combatti","борись"),
    ("escluso","исключил"), ("discuterne","обсудить это"),
    ("chiamami","позови меня"), ("cattura","ловит"),
    ("is","есть (англ.)"), ("ragae'","ребята!"),
]:
    _v(orig, ru)

# ─── Remaining ADJs ──────────────────────────────────────────────────────────
for orig, ru in [
    ("ultima","последняя"), ("orribile","ужасный"),
    ("grave","серьёзный/тяжёлый"), ("benvenuti","добро пожаловать (мн.)"),
    ("inghilterra","Англия (ошибка тега)"), ("assicuro","я уверяю"),
    ("affascinante","очаровательный"), ("sufficiente","достаточный"),
    ("fottiti","пошёл к чёрту"), ("randy","Рэнди"),
    ("chissa","кто знает"), ("pessimo","очень плохой"),
    ("ubriaca","пьяная"), ("esagerato","преувеличенный"),
    ("francisco","Франсиско"), ("minore","меньший"),
    ("taci","молчи"), ("matta","сумасшедшая"),
    ("distintivo","отличительный"), ("riuscite","вы справились"),
    ("zitti","тихие/молчащие"), ("disperato","отчаянный"),
    ("serena","спокойная/безмятежная"), ("tipa","девушка (сленг)"),
    ("nervi","нервы"), ("diretti","прямые"),
    ("datti","дай себе"), ("benvenuti","добро пожаловать (мн.)"),
    ("elementare","элементарный"), ("liquido","жидкий"),
    ("obitorio","морг (ошибка тега)"), ("preferiti","предпочтительные"),
    ("roman","Роман"), ("divertitevi","развлекайтесь (ошибка тега)"),
    ("sfigato","неудачник/лузер"), ("instabile","нестабильный"),
    ("piccoletto","маленький"), ("seguente","следующий"),
    ("stessimo","тот же самый"), ("vigili","бдительные"),
    ("vigiliacco","трусливый"), ("differenti","различные"),
    ("spiacevole","неприятный"), ("uccidilo","убей его"),
    ("vincente","побеждающий/выигрышный"), ("price","Прайс"),
    ("rafael","Рафаэль"), ("fastidioso","надоедливый"),
    ("mills","Миллс"), ("dante","Данте"),
    ("trish","Триш"), ("vega","Вега"),
    ("gabrielle","Габриэль"), ("rodney","Родни"),
    ("rilevante","значительный"), ("furioso","яростный"),
    ("lealta","верный"), ("liscia","гладкая"),
    ("sottile","тонкий"), ("callie","Кэлли"),
    ("verbale","устный/словесный"), ("sordo","глухой"),
    ("metropolitana","метрополитен/метро"), ("telefonica","телефонная"),
    ("unite","объединённые"), ("tosta","крутая"),
    ("meccanico","механический"), ("arrogante","высокомерный"),
    ("capra","коза (ошибка тега)"), ("promettimi","пообещай мне"),
    ("botta","удар"), ("grave","тяжёлый/серьёзный"),
    ("calmi","спокойные"), ("marie","Мария (ошибка тега)"),
    ("muori","ты умираешь (ошибка тега: VERB→ADJ)"),
    ("depresso","подавленный"), ("inghilterra","английский (ошибка тега)"),
    ("accomodi","располагайтесь"), ("june","Джун (ошибка тега)"),
    ("reese","Рис (ошибка тега)"), ("valerie","Валери (ошибка тега)"),
    ("chelsea","Челси (ошибка тега)"), ("shirley","Ширли (ошибка тега)"),
    ("brendan","Брендан (ошибка тега)"), ("dallas","Даллас (ошибка тега)"),
    ("mick","Мик (ошибка тега)"), ("alec","Алек (ошибка тега)"),
    ("isaac","Айзек (ошибка тега)"), ("phoebe","Фиби (ошибка тега)"),
    ("cat","Кэт (ошибка тега)"), ("ari","Ари (ошибка тега)"),
    ("randall","Рэндалл (ошибка тега)"), ("carmen","Кармен (ошибка тега)"),
    ("miranda","Миранда (ошибка тега)"), ("veronica","Вероника (ошибка тега)"),
    ("cat","Кэт (ошибка тега)"), ("damon","Деймон (ошибка тега)"),
    ("anthony","Энтони (ошибка тега)"), ("raymond","Рэймонд (ошибка тега)"),
    ("nicole","Николь (ошибка тега)"), ("lydia","Лидия (ошибка тега)"),
    ("shane","Шейн (ошибка тега)"), ("neil","Нил (ошибка тега)"),
    ("dick","Дик (ошибка тега)"), ("vanessa","Ванесса (ошибка тега)"),
    ("natalie","Натали (ошибка тега)"), ("diane","Диана (ошибка тега)"),
    ("megan","Меган (ошибка тега)"), ("quinn","Куинн (ошибка тега)"),
    ("charlotte","Шарлотта (ошибка тега)"), ("alice","Алиса (ошибка тега)"),
    ("jamie","Джейми (ошибка тега)"), ("douglas","Дуглас (ошибка тега)"),
    ("doug","Дуг (ошибка тега)"), ("margaret","Маргарет (ошибка тега)"),
    ("caroline","Каролина (ошибка тега)"),
]:
    _a(orig, ru)

# ─── Remaining ADVs ──────────────────────────────────────────────────────────
for orig, ru in [
    ("chissà","кто знает"), ("frattempo","тем временем"),
    ("pochino","чуть-чуть"), ("pò","немного"),
    ("mmm","ммм"), ("dopotutto","в конце концов"),
    ("apposta","нарочно"), ("eccome","ещё как"),
    ("diversamente","иначе"), ("indosso","на себе"),
    ("altrettanto","также/настолько же"), ("dovessimo","если бы мы должны были"),
    ("quaggiù","вон там внизу"), ("fisso","неподвижно"),
    ("etero","гетеро"), ("delinquente","преступно"),
    ("divino","божественно"), ("deprimente","удручающе"),
]:
    _d(orig, ru)

# ─── Final 49 entries ─────────────────────────────────────────────────────────
_n("jay","Джей"); _n("west","Запад"); _n("tornera","он вернётся")
_n("diglielo","скажи ему это"); _n("accompagno","я сопровождаю")
_n("verro","я приду"); _n("sbirri","копы")
_n("grey","Грей"); _n("manco","не хватает/даже не")
_n("quantità","количество"); _n("pipì","пи-пи")
_n("probabilità","вероятность"); _n("iniezione","инъекция")
_n("woo","ву"); _n("supplico","я умоляю")
_n("personalità","личность"); _n("aspettarmi","ждать меня")
_n("carità","милосердие/благотворительность"); _n("priorità","приоритет")
_n("fusione","слияние"); _n("mentirmi","лгать мне")
_n("braccialetto","браслет"); _n("frammenti","обломки")
_n("casinò","казино"); _n("curiosità","любопытство")
_n("eternità","вечность"); _n("restera","она останется")
_n("pensera","он подумает"); _n("nuoto","плавание")
_n("profondità","глубина"); _n("spegni","выключи")

_v("conosca","знать (сосл.)"); _v("giochiamo","играем")
_v("dispiaciuto","огорчённый"); _v("tenerti","держать тебя")
_v("fatevi","сделайте себе"); _v("fermatevi","остановитесь")
_v("cominci","начни"); _v("muoia","пусть умрёт (сосл.)")
_v("ni","ни (ошибка тега)"); _v("lascerai","ты оставишь")
_v("aveste","имели (сосл.)"); _v("farebbero","сделали бы")
_v("garantisco","гарантирую"); _v("curva","поворачивает")
_v("finiscila","закончим это"); _v("glieli","их ему")
_v("chiedermelo","спросить меня об этом")

_a("vigliacco","трусливый")


# ═══════════════════════════════════════════════════════════════════════════════
# SMART FALLBACK — handles what EXACT dict misses
# ═══════════════════════════════════════════════════════════════════════════════

# Base verb infinitive → Russian
BASE_VERBS = {
    "parlare": "говорить", "andare": "идти", "fare": "делать", "dire": "сказать",
    "potere": "мочь", "volere": "хотеть", "dovere": "должен", "sapere": "знать",
    "stare": "быть/находиться", "dare": "давать", "venire": "приходить",
    "vedere": "видеть", "avere": "иметь", "essere": "быть", "cercare": "искать",
    "prendere": "брать", "mettere": "класть/ставить", "trovare": "находить",
    "lasciare": "оставлять", "volere": "хотеть", "sentire": "слышать/чувствовать",
    "credo": "верить", "pensare": "думать", "capire": "понимать",
    "scrivere": "писать", "leggere": "читать", "mangiare": "есть",
    "bere": "пить", "dormire": "спать", "correre": "бежать",
    "vivere": "жить", "morire": "умирать", "salire": "подниматься",
    "scendere": "спускаться", "entrare": "входить", "uscire": "выходить",
    "tornare": "возвращаться", "rimanere": "оставаться", "diventare": "становиться",
    "cominciare": "начинать", "finire": "заканчивать", "continuare": "продолжать",
    "provare": "пробовать", "decidere": "решать", "scegliere": "выбирать",
    "credere": "верить", "sembrare": "казаться", "arrivare": "прибывать",
    "partire": "уезжать", "passare": "проходить", "chiamare": "звонить/называть",
    "guardare": "смотреть", "ascoltare": "слушать", "rispondere": "отвечать",
    "chiedere": "спрашивать", "risolvere": "решать", "aiutare": "помогать",
    "amare": "любить", "odiare": "ненавидеть", "uccidere": "убивать",
    "proteggere": "защищать", "salvare": "спасать", "sposare": "жениться",
    "presentare": "представлять", "spiegare": "объяснять", "permettere": "позволять",
    "preparare": "готовить", "assicurare": "убеждать/страховать",
    "ringraziare": "благодарить", "accompagnare": "сопровождать",
    "incontrare": "встречать", "evitare": "избегать", "accettare": "принимать",
    "sostenere": "поддерживать", "sopportare": "терпеть", "tirare": "тянуть",
    "toccare": "трогать", "alzare": "поднимать", "muovere": "двигать",
    "nascondere": "прятать", "ricordare": "помнить", "dimenticare": "забывать",
    "occupare": "занимать", "cambiare": "менять", "usare": "использовать",
    "seguire": "следовать", "sedere": "сидеть", "benedire": "благословлять",
    "smettere": "прекращать", "scommettere": "держать пари",
    "offrire": "предлагать", "preferire": "предпочитать",
    "riuscire": "успевать/справляться", "piacere": "нравиться",
    "scappare": "убегать", "sparare": "стрелять", "scoprire": "обнаруживать",
    "sorridere": "улыбаться", "suonare": "играть (музыку)",
    "funzionare": "работать (функционировать)", "raggiungere": "достигать",
    "perdonare": "прощать", "rendersi": "становиться", "pagare": "платить",
    "firmare": "подписывать", "garantire": "гарантировать",
    "sostenere": "поддерживать", "aprire": "открывать", "chiudere": "закрывать",
    "conoscere": "знать/быть знакомым", "riconoscere": "узнавать",
    "supporre": "предполагать", "immaginare": "представлять",
    "promettere": "обещать", "giocare": "играть", "mostrare": "показывать",
    "raccontare": "рассказывать", "dimettere": "увольнять",
    "baio": "бежать", "impazzire": "сходить с ума",
}

# Clitic pronoun meanings
CLITICS = {
    "mi": "мне/меня", "ti": "тебе/тебя", "ci": "нам/нас",
    "vi": "вам/вас", "lo": "его/это", "la": "её/это",
    "li": "их (м.р.)", "le": "их (ж.р.)/ей",
    "ne": "об этом/из этого", "si": "себя/ся",
    "gli": "ему/им", "se": "себя",
}

def _try_verb_conjugation(word):
    """Try to map a conjugated Italian verb form back to its infinitive and translate."""
    w = word.lower()
    # Direct lookup first
    if w in BASE_VERBS:
        return BASE_VERBS[w]

    # Common irregular patterns
    IRREGULAR = {
        # essere (быть)
        "sono": "быть", "sei": "быть (ты)", "e": "быть", "era": "быть (прош.)",
        "eri": "быть (прош.)", "eravate": "быть (прош. мн.)", "fui": "быть (прош.)",
        "foste": "быть (прош. мн.)", "saro": "быть (будущ.)", "sarai": "быть (будущ.)",
        "sara": "быть (будущ.)", "saremo": "быть (будущ. мн.)", "sarete": "быть (будущ. мн.)",
        "sarei": "быть (усл.)", "saresti": "быть (усл.)", "sarebbe": "быть (усл.)",
        "saremmo": "быть (усл. мн.)", "sareste": "быть (усл. мн.)",
        "fossi": "быть (сосл.)", "fosse": "быть (сосл.)",
        "fossimo": "быть (сосл. мн.)", "fossero": "быть (сосл. мн.)",
        "sii": "быть (повел.)", "siate": "быть (повел. мн.)",
        "stia": "быть (сосл.)", "stiano": "быть (сосл. мн.)",
        "stiate": "быть (сосл. мн.)",
        # avere (иметь)
        "ho": "иметь", "hai": "иметь", "ha": "иметь", "abbiamo": "иметь (мн.)",
        "hanno": "иметь", "avevi": "иметь (прош.)", "aveva": "иметь (прош.)",
        "avevate": "иметь (прош. мн.)", "ebbi": "иметь (прош.)",
        "avra": "иметь (будущ.)", "avrai": "иметь (будущ.)",
        "avro": "иметь (будущ.)", "avremo": "иметь (будущ. мн.)",
        "avreste": "иметь (усл. мн.)", "averti": "иметь тебя",
        # andare (идти)
        "vado": "идти", "vai": "идти", "va": "идти",
        "andiamo": "идти (мн.)", "vanno": "идти",
        "andrai": "идти (будущ.)", "andra": "идти (будущ.)",
        "andranno": "идти (будущ. мн.)", "andrei": "идти (усл.)",
        "andremo": "идти (будущ. мн.)", "andassi": "идти (сосл.)",
        # fare (делать)
        "faccio": "делать", "fai": "делать", "fa": "делать",
        "fanno": "делать (мн.)", "facevo": "делать (прош.)",
        "facevi": "делать (прош.)", "facevate": "делать (прош. мн.)",
        "farai": "делать (будущ.)", "faro": "делать (будущ.)",
        "faremo": "делать (будущ. мн.)", "fareste": "делать (усл. мн.)",
        "fossimo": "быть (сосл. мн.)",
        # dire (сказать)
        "dico": "говорить", "dice": "говорить", "dici": "говорить",
        "dicono": "говорить (мн.)", "dicevi": "говорить (прош.)",
        "dicevo": "говорить (прош.)", "disse": "сказать (прош.)",
        "dirai": "сказать (будущ.)", "dira": "сказать (будущ.)",
        "diro": "сказать (будущ.)", "diremo": "сказать (будущ. мн.)",
        "diresti": "сказать (усл.)", "dicessi": "сказать (сосл.)",
        # potere (мочь)
        "posso": "мочь", "puo": "мочь", "puoi": "мочь",
        "possiamo": "мочь (мн.)", "possono": "мочь (мн.)",
        "potevi": "мочь (прош.)", "poteva": "мочь (прош.)",
        "potra": "мочь (будущ.)", "potrai": "мочь (будущ.)",
        "potro": "мочь (будущ.)", "potremo": "мочь (будущ. мн.)",
        "potrete": "мочь (будущ. мн.)", "potrei": "мочь (усл.)",
        "potresti": "мочь (усл.)", "potrebbe": "мочь (усл.)",
        "potreste": "мочь (усл. мн.)", "potessi": "мочь (сосл.)",
        "poterlo": "мочь это",
        # volere (хотеть)
        "voglio": "хотеть", "vuoi": "хотеть", "vuole": "хотеть",
        "vogliamo": "хотеть (мн.)", "vogliono": "хотеть (мн.)",
        "volevi": "хотеть (прош.)", "voleva": "хотеть (прош.)",
        "volevate": "хотеть (прош. мн.)", "vorra": "хотеть (будущ.)",
        "vorrei": "хотеть (усл.)", "vorresti": "хотеть (усл.)",
        "vorremmo": "хотеть (усл. мн.)", "vorreste": "хотеть (усл. мн.)",
        "vorrebbero": "хотеть (усл. мн.)", "volessi": "хотеть (сосл.)",
        # dovere (должен)
        "devo": "должен", "devi": "должен", "deve": "должен",
        "dobbiamo": "должны (мн.)", "devono": "должны (мн.)",
        "dovevi": "был должен", "doveva": "был должен",
        "dovevate": "были должны", "dovra": "будет должен",
        "dovrai": "будешь должен", "dovro": "буду должен",
        "dovremo": "будем должны", "dovreste": "будете должны",
        "dovrei": "должен (усл.)", "dovresti": "должен (усл.)",
        "dovrebbe": "должен (усл.)", "dovessimo": "если должны будем",
        # sapere (знать)
        "so": "знать", "sai": "знать", "sa": "знать",
        "sappiamo": "знать (мн.)", "sanno": "знать (мн.)",
        "sapevi": "знать (прош.)", "sapeva": "знать (прош.)",
        "sapevate": "знать (прош. мн.)", "saprare": "знать (будущ.)",
        "sapra": "узнать (будущ.)", "sappia": "знать (сосл.)",
        "sappiate": "знать (сосл. мн.)", "sappiano": "знать (сосл. мн.)",
        "saprei": "знать (усл.)", "sapessi": "знать (сосл.)",
        "sapremo": "узнать (будущ. мн.)",
        # venire (приходить)
        "vengo": "приходить", "viene": "приходить", "venite": "приходить (мн.)",
        "vengono": "приходить (мн.)", "venni": "прийти (прош.)",
        "verra": "прийти (будущ.)", "verrai": "прийти (будущ.)",
        "verro": "прийти (будущ.)", "verremo": "прийти (будущ. мн.)",
        "venissi": "прийти (сосл.)",
        # stare (быть/находиться)
        "sto": "быть/находиться", "stai": "находиться",
        "sta": "находиться", "stiamo": "находиться (мн.)",
        "stanno": "находиться (мн.)", "stavi": "находиться (прош.)",
        "stava": "находиться (прош.)", "stavate": "находиться (прош. мн.)",
        "starai": "находиться (будущ.)", "stara": "находиться (будущ.)",
        "staro": "находиться (будущ.)", "starei": "находиться (усл.)",
        # dare (давать)
        "do": "давать", "dai": "давать", "danno": "давать (мн.)",
        "dava": "давать (прош.)", "davo": "давать (прош.)",
        "dara": "дать (будущ.)", "daro": "дать (будущ.)",
        # vedere (видеть)
        "vedo": "видеть", "vede": "видеть", "vedi": "видеть",
        "vediamo": "видеть (мн.)", "vedevi": "видеть (прош.)",
        "vedro": "увидеть (будущ.)",
        # other common verbs
        "credo": "верить/думать", "credi": "верить", "crede": "верить",
        "penso": "думать", "pensi": "думать",
        "immagino": "представлять", "suppongo": "предполагаю",
        "cerchiamo": "искать (мн.)",
        "chiamami": "позови меня", "corri": "беги",
        "bevi": "пей", "esci": "выходи",
        "lascialo": "оставь его",
    }
    if w in IRREGULAR:
        return IRREGULAR[w]

    # Pattern matching for regular verbs
    # -are verbs
    if w.endswith("iamo"):
        stem = w[:-4]
        for end, inf in [("er","are"),("ir","ire")]:
            candidate = stem + inf
            if candidate in BASE_VERBS:
                return BASE_VERBS[candidate] + " (мн.)"
        return None
    if w.endswith("eranno"):
        stem = w[:-6]
        candidate = stem + "are"
        if candidate in BASE_VERBS:
            return BASE_VERBS[candidate] + " (будущ. мн.)"
        return None
    if w.endswith("aranno"):
        stem = w[:-6]
        candidate = stem + "are"
        if candidate in BASE_VERBS:
            return BASE_VERBS[candidate] + " (будущ. мн.)"
        return None
    if w.endswith("erai"):
        stem = w[:-4]
        for end, inf in [("er","are"),("ir","ire")]:
            candidate = stem + inf
            if candidate in BASE_VERBS:
                return BASE_VERBS[candidate] + " (будущ.)"
        return None
    if w.endswith("ete"):
        stem = w[:-3]
        candidate = stem + "are"
        if candidate in BASE_VERBS:
            return BASE_VERBS[candidate] + " (мн.)"
        return None

    # -ere/-ire verbs
    if w.endswith("irai"):
        stem = w[:-4]
        candidate = stem + "ire"
        if candidate in BASE_VERBS:
            return BASE_VERBS[candidate] + " (будущ.)"
        return None
    if w.endswith("iro"):
        stem = w[:-3]
        for inf in ["ire","are","ere"]:
            candidate = stem + inf
            if candidate in BASE_VERBS:
                return BASE_VERBS[candidate] + " (будущ.)"
        return None

    # Future: -ero, -erà, -arà
    for suffix in ["ero","era","ara","ara"]:
        if w.endswith(suffix):
            stem = w[:-3]
            for inf in ["are","ere","ire"]:
                candidate = stem + inf
                if candidate in BASE_VERBS:
                    return BASE_VERBS[candidate] + " (будущ.)"

    # Past: -avi, -evi, -ivi
    for suffix in ["avi","eva","evi","ivo","ivi"]:
        if w.endswith(suffix):
            stem = w[:-3]
            for inf in ["are","ere","ire"]:
                candidate = stem + inf
                if candidate in BASE_VERBS:
                    return BASE_VERBS[candidate] + " (прош.)"

    # Conditional: -erei, -irei, -aresti, -eresti
    for suffix in ["erei","irei","arei"]:
        if w.endswith(suffix):
            stem = w[:-4]
            for inf in ["are","ere","ire"]:
                candidate = stem + inf
                if candidate in BASE_VERBS:
                    return BASE_VERBS[candidate] + " (усл.)"
    for suffix in ["eresti","iresti","aresti"]:
        if w.endswith(suffix):
            stem = w[:-5]
            for inf in ["are","ere","ire"]:
                candidate = stem + inf
                if candidate in BASE_VERBS:
                    return BASE_VERBS[candidate] + " (усл.)"

    # Subjunctive: -assi, -essi, -issi
    for suffix in ["assi","essi","issi"]:
        if w.endswith(suffix):
            stem = w[:-4]
            for inf in ["are","ere","ire"]:
                candidate = stem + inf
                if candidate in BASE_VERBS:
                    return BASE_VERBS[candidate] + " (сосл.)"

    # Imperative: -a (from -are), -i (from -ere/-ire)
    # Too ambiguous, skip

    # Present: -o, -i, -a
    if w.endswith("o") and len(w) > 2:
        stem = w[:-1]
        for inf in ["are","ere","ire"]:
            candidate = stem + inf
            if candidate in BASE_VERBS:
                return BASE_VERBS[candidate]
    if w.endswith("i") and len(w) > 2:
        stem = w[:-1]
        for inf in ["are","ere","ire"]:
            candidate = stem + inf
            if candidate in BASE_VERBS:
                return BASE_VERBS[candidate]

    return None


def _try_adj_normalize(word):
    """Try to normalize an adjective to its masculine singular form and look up."""
    w = word.lower()
    # -a ending → try -o (fem → masc)
    if w.endswith("a") and len(w) > 2:
        masc = w[:-1] + "o"
        if (masc, "ADJ") in EXACT:
            return EXACT[(masc, "ADJ")]
    # -i ending → try -o (plural → singular)
    if w.endswith("i") and len(w) > 2:
        masc = w[:-1] + "o"
        if (masc, "ADJ") in EXACT:
            return EXACT[(masc, "ADJ")]
    # -e ending → might already be base form
    # -issime → try -issimo
    if w.endswith("issime"):
        base = w[:-2] + "o"
        if (base, "ADJ") in EXACT:
            return EXACT[(base, "ADJ")]
    return None


def smart_translate(original, lemma, pos):
    """Try to translate using patterns when exact match fails."""
    orig = original.strip().rstrip("'").rstrip("`").rstrip("’")
    low = orig.lower()

    # Handle truncated forms with apostrophe: dov' → dovere, com' → come, dev' → dovere
    if original.endswith("'") or original.endswith("’"):
        truncated = original.rstrip("'").rstrip("’").lower()
        TRUNCATED_MAP = {
            "dov": "должен (сокр.)", "com": "как (сокр.)", "dev": "должен (сокр.)",
            "nient": "ничего (сокр.)", "un": "один (сокр.)", "tutt": "весь (сокр.)",
            "qual": "какой (сокр.)", "buon": "хороший (сокр.)",
            "gliel": "ему это (сокр.)", "m": "мне (сокр.)",
            "senz": "без (сокр.)", "mezz": "половина (сокр.)",
            "vent": "двадцать (сокр.)", "trent": "тридцать (сокр.)",
            "neanch": "даже не (сокр.)", "sott": "под (сокр.)",
            "brav": "хороший (сокр.)", "bell": "красивый (сокр.)",
            "finch": "пока (сокр.)",
        }
        if truncated in TRUNCATED_MAP:
            return TRUNCATED_MAP[truncated]

    # 2. For NOUNS: proper names (capitalized)
    if pos == "NOUN" and orig[0:1].isupper():
        return orig  # Keep name as-is
    # Short nouns
    if pos == "NOUN" and len(orig) <= 2:
        return orig

    # 3. For VERBs
    if pos == "VERB":
        # Check if it's a name mislabeled as verb
        if orig[0:1].isupper() and orig.isalpha() and len(orig) > 2:
            return orig
        # Interjections
        if low in ("uh","ehm","ia","ahi","caspita","evviva","whoa","wow","hey","ah","gia"):
            return low

        # Try lemma-based verb+clitic first
        lem_parts = lemma.split()
        base_verb = lem_parts[0] if lem_parts else lemma
        if base_verb in BASE_VERBS:
            ru_verb = BASE_VERBS[base_verb]
            clitic_rus = []
            for cl in lem_parts[1:]:
                if cl in CLITICS:
                    clitic_rus.append(CLITICS[cl])
            if clitic_rus:
                return f"{ru_verb} ({', '.join(clitic_rus)})"
            return ru_verb

        # Try conjugation pattern matching on original
        result = _try_verb_conjugation(orig)
        if result:
            return result

        # Try with original -lo, -la, -mi, -ti, -ci, -vi, -ne stripped
        for clitic in ["ci","vi","ne","mi","ti","lo","la","li","le"]:
            if orig.endswith(clitic) and len(orig) > len(clitic) + 2:
                stem = orig[:-len(clitic)]
                result = _try_verb_conjugation(stem)
                if result:
                    return f"{result} ({CLITICS.get(clitic, clitic)})"
                # Try stem+are/ere/ire
                for inf in ["are","ere","ire"]:
                    candidate = stem + inf
                    if candidate in BASE_VERBS:
                        return f"{BASE_VERBS[candidate]} ({CLITICS.get(clitic, clitic)})"

        # Try stripping -rsi, -rmi, -rti, -rci, -rvi, -rne for infinitive+clitic
        for suffix in ["rsi","rmi","rti","rci","rvi","rne"]:
            if orig.endswith(suffix) and len(orig) > len(suffix) + 2:
                stem = orig[:-len(suffix)]
                for inf in ["are","ere","ire"]:
                    candidate = stem + inf
                    if candidate in BASE_VERBS:
                        cl = suffix[1:]  # si, mi, ti, ci, vi, ne
                        return f"{BASE_VERBS[candidate]} ({CLITICS.get(cl, cl)})"

        # Try stripping -tela, -telo, -telo, -temi, -teti, -teci
        for suffix in ["tela","telo","temi","teti","teci","tevi","teli","tele"]:
            if orig.endswith(suffix) and len(orig) > len(suffix) + 2:
                stem = orig[:-len(suffix)]
                for inf in ["are","ere","ire"]:
                    candidate = stem + inf
                    if candidate in BASE_VERBS:
                        return BASE_VERBS[candidate]

        # Double clitic: andartene → andare + ti + ne
        if "tene" in orig[-5:]:
            stem = orig[:-5]
            for inf in ["are","ere","ire"]:
                candidate = stem + inf
                if candidate in BASE_VERBS:
                    return f"{BASE_VERBS[candidate]} (отсюда)"
        if "cene" in orig[-4:]:
            stem = orig[:-4]
            for inf in ["are","ere","ire"]:
                candidate = stem + inf
                if candidate in BASE_VERBS:
                    return f"{BASE_VERBS[candidate]} (отсюда)"
        if "vene" in orig[-4:]:
            stem = orig[:-4]
            for inf in ["are","ere","ire"]:
                candidate = stem + inf
                if candidate in BASE_VERBS:
                    return f"{BASE_VERBS[candidate]} (прочь)"
        if "mene" in orig[-4:]:
            stem = orig[:-4]
            for inf in ["are","ere","ire"]:
                candidate = stem + inf
                if candidate in BASE_VERBS:
                    return f"{BASE_VERBS[candidate]} (отсюда)"

        # Gerund: -ando, -endo
        if orig.endswith("ando"):
            stem = orig[:-5]
            candidate = stem + "are"
            if candidate in BASE_VERBS:
                return BASE_VERBS[candidate] + " (герундий)"
        if orig.endswith("endo"):
            stem = orig[:-4]
            for inf in ["ere","ire"]:
                candidate = stem + inf
                if candidate in BASE_VERBS:
                    return BASE_VERBS[candidate] + " (герундий)"

        # Past participle used as verb
        if orig.endswith("ato") and len(orig) > 4:
            stem = orig[:-3]
            candidate = stem + "are"
            if candidate in BASE_VERBS:
                return BASE_VERBS[candidate] + " (прош.)"
        if orig.endswith("uto") and len(orig) > 4:
            stem = orig[:-3]
            candidate = stem + "ere"
            if candidate in BASE_VERBS:
                return BASE_VERBS[candidate] + " (прош.)"
        if orig.endswith("ito") and len(orig) > 4:
            stem = orig[:-3]
            for inf in ["ire","ere"]:
                candidate = stem + inf
                if candidate in BASE_VERBS:
                    return BASE_VERBS[candidate] + " (прош.)"

    # 4. For ADJs
    if pos == "ADJ":
        # Proper names mislabeled
        if orig[0:1].isupper() and orig.isalpha() and len(orig) > 2:
            return orig
        # Interjections/exclamations
        if low in ("shh","aah","hei","oddio"):
            return low

        # Try normalizing fem/plural → masc singular
        result = _try_adj_normalize(orig)
        if result:
            return result

        # Try in EXACT for the adj itself
        if (low, "ADJ") in EXACT:
            return EXACT[(low, "ADJ")]

    # 5. For ADV
    if pos == "ADV":
        ADV_EXTRA = {
            "però": "но/однако", "cioè": "то есть", "laggiù": "вон там",
            "eccolo": "вот он", "eccoci": "вот мы", "eccola": "вот она",
            "do": "до (нота)", "tantissimo": "очень много",
            "domattina": "завтра утром", "lassù": "наверху",
            "sodo": "крепко", "project": "проект", "fango": "в грязи",
            "shaw": "Шо", "avery": "Эвери", "clyde": "Клайд",
            "orchestra": "оркестр", "oriente": "восток",
            "calvin": "Кальвин", "alpha": "альфа",
            "ammettilo": "признай это", "be": "ну",
            "are": "аре", "logan": "Логан",
            "tutt": "совсем", "sin": "до/с",
        }
        if low in ADV_EXTRA:
            return ADV_EXTRA[low]
        # Try to match with accent variants
        ACCENT_MAP = {"e":"è","a":"à","i":"ì","o":"ò","u":"ù"}
        for char, accented in ACCENT_MAP.items():
            variant = low.replace(char, accented)
            if (variant, "ADV") in EXACT:
                return EXACT[(variant, "ADV")]

    # 6. Fallback: return original
    return f"[{original}]"

# ─── Italian adjective agreement ─────────────────────────────────────────────

def adjective_forms(lemma):
    lem = lemma.strip().rstrip("'")
    # Handle special cases
    if lem in ("blu", "roi", "ros", "beige", "pocho"):
        return lem, lem, lem, lem
    if lem.endswith("ista"):
        return lem, lem, lem[:-2] + "isti", lem[:-2] + "iste"
    if lem.endswith("e") and not lem.endswith(("ale","ile","ose","are","ere","ire","nte")):
        return lem, lem, lem[:-1] + "i", lem[:-1] + "i"
    if lem.endswith("o"):
        stem = lem[:-1]
        return lem, stem + "a", stem + "i", stem + "e"
    return lem, lem, lem, lem

# ─── Resolve translation ─────────────────────────────────────────────────────

def get_translation(rank, original, lemma, pos):
    # 1. Exact match by (original, POS)
    key = (original, pos)
    if key in EXACT:
        return EXACT[key]
    # 2. Try lowercase
    key2 = (original.lower(), pos)
    if key2 in EXACT:
        return EXACT[key2]
    # 3. Try by lemma
    key3 = (lemma, pos)
    if key3 in EXACT:
        return EXACT[key3]
    # 4. Smart fallback
    return smart_translate(original, lemma, pos)

# ─── Write CSVs ──────────────────────────────────────────────────────────────

nouns, verbs, adjs, advs = [], [], [], []
missing = []

for rank, original, lemma, pos in entries:
    ru = get_translation(rank, original, lemma, pos)
    if ru.startswith("[") and ru.endswith("]"):
        missing.append((rank, pos, original, lemma))
    if pos == "NOUN":
        nouns.append((rank, lemma, "", ru))
    elif pos == "VERB":
        verbs.append((rank, lemma, "", ru))
    elif pos == "ADJ":
        msg = lemma
        fsg, mpl, fpl = adjective_forms(lemma)[1:]
        adjs.append((rank, lemma, msg, fsg, mpl, fpl, "", ru))
    elif pos == "ADV":
        advs.append((rank, lemma, "", "", ru, ""))

def write_csv(path, header, rows):
    with open(path, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(header)
        for row in rows:
            w.writerow(row)

write_csv(os.path.join(BASE, "v2_new_nouns.csv"),
    ["rank","noun","collocations","ru"], nouns)
write_csv(os.path.join(BASE, "v2_new_verbs.csv"),
    ["rank","verb","collocations","ru"], verbs)
write_csv(os.path.join(BASE, "v2_new_adjectives.csv"),
    ["rank","adjective","msg","fsg","mpl","fpl","collocations","ru"], adjs)
write_csv(os.path.join(BASE, "v2_new_adverbs.csv"),
    ["rank","adverb","comparative","superlative","ru","collocations"], advs)

print(f"NOUNS: {len(nouns)}")
print(f"VERBS: {len(verbs)}")
print(f"ADJ:   {len(adjs)}")
print(f"ADV:   {len(advs)}")
print(f"TOTAL: {len(nouns)+len(verbs)+len(adjs)+len(advs)}")
print(f"MISSING: {len(missing)}")
if missing:
    print("--- Missing translations (first 50) ---")
    for rank, pos, orig, lem in missing[:50]:
        print(f"  rank={rank} {pos} orig={orig} lemma={lem}")
    if len(missing) > 50:
        print(f"  ... and {len(missing)-50} more")
    # Write full missing list to file
    with open(os.path.join(BASE, "still_missing.txt"), "w", encoding="utf-8") as mf:
        for rank, pos, orig, lem in missing:
            mf.write(f"{rank}\t{pos}\t{orig}\t{lem}\n")
