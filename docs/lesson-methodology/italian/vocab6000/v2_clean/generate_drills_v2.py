#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generate drill CSV files from v2_new_lemmas.txt (top 2760 entries).
All translations are embedded. No external APIs needed.
"""

import csv, os, sys

# Force UTF-8 output on Windows
if sys.stdout.encoding != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8')

BASE = os.path.dirname(os.path.abspath(__file__))
INPUT = os.path.join(BASE, "v2_new_lemmas.txt")

# ─── Read top 2760 ───────────────────────────────────────────────────────────
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

# ─── COMPLETE TRANSLATION DICTIONARY ────────────────────────────────────────
# Key: (original, lemma, POS) → Russian translation
# Built by reading the full v2_new_lemmas.txt and translating each entry.

T = {}

def a(orig, lemma, pos, ru):
    T[(orig, lemma, pos)] = ru

# ═══════════════════════════════════════════════════════════════════════════════
# NOUNS (1194)
# ═══════════════════════════════════════════════════════════════════════════════
a("cosa","cosa","NOUN","вещь/дело")
a("grazie","grazie","NOUN","спасибо")
a("dio","Dio","NOUN","Бог")
a("okay","okay","NOUN","окей")
a("morte","morto","NOUN","смерть")
a("piacere","piacere","NOUN","удовольствие")
a("marito","marito","NOUN","муж")
a("auto","auto","NOUN","машина")
a("genere","genere","NOUN","род/жанр/вид")
a("signorina","signorino","NOUN","синьорина")
a("be","be","NOUN","междометие")
a("menta","menta","NOUN","мята/разум")
a("cioe","cioe","NOUN","то есть")
a("fammi","fammi","NOUN","дай-ка мне")
a("smettila","smettila","NOUN","прекрати")
a("jack","Jack","NOUN","Джек")
a("wow","wow","NOUN","вау")
a("giornata","giornata","NOUN","день/сутки")
a("giu","giue","NOUN","вниз")
a("scusami","scusami","NOUN","извини меня")
a("cristo","cristo","NOUN","Христос")
a("pero","pero","NOUN","но/однако")
a("perche'","perche","NOUN","почему/потому что")
a("lista","listare","NOUN","список")
a("lasciami","lasciare mi","NOUN","оставь меня")
a("zitto","ziggere","NOUN","тихо/молчи")
a("occhiata","occhiare","NOUN","взгляд")
a("hey","hey","NOUN","эй")
a("saresti","saresto","NOUN","ты был бы")
a("fame","fama","NOUN","голод")
a("proposito","proposito","NOUN","намерение/цель")
a("frank","frank","NOUN","Фрэнк")
a("uscita","uscita","NOUN","выход")
a("spalle","spalla","NOUN","плечи/спина")
a("serata","serata","NOUN":"вечер")
a("esercito","esercito","NOUN","армия")
a("fbi","fbi","NOUN","ФБР")
a("danny","danny","NOUN","Дэнни")
a("mare","mare","NOUN","море")
a("nick","nick","NOUN","Ник")
a("lato","lato","NOUN","сторона/бок")
a("gliel'","gliel","NOUN","ему/ей это")
a("m'","m","NOUN","мне")
a("posti","posto","NOUN","места")
a("accidenti","accidento","NOUN","чёрт возьми")
a("caffe","Caffe","NOUN","кофе")
a("benvenuto","benvenuto","NOUN","добро пожаловать")
a("banca","bancare","NOUN","банк")
a("stronzate","stronzata","NOUN","чушь/хрень")
a("bill","bill","NOUN","Билл")
a("tony","tony","NOUN","Тони")
a("ray","ray","NOUN","Рэй")
a("animali","animale","NOUN","животные")
a("will","will","NOUN","Уилл")
a("bob","bob","NOUN","Боб")
a("dati","dato","NOUN","данные")
a("college","college","NOUN","колледж")
a("chiamate","chiamata","NOUN","звонки")
a("guardami","guardamo","NOUN","посмотри на меня")
a("amy","amy","NOUN","Эми")
a("billy","Billy","NOUN","Билли")
a("richard","richard","NOUN","Ричард")
a("verra","verrae","NOUN","он придёт")
a("digli","diglio","NOUN","скажи ему")
a("show","show","NOUN","шоу/спектакль")
a("andro","andro","NOUN","я пойду")
a("vaffanculo","vaffanculo","NOUN","иди к чёрту")
a("faresti","farestare","NOUN","ты сделал бы")
a("sottotitoli","sottotitole","NOUN","субтитры")
a("latte","latta","NOUN","молоко")
a("bobby","bobby","NOUN","Бобби")
a("mercato","mercato","NOUN","рынок")
a("drink","drink","NOUN","напиток")
a("andy","Andy","NOUN","Энди")
a("ponte","Ponte","NOUN","мост")
a("perdita","perdita","NOUN":"потеря")
a("scuse","scusa","NOUN","извинения")
a("scusatemi","scusatema","NOUN","извините меня")
a("ie","ie","NOUN","то есть")
a("fidanzata","fidanzato","NOUN","невеста/подруга")
a("grace","grace","NOUN","Грейс")
a("dita","dito","NOUN","пальцы")
a("josh","Josh","NOUN","Джош")
a("copertura","coperturare","NOUN","покрытие")
a("nient'","nient","NOUN","ничего")
a("porte","porta","NOUN","двери")
a("matt","matt","NOUN","Мэтт")
a("fan","fan","NOUN","фанат")
a("quartiere","quartiere","NOUN","квартал/район")
a("mr","mr","NOUN","мистер")
a("annie","annia","NOUN","Энни")
a("c","c","NOUN","до (нота)")
a("tette","tetta","NOUN","груди")
a("street","street","NOUN","улица")
a("sali","sale","NOUN","ступени/соль")
a("est","Est","NOUN","восток")
