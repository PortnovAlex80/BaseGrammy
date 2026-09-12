# -*- coding: utf-8 -*-
"""Проверка целостности грамматической лестницы пака.

Для каждого грамматического маркера ищет ПЕРВЫЙ урок, где он встречается в
итальянской части (колонка ответа), и сравнивает с уроком, где он объявлен
введённым. first_use < intro  =>  опережающая ссылка (forward reference).

Запуск:  PYTHONIOENCODING=utf-8 python tools/audit_ladder_integrity.py
"""
import zipfile
import re
import sys
import statistics

PACKS = "app/src/main/assets/grammarmate/packs/"

# marker -> (regex по итальянскому предложению, номер урока введения в IMPERIAL)
PROBES = [
    ("артикль un/una/uno",      r"\b(un|una|uno|un')\b",                      23),
    ("артикль il/la",           r"\b(il|la)\b",                               26),
    ("артикль l'",              r"\bl'",                                      34),
    ("артикль lo",              r"\blo\b",                                    35),
    ("артикль i/gli/le",        r"\b(gli|le|i)\b",                            36),
    ("avere (личные формы)",    r"\b(ho|hai|ha|abbiamo|avete|hanno)\b",       25),
    ("essere (личные формы)",   r"\b(sono|sei|è|siamo|siete)\b",              29),
    ("глагол + инфинитив",      r"\b(posso|puoi|può|possiamo|potete|possono|voglio|vuoi|vuole|vogliamo|volete|vogliono|devo|devi|deve|dobbiamo|dovete|devono|vado|vai|va|andiamo|andate|vanno|preferisco|amo|so|sai)\s+\w+(are|ere|ire)\b", 43),
    ("potere",                  r"\b(posso|puoi|può|possiamo|potete|possono)\b", 45),
    ("andare",                  r"\b(vado|vai|va|andiamo|andate|vanno)\b",    46),
    ("предлог a",               r"\ba\b",                                     47),
    ("предлог in",              r"\bin\b",                                    48),
    ("fare",                    r"\b(faccio|fai|fa|facciamo|fate|fanno)\b",   52),
    ("venire",                  r"\b(vengo|vieni|viene|veniamo|venite|vengono)\b", 53),
    ("bere",                    r"\b(bevo|bevi|beve|beviamo|bevete|bevono)\b", 54),
    ("предлог con",             r"\bcon\b",                                   56),
    ("предлог per",             r"\bper\b",                                   57),
    ("volere",                  r"\b(voglio|vuoi|vuole|vogliamo|volete|vogliono)\b", 58),
    ("dovere",                  r"\b(devo|devi|deve|dobbiamo|dovete|devono)\b", 59),
    ("passato prossimo",        r"\b(ho|hai|ha|abbiamo|avete|hanno|sono|sei|è|siamo|siete)\s+(stato|stata|stati|state|andato|andata|andati|andate|fatto|detto|letto|scritto|bevuto|venuto|venuta|mangiato|comprato|parlato|lavorato|dormito|partito|partita|finito|potuto|voluto|dovuto|visto|preso|messo|aperto|chiuso|nato|nata|morto|rimasto|vissuto|cantato|studiato|guardato|ascoltato|ballato|giocato|cercato|trovato|viaggiato|sognato|cucinato|capito|sentito|uscito|uscita|arrivato|arrivata|tornato|tornata|entrato|entrata|salito|sceso|caduto|piaciuto|chiesto|risposto|conosciuto|saputo)\b", 62),
    ("stare",                   r"\b(sto|stai|sta|stiamo|state|stanno)\b",    71),
    ("прям. мест. lo/la/li/le", r"\b(lo|la|li|le)\s+(vedo|vedi|vede|vediamo|vedete|vedono|conosco|conosci|conosce|mangio|mangi|mangia|compro|compri|compra|prendo|prendi|prende|faccio|fai|fa|chiamo|chiami|chiama|aspetto|aspetti|aspetta|guardo|guardi|guarda|ascolto|ascolti|ascolta|leggo|leggi|legge|scrivo|scrivi|scrive|amo|ami|ama|voglio|vuoi|vuole|ho|hai|ha|porto|porti|porta|cerco|cerchi|cerca|trovo|trovi|trova|bevo|bevi|beve)\b", 78),
    ("возвратные mi/ti/si",     r"(?<!c)\b(mi|ti|si|vi)\s+\w+(o|i|a|iamo|ate|ete|ite|ano|ono)\b", 80),
    ("questo",                  r"\b(questo|questa|questi|queste)\b",         85),
    ("imperfetto",              r"\b(?!vivo\b|scrivo\b|arrivo\b|ricevo\b|devo\b|bevo\b|servo\b|salvo\b)\w+(avo|ava|avamo|avate|avano|evo|eva|evamo|evate|evano|ivo|iva|ivamo|ivate|ivano)\b", 94),
    ("ma",                      r"\bma\b",                                    99),
    ("числительное",            r"\b(due|tre|quattro|cinque|sette|otto|nove|dieci|undici|dodici)\b", 101),
    ("perché",                  r"\bperché\b",                                102),
    ("o (альтернатива)",        r"\bo\b",                                     105),
    ("se (условие)",            r"\bse\b",                                    107),
    ("molto/poco/troppo",       r"\b(molto|molta|molti|molte|poco|poca|pochi|poche|troppo|troppa|troppi|troppe)\b", 119),
    ("притяжательные",          r"\b(mio|mia|miei|mie|tuo|tua|tuoi|tue|suo|sua|suoi|sue|nostro|nostra|nostri|nostre|vostro|vostra|vostri|vostre)\b", 118),
    ("предлог di",              r"\bdi\b",                                    121),
    ("слитн. предлог di+art",   r"\b(del|dello|della|dell'|dei|degli|delle)\b", 122),
    ("quello",                  r"\b(quello|quella|quelli|quelle|quel|quei)\b", 123),
    ("слитн. предлог a+art",    r"\b(al|allo|alla|all'|ai|agli|alle)\b",      142),
    ("предлог da",              r"\bda\b",                                    143),
    ("слитн. предлог da+art",   r"\b(dal|dallo|dalla|dall'|dagli|dalle)\b",   146),
    ("слитн. предлог in+art",   r"\b(nel|nello|nella|nell'|nei|negli|nelle)\b", 148),
    ("предлог su",              r"\bsu\b",                                    149),
    ("senza",                   r"\bsenza\b",                                 158),
    ("tra/fra",                 r"\b(tra|fra)\b",                             159),
    ("gerundio",                r"\b(?!quando\b|secondo\b|tremendo\b|mondo\b)\w+(ando|endo)\b", 176),
]


