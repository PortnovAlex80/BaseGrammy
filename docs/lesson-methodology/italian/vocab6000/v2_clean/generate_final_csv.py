"""
Step 3: Generate final drill CSV files from v2_lemmas_clean.txt
- Exclude words already in existing drills
- Take top N by rank to reach 6000 total
- Generate 4 CSV files with Russian translations
"""
import sys
import os
import csv
import re

sys.stdout.reconfigure(encoding='utf-8')

BASE = os.path.dirname(__file__)

# ============================================================
# RUSSIAN TRANSLATION DICTIONARY
# Comprehensive Italian -> Russian mapping
# ============================================================
IT_RU = {
    # === HIGH FREQUENCY NOUNS (rank 1-500) ===
    'cosa': 'вещь/дело', 'uomo': 'мужчина/человек', 'anno': 'год',
    'giorno': 'день', 'volta': 'раз', 'mondo': 'мир', 'vita': 'жизнь',
    'uomo': 'мужчина', 'donna': 'женщина', 'figlio': 'сын', 'figlia': 'дочь',
    'amico': 'друг', 'amica': 'подруга', 'madre': 'мать', 'padre': 'отец',
    'fratello': 'брат', 'sorella': 'сестра', 'marito': 'муж', 'moglie': 'жена',
    'bambino': 'ребёнок', 'bambina': 'девочка', 'ragazzo': 'парень/мальчик',
    'ragazza': 'девушка', 'nome': 'имя', 'casa': 'дом', 'scuola': 'школа',
    'lavoro': 'работа', 'tempo': 'время', 'modo': 'способ', 'parte': 'часть',
    'mano': 'рука', 'occhio': 'глаз', 'testa': 'голова', 'cuore': 'сердце',
    'viso': 'лицо', 'porta': 'дверь', 'faccia': 'лицо', 'notte': 'ночь',
    'acqua': 'вода', 'aria': 'воздух', 'luce': 'свет', 'terra': 'земля',
    'piazza': 'площадь', 'strada': 'улица', 'macchina': 'машина',
    'libro': 'книга', 'parola': 'слово', 'storia': 'история/рассказ',
    'canzone': 'песня', 'film': 'фильм', 'musica': 'музыка',
    'problema': 'проблема', 'domanda': 'вопрос', 'risposta': 'ответ',
    'città': 'город', 'paese': 'страна/деревня', 'regalo': 'подарок',
    'soldi': 'деньги', 'prezzo': 'цена', 'numero': 'число/номер',
    'telefono': 'телефон', 'famiglia': 'семья', 'cugino': 'двоюродный брат',
    'zio': 'дядя', 'zia': 'тётя', 'nonno': 'дедушка', 'nonna': 'бабушка',
    'figlio': 'сын', 'persona': 'человек/личность', 'gente': 'люди',
    'amore': 'любовь', 'guerra': 'война', 'pace': 'мир',
    'forza': 'сила', 'diritto': 'право', 'legge': 'закон',
    'petto': 'грудь', 'sangue': 'кровь', 'pelle': 'кожа',
    'dito': 'палец', 'piede': 'нога (ступня)', 'braccio': 'рука (от плеча)',
    'spalla': 'плечо', 'ginocchio': 'колено', 'dente': 'зуб',
    'letto': 'кровать', 'tavolo': 'стол', 'sedia': 'стул',
    'finestra': 'окно', 'muro': 'стена', 'pavimento': 'пол',
    'tetto': 'крыша', 'camera': 'комната', 'cucina': 'кухня',
    'bagno': 'ванная', 'scala': 'лестница', 'giardino': 'сад',
    'cibo': 'еда', 'pane': 'хлеб', 'vino': 'вино', 'latte': 'молоко',
    'carne': 'мясо', 'pesce': 'рыба', 'frutta': 'фрукты',
    'verdura': 'овощи', 'zucchero': 'сахар', 'sale': 'соль',
    'colazione': 'завтрак', 'pranzo': 'обед', 'cena': 'ужин',
    'caffè': 'кофе', 'tè': 'чай', 'birra': 'пиво',
    ' Sole': 'солнце', 'luna': 'луна', 'stella': 'звезда',
    'mare': 'море', 'fiume': 'река', 'lago': 'озеро',
    'montagna': 'гора', 'bosco': 'лес', 'campo': 'поле',
    'fiore': 'цветок', 'albero': 'дерево', 'erba': 'трава',
    'animale': 'животное', 'cane': 'собака', 'gatto': 'кот',
    'cavallo': 'лошадь', 'uccello': 'птица', 'pesce': 'рыба',
    'vestito': 'одежда/платье', 'camicia': 'рубашка', 'pantaloni': 'брюки',
    'scarpa': 'ботинок', 'cappello': 'шляпа', 'borsa': 'сумка',
    'penna': 'ручка', 'foglio': 'лист', 'quaderno': 'тетрадь',
    'chiave': 'ключ', 'orologio': 'часы', 'specchio': 'зеркало',
    'coltello': 'нож', 'forchetta': 'вилка', 'cucchiaio': 'ложка',
    'piatto': 'тарелка/блюдо', 'bicchiere': 'стакан', 'bottiglia': 'бутылка',
    'gioco': 'игра', 'sport': 'спорт', 'partita': 'матч/партия',
    'chiesa': 'церковь', 'ospedale': 'больница', 'farmacia': 'аптека',
    'banca': 'банк', 'ufficio': 'офис', 'negozio': 'магазин',
    'ristorante': 'ресторан', 'bar': 'бар', 'albergo': 'гостиница',
    'stazione': 'станция', 'aeroporto': 'аэропорт', 'porto': 'порт',
    'biglietto': 'билет', 'valigia': 'чемодан', 'viaggio': 'путешествие',
    'colore': 'цвет', 'suono': 'звук', 'odore': 'запах',
    'sapore': 'вкус', 'forma': 'форма', 'dimensione': 'размер',
    'peso': 'вес', 'altezza': 'высота', 'larghezza': 'ширина',
    'profondità': 'глубина', 'lunghezza': 'длина', 'distanza': 'расстояние',
    'centro': 'центр', 'inizio': 'начало', 'fine': 'конец',
    'punto': 'точка/пункт', 'linea': 'линия', 'cerchio': 'круг',
    'quadrato': 'квадрат', 'triangolo': 'треугольник',
    'errore': 'ошибка', 'verità': 'правда', 'menzogna': 'ложь',
    'sogno': 'мечта/сон', 'ricordo': 'воспоминание', 'pensiero': 'мысль',
    'idea': 'идея', 'progetto': 'проект', 'piano': 'план',
    'speranza': 'надежда', 'paura': 'страх', 'gioia': 'радость',
    'tristezza': 'грусть', 'rabbia': 'злость', 'noia': 'скука',
    'colpa': 'вина', 'merito': 'заслуга', 'dovere': 'долг/обязанность',
    'successo': 'успех', 'fallimento': 'провал',
    'giustizia': 'справедливость', 'libertà': 'свобода',
    'estate': 'лето', 'inverno': 'зима', 'primavera': 'весна', 'autunno': 'осень',
    'lunedì': 'понедельник', 'martedì': 'вторник', 'mercoledì': 'среда',
    'giovedì': 'четверг', 'venerdì': 'пятница', 'sabato': 'суббота', 'domenica': 'воскресенье',
    'gennaio': 'январь', 'febbraio': 'февраль', 'marzo': 'март',
    'aprile': 'апрель', 'maggio': 'май', 'giugno': 'июнь',
    'luglio': 'июль', 'agosto': 'август', 'settembre': 'сентябрь',
    'ottobre': 'октябрь', 'novembre': 'ноябрь', 'dicembre': 'декабрь',
    'mese': 'месяц', 'settimana': 'неделя', 'ora': 'час/время',
    'minuto': 'минута', 'secondo': 'секунда/второй',
    'mattina': 'утро', 'pomeriggio': 'день (время суток)', 'sera': 'вечер',
    'nord': 'север', 'sud': 'юг', 'est': 'восток', 'ovest': 'запад',
    'dio': 'Бог', 'chiesa': 'церковь', 'anima': 'душа',
    'piacere': 'удовольствие', 'dispiacere': 'сожаление',
    'aiuto': 'помощь', 'consiglio': 'совет', 'promessa': 'обещание',
    'regola': 'правило', 'eccezione': 'исключение',
    'genere': 'род/жанр/вид', 'specie': 'вид/сорт',
    'limite': 'предел/граница', 'confine': 'граница',
    'evento': 'событие', 'situazione': 'ситуация',
    'esperienza': 'опыт', 'conoscenza': 'знание',
    'ricerca': 'поиск/исследование', 'scoperta': 'открытие',
    'macchina': 'машина', 'automobile': 'автомобиль',
    'bici': 'велосипед', 'bicicletta': 'велосипед',
    'aereo': 'самолёт', 'nave': 'корабль', 'treno': 'поезд',
    'autobus': 'автобус', 'metro': 'метро', 'metropolitana': 'метро',
    'taxi': 'такси', 'moto': 'мотоцикл',
    'dottore': 'доктор', 'professore': 'профессор', 'avvocato': 'адвокат',
    'ingegnere': 'инженер', 'architetto': 'архитектор',
    'artista': 'художник/артист', 'attore': 'актёр', 'attrice': 'актриса',
    'scrittore': 'писатель', 'giornalista': 'журналист',
    'cuoco': 'повар', 'cameriere': 'официант',
    'autista': 'водитель', 'pilota': 'пилот',
    'poliziotto': 'полицейский', 'soldato': 'солдат',
    're': 'король', 'regina': 'королева', 'principe': 'принц',
    'principessa': 'принцесса', 'imperatore': 'император',
    'presidente': 'президент', 'ministro': 'министр',
    'vicino': 'сосед', 'nemico': 'враг', 'alleato': 'союзник',
    'cliente': 'клиент', 'padrone': 'хозяин',
    'ospite': 'гость', 'turista': 'турист',
    'maestro': 'учитель/мастер', 'allievo': 'ученик',
    'eroe': 'герой', 'vittima': 'жертва',
    'segno': 'знак', 'simbolo': 'символ', 'numero': 'число/номер',
    'lettera': 'буква/письмо', 'frase': 'фраза/предложение',
    'pagina': 'страница', 'capitolo': 'глава',
    'titolo': 'заголовок/название', 'argomento': 'аргумент/тема',
    'esempio': 'пример', 'risultato': 'результат',
    'motivo': 'причина/мотив', 'causa': 'причина',
    'effetto': 'эффект/результат', 'conseguenza': 'последствие',
    'condizione': 'условие', 'possibilità': 'возможность',
    'necessità': 'необходимость', 'importanza': 'важность',
    'differenza': 'разница/различие', 'somiglianza': 'сходство',
    'magia': 'магия', 'incantesimo': 'заклинание',
    'strega': 'ведьма', 'mostro': 'монстр', 'drago': 'дракон',

    # === HIGH FREQUENCY VERBS ===
    'essere': 'быть', 'avere': 'иметь', 'fare': 'делать',
    'dire': 'говорить/сказать', 'andare': 'идти/ехать', 'venire': 'приходить',
    'volere': 'хотеть', 'potere': 'мочь', 'dovere': 'долженствовать',
    'sapere': 'знать/уметь', 'stare': 'находиться/стоять',
    'dare': 'давать', 'vedere': 'видеть', 'mangiare': 'есть/кушать',
    'parlare': 'говорить', 'prendere': 'брать', 'trovare': 'находить',
    'pensare': 'думать', 'credere': 'верить', 'sentire': 'чувствовать/слышать',
    'vivere': 'жить', 'morire': 'умирать', 'nascere': 'рождаться',
    'crescere': 'расти', 'diventare': 'становиться',
    'lavorare': 'работать', 'studiare': 'учить/изучать',
    'scrivere': 'писать', 'leggere': 'читать', 'imparare': 'учить/узнавать',
    'insegnare': 'учить/преподавать', 'capire': 'понимать',
    'conoscere': 'знать/быть знакомым', 'ricordare': 'помнить/напоминать',
    'dimenticare': 'забывать', 'scegliere': 'выбирать', 'decidere': 'решать',
    'cominciare': 'начинать', 'iniziare': 'начинать', 'finire': 'заканчивать',
    'terminare': 'завершать', 'continuare': 'продолжать',
    'cambiare': 'менять', 'lasciare': 'оставлять/покидать',
    'portare': 'носить/приносить', 'mandare': 'посылать',
    'ricevere': 'получать', 'accettare': 'принимать', 'rifiutare': 'отказывать',
    'comprare': 'покупать', 'vendere': 'продавать', 'pagare': 'платить',
    'spendere': 'тратить', 'costare': 'стоить',
    'aprire': 'открывать', 'chiudere': 'закрывать',
    'entrare': 'входить', 'uscire': 'выходить', 'passare': 'проходить',
    'tornare': 'возвращаться', 'ritornare': 'возвращаться',
    'arrivare': 'прибывать', 'partire': 'уезжать/отправляться',
    'viaggiare': 'путешествовать', 'guidare': 'вести/водить',
    'correre': 'бежать', 'camminare': 'идти пешком', 'saltare': 'прыгать',
    'volare': 'летать', 'nuotare': 'плавать',
    'dormire': 'спать', 'svegliarsi': 'просыпаться', 'addormentarsi': 'засыпать',
    'sedersi': 'садиться', 'alzarsi': 'вставать', 'sdraiarsi': 'ложиться',
    'chiamare': 'звать/называть', 'rispondere': 'отвечать',
    'domandare': 'спрашивать', 'chiedere': 'просить/спрашивать',
    'aiutare': 'помогать', 'bisogna': 'нужно/необходимо',
    'provare': 'пробовать/испытывать', 'sembrare': 'казаться',
    'parere': 'казаться', 'piacere': 'нравиться',
    'bastare': 'хватать/достаточно', 'occorrere': 'требоваться',
    'servire': 'служить', 'funzionare': 'работать (о механизме)',
    'significare': 'означать', 'rappresentare': 'представлять',
    'contenere': 'содержать', 'appartenere': 'принадлежать',
    'esistere': 'существовать', 'sembrare': 'казаться',
    'succedere': 'случаться', 'accadere': 'происходить',
    'avvenire': 'происходить', 'capitare': 'случаться',
    'dividere': 'делить', 'unire': 'объединять',
    'mescolare': 'смешивать', 'riempire': 'наполнять',
    'vuotare': 'опустошать', 'spegnere': 'тушить/выключать',
    'accendere': 'зажигать/включать', 'bruciare': 'гореть/жечь',
    'scaldare': 'нагревать', 'raffreddare': 'охлаждать',
    'pulire': 'чистить', 'sporcare': 'пачкать',
    'lavare': 'мыть', 'asciugare': 'сушить',
    'costruire': 'строить', 'distruggere': 'разрушать',
    'rompere': 'ломать', 'riparare': 'чинить',
    'attaccare': 'прикреплять/атаковать', 'difendere': 'защищать',
    'vincere': 'побеждать', 'perdere': 'терять/проигрывать',
    'lottare': 'бороться', 'combattere': 'сражаться',
    'sparare': 'стрелять', 'uccidere': 'убивать',
    'salvare': 'спасать', 'proteggere': 'защищать',
    'creare': 'создавать', 'produrre': 'производить',
    'sviluppare': 'развивать', 'migliorare': 'улучшать',
    'peggiorare': 'ухудшать', 'aumentare': 'увеличивать',
    'diminuire': 'уменьшать', 'crescere': 'расти',
    'calare': 'снижаться', 'salire': 'подниматься',
    'scendere': 'спускаться', 'ritornare': 'возвращаться',
    'avvicinarsi': 'приближаться', 'allontanarsi': 'удаляться',
    'raggiungere': 'достигать', 'superare': 'преодолевать',
    'evitare': 'избегать', 'cercare': 'искать',
    'nascondere': 'прятать', 'scoprire': 'открывать/обнаруживать',
    'mostrare': 'показывать', 'vedere': 'видеть', 'guardare': 'смотреть',
    'ascoltare': 'слушать', 'udire': 'слышать',
    'annusare': 'нюхать', 'toccare': 'трогать',
    'assaggiare': 'пробовать на вкус', 'baciare': 'целовать',
    'abbracciare': 'обнимать', 'carezzare': 'гладить',
    'picchiare': 'бить', 'graffiare': 'царапать',
    'ridere': 'смеяться', 'piangere': 'плакать', 'sorridere': 'улыбаться',
    'gridare': 'кричать', 'sussurrare': 'шептать',
    'cantare': 'петь', 'suonare': 'играть (музыку)',
    'danzare': 'танцевать', 'ballare': 'танцевать',
    'disegnare': 'рисовать', 'dipingere': 'писать (красками)',
    'fotografare': 'фотографировать', 'recitare': 'играть (роль)',
    'giocare': 'играть', 'vincere': 'побеждать',
    'partecipare': 'участвовать', 'organizzare': 'организовывать',
    'festeggiare': 'праздновать', 'celebrare': 'праздновать',
    'regalare': 'дарить', 'offrire': 'предлагать',
    'negare': 'отрицать', 'ammettere': 'признавать',
    'permettere': 'позволять', 'proibire': 'запрещать',
    'avvertire': 'предупреждать', 'minacciare': 'угрожать',
    'punire': 'наказывать', 'perdonare': 'прощать',
    'ringraziare': 'благодарить', 'scusare': 'извинять',
    'lamentarsi': 'жаловаться', 'criticare': 'критиковать',
    'elogiare': 'хвалить', 'incoraggiare': 'поощрять',
    'sconsigliare': 'отговаривать', 'consigliare': 'советовать',
    'convincere': 'убеждать', 'persuadere': 'убеждать',
    'tentare': 'пытаться', 'riuscire': 'преуспевать',
    'fallire': 'терпеть неудачу', 'abbandonare': 'бросать/покидать',
    'continuare': 'продолжать', 'smettere': 'прекращать',
    'ripetere': 'повторять', 'copiare': 'копировать',
    'tradurre': 'переводить', 'spiegare': 'объяснять',
    'dimostrare': 'демонстрировать', 'provare': 'пробовать/доказывать',
    'ritenere': 'считать/полагать', 'affermare': 'утверждать',
    'negare': 'отрицать', 'dubitare': 'сомневаться',
    'sperare': 'надеяться', 'desiderare': 'желать',
    'preferire': 'предпочитать', 'odiare': 'ненавидеть',
    'temere': 'бояться', 'godere': 'наслаждаться',
    'soffrire': 'страдать', 'piangere': 'плакать',
    'lamentare': 'жаловаться', 'preoccuparsi': 'беспокоиться',
    'rilassarsi': 'расслабляться', 'divertirsi': 'веселиться',
    'annoiarsi': 'скучать', 'innamorarsi': 'влюбляться',
    'odiare': 'ненавидеть', 'rispettare': 'уважать',
    'apprezzare': 'ценить', 'ammirare': 'восхищаться',
    'imparare': 'учить', 'insegnare': 'преподавать',
    'esercitarsi': 'тренироваться', 'praticare': 'практиковать',
    'viaggiare': 'путешествовать', 'esplorare': 'исследовать',
    'visitare': 'посещать', 'fermarsi': 'останавливаться',
    'restare': 'оставаться', 'abitare': 'жить/проживать',
    'trasferirsi': 'переезжать', 'fuggire': 'бежать/убегать',
    'seguire': 'следовать', 'guidare': 'вести',
    'accompagnare': 'сопровождать', 'incontrare': 'встречать',
    'presentare': 'представлять', 'presentarsi': 'представляться',
    'salutare': 'здороваться/прощаться', 'ringraziare': 'благодарить',
    'invitare': 'приглашать', 'accogliere': 'принимать/встречать',
    'riconoscere': 'узнавать', 'ignorare': 'игнорировать',
    'sbagliare': 'ошибаться', 'correggere': 'исправлять',
    'riparare': 'чинить/исправлять', 'migliorare': 'улучшать',
    'basta': 'хватит', 'piangere': 'плакать', 'cucire': 'шить',
    'tagliare': 'резать', 'incollare': 'клеить',
    'legare': 'связывать', 'slegare': 'развязывать',
    'avvolgere': 'заворачивать', 'svolgere': 'разворачивать',
    'coprire': 'покрывать', 'scoprire': 'открывать/обнаруживать',
    'mettere': 'класть/ставить', 'togliere': 'убирать/снимать',
    'posare': 'класть', 'sollevare': 'поднимать',
    'spingere': 'толкать', 'tirare': 'тянуть',
    'stringere': 'сжимать', 'allentare': 'ослаблять',
    'torcere': 'крутить', 'piegare': 'сгибать',
    'raddrizzare': 'выпрямлять', 'girare': 'поворачивать',
    'voltare': 'поворачивать', 'rovesciare': 'опрокидывать',
    'scuotere': 'трясти', 'battere': 'бить',
    'grattare': ' чесать', 'strofinare': 'тереть',
    'bagnare': 'мочить', 'inzuppare': 'вымачивать',
    'asciugare': 'сушить', 'gocciolare': 'капать',
    'versare': 'лить', 'spargere': 'рассыпать',
    'raccogliere': 'собирать', 'spiegare': 'объяснять',
    'piegare': 'сгибать', 'riporre': 'убирать',
    'sistemare': 'устроить/расставить', 'ordinare': 'заказывать',
    'preparare': 'готовить', 'cucinare': 'готовить еду',
    'cuocere': 'варить/печь', 'friggere': 'жарить',
    'bollire': 'кипятить', 'arrostire': 'жарить (на огне)',
    'congelare': 'замораживать', 'scongelare': 'размораживать',
    'conservare': 'сохранять', 'mantenere': 'поддерживать',
    'garantire': 'гарантировать', 'assicurare': 'страховать/гарантировать',
    ' confermare': 'подтверждать', 'smentire': 'опровергать',
    'affermare': 'утверждать', 'dichiara': 'заявлять',
    'giurare': 'клясться', 'promettere': 'обещать',
    'scommettere': 'держать пари', 'puntare': 'ставить (на)',
    'tentare': 'пытаться', 'provare': 'пробовать',
    'sperimentare': 'экспериментировать', 'verificare': 'проверять',
    'controllare': 'контролировать', 'esaminare': 'рассматривать',
    'analizzare': 'анализировать', 'valutare': 'оценивать',
    'calcolare': 'вычислять', 'misurare': 'измерять',
    'pesare': 'взвешивать', 'contare': 'считать',
    'raggruppare': 'группировать', 'classificare': 'классифицировать',
    'catalogare': 'каталогизировать', 'elencare': 'перечислять',
    'descrivere': 'описывать', 'narrare': 'рассказывать',
    'raccontare': 'рассказывать', 'riflettere': 'размышлять',
    'meditare': 'медитировать', 'considerare': 'рассматривать',
    'immaginare': 'воображать', 'fantasticare': 'фантазировать',
    'sognare': 'мечтать/видеть сны', 'presagire': 'предчувствовать',
    'intuire': 'интуитивно понимать', 'percepire': 'воспринимать',
    'notare': 'замечать', 'osservare': 'наблюдать',
    'esprimere': 'выражать', 'comunicare': 'сообщать',
    'informare': 'информировать', 'avvisare': 'уведомлять',
    'notificare': 'уведомлять', 'avvertire': 'предупреждать',
    'consigliare': 'советовать', 'raccomandare': 'рекомендовать',
    'suggerire': 'предлагать/подсказывать', 'proporre': 'предлагать',
    'offrire': 'предлагать/угощать', 'chiedere': 'спрашивать/просить',
    'pretendere': 'требовать', 'esigere': 'требовать',
    'imporre': 'навязывать', 'comandare': 'командовать',
    'ordinare': 'приказывать/заказывать',
    'obbedire': 'подчиняться', 'ribellarsi': 'бунтовать',
    'resistere': 'сопротивляться', 'arrendersi': 'сдаваться',
    'accettare': 'принимать', 'rifiutare': 'отказывать',
    'assumere': 'принимать на работу', 'licenziare': 'увольнять',
    'guadagnare': 'зарабатывать', 'risparmiare': 'экономить',
    'investire': 'инвестировать', 'donare': 'дарить/жертвовать',
    'distribuire': 'распределять', 'condividere': 'делить/разделять',
    'partecipare': 'участвовать', 'collaborare': 'сотрудничать',
    'cooperare': 'сотрудничать', 'aiutare': 'помогать',
    'sostenere': 'поддерживать', 'supportare': 'поддерживать',
    'favorire': 'благоприятствовать', 'ostacolare': 'препятствовать',
    'impedire': 'мешать', 'interferire': 'вмешиваться',
    'disturbare': 'беспокоить', 'infastidire': 'раздражать',
    'preoccupare': 'беспокоить', 'calmare': 'успокаивать',
    'tranquillizzare': 'успокаивать', 'consolare': 'утешать',
    'incoraggiare': 'ободрять', 'motivare': 'мотивировать',
    'ispirare': 'вдохновлять', 'stimolare': 'стимулировать',
    'sorprendere': 'удивлять', 'meravigliare': 'удивлять',
    'impressionare': 'впечатлять', 'confondere': 'путать',
    'imbarazzare': 'смущать', 'umiliare': 'унижать',
    'rispettare': 'уважать', 'ammirare': 'восхищаться',
    'invidiare': 'завидовать', 'compatire': 'сочувствовать',

    # === COMMON ADJECTIVES ===
    'buono': 'хороший', 'brutto': 'некрасивый/плохой', 'bello': 'красивый',
    'grande': 'большой', 'piccolo': 'маленький', 'nuovo': 'новый',
    'vecchio': 'старый', 'giovane': 'молодой', 'lungo': 'длинный',
    'corto': 'короткий', 'alto': 'высокий', 'basso': 'низкий',
    'largo': 'широкий', 'stretto': 'узкий', 'pesante': 'тяжёлый',
    'leggero': 'лёгкий', 'grosso': 'большой/толстый', 'forte': 'сильный',
    'debole': 'слабый', 'caldo': 'горячий/тёплый', 'freddo': 'холодный',
    'duro': 'твёрдый/жёсткий', 'morbido': 'мягкий', 'liscio': 'гладкий',
    'ruvido': 'шершавый', 'umido': 'влажный', 'secco': 'сухой',
    'pulito': 'чистый', 'sporco': 'грязный', 'pieno': 'полный',
    'vuoto': 'пустой', 'aperto': 'открытый', 'chiuso': 'закрытый',
    'rotto': 'сломанный', 'intero': 'целый', 'completo': 'полный/завершённый',
    'vero': 'настоящий/верный', 'falso': 'ложный', 'giusto': 'правильный',
    'sbagliato': 'неправильный', 'esatto': 'точный', 'preciso': 'точный',
    'esatto': 'точный', 'sicuro': 'безопасный/уверенный',
    'pericoloso': 'опасный', 'difficile': 'трудный', 'facile': 'лёгкий/простой',
    'importante': 'важный', 'interessante': 'интересный',
    'noioso': 'скучный', 'divertente': 'забавный/весёлый',
    'serio': 'серьёзный', 'felice': 'счастливый', 'triste': 'грустный',
    'arrabbiato': 'злой', 'spaventato': 'испуганный',
    'sorpreso': 'удивлённый', 'preoccupato': 'обеспокоенный',
    'tranquillo': 'спокойный', 'nervoso': 'нервный',
    'stanco': 'усталый', 'riposato': 'отдохнувший',
    'malato': 'больной', 'sano': 'здоровый',
    'vivo': 'живой', 'morto': 'мёртвый',
    'ricco': 'богатый', 'povero': 'бедный',
    'caro': 'дорогой/милый', 'economico': 'дешёвый',
    'speciale': 'особенный', 'normale': 'нормальный',
    'strano': 'странный', 'comune': 'обычный/общий',
    'raro': 'редкий', ' frequente': 'частый',
    'noto': 'известный', 'sconosciuto': 'неизвестный',
    'famoso': 'знаменитый', 'popolare': 'популярный',
    'bravo': 'умелый/хороший', 'abile': 'способный',
    'intelligente': 'умный', 'stupido': 'глупый',
    'gentile': 'любезный/милый', 'educato': 'вежливый',
    'maleducato': 'невежливый', 'generoso': 'щедрый',
    'egoista': 'эгоистичный', 'buono': 'добрый',
    'cattivo': 'плохой/злой', 'onesto': 'честный',
    'disonesto': 'нечестный', 'coraggioso': 'храбрый',
    'codardo': 'трусливый', 'leale': 'верный',
    'fedele': 'верный', 'traditore': 'предательский',
    'dolce': 'сладкий/мягкий', 'amaro': 'горький',
    'salato': 'солёный', 'acido': 'кислый',
    'piccante': 'острый', 'caldo': 'горячий',
    'freddo': 'холодный', 'tiepido': 'тёплый',
    'gustoso': 'вкусный', 'saporito': 'вкусный',
    'insipido': 'пресный', 'delizioso': 'вкусный/изысканный',
    'buono': 'хороший/вкусный',
    'rosso': 'красный', 'blu': 'синий', 'giallo': 'жёлтый',
    'verde': 'зелёный', 'nero': 'чёрный', 'bianco': 'белый',
    'grigio': 'серый', 'marrone': 'коричневый',
    'arancione': 'оранжевый', 'rosa': 'розовый',
    'viola': 'фиолетовый', 'azzurro': 'голубой',
    'scuro': 'тёмный', 'chiaro': 'светлый/ясный',
    'veloce': 'быстрый', 'lento': 'медленный',
    'rapido': 'быстрый', 'immediato': 'немедленный',
    'improvviso': 'внезапный', 'graduale': 'постепенный',
    'constante': 'постоянный', 'variabile': 'переменный',
    'stesso': 'тот же/самый', 'diverso': 'разный/другой',
    'simile': 'похожий', 'uguale': 'равный/одинаковый',
    'unico': 'единственный/уникальный', 'doppio': 'двойной',
    'multiplo': 'множественный', 'singolo': 'единичный',
    'primo': 'первый', 'ultimo': 'последний',
    'prossimo': 'следующий/ближайший', 'precedente': 'предыдущий',
    'seguente': 'следующий', 'successivo': 'последующий',
    'principale': 'главный', 'secondario': 'второстепенный',
    'fondamentale': 'фундаментальный', 'essenziale': 'существенный',
    'necessario': 'необходимый', 'indispensabile': 'незаменимый',
    'utile': 'полезный', 'inutile': 'бесполезный',
    'possibile': 'возможный', 'impossibile': 'невозможный',
    'probabile': 'вероятный', 'improbabile': 'маловероятный',
    'certo': 'уверенный/верный', 'incerto': 'неуверенный',
    'ovvio': 'очевидный', 'evidente': 'очевидный',
    'nascosto': 'скрытый', 'visibile': 'видимый',
    'invisibile': 'невидимый', 'trasparente': 'прозрачный',
    'opaco': 'непрозрачный', 'luminoso': 'светлый/яркий',
    'brillante': 'блестящий', 'splendido': 'великолепный',
    'meraviglioso': 'чудесный', 'fantastico': 'фантастический',
    'incredibile': 'невероятный', 'straordinario': 'экстраординарный',
    'perfetto': 'идеальный/совершенный', 'magnifico': 'великолепный',
    'terribile': 'ужасный', 'orribile': 'отвратительный',
    'spaventoso': 'пугающий', 'pauroso': 'страшный',
    'enorme': 'огромный', 'gigantesco': 'гигантский',
    'minuscolo': 'крошечный', 'microscopico': 'микроскопический',
    'immenso': 'безграничный', 'vasto': 'обширный',
    'ampio': 'широкий', 'profondo': 'глубокий',
    'superficiale': 'поверхностный', 'piatto': 'плоский',
    'ricurvo': 'изогнутый', 'dritto': 'прямой',
    'diagonale': 'диагональный', 'orizzontale': 'горизонтальный',
    'verticale': 'вертикальный', 'parallelo': 'параллельный',
    'retto': 'прямой (угол)', 'acuto': 'острый',
    'ottuso': 'тупой', 'rotondo': 'круглый',
    'quadrato': 'квадратный', 'triangolare': 'треугольный',
    'rettangolare': 'прямоугольный', 'ovale': 'овальный',
    'sferico': 'сферический', 'cilindrico': 'цилиндрический',
    'cubo': 'куб', 'piramidale': 'пирамидальный',
    'musicale': 'музыкальный', 'artistico': 'художественный',
    'creativo': 'творческий', 'imaginativo': 'воображаемый',
    'realistico': 'реалистичный', 'ideale': 'идеальный',
    'pratico': 'практичный', 'teorico': 'теоретический',
    'concreto': 'конкретный', 'astratto': 'абстрактный',
    'logico': 'логичный', 'illogico': 'нелогичный',
    'razionale': 'рациональный', 'irrazionale': 'иррациональный',
    'sensato': 'разумный', 'assurdo': 'абсурдный',
    'materno': 'материнский', 'paterno': 'отцовский',
    'fraterno': 'братский', 'familiare': 'семейный/знакомый',
    'amichevole': 'дружеский', 'nemico': 'вражеский',
    'italiano': 'итальянский', 'straniero': 'иностранный',
    'nazionale': 'национальный', 'internazionale': 'международный',
    'locale': 'местный', 'regionale': 'региональный',
    'urbano': 'городской', 'rurale': 'сельский',
    'pubblico': 'публичный/общественный', 'privato': 'частный',
    'sociale': 'социальный', 'politico': 'политический',
    'economico': 'экономический', 'finanziario': 'финансовый',
    'commerciale': 'коммерческий', 'industriale': 'промышленный',
    'tecnologico': 'технологический', 'scientifico': 'научный',
    'culturale': 'культурный', 'storico': 'исторический',
    'tradizionale': 'традиционный', 'moderno': 'современный',
    'antico': 'древний', 'futuro': 'будущий',
    'attuale': 'нынешний/текущий', 'presente': 'настоящий/присутствующий',
    'passato': 'прошлый', 'recente': 'недавний',
    'remoto': 'отдалённый', 'prossimo': 'близкий/следующий',
    'lontano': 'далёкий', 'vicino': 'близкий/соседний',
    'interno': 'внутренний', 'esterno': 'внешний',
    'centrale': 'центральный', 'laterale': 'боковой',
    'anteriore': 'передний', 'posteriore': 'задний',
    'superiore': 'верхний/высший', 'inferiore': 'нижний/низший',
    'destro': 'правый', 'sinistro': 'левый',
    'positivo': 'положительный', 'negativo': 'отрицательный',
    'attivo': 'активный', 'passivo': 'пассивный',
    'creativo': 'творческий', 'distruttivo': 'разрушительный',
    'produttivo': 'продуктивный', 'efficace': 'эффективный',
    'efficiente': 'эффективный', 'sufficiente': 'достаточный',
    'abbondante': 'обильный', 'scarso': 'скудный',
    'numeroso': 'многочисленный', 'pochino': 'немного',
    'tutto': 'весь/всё', 'alcuno': 'какой-то/некоторый',
    'nessuno': 'никакой/никто', 'ogni': 'каждый',
    'molto': 'много', 'poco': 'мало', 'tanto': 'столько/много',
    'troppo': 'слишком', 'quanto': 'сколько',
    'pieno': 'полный', 'vuoto': 'пустой',
    'calmo': 'спокойный/тихий', 'silenzioso': 'тихий',
    'rumoroso': 'шумный', 'sonoro': 'звучный',
    'musicale': 'музыкальный', 'armonioso': 'гармоничный',
    'disarmonico': 'дисгармоничный', 'gradevole': 'приятный',
    'sgradevole': 'неприятный', 'dolce': 'сладкий/мягкий',
    'pungente': 'едкий/острый', 'profumato': 'ароматный',
    'puzzolente': 'вонючий', 'odore': 'запах',
    'salvo': 'целый и невредимый', 'pronto': 'готовый',
    'libero': 'свободный', 'occupato': 'занятый',
    'disponibile': 'доступный', 'accessibile': 'доступный',
    'chiuso': 'закрытый', 'aperto': 'открытый',
    'riservato': 'зарезервированный/сдержанный',
    'esclusivo': 'исключительный', 'comune': 'общий/обычный',
    'personale': 'личный', 'generale': 'общий',
    'particolare': 'особый/частный', 'speciale': 'специальный',
    'extraordinario': 'экстраординарный', 'normale': 'нормальный',
    'anormale': 'ненормальный', 'regolare': 'регулярный',
    'irregolare': 'нерегулярный', 'costante': 'постоянный',
    'variabile': 'переменный', 'fisso': 'фиксированный',
    'mobile': 'подвижный', 'statico': 'статичный',
    'dinamico': 'динамичный', 'stabile': 'стабильный',
    'instabile': 'нестабильный', 'equilibrato': 'уравновешенный',
    'squilibrato': 'неуравновешенный', 'simmetrico': 'симметричный',
    'asimmetrico': 'асимметричный', 'proporzionato': 'пропорциональный',
    'sproporzionato': 'непропорциональный', 'adatto': 'подходящий',
    'inadatto': 'неподходящий', 'idoneo': 'пригодный',
    'inidoneo': 'непригодный', 'adeguato': 'адекватный',
    'inadeguato': 'неадекватный', 'appropriato': 'уместный',
    'inappropriato': 'неуместный', 'conveniente': 'удобный/выгодный',
    'inconveniente': 'неудобный', 'comodo': 'удобный',
    'scomodo': 'неудобный', 'pratico': 'практичный',
    'teorico': 'теоретический',
    'migliore': 'лучший', 'peggiore': 'худший',
    'ottimo': 'отличный/превосходный', 'pessimo': 'очень плохой',
    'massimo': 'максимальный', 'minimo': 'минимальный',
    'maggiore': 'больший/старший', 'minore': 'меньший/младший',
    'superiore': 'высший/верхний', 'inferiore': 'низший/нижний',
    'estremo': 'крайний', 'medio': 'средний',
    'centrale': 'центральный',

    # === COMMON ADVERBS ===
    'non': 'не', 'piu': 'более', 'anche': 'тоже/также',
    'molto': 'очень/много', 'bene': 'хорошо', 'male': 'плохо',
    'come': 'как', 'quando': 'когда', 'dove': 'где/куда',
    'perche': 'почему/потому что', 'sempre': 'всегда',
    'mai': 'никогда', 'ancora': 'ещё/ всё ещё',
    'gia': 'уже', 'solo': 'только', 'poi': 'потом/затем',
    'subito': 'сразу/немедленно', 'tardi': 'поздно',
    'presto': 'рано/быстро', 'ora': 'сейчас/теперь',
    'oggi': 'сегодня', 'ieri': 'вчера', 'domani': 'завтра',
    'qui': 'здесь', 'qua': 'здесь/сюда', 'li': 'там',
    'la': 'там/туда', 'laggiu': 'вон там',
    'sopra': 'над/сверху', 'sotto': 'под/внизу',
    'dentro': 'внутри', 'fuori': 'снаружи/наружу',
    'davanti': 'перед/впереди', 'dietro': 'позади/сзади',
    'vicino': 'близко', 'lontano': 'далеко',
    'su': 'на/вверх', 'giu': 'вниз',
    'insieme': 'вместе', 'separatamente': 'отдельно',
    'forse': 'возможно/может быть', 'certamente': 'конечно/наверняка',
    'probabilmente': 'вероятно/наверное', 'ovviamente': 'очевидно',
    'esattamente': 'точно', 'precisamente': 'точно/именно',
    'veramente': 'действительно', 'realmente': 'реально/действительно',
    'quasi': 'почти', 'circa': 'около/приблизительно',
    'appena': 'только что/едва', 'tuttora': 'до сих пор',
    'finalmente': 'наконец', 'recentemente': 'недавно',
    'attualmente': 'в настоящее время', 'prima': 'до/раньше',
    'dopo': 'после/потом', 'duramente': 'жестко',
    'spesso': 'часто', 'raramente': 'редко',
    'talvolta': 'иногда', 'qualche': 'несколько/какой-то',
    'ogni': 'каждый', 'nessun': 'никакой',
    'specialmente': 'особенно', 'particolarmente': 'в частности',
    'principalmente': 'главным образом', 'soprattutto': 'прежде всего',
    'infatti': 'в самом деле', 'davvero': 'действительно',
    'certamente': 'конечно', 'naturalmente': 'естественно',
    'ovviamente': 'очевидно', 'chiaramente': 'ясно',
    'evidentemente': 'очевидно', 'assolutamente': 'абсолютно',
    'completamente': 'полностью', 'totalmente': 'полностью/целиком',
    'parzialmente': 'частично', 'esclusivamente': 'исключительно',
    'principalmente': 'главным образом', 'generalmente': 'обычно',
    'normalmente': 'обычно/нормально', 'usualmente': 'обычно',
    'solitamente': 'обычно', 'spesso': 'часто',
    'raramente': 'редко', 'mai': 'никогда',
    'sempre': 'всегда', 'ancora': 'ещё',
    'gia': 'уже', 'appena': 'только что',
    'subito': 'немедленно/сразу', 'immediatamente': 'немедленно',
    'presto': 'скоро/быстро', 'tardi': 'поздно',
    'velocemente': 'быстро', 'lentamente': 'медленно',
    'purtroppo': 'к сожалению', 'fortunatamente': 'к счастью',
    'comunque': 'во всяком случае/всё равно',
    'invece': 'вместо/напротив', 'anzi': 'наоборот/скорее',
    'quindi': 'поэтому', 'dunque': 'итак/значит',
    'perciò': 'поэтому', 'pertanto': 'следовательно',
    'cosi': 'так/таким образом', 'cosi': 'так',
    'inoltre': 'к тому же/кроме того', 'oltretutto': 'к тому же',
    'tuttavia': 'однако/тем не менее', 'però': 'но/однако',
    'bensì': 'а/но', 'neanche': 'даже не',
    'nemmeno': 'даже не', 'neppure': 'даже не',
    'ne': 'ни/оттуда', 'pure': 'также/даже',
    'addirittura': 'даже/вплоть до', 'addirittura': 'буквально',
    'almeno': 'по крайней мере', 'almeno': 'хотя бы',
    'piuttosto': 'довольно/скорее', 'insomma': 'в общем',
    'davvero': 'действительно', 'effettivamente': 'фактически',
    'praticamente': 'практически', 'in effetti': 'по сути',
    'sinceramente': 'искренне/честно', 'onestamente': 'честно',
    'seriamente': 'серьёзно', 'decisamente': 'решительно',
    'fermamente': 'твёрдо', 'calmamente': 'спокойно',
    'gentilmente': 'любезно', 'volontieri': 'охотно',
    'volentieri': 'охотно', 'malvolentieri': 'неохотно',
    'direttamente': 'напрямую', 'indirettamente': 'косвенно',
    'personalmente': 'лично', 'individualmente': 'индивидуально',
    'socialmente': 'социально', 'politicamente': 'политически',
    'economicamente': 'экономически', 'financialmente': 'финансово',
    'altrimenti': 'иначе', 'viceversa': 'наоборот',
    'similmente': 'подобным образом', 'ugualmente': 'одинаково/равно',
    'equamente': 'справедливо', 'giustamente': 'справедливо',
    'correttamente': 'правильно', 'sbagliatamente': 'неправильно',
    'facilmente': 'легко', 'difficilmente': 'с трудом',
    'fortunatamente': 'к счастью', 'sfortunatamente': 'к несчастью',
    'perfortuna': 'к счастью', 'sfortuna': 'невезение',
    'ultimamente': 'в последнее время', 'recentemente': 'недавно',
    'attualmente': 'в настоящее время', 'correntemente': 'в настоящее время',
    'precedentemente': 'ранее', 'successivamente': 'впоследствии',
    'inizialmente': 'изначально', 'originariamente': 'изначально',
    'finalmente': 'наконец', 'eventualmente': 'возможно',
    'eventualmente': 'в случае', 'ipoteticamente': 'гипотетически',
    'teorizzabilmente': 'теоретически', 'praticamente': 'практически',
    'sicuramente': 'наверняка', 'certamente': 'конечно',
    'indubbiamente': 'несомненно', 'indubitabilmente': 'несомненно',
    'innegabilmente': 'неоспоримо', 'innegabilmente': 'бесспорно',
    'incredibilmente': 'невероятно', 'meravigliosamente': 'чудесно',
    'splendidamente': 'великолепно', 'perfettamente': 'идеально',
    'benissimo': 'очень хорошо', 'malissimo': 'очень плохо',
    'moltissimo': 'очень много', 'pochissimo': 'очень мало',
    'grandissimo': 'очень большой', 'piccolissimo': 'очень маленький',
    'lontanissimo': 'очень далеко', 'vicinissimo': 'очень близко',
    'altissimo': 'очень высокий', 'bassissimo': 'очень низкий',
    'altrimenti': 'иначе', 'in qualche modo': 'как-нибудь',
    'in qualche modo': 'каким-то образом',
    'abbastanza': 'достаточно', 'parecchio': 'довольно много',
    'pochino': 'чуть-чуть', 'tantino': 'чуть-чуть',
    'benone': 'прекрасно', 'peggio': 'хуже',
}