def load(pack):
    z = zipfile.ZipFile(PACKS + pack)
    names = sorted(x for x in z.namelist()
                   if x.endswith(".csv") and "lesson" in x.lower())
    out = []
    for n in names:
        lines = z.read(n).decode("utf-8", "replace").strip().splitlines()
        title = lines[0] if lines else ""
        rows = []
        for ln in lines[1:]:
            if ";" not in ln:
                continue
            ru, it = ln.split(";", 1)
            rows.append((ru.strip(), it.strip()))
        out.append((n, title, rows))
    return out


def first_use(lessons, rx):
    r = re.compile(rx, re.IGNORECASE)
    for i, (_n, _t, rows) in enumerate(lessons, start=1):
        for _ru, it in rows:
            if r.search(it):
                return i, it
    return None, None


def report(pack, lessons, check_probes):
    print("=" * 78)
    print("ПАК: %s   уроков: %d   предложений: %d"
          % (pack, len(lessons), sum(len(r) for _n, _t, r in lessons)))
    sizes = [len(r) for _n, _t, r in lessons]
    print("строк в уроке: min %d  median %g  max %d"
          % (min(sizes), statistics.median(sizes), max(sizes)))

    if not check_probes:
        return
    print("-" * 78)
    print("%-26s %6s %6s  %s" % ("маркер", "введён", "1-е исп.", "вердикт"))
    print("-" * 78)
    violations = 0
    for name, rx, intro in PROBES:
        fu, sample = first_use(lessons, rx)
        if fu is None:
            print("%-26s %6d %6s  не встречается" % (name, intro, "-"))
            continue
        if fu < intro:
            violations += 1
            print("%-26s %6d %6d  ОПЕРЕЖЕНИЕ на %d  | %s"
                  % (name, intro, fu, intro - fu, sample[:44]))
        else:
            print("%-26s %6d %6d  ok" % (name, intro, fu))
    print("-" * 78)
    print("опережающих ссылок: %d из %d маркеров" % (violations, len(PROBES)))


def lexical_growth(lessons):
    seen = set()
    per = []
    for _n, _t, rows in lessons:
        new = 0
        for _ru, it in rows:
            for w in re.findall(r"[a-zà-ù']+", it.lower()):
                if w not in seen:
                    seen.add(w)
                    new += 1
        per.append(new)
    return len(seen), per


def dupes(lessons):
    seen = {}
    dup = 0
    for i, (_n, _t, rows) in enumerate(lessons, start=1):
        for _ru, it in rows:
            k = it.lower()
            if k in seen:
                dup += 1
            else:
                seen[k] = i
    return dup, len(seen)


if __name__ == "__main__":
    for pack, probes in [("ITALIAN_IMPERIAL_COURSE.zip", True),
                         ("ITALIAN_EXPRESS_SHORT.zip", True)]:
        lessons = load(pack)
        report(pack, lessons, probes)
        total, per = lexical_growth(lessons)
        print("уникальных словоформ: %d | новых в уроке: median %g, max %d"
              % (total, statistics.median(per), max(per)))
        d, uniq = dupes(lessons)
        print("повторов предложений: %d (уникальных %d)" % (d, uniq))
        print()