# ============================================================
# FUNCTIONS
# ============================================================

def get_translation(word, pos):
    """Get Russian translation for an Italian word."""
    w = word.lower().strip()
    # Direct lookup
    if w in IT_RU:
        return IT_RU[w]
    # Try without accent
    clean = w.replace('à', 'a').replace('è', 'e').replace('ì', 'i').replace('ò', 'o').replace('ù', 'u')
    if clean in IT_RU:
        return IT_RU[clean]
    return ''

def adjective_forms(lemma):
    """Generate Italian adjective agreement forms: msg, fsg, mpl, fpl."""
    l = lemma.lower().strip()
    # Invariable adjectives
    invariable = {'blu', 'rosa', 'viola', 'per', 'roba'}
    if l in invariable:
        return l, l, l, l

    # -o ending: -o, -a, -i, -e
    if l.endswith('o'):
        # Special: buono -> buono, buona, buoni, buone
        # Regular: nuovo -> nuovo, nuova, nuovi, nuove
        stem = l[:-1]
        return l, stem + 'a', stem + 'i', stem + 'e'

    # -e ending: -e, -e, -i, -i
    if l.endswith('e'):
        stem = l[:-1]
        return l, l, stem + 'i', stem + 'i'

    # -a ending (often invariable or feminine): -a, -a, -i, -e
    if l.endswith('a'):
        stem = l[:-1]
        return l, l, stem + 'i', stem + 'e'

    # -ista ending: -ista, -ista, -isti, -iste
    if l.endswith('ista'):
        return l, l, l[:-1] + 'i', l[:-1] + 'e'

    # Default: invariable
    return l, l, l, l


# ============================================================
# MAIN
# ============================================================

# Read clean lemmas
with open(os.path.join(BASE, 'v2_lemmas_clean.txt'), 'r', encoding='utf-8') as f:
    lines = f.read().splitlines()

entries = []
for line in lines[1:]:  # skip header
    parts = line.split('\t')
    if len(parts) < 4:
        continue
    rank = int(parts[0])
    original = parts[1]
    lemma = parts[2]
    pos = parts[3]
    entries.append((rank, original, lemma, pos))

print(f"Read {len(entries)} clean lemmas")

# Load existing drill words
drill_dir = r'D:\Development\BaseGrammy\app\src\main\assets\grammarmate\vocab\it'
drill_words = set()
for fname in ['it_drill_nouns.csv', 'it_drill_verbs.csv', 'it_drill_adjectives.csv',
              'it_drill_adverbs.csv', 'it_drill_numbers.csv', 'it_drill_pronouns.csv']:
    fpath = os.path.join(drill_dir, fname)
    if not os.path.exists(fpath):
        continue
    with open(fpath, 'r', encoding='utf-8') as f:
        reader = csv.reader(f)
        next(reader, None)  # skip header
        for row in reader:
            if len(row) > 1:
                word = row[1].strip().lower()
                if word:
                    drill_words.add(word)

print(f"Existing drill words: {len(drill_words)}")

# Filter: exclude words already in drills
new_entries = []
for rank, original, lemma, pos in entries:
    if lemma.lower() in drill_words:
        continue
    new_entries.append((rank, original, lemma, pos))

print(f"After removing existing: {len(new_entries)}")

# Take top 6000 - existing
new_needed = max(0, 6000 - len(drill_words))
if new_needed < len(new_entries):
    new_entries = new_entries[:new_needed]
print(f"Taking top {len(new_entries)} (target 6000 total)")

# Separate by POS
nouns = [(r, o, l) for r, o, l, p in new_entries if p == 'NOUN']
verbs = [(r, o, l) for r, o, l, p in new_entries if p == 'VERB']
adjs = [(r, o, l) for r, o, l, p in new_entries if p == 'ADJ']
advs = [(r, o, l) for r, o, l, p in new_entries if p == 'ADV']

print(f"\nBreakdown: nouns={len(nouns)}, verbs={len(verbs)}, adj={len(adjs)}, adv={len(advs)}")

# Generate CSV files
missing = 0

# NOUNS
with open(os.path.join(BASE, 'v2_new_nouns.csv'), 'w', encoding='utf-8', newline='') as f:
    writer = csv.writer(f)
    writer.writerow(['rank', 'noun', 'collocations', 'ru'])
    for rank, orig, lemma in nouns:
        tr = get_translation(lemma, 'NOUN') or get_translation(orig, 'NOUN')
        if not tr:
            missing += 1
        writer.writerow([rank, lemma, '', tr])

# VERBS
with open(os.path.join(BASE, 'v2_new_verbs.csv'), 'w', encoding='utf-8', newline='') as f:
    writer = csv.writer(f)
    writer.writerow(['rank', 'verb', 'collocations', 'ru'])
    for rank, orig, lemma in verbs:
        tr = get_translation(lemma, 'VERB') or get_translation(orig, 'VERB')
        if not tr:
            missing += 1
        writer.writerow([rank, lemma, '', tr])

# ADJECTIVES
with open(os.path.join(BASE, 'v2_new_adjectives.csv'), 'w', encoding='utf-8', newline='') as f:
    writer = csv.writer(f)
    writer.writerow(['rank', 'adjective', 'msg', 'fsg', 'mpl', 'fpl', 'collocations', 'ru'])
    for rank, orig, lemma in adjs:
        msg, fsg, mpl, fpl = adjective_forms(lemma)
        tr = get_translation(lemma, 'ADJ') or get_translation(orig, 'ADJ')
        if not tr:
            missing += 1
        writer.writerow([rank, lemma, msg, fsg, mpl, fpl, '', tr])

# ADVERBS
with open(os.path.join(BASE, 'v2_new_adverbs.csv'), 'w', encoding='utf-8', newline='') as f:
    writer = csv.writer(f)
    writer.writerow(['rank', 'adverb', 'comparative', 'superlative', 'ru', 'collocations'])
    for rank, orig, lemma in advs:
        tr = get_translation(lemma, 'ADV') or get_translation(orig, 'ADV')
        if not tr:
            missing += 1
        writer.writerow([rank, lemma, '', '', tr, ''])

print(f"\nCSV files generated!")
print(f"Missing translations: {missing} out of {len(new_entries)}")
