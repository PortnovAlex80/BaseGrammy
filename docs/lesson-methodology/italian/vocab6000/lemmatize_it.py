#!/usr/bin/env python3
"""
Italian frequency list lemmatizer v3 — pragmatic approach.
Uses suffix rules + compact irregular tables for top 6000 words.
"""
import os, sys

INPUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "it_12500_frequency.txt")
OUTPUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "it_6000_lemmas.txt")

# ═══════════════════════════════════════════════════════════════
# 1. WORDS TO DELETE — built from token batches
# ═══════════════════════════════════════════════════════════════

def mkset(*batches):
    s = set()
    for b in batches:
        s.update(b.split())
    return s

DELETE = mkset(
    # Articles
    "il lo la l' i gli le un uno una un'",
    # Prepositions
    "di a da in con su per tra fra ad",
    # Articulated prepositions
    "del della dei delle dello degli degl al alla ai alle allo agli"
    " dal dalla dai dalle dallo dagli"
    " nel nella nei nelle nello negli"
    " sul sulla sui sulle sullo sugli"
    " dall' dell' sull' nell' all'"
    " dell sull nell all dall col coi",
    # Conjunctions
    "e ed o ma se che quindi inoltre mentre quando anzi oppure"
    " perché perche perchè perche' però pero",
    # Clitic pronouns
    "mi ti lo le li gli ci vi si me te se sé ne",
    # Subject pronouns
    "io tu lui lei noi voi loro",
    # Demonstratives
    "questo questa quella questi queste quello quelle quegli quel quei quell' quest'"
    " qualcosa qualcuno qualcuna",
    # Relatives/interrogatives
    "chi cui qual quale quali quanto quanti quanta quante",
    # Possessives
    "mio mia miei mie tuo tua tuoi tue suo sua suoi sue"
    " nostro nostra nostri nostre vostro vostra vostri vostre",
    # Quantifiers/determiners
    "ogni qualche nessuno nessuna nessun niente nulla"
    " tutto tutti tutta tutte molto molti molta molte"
    " poco pochi poca poche tanto tanti tanta tante"
    " troppo troppi troppa troppe alcuno alcuna alcuni alcune alcun"
    " qualsiasi qualunque"
    " altro altra altri altre diverso diversa diversi diverse"
    " stesso stessa stessi stesse tale tali vario varia vari varie"
    " proprio propria propri proprie certo certa certi certe",
    # Time/discourse adverbs (function-like)
    "anche ancora dopo prima poi subito sempre mai"
    " già gia gia' ora oggi ieri domani quasi forse"
    " più piu meno solo appena finora",
    "come dove perché perchè sebbene benché benche benchè"
    " piuttosto magari almeno insomma"
    " soltanto solamente nonostante addirittura"
    " invece comunque allora dunque però"
    " sia pure persino neppure nemmeno neanche"
    " attraverso attorno intorno circa presso verso mediante"
    " durante senza tranne eccetto contro"
    " entro oltre ecco sennò"
    " finché finchè finche finche' pur"
    " santo san sant' signora signor signore signori"
    " dev' quand' gliel' qualcos' nient'",
    # More function/junk
    "così cosi cosi' cio cioè cioe po po' pò"
    " new ex fin nord sud est ovest"
    " lì là giu giù de will project"
    " super star fan club gay sexy blu"
    " test killer team rock band show"
    " coach leader partner virus gas"
    " weekend drink football camion autobus"
    " van city park street miss baby"
    " gratis tunnel season km"
    " tè"
    " non no quelli queste quelli"
    " quanti qualcun"
    " né neanch' perciò infatti"
    " dottor miglior gran maggior"
    " s l"
    " fbi taxi",
    # More junk/greetings
    " avanti eccoci benvenuti sbrigati accidenti"
    " guai altrimenti dollari milioni sottotitoli",
    # More English/foreign noise
    " lady big man mac doc stop black el las golf baseball"
    " robot sport penny jeremy jo jay west king cooper marty lucas"
    " oscar professor",
    # Discourse/junk
    " chissà figurati dì alibi",
    # Italian function words missed
    " egli contanti alcol tal",
    # Numbers
    " dodici",
    # English/foreign words in subtitle data
    " tour stress gang chef set manager party email motel album top boss"
    " shock standard staff boom sms serial cowboy whisky"
    " halloween record robot",
    # More names
    " carlos steven julian teddy nancy randy grant marshall jules riley"
    " edward ken hill quinn lou audrey francis miles harris leonard"
    " april clay megan young allison albert walt lois felix elliot"
    " travis booth brav' grae' young vent'",
    # More names from other
    " junior keith by han lassu lassù",
    # More English/foreign and names round 2
    " wendy ashley dick green warren alison mickey adrian tracy damon anthony"
    " ford barney amber kit craig leon stefan shaw dwight meredith blair"
    " carmen franklin bishop vic kong jung angel cleveland iraq afghanistan"
    " flynn graham reid avery ralph sheldon mulder jen grayson collins kent"
    " sandy avery madison violet rob tess zach tucker curtis troy donald"
    " lloyd boyd mick ned alec isaac scout",
    " roman kennedy duncan anderson lex harper manny kang juan sharon violet"
    " flynn hector flash east",
    # More English words
    " mister hamburger privacy babysitter facebook bang jersey"
    " password island road laser poker cocktail pub scotch alcool"
    " campus festival jeans times clown menu shopping mail"
    " night world dvd jet tennis clan sun beach long"
    " dog girl boy hot out your day pat bay",
    # More Italian function/missed
    " sin ni cos t r eun nè quaggiu quì giovedì lunedì"
    " ancor dir dar telefilm",
    # Final round of names/English
    " gabriel chip hong hall quartier post won tac cin cal liv kitty jin"
    " grey von vietnam lemon joy sid brick may basket",
    # More names
    " kevin chris louis clark gary jackson oliver arthur terry"
    " red tim chuck stan los rock bell"
    " cana dean sam rene alex ivan neal"
    " phil gene malcolm rory kurt mel pablo"
    " bo derek fritz leonardo",
    # Contractions
    "c' v' l' d' n' s' t' m'"
    " cos' dov' com' e' perch' cio' qual' quant'"
    " tutt' mezz' anch' nient' nessun' senz'",
    # English function words
    "the of to is it and in on for you my that go up",
    # Other
    "sì ce ve glielo gliela gliene glieli gliele",
)

JUNK = mkset(
    "oh ah eh ehi beh boh uh ahi wow hey ooh huh uhm mhm hmm um mmm"
    " yeah yo shh bla whoa"
    " ok okay ciao salve grazie prego"
    " scusa scusi scusate scusami scusarmi scusatemi scuso"
    " ehm ben vabbè vabbe addio"
    " buongiorno buonasera buonanotte arrivederci"
    " perdonami auguri congratulazioni complimenti"
    " brindisi evviva ringrazio ringraziamento ringraziarti"
    " amen oddio basta piacere sissignore ehila"
    " sbrigatevi muovetevi muoviti svegliati calmati"
    " alzati levati togliti siediti fermati rilassati stammi"
    " be bo abbo",
)

# Names: lowercase set
NAMES = mkset(
    "dio cristo gesù gesu papa papà signore signor signora signorina signori"
    # Subtitle artifacts
    " resynch synch subspedia subsfactory iscrew srt ndt dl ln ii ie ia e'o"
    " ll b c d h j m n na ra v x u q g pa mo"
    # Foreign names from subtitle data
    " peter george jack tom john david michael james robert william richard"
    " thomas mark luke paul matthew simon andrew philip"
    " anna elena francesca sara sarah giovanni marco luca alessandro"
    " andrea matteo lorenzo antonio francesco roberto stefano paolo"
    " carlo luigi pietro filippo enzo salvatore michele giorgio riccardo"
    " nicola sergio bruno diego alfredo gabriele tommaso simone daniele"
    " samuele elisa giada martina federica silvia monica laura patrizia"
    " barbara sandra rosa angela caterina teresa rita beppe peppe nino toto"
    " bob bill joe jim fred harry henry sam ben dan alex max"
    " jean pierre hans karl klaus mary jane ann liz sue kate rose lily"
    " charlie mike frank tony bobby billy danny nick johnny jake jimmy"
    " dave steve jerry pete matt eric josh jason kyle leo alan barry"
    " brian ryan adam sean ray lee roy carl scott jeff ted doug don"
    " victor wayne gordon martin walter derek marcus aaron caleb tyler"
    " owen nathan seth noah ethan liam jordan connor logan trevor evan"
    " justin dennis dean dylan cole todd ian shawn bruce tommy eddie andy"
    " grace amy emma emily olivia alice claire hannah karen rachel lisa"
    " susan julia jessica jennifer elizabeth maggie katie catherine molly"
    " chloe rebecca lindsay lauren sophie natalie victoria sally caroline"
    " martha ruth annie abby betty helen ellen margaret janet joan jill"
    " angie lucy jenny kim meg naomi ruby sammy benny joey ricky rick"
    " robin jess jamie phil norman gus ronnie ron mr mrs sig sir lord"
    " howard harvey hank earl larry roger taylor cameron spencer lewis"
    " russell wade reed morgan mitchell walker hunter kelly heather neil"
    " brad charles patrick daniel christopher joseph benjamin jonathan"
    " vincent greg colin nelson raymond katherine bailey blake stuart"
    " brooke melissa lydia chase sebastian allen carter parker mason"
    " mitch cam eva rita pam gina daisy murphy gibbs harold stanley"
    " wallace doyle turner brennan ross miller davis wilson jones smith"
    " brown johnson williams philip stephen marie julie beth holly carol"
    " fiona tara patty connie cassie becca stella ella"
    " chicago york angeles boston miami vegas hollywood washington"
    " londra parigi roma francia inghilterra germania italia america"
    " california florida texas seattle brooklyn manhattan lincoln"
    " hitler jesus christ monsieur madame reverendo frankie joel jenna"
    " kenny toby finn clara veronica jacob shane brandon drew casey"
    " andrew cesare valentino garcia evvai"
    " kevin chris louis clark gary jackson oliver arthur terry"
    " red tim chuck stan los rock bell"
    " cana dean sam rene alex ivan neal"
    " phil gene malcolm rory kurt mel pablo"
    " bo derek fritz leonardo",

)

DELETE.update(JUNK)
DELETE.update(NAMES)

# ═══════════════════════════════════════════════════════════════
# 2. IRREGULAR FORMS: form → (lemma, POS)
# Built by parsing compact comma-separated strings
# ═══════════════════════════════════════════════════════════════

IRREG = {}

def _vb(inf, forms_str):
    """Add verb forms mapped to infinitive."""
    for f in forms_str.split(","):
        f = f.strip()
        if f:
            IRREG[f] = (inf, "verb")

def _nn(forms_str, lemma):
    """Add noun forms mapped to singular."""
    for f in forms_str.split(","):
        f = f.strip()
        if f:
            IRREG[f] = (lemma, "noun")

def _adj(forms_str, lemma):
    """Add adjective forms mapped to m.sg."""
    for f in forms_str.split(","):
        f = f.strip()
        if f:
            IRREG[f] = (lemma, "adjective")

# ── Irregular verbs ──
_vb("essere","sono,sei,è,siamo,siete,ero,eri,era,eravamo,eravate,erano,fui,fu,fummo,furono,sarò,sarai,sarà,saremo,sarete,saranno,sii,siano,fosse,fossero,fossi,fossimo,sarei,saresti,sarebbe,saremmo,sareste,sarebbero,essendo,stato,stata,stati,state,esserne,esserci,essermi,esserti,essersi,esserlo,sara,saro,esser")
_vb("avere","ho,hai,ha,abbiamo,avete,hanno,avevo,avevi,aveva,avevamo,avevate,avevano,ebbi,ebbe,avemmo,ebbero,avrò,avrai,avrà,avremo,avrete,avranno,abbia,abbiano,avessi,avesse,avessimo,avessero,avrei,avresti,avrebbe,avremmo,avreste,avrebbero,avendo,avuto,avuta,avuti,avute,averci,avermi,averti,aversi,averlo,averla,averle,averli,averne,avro,avra,aver")
_vb("fare","faccio,fai,fa,facciamo,fate,fanno,facevo,facevi,faceva,facevamo,facevate,facevano,feci,fece,facemmo,fecero,farò,farai,farà,faremo,farete,faranno,faccia,facciano,facessi,facesse,facessimo,facessero,farei,faresti,farebbe,faremmo,fareste,farebbero,facendo,fatto,fatta,fatti,fatte,farlo,farla,farle,farli,farci,farmi,farti,fargli,farne,farvi,faro,fara,fallo")
_vb("andare","vado,vai,va,andiamo,andate,vanno,andavo,andavi,andava,andavamo,andavate,andavano,andai,andò,andammo,andarono,andrò,andrai,andrà,andremo,andrete,andranno,vada,vadano,andassi,andasse,andassimo,andassero,andrei,andresti,andrebbe,andremmo,andreste,andrebbero,andando,andato,andata,andati,andate,andarci,andarmene,andarsene,andra,andro,ando")
_vb("stare","sto,stai,sta,stiamo,state,stanno,stavo,stavi,stava,stavamo,stavate,stavano,stetti,stette,stemmo,stettero,starò,starai,starà,staremo,starete,staranno,stia,stiano,stessi,stesse,stessimo,stessero,starei,staresti,starebbe,staremmo,stareste,starebbero,stando,stara,staro")
_vb("dire","dico,dici,dice,diciamo,dite,dicono,dicevo,dicevi,diceva,dicevano,dissi,disse,dicemmo,dissero,dirò,dirai,dirà,diremo,direte,diranno,dica,dicano,dicendo,detto,detta,detti,dette,dirlo,dirgli,dirle,dirvi,dirne,dirci,dirmi,dirti,dillo,dimmelo,dimmi,digliele,dira,diro")
_vb("sapere","so,sai,sa,sappiamo,sapete,sanno,sapevo,sapevi,sapeva,sapevamo,sapevate,sapevano,seppi,seppe,sapemmo,seppero,saprò,saprai,saprà,sapremo,saprete,sapranno,sappia,sappiano,sapessi,sapesse,sapessimo,sapessero,saprei,sapresti,saprebbe,sapremmo,sapreste,saprebbero,sapendo,saputo,saputa,saputi,sapute,saperlo,sapra,saro")
_vb("volere","voglio,vuoi,vuole,vogliamo,volete,vogliono,volevo,volevi,voleva,volevano,volli,volemmo,vollero,vorrò,vorrai,vorrà,vorremo,vorrete,vorranno,voglia,volessi,volesse,volessero,vorrei,vorresti,vorrebbe,vorremmo,vorreste,vorrebbero,volendo,voluto,voluta,voluti,volute,volerlo,volerla,volerne,volerci,vuol,voler,vorra,vorro")
_vb("potere","posso,puoi,può,possiamo,potete,possono,potevo,potevi,poteva,potevamo,potevate,potevano,potei,poté,potemmo,poterono,potrò,potrai,potrà,potremo,potrete,potranno,possa,possano,potessi,potesse,potessimo,potessero,potrei,potresti,potrebbe,potremmo,potreste,potrebbero,potendo,potuto,potuta,potuti,potute,poter,poterlo,potrà,potranno,potra,potro,puo")
_vb("dovere","devo,devi,deve,dobbiamo,dovete,devono,dovevo,dovevi,doveva,dovevamo,dovevate,dovevano,dovetti,dovette,dovemmo,dovettero,dovrò,dovrai,dovrà,dovremo,dovrete,dovranno,dovessi,dovesse,dovessero,dovrei,dovresti,dovrebbe,dovremmo,dovreste,dovrebbero,dovendo,dovuto,dovuta,dovuti,dovute,dover,doverlo,dovrà,dovranno,dovra,dovro,debba")
_vb("venire","vengo,vieni,viene,veniamo,venite,vengono,venivo,venivi,veniva,venivano,venni,venne,venimmo,vennero,verrò,verrai,verrà,verremo,verrete,verranno,venga,vengano,venissi,venisse,venissero,verrei,verresti,verrebbe,verremmo,verreste,verrebbero,venendo,venuto,venuta,venuti,venute,verra,verro")
_vb("dare","do,dai,dà,diamo,date,danno,davo,davi,dava,davano,diedi,diede,demmo,diedero,darò,darai,darà,daremo,darete,daranno,dia,dando,dato,data,dati,dargli,darle,darlo,darmi,darti,darci,darne,darvi,dammelo,dammi,dacci,dara,daro")
_vb("uscire","esco,esci,esce,usciamo,uscite,escono,uscendo,uscito,uscita,usciti,uscite,uscirne,esca,escano")
_vb("bere","bevo,bevi,beve,beviamo,bevete,bevono,bevevo,beveva,bevemmo,bevvero,bevendo,bevuto,bevuta")
_vb("vedere","vedo,vedi,vede,vediamo,vedete,vedono,vedevo,vedeva,vidi,vide,videro,vedrò,vedrai,vedrà,vedremo,vedrete,vedranno,veda,vedano,vedendo,visto,vista,visti,viste,veduto,veduta,vederci,vedermi,vederti,vederlo,vederla,vederli,vederle,vedervi,vedra,vedro")
_vb("vivere","vivo,vivi,vive,viviamo,vivete,vivono,vivevo,viveva,visse,vissero,vivrò,vivendo,vissuto,vissuta,vissuti,vissute")
_vb("morire","muoio,muori,muore,muoiono,morivo,moriva,morì,morimmo,morirono,morendo,morto,morta,morti,morte,muoia,muoiano,morira")
_vb("nascere","nasco,nasci,nasce,nascono,nasceva,nacqui,nacque,nacquero,nascendo,nato,nata,nati,nate")
_vb("piacere","piaccio,piaci,piace,piacciono,piaceva,piacqui,piacque,piacquero,piacendo,piaciuto,piaciuta,piaccia,piacerebbe")
_vb("scrivere","scrivo,scrivi,scrive,scriviamo,scrivete,scrivono,scriveva,scrissi,scrisse,scrissero,scrivendo,scritto,scritta,scritti,scritte")
_vb("leggere","leggo,leggi,legge,leggiamo,leggono,leggeva,lessi,lesse,lessero,leggendo,letto,letta,letti,lette")
_vb("mettere","metto,metti,mette,mettiamo,mettete,mettono,metteva,misi,mise,misero,mettendo,messo,messa,messi,messe,mettersi,metterlo,metterla,metterci,mettermi,metterti,metterò")
_vb("prendere","prendo,prendi,prende,prendiamo,prendete,prendono,prendeva,presi,presero,prendendo,preso,presa,prese,prendersi,prendilo,prendila,prendici,prendimi,prenderti")
_vb("chiudere","chiudo,chiudi,chiude,chiudono,chiuse,chiusero,chiudendo,chiuso,chiusa,chiusi")
_vb("aprire","apro,apri,apre,aprite,aprendo,aperto,aperta,aperti,aperte")
_vb("conoscere","conosco,conosci,conosce,conosciamo,conoscete,conoscono,conosceva,conobbi,conobbe,conobbero,conoscendo,conosciuto,conosciuta,conosciuti,conosciute")
_vb("rompere","rompo,rompi,rompe,rompono,ruppi,ruppero,rompendo,rotto,rotta,rotti,rotte")
_vb("vincere","vinco,vinci,vince,vincono,vinsi,vinse,vinsero,vincendo,vinto,vinta,vinti,vinte")
_vb("perdere","perdo,perdi,perde,perdono,perdevo,persi,perse,persero,perdendo,perso,persa,perduto,perduta")
_vb("rimanere","rimango,rimani,rimane,rimangono,rimasi,rimase,rimasero,rimanendo,rimasto,rimasta,rimasti,rimaste,rimanga")
_vb("tenere","tengo,tiene,tiene,teniamo,tenete,tengono,tenne,tennero,tenendo,tenuto,tenuta,tenuti,tenute,tenga")
_vb("scegliere","scelgo,scegli,sceglie,scelgono,scelsi,scese,scelsero,scegliendo,scelto,scelta,scelti,scelte")
_vb("crescere","cresco,cresce,crescono,cresciuto,cresciuta,crescendo")
_vb("cadere","cado,cadi,cade,cadono,cadde,caddero,cadendo,caduto,caduta")
_vb("correre","corro,corri,corre,corrono,corsi,corse,corsero,correndo")
_vb("salire","salgo,sali,sale,salgono,salendo,salito,salita")
_vb("scendere","scendo,scendi,scende,scendono,scendendo,sceso,scesa")
_vb("togliere","tolgo,togli,toglie,tolgono,tolsi,tolse,tolsero,togliendo,tolto,tolta")
_vb("spegnere","spengo,spegni,spegne,spense,spegnendo,spento,spenta")
_vb("apparire","appare,appaiono,apparve,apparvero,apparendo,apparso,apparsa")
_vb("ridere","rido,ridi,ride,ridono,risi,rise,risero,ridendo,riso")
_vb("offrire","offro,offri,offre,offrono,offrendo,offerto,offerta")
_vb("tradurre","traduce,tradusse,traducendo,tradotto,tradotta")
_vb("condurre","conduce,condusse,conducendo,condotto,condotta")
_vb("produrre","produce,producendo,prodotto")
_vb("giungere","giunge,giunse,giunsero,giungendo,giunto,giunta")
_vb("sedere","seduto,seduta,seduti,sedute,siede,siedono")
_vb("cuocere","cotto,cotta,cotti,cotte")
_vb("muovere","muovo,muovi,muove,muovono,mossi,mosse,mossero,mosso,mossa,muova")
_vb("porre","pongo,poni,pone,pongono,pose,posero,posto,posta,posti,poste")
_vb("trarre","traggo,trae,traggono,trasse,trassero,tratto,tratta")
_vb("piangere","piange,piangendo,pianto")
_vb("parere","pare,paiono,parse,parvero,parso")
_vb("comparire","compare,comparve,comparso,comparsa")
_vb("volere","vorrei,vorresti,vorrebbe,vorremmo,vorreste,vorrebbero")
_vb("dare","darei,daresti,darebbe")
_vb("avere","avrei,avresti,avrebbe,avremmo,avreste,avrebbero,averla,averli")
_vb("essere","sarei,saresti,sarebbe,saremmo,sareste,sarebbero")
_vb("fare","farei,faresti,farebbe,faremmo,fareste,farebbero")
_vb("andare","andrei,andresti,andrebbe,andremmo,andreste,andrebbero")
_vb("stare","starei,staresti,starebbe,staremmo,stareste,starebbero")
_vb("dire","direi,diresti,direbbe,diremmo,direste,direbbero")
_vb("potere","potrei,potresti,potrebbe,potremmo,potreste,potrebbero")
_vb("dovere","dovrei,dovresti,dovrebbe,dovremmo,dovreste,dovrebbero")
_vb("venire","verrei,verresti,verrebbe,verremmo,verreste,verrebbero,venisse,venissero")
_vb("sapere","saprei,sapresti,saprebbe,sapremmo,sapreste,saprebbero")
_vb("sapere","sapesse,sapessero")
_vb("vedere","vedremo,vedrete,vedranno")
# ── Regular verb forms that appear in top 6000 as 1sg/2sg/3sg ──
# These are present tense forms of common verbs
_vb("sembrare","sembra,sembrano,sembrava,sembravi,sembravano,sembrerebbe,sembrò,sembrato")
_vb("credere","credo,credi,crede,credono,credeva,credevi,credevamo,credevate,credevano,crediamo,credete,creduto,creda,creda,credo,credi,crede,credono,credeva,crediamo,credete,creduto,credete,credono,creda,credano,credevamo,crediamo,credono,credono,credono")
_vb("pensare","penso,pensi,pensa,pensiamo,pensate,pensano,pensavo,pensavi,pensava,pensavamo,pensavate,pensavano,pensai,pensò,pensammo,pensarono,penserò,penserai,penserà,penseremo,penserete,penseranno,pensando,pensato,pensata,pensati,pensate,pensandoci,pensarci")
_vb("dispiacere","dispiace,dispiacciono,dispiacque,dispiaciuto,dispiacerebbe,dispiaceva")
_vb("aspettare","aspetta,aspetti,aspetta,aspettiamo,aspettate,aspettano,aspettava,aspettavi,aspettava,aspettavamo,aspettavate,aspettavano,aspettando,aspettato")
_vb("sentire","sento,senti,sente,sentiamo,sentite,sentono,sentivo,sentivi,sentiva,sentivamo,sentivate,sentivano,sentii,sentì,sentimmo,sentirono,sentendo,sentito,sentita,sentiti,sentite,sentirlo,sentirmi,sentirti,sentirsi,sentirci")
_vb("guardare","guarda,guardi,guarda,guardiamo,guardate,guardano,guardavo,guardavi,guardava,guardavamo,guardavate,guardavano,guardai,guardò,guardammo,guardarono,guardando,guardato,guardata,guardati,guardate,guardarlo,guardarmi")
_vb("servire","serve,servi,servo,serviamo,servite,servono,serviva,servivano,servendo,servito,servita,serviti,servite,servirà,serviranno,servira,servono,servisse,servissero")
_vb("succedere","succede,succedono,succedeva,succedevano,successe,succedettero,succedendo,successo,successa,successi,successe,succederà,succederanno,succedera")
_vb("capire","capisco,capisci,capisce,capiamo,capite,capiscono,capivo,capivi,capiva,capii,capì,capimmo,capirono,capendo,capito,capita,capiti,capite,capendo,capirlo,capirai,capisco")
_vb("significare","significa,significano,significava,significava,significato,significata,significati,significate,significhi,significando,significhi")
_vb("importare","importa,importano,importava,importavano,importando,importato,importi")
_vb("riuscire","riesco,riesci,riesce,riusciamo,riescono,riuscivo,riuscivi,riusciva,riuscimmo,riuscirono,riuscendo,riuscito,riuscita,riusciti,riuscite,riuscirai,riusciremo,riesca,riuscirci")
_vb("chiamare","chiama,chiami,chiamiamo,chiamate,chiamano,chiamavo,chiamavi,chiamava,chiamavamo,chiamavate,chiamavano,chiamai,chiamò,chiamammo,chiamarono,chiamando,chiamato,chiamata,chiamati,chiamate,chiamarsi,chiamarlo,chiamarla,chiamarmi,chiamarti")
_vb("parere","pare,paiono,parse,parvero,parso,parsa,parendo")
_vb("cercare","cerco,cerchi,cerca,cercate,cercano,cercavo,cercavi,cercava,cercavamo,cercavate,cercavano,cercai,cercò,cercammo,cercarono,cercando,cercato,cercata,cercati,cercarlo")
_vb("immaginare","immagino,immagini,immagina,immaginiamo,immaginate,immaginano,immaginavo,immaginava,immaginando,immaginato,immaginata,immaginati,immaginate,immaginare")
_vb("lavorare","lavora,lavori,lavoriamo,lavorate,lavorano,lavoravo,lavoravi,lavorava,lavoravamo,lavoravate,lavoravano,lavorai,lavorò,lavorammo,lavorarono,lavorando,lavorato,lavorata,lavorati,lavorate")
_vb("tornare","torna,torni,torniamo,tornate,tornano,tornavo,tornavi,tornava,tornavano,tornai,tornò,tornammo,tornarono,tornando,tornato,tornata,tornati,tornate")
_vb("trovare","trovo,trovi,trova,troviamo,trovate,trovano,trovavo,trovavi,trovava,trovavano,trovai,trovò,trovammo,trovarono,trovando,trovato,trovata,trovati,trovate,trovarlo,trovarmi,trovarti,trovarci")
_vb("rispondere","rispondo,rispondi,risponde,rispondiamo,rispondete,rispondono,rispondeva,rispondevano,risposi,rispose,risposero,rispondendo,risposto,risposta")
_vb("aiutare","aiuto,aiuti,aiuta,aiutiamo,aiutate,aiutano,aiutavo,aiutava,aiutando,aiutato,aiutata,aiutati,aiutate,aiutarti,aiutarlo,aiutarla,aiutarmi,aiutarci,aiutarvi,aiutami")
_vb("lasciare","lascia,lasci,lasciamo,lasciate,lasciano,lasciava,lasciavano,lasciando,lasciato,lasciata,lasciati,lasciate,lasciarlo,lasciarla,lasciarmi,lasciarti,lasciarci,lasciami,lascialo,lasciala")
_vb("continuare","continua,continui,continuiamo,continuate,continuano,continuava,continuavano,continuando,continuato,continuata,continuati,continue,continuerà,continueranno")
_vb("arrivare","arriva,arrivi,arriviamo,arrivate,arrivano,arrivava,arrivavano,arrivando,arrivato,arrivata,arrivati,arrivate,arriverà,arriveranno")
_vb("cambiare","cambia,cambi,cambiamo,cambiate,cambiano,cambiava,cambiavano,cambiando,cambiato,cambiata,cambiati,cambiate")
_vb("mangiare","mangia,mangi,mangiamo,mangiate,mangiano,mangiava,mangiavano,mangiando,mangiato,mangiata,mangiati,mangiate")
_vb("scoprire","scopro,scopri,scopre,scopriamo,scoprite,scoprono,scopriva,scoprivano,scoprendo,scoperto,scoperta,scoperti,scoperte,scoprirlo")
_vb("ascoltare","ascolto,ascolti,ascolta,ascoltiamo,ascoltate,ascoltano,ascoltava,ascoltavano,ascoltando,ascoltato,ascoltami,ascoltatemi")
_vb("uccidere","uccido,uccidi,uccide,uccidono,uccideva,uccisero,uccidendo,ucciso,uccisa,uccisi,uccise,ucciderlo,ucciderla,uccidermi,ucciderò,ucciderà,uccideranno")
_vb("finire","finisci,finisce,finiamo,finite,finiscono,finivo,finiva,finirono,finendo,finito,finita,finiti,finite,finirà,finiranno,finira")
_vb("cominciare","comincia,cominci,cominciamo,cominciate,cominciano,cominciava,cominciavano,cominciando,cominciato,cominciata")
_vb("diminuire","diminuisce,diminuiscono")
_vb("dimenticare","dimentica,dimentichi,dimentico,dimenticavo,dimenticando,dimenticato,dimenticata")
_vb("bisognare","bisogna")
_vb("interessare","interessa,interessano,interessava,interessando")
_vb("preoccupare","preoccupa,preoccupi,preoccupava,preoccupando,preoccupato,preoccupata,preoccupati,preoccupate,preoccuparmi,preoccuparti")
_vb("amare","amo,ami,ama,amiamo,amate,amano,amava,amavano,amando,amato,amata,amati,amate")
_vb("odiare","odio,odi,odia,odiamo,odiano,odiava,odiando,odiato")
_vb("odiare","odiano")
_vb("pagare","pago,paghi,paga,paghiamo,pagate,pagano,pagava,pagavano,pagando,pagato,pagata,pagati,pagate,paghi,pagano")
_vb("ricordare","ricordo,ricordi,ricorda,ricordiamo,ricordate,ricordano,ricordavo,ricordavi,ricordava,ricordavano,ricordando,ricordato,ricordata,ricordati,ricordate,ricordati")
_vb("sperare","spero,speri,spera,speriamo,sperate,sperano,speravo,speravi,sperava,speravano,sperando,sperato")
_vb("abbracciare","abbraccio,abbracci,abbraccia,abbracciamo,abbracciano,abbracciando,abbracciato")
_vb("arrestare","arresta,arresti,arrestiamo,arrestano,arrestava,arrestando,arrestato,arrestata,arrestati")
_vb("bloccare","blocca,blocchi,blocchiamo,bloccano,bloccava,bloccando,bloccato,bloccata,bloccati")
_vb("chiedere","chiedo,chiedi,chiede,chiediamo,chiedete,chiedono,chiedevo,chiedevi,chiedeva,chiedevano,chiesi,chiese,chiesero,chiedendo,chieduto")
_vb("comprare","compro,compri,compra,compriamo,comprate,comprano,comprava,compravano,comprando,comprato,comprata")
_vb("divertire","diverte,divertono,divertendo,divertito,divertita,divertiti,divertite,divertirsi")
_vb("dormire","dormo,dormi,dorme,dormiamo,dormite,dormono,dormivo,dormiva,dormendo,dormito")
_vb("entrare","entra,entri,entriamo,entrate,entrano,entrava,entravano,entrando,entrato,entrata,entrati,entrate,entriamo")
_vb("fermare","ferma,fermi,fermiamo,fermate,fermano,fermava,fermavano,fermando,fermato,fermata,fermati,fermate,fermarti,fermarmi,fermarci,fermatevi,fermarlo")
_vb("giocare","gioco,gioca,giocate,giocano,giocava,giocavano,giocando,giocato")
_vb("guidare","guida,guidi,guidiamo,guidate,guidano,guidava,guidando,guidato")
_vb("incontrare","incontro,incontri,incontra,incontriamo,incontrate,incontrano,incontrava,incontravano,incontrando,incontrato,incontrata,incontrati,incontrati,incontrarci")
_vb("mandare","manda,mandi,mandiamo,mandate,mandano,mandava,mandavano,mandando,mandato,mandata,mandati")
_vb("meritare","merita,merito,meriti,meritiamo,meritano,meritando,meritato")
_vb("mettere","metto,metti,mette,mettiamo,mettete,mettono,metteva,misi,mise,misero,mettendo,messo,messa,messi,messe,metterlo,metterla,metterci,mettermi,metterti,metterò,mettila,mettilo,mettiti,mettetevi")
_vb("partire","parto,parti,parte,partiamo,partite,partono,partiva,partivano,partii,partì,partimmo,partirono,partendo,partito,partita,partiti,partite,partiamo")
_vb("pensare","penso,pensa,pensano,pensavo,pensava,pensando,pensato")
_vb("piangere","piange,piangono,piangeva,piangendo,pianto")
_vb("portare","porto,porti,porta,portiamo,portate,portano,portava,portavano,portando,portato,portata,portati,portate,portarlo,portarla,portarmi,portarti,portalo,portala")
_vb("provare","provo,provi,prova,proviamo,provate,provano,provava,provavano,provando,provato,provata,provati,provate,provarlo")
_vb("restare","resta,resti,restiamo,restate,restano,restava,restavano,restando,restato,restiamo")
_vb("salvare","salva,salvi,salviamo,salvate,salvano,salvava,salvavano,salvando,salvato,salvata,salvati,salvate,salvarti")
_vb("scrivere","scrivo,scrivi,scrive,scriviamo,scrivete,scrivono,scriveva,scrissi,scrisse,scrissero,scrivendo,scritto,scritta,scritti,scritte")
_vb("sparare","spara,spari,spariamo,sparate,sparano,sparava,sparavano,sparando,sparato")
_vb("spaventare","spaventa,spaventi,spaventato,spaventata")
_vb("studiare","studio,studi,studia,studiare,studiamo,studiate,studiano,studiava,studavano,studiando,studiato")
_vb("cercare","cerca,cerchi,cerchiamo,cercate,cercano,cercava,cercavano,cercando,cercato,cercarlo")
_vb("risolvere","risolvo,risolvi,risolve,risolviamo,risolvono,risolveva,risolse,risolsero,risolvendo,risolto")
_vb("dormire","dormo,dormi,dorme,dormiamo,dormite,dormono,dormendo,dormito")
_vb("accettare","accetta,accetti,accettiamo,accettano,accettava,accettando,accettato,accettata")
_vb("ammettere","ammetto,ammetti,ammette,ammettono,ammetteva,ammettendo,ammesso")
_vb("andare","andiamo,andate,ando")
_vb("andare","andrei,andresti,andrebbe,andremmo,andreste,andrebbero,andasse,andassero")
_vb("andare","andra,andro")
_vb("apparire","appare,appaiono")
_vb("basta","bastare")
_vb("bastare","basta,bastava,bastando,bastato")
_vb("cancellare","cancella,cancelli,cancelliamo,cancellano,cancellando,cancellato,cancellata")
_vb("cantare","canto,canti,canta,cantiamo,cantate,cantano,cantava,cantavano,cantando,cantato")
_vb("celebrare","celebra,celebri,celebriamo,celebrano,celebrando,celebrato")
_vb("confermare","conferma,confermi,confermiamo,confermano,confermava,confermando,confermato")
_vb("conoscere","conosco,conosci,conosce,conosciamo,conoscete,conoscono,conosceva,conoscevi,conoscevamo,conobbi,conobbe,conobbero,conoscendo,conosciuto,conosciuta,conosciuti,conosciute,conoscerla,conoscerlo,conoscerti")
_vb("costruire","costruisco,costruisci,costruisce,costruiamo,costruite,costruiscono,costruiva,costruirono,costruendo,costruito,costruita")
_vb("dimostrare","dimostra,dimostri,dimostrano,dimostrava,dimostrando,dimostrato")
_vb("diventare","diventa,diventi,diventiamo,diventate,diventano,diventava,diventavano,diventando,diventato,diventata,diventati")
_vb("divertire","diverte,divertono,divertendo,divertito,divertirsi")
_vb("giudicare","giudico,giudichi,giudica,giudichiamo,giudicano,giudicando,giudicato")
_vb("insegnare","insegno,insegni,insegna,insegniamo,insegnano,insegnava,insegnando,insegnato")
_vb("lamentare","lamenta,lamenti,lamentiamo,lamentano,lamentando,lamentato")
_vb("offendere","offende,offesi,offese,offendendo,offeso,offesa")
_vb("piangere","piange,piangono,piangeva,piangendo")
_vb("riconoscere","riconosco,riconosci,riconosce,riconosciamo,riconoscono,riconobbi,riconobbe,riconobbero,riconoscendo,riconosciuto")
_vb("rischiare","rischio,rischi,rischia,rischiamo,rischiano,rischiando,rischiato")
_vb("rubare","rubo,rubi,ruba,rubiamo,rubate,rubano,rubava,rubavano,rubando,rubato,rubata,rubati")
_vb("scappare","scappa,scappi,scappiamo,scappano,scappava,scappando,scappato")
_vb("scendere","scendo,scendi,scende,scendiamo,scendete,scendono,scendeva,scendevano,scendendo,sceso,scesa")
_vb("scommettere","scommetto,scommetti,scommette,scommettiamo,scommettono,scommetteva,scommettendo,scommesso")
_vb("scoprire","scopro,scopri,scopre,scopriamo,scoprite,scoprono,scopriva,scoprivano,scoprendo,scoperto,scoperta,scoperti,scoperte")
_vb("scegliere","scelgo,scegli,sceglie,scelgono,scelsi,scese,scelsero,scegliendo,scelto,scelta,scelti,scelte")
_vb("spegnere","spengo,spegni,spegne,spengono,spense,spensero,spegnendo,spento,spenta")
_vb("sparire","sparisco,sparisci,sparisce,spariscono,spariva,sparirono,sparendo,sparito,sparita")
_vb("soffrire","soffro,soffri,soffre,soffrono,soffriva,soffrendo,sofferto")
_vb("suonare","suona,suoni,suoniamo,suonano,suonava,suonando,suonato")
_vb("vivere","vivo,vivi,vive,viviamo,vivete,vivono,vivevo,vivevi,viveva,vivevamo,vivevate,vivevano,visse,vissero,vivendo,vissuto,vissuta")
# ── More verb forms from 'other' list ──
_vb("tenere","tieni,tengono,teneva,tenevano,tenendo,tenuto,tenuta")
_vb("parlare","parlo,parli,parla,parliamo,parlate,parlano,parlavo,parlavi,parlava,parlavamo,parlavate,parlavano,parlai,parlò,parlammo,parlarono,parlando,parlato,parlata,parlati,parlate,parlarne,parlarti,parlargli,parlarci,parlami")
_vb("sembrare","sembri,sembra,sembrano,sembrava,sembravano,sembrando,sembrato")
_vb("intendere","intendo,intendi,intende,intendiamo,intendono,intendeva,intendevano,intendendo,inteso")
_vb("fare","fammi,fallo,fatevi,facciamolo,fagli,faglielo")
_vb("guardare","guardami,guardati,guardate,guardalo,guardarmi")
_vb("chiedere","chiedo,chiedi,chiede,chiediamo,chiedete,chiedono,chiedeva,chiedevi,chiedevano,chiedendo,chiederti,chiedermi,chiedile")
_vb("dire","digli,dirci,dirmi,dirti,dillo,dimmelo,dille")
_vb("prendere","prendo,prendi,prende,prendiamo,prendete,prendono,prendeva,prendevano,prendendo,preso,presa,prese,prendersi,prendilo,prendila,prendici,prendimi,prenderti,prenderei,prendiamo,prendilo,prendila,prenderti,prendimi,prendila")
_vb("fidare","fida,fidati,fidata,fidarci,fidarmi,fidarti")
_vb("salutare","saluta,saluti,salutiamo,salutano,salutando,salutato")
# eccomi/eccoci are discourse markers, map to themselves
for w in ["eccomi","eccoci","eccoti","eccolo","eccola","eccoli"]:
    IRREG[w] = (w, "other")
_vb("andare","andiamo,andate,ando")
_vb("essere","esserci,esserti,essermi,essersi")
_vb("avere","averla,averli,averle,averne,averlo")
_vb("salire","sali,sale,salgono,salendo,salito,salita,saliamo,salite")
_vb("scegliere","scegli,scelgo,scelga")
_vb("venire","veniamo,venite,vengano")
_vb("volere","voglia,vogliamo,vogliate")
_vb("mangiare","mangia,mangi,mangiamo,mangiate,mangiano,mangiava,mangiavano,mangiando,mangiato,mangiata")
_vb("baciare","bacio,baci,bacia,baciamo,baciano,baciando,baciato")
_vb("correre","corri,corriamo,correte,corrano")
_vb("dormire","dormi,dorme,dormiamo,dormite,dormono,dormendo,dormito")
_vb("scendere","scendi,scende,scendiamo,scendete,scendono")
_vb("chiudere","chiudi,chiude,chiudiamo,chiudete,chiudete,chiuse")
_vb("vivere","viviamo,vivendo,vivendo,vissuto")
_vb("morire","muoio,muori,muore,muoiono,moriamo,morite,morira,morirà")
_vb("guadagnare","guadagno,guadagni,guadagna,guadagniamo,guadagnate,guadagnano,guadagnava,guadagnando,guadagnato")
_vb("cadere","cado,cadi,cade,cadono,cadendo,caduto,caduta")

# ── More noun plurals -i → singular ──
_nn("capelli","capello")
_nn("armi","arma")
_nn("vestiti","vestito")
_nn("pezzi","pezzo")
_nn("guai","guaio")
_nn("documenti","documento")
_nn("informazioni","informazione")
_nn("tempi","tempo")
_nn("sogni","sogno")
_nn("ordini","ordine")
_nn("segreti","segreto")
_nn("metri","metro")
_nn("chiavi","chiave")
_nn("agenti","agente")
_nn("clienti","cliente")
_nn("libri","libro")
_nn("umani","umano")
_nn("punti","punto")
_nn("fiori","fiore")
_nn("sentimenti","sentimento")
_nn("poliziotti","poliziotto")
_nn("giochi","gioco")
_nn("pantaloni","pantalone")
_nn("conti","conto")
_nn("risultati","risultato")
_nn("condizioni","condizione")
_nn("dettagli","dettaglio")
_nn("momenti","momento")
_nn("omicidi","omicidio")
_nn("dollari","dollaro")
_nn("milioni","milione")
_nn("affari","affare")
_nn("passi","passo")
_nn("secondi","secondo")
_nn("vicini","vicino")
_nn("famiglie","famiglia")
_nn("paesi","paese")
_nn("studenti","studente")
_nn("bambini","bambino")
_nn("giorni","giorno")
_nn("capelli","capello")
_nn("molti","molto")
_nn("problemi","problema")
_nn("genitori","genitore")
_nn("ragazzi","ragazzo")
_nn("soldi","soldo")
_nn("occhi","occhio")
_nn("anni","anno")
_nn("cose","cosa")
_nn("uomini","uomo")
_nn("donne","donna")
_nn("amici","amico")
_nn("figli","figlio")
_nn("gambe","gamba")
_nn("piedi","piede")
_nn("denti","dente")
_nn("mesi","mese")
_nn("ore","ora")
_nn("minuti","minuto")
_nn("casi","caso")
_nn("morti","morto")
_nn("cavalli","cavallo")
_nn("soldati","soldato")
_nn("animali","animale")
_nn("ospiti","ospite")
_nn("cani","cane")
_nn("braccia","braccio")
_nn("parole","parola")
_nn("ragazze","ragazza")
_nn("fratelli","fratello")
_nn("sorelle","sorella")
_nn("figlie","figlia")
_nn("bambine","bambina")
_nn("persone","persona")
_nn("nomi","nome")
_nn("numeri","numero")
_nn("motivi","motivo")
_nn("prove","prova")
_nn("luoghi","luogo")
_nn("dita","dito")
_nn("baci","bacio")
_nn("strade","strada")
_nn("madri","madre")
_nn("padri","padre")
_nn("cittadini","cittadino")
_nn("settimane","settimana")
_nn("porte","porta")
_nn("camere","camera")
_nn("ferite","ferita")
_nn("cose","cosa")
_nn("danni","danno")
_nn("ragioni","ragione")
_nn("pezzi","pezzo")
_nn("punti","punto")
_nn("dita","dito")
_nn("settimane","settimana")
_nn("sistemi","sistema")
_nn("programmi","programma")
_nn("piani","piano")
_nn("senzi","senso")
_nn("telefonate","telefonata")
_nn("lettere","lettera")
_nn("ricerche","ricerca")
_nn("studenti","studente")
_nn("chiavi","chiave")
_nn("denti","dente")
_nn("ospedali","ospedale")
_nn("chiese","chiesa")
_nn("mondi","mondo")
_nn("vite","vita")
_nn("giorni","giorno")
_nn("piante","pianta")
_nn("luci","luce")
_nn("palle","palla")
_nn("pugni","pugno")
_nn("colpi","colpo")
_nn("leggi","legge")
_nn("facce","faccia")
_nn("sangue","sangue")
_nn("chiavi","chiave")
_nn("armi","arma")
_nn("forze","forza")
_nn("voci","voce")
_nn("leggi","legge")
_nn("piante","pianta")
_nn("torte","torta")
_nn("scarpe","scarpa")
_nn("facce","faccia")
_nn("bocca","bocca")
_nn("bocche","bocca")
_nn("cose","cosa")
_nn("giorni","giorno")
_nn("mani","mano")
_nn("occhi","occhio")
_nn("strade","strada")
_nn("ragazzi","ragazzo")
_nn("soldi","soldo")
_nn("bambini","bambino")
_nn("uomini","uomo")
_nn("donne","donna")
_nn("amici","amico")
_nn("figli","figlio")
_nn("persone","persona")
_nn("bambine","bambina")
_nn("ragazze","ragazza")
_nn("fratelli","fratello")
_nn("sorelle","sorella")
_nn("figlie","figlia")
_nn("casi","caso")
_nn("motivi","motivo")
_nn("risultati","risultato")
_nn("documenti","documento")
_nn("punti","punto")
_nn("pezzi","pezzo")
_nn("fiori","fiore")
_nn("segreti","segreto")
_nn("sogni","sogno")
_nn("libri","libro")
_nn("capelli","capello")
_nn("vestiti","vestito")
_nn("pantaloni","pantalone")
_nn("chiavi","chiave")
_nn("giochi","gioco")
_nn("affari","affare")
_nn("omicidi","omicidio")
_nn("informazioni","informazione")
_nn("condizioni","condizione")
_nn("dettagli","dettaglio")
_nn("sentimenti","sentimento")
_nn("momenti","momento")
_nn("milioni","milione")
_nn("dollari","dollaro")
_nn("armi","arma")
_nn("agenti","agente")
_nn("clienti","cliente")
_nn("secondi","secondo")
_nn("vicini","vicino")
_nn("metri","metro")
_nn("guai","guaio")
_nn("tempi","tempo")
_nn("precedenti","precedente")
_nn("poliziotti","poliziotto")
_nn("ordini","ordine")
_nn("baci","bacio")
# ── More noun plurals from 'other' ──
_nn("pazienti","paziente")
_nn("testimoni","testimone")
_nn("segni","segno")
_nn("azioni","azione")
_nn("tipi","tipo")
_nn("pazzi","pazzo")
_nn("nemici","nemico")
_nn("messaggi","messaggio")
_nn("modi","modo")
_nn("mostri","mostro")
_nn("diritti","diritto")
_nn("errori","errore")
_nn("poteri","potere")
_nn("membri","membro")
_nn("corpi","corpo")
_nn("rapporti","rapporto")
_nn("compagni","compagno")
_nn("occhiali","occhiale")
_nn("pensieri","pensiero")
_nn("giornali","giornale")
_nn("idioti","idiota")
_nn("interessi","interesse")
_nn("medici","medico")
_nn("colleghi","collega")
_nn("voti","voto")
_nn("lezioni","lezione")
_nn("sbagli","sbaglio")
_nn("alberi","albero")
_nn("bastardi","bastardo")
_nn("avvocati","avvocato")
_nn("chili","chilo")
_nn("chilometri","chilometro")
_nn("venti","vento")
_nn("servizi","servizio")
_nn("attenti","attento")
_nn("americani","americano")
_nn("uniti","unito")
_nn("passati","passato")
_nn("sposati","sposato")
_nn("simili","simile")
_nn("liberi","libero")
_nn("presenti","presente")
_nn("analisi","analisi")
_nn("crisi","crisi")
_nn("pari","pari")
_nn("ali","ala")
_nn("occhiali","occhiale")
_nn("fondi","fondo")
_nn("desideri","desiderio")

# ── More verb forms from 'other' ──
_vb("fare","far,farsi,fallo,fatemi,lasciatemi")
_vb("prendere","prendermi")
_vb("chiamare","chiamami")
_vb("credere","crederci,credimi")
_vb("lasciare","lasciatemi,lasciami")
_vb("portare","portami")
_vb("provare","provarci")
_vb("pensare","pensaci")
_vb("rendere","rendi,rende,rendono,rendendo,resa")
_vb("parlare","parlarmi")
_vb("muovere","muoversi")
_vb("potere","puo',poteri")

# ── Spelling variants → map to canonical form ──
IRREG["é"] = ("essere", "verb")
IRREG["son"] = ("essere", "verb")
IRREG["far"] = ("fare", "verb")
IRREG["amor"] = ("amore", "noun")
IRREG["mal"] = ("male", "adverb")
IRREG["laggiù"] = ("laggiù", "adverb")
IRREG["laggiu"] = ("laggiù", "adverb")
IRREG["ciò"] = ("ciò", "pronoun")
IRREG["dieci"] = ("dieci", "number")
IRREG["entrambi"] = ("entrambi", "pronoun")

# ── Final batch: more noun plurals -i ──
_nn("episodi","episodio")
_nn("criminali","criminale")
_nn("personali","personale")
_nn("compiti","compito")
_nn("calci","calcio")
_nn("tratti","tratto")
_nn("regali","regalo")
_nn("contatti","contatto")
_nn("esami","esame")
_nn("crimini","crimine")
_nn("canzoni","canzone")
_nn("navi","nave")
_nn("uccelli","uccello")
_nn("decisioni","decisione")
_nn("stronzi","stronzo")
_nn("superiori","superiore")
_nn("uguali","uguale")
_nn("adulti","adulto")
_nn("emozioni","emozione")
_nn("biscotti","biscotto")
_nn("gradi","grado")
_nn("ragazzini","ragazzino")
_nn("relazioni","relazione")
_nn("eventi","evento")
_nn("abiti","abito")
_nn("dottori","dottore")
_nn("indagini","indagine")
_nn("dubbi","dubbio")
_nn("fantasmi","fantasma")
_nn("rinforzi","rinforzo")
_nn("notti","notte")
_nn("racconti","racconto")
_nn("consigli","consiglio")
_nn("cadaveri","cadavere")
_nn("assassini","assassino")
_nn("funzioni","funzione")
_nn("russi","russo")
_nn("giri","giro")
_nn("lontani","lontano")
_nn("tedeschi","tedesco")
_nn("fortunati","fortunato")
_nn("miliardi","miliardo")
_nn("esseri","essere")
_nn("inizi","inizio")
_nn("normali","normale")
_nn("prigionieri","prigioniero")
_nn("civili","civile")
_nn("ebrei","ebreo")
_nn("inglesi","inglese")
_nn("francesi","francese")
_nn(" Innocenti","innocente")
_nn("turchi","turco")
_nn("neri","nero")
_nn("bianchi","bianco")
_nn("cattivi","cattivo")
_nn("rossi","rosso")
_nn("verdi","verde")
_nn("gialli","giallo")
_nn("biondi","biondo")
_nn("sani","sano")
_nn("pigri","pigro")
_nn("arrabbiati","arrabbiato")
_nn("veri","vero")
_nn("facili","facile")
_nn("intelligenti","intelligente")
_nn("difficili","difficile")
_nn("importanti","importante")
_nn("particolari","particolare")
_nn("speciali","speciale")
_nn("cinesi","cinese")
_nn("giapponesi","giapponese")
_nn("tedeschi","tedesco")
_nn("spagnoli","spagnolo")
_nn("americani","americano")
_nn("neri","nero")
_nn("altri","altro")
_nn("certi","certo")
_nn("nuovi","nuovo")
_nn("vecchi","vecchio")
_nn("piccoli","piccolo")
_nn("grandi","grande")
_nn("alti","alto")
_nn("bassi","basso")
_nn("lunghi","lungo")
_nn("corti","corto")
_nn("veloci","veloce")
_nn("bravi","bravo")
_nn("seri","serio")
_nn("cari","caro")
_nn("forti","forte")
_nn("deboli","debole")
_nn("strani","strano")
_nn("brutti","brutto")
_nn("stupidi","stupido")
_nn("puliti","pulito")
_nn("sporchi","sporco")
_nn("contenti","contento")
_nn("tristi","triste")
_nn("felici","felice")
_nn("sicuri","sicuro")
_nn("pronti","pronto")
_nn("bassi","basso")

# ── Final batch: more verb forms ──
_vb("trovare","troverai,troverà,troveremo,troverete,troveranno,trovera")
_vb("uccidere","ucciderti")
_vb("mancare","manchi,manca,manchiamo,mancono,manca,manchi,manco")
_vb("tornare","tornerà,tornerai,torneremo,tornerete,torneranno,tornera")
_vb("lasciare","lasciar")
_vb("muovere","muoverti")
_vb("scherzare","scherzi,scherza,scherzano,scherzando,scherzato")
_vb("dire","dicessi")
_vb("dare","darsi,dammi,dacci")
_vb("prendere","prenditi")
_vb("rivedere","rivederti")
_vb("aiutare","aiutatemi")
_vb("dire","datemi")
_vb("essere","dì")  # imperative of dire sometimes maps to dì but it's "di" for dire
# Actually 'dì' is imperative of dire -> let's map it
IRREG["dì"] = ("dire", "verb")

# ── Final cleanup: remaining verb forms, nouns, adverbs ──
_vb("andare","andar")
_vb("portare","porterà,porterai,porteremo,porterete,porteranno,portera")
_vb("piacere","piacerà,piacerai,piaceremo,piacerete,piaceranno,piacera")
_vb("venire","verranno")
_vb("essere","saranno,saremo,sarete")
_vb("avere","avranno,avremo,avrete")
_vb("fare","faranno,faremo,farete")
_vb("volere","vorranno,vorremo,vorrete")
_vb("potere","potranno,potremo,potrete")
_vb("dovere","dovranno,dovremo,dovrete")
_vb("stare","staranno,staremo,starete")
_vb("dire","diranno,diremo,direte")
_vb("migliorare","migliora,migliori,migliorano,migliorava,migliorando,migliorato")
_vb("peggiorare","peggiora,peggiori,peggiorano,peggiorava,peggiorando,peggiorato")
_vb("aiutare","aiuterà,aiuterai,aiuteremo,aiuterete,aiuteranno,aiutera")
_vb("funzionare","funziona,funzionano,funzionava,funzionando,funzionato,funzionerà,funzioneranno,funzionera")
_vb("accadere","accade,accadono,accadeva,accadde,accadendo,accadrà,accadranno,accadra")
_vb("diventare","diventerà,diventerai,diventeremo,diventerete,diventeranno,diventera")
_vb("passare","passerà,passerai,passeremo,passerete,passeranno,passera")
_vb("succedere","succederà,succederanno,succedera")
_vb("mancare","mancherà,mancherai,mancheremo,mancherete,mancheranno,manchera")
_vb("prendere","prenderà,prenderai,prenderemo,prenderete,prenderanno,prendera")
# Italian words that need proper classification
IRREG["caos"] = ("caos", "noun")
IRREG["venerdì"] = ("venerdì", "noun")
IRREG["venerdi"] = ("venerdì", "noun")
IRREG["possibilità"] = ("possibilità", "noun")
IRREG["possibilita"] = ("possibilità", "noun")
IRREG["possibilita'"] = ("possibilità", "noun")
IRREG["citta'"] = ("città", "noun")
IRREG["ragae'"] = None  # delete junk
# eccolo family → delete (discourse markers)
IRREG["eccolo"] = None
IRREG["eccola"] = None
IRREG["eccoti"] = None
IRREG["eccomi"] = None
IRREG["eccoci"] = None
IRREG["eccoli"] = None
IRREG["lassù"] = ("lassù", "adverb")
IRREG["lassu"] = ("lassù", "adverb")
IRREG["peggior"] = ("peggiorare", "verb")
IRREG["piuttosto"] = ("piuttosto", "adverb")
IRREG["disse"] = ("dire", "verb")
IRREG["poiché"] = None  # delete function word
IRREG["poiche"] = None
IRREG["ancor"] = ("ancorare", "verb")
IRREG["quaggiù"] = ("quaggiù", "adverb")
IRREG["quaggiu"] = ("quaggiù", "adverb")
IRREG["quì"] = ("qui", "adverb")
# More specific fixes
_vb("rimanere","rimanete,rimaniamo,rimanete,rimanga,rimangano")
_vb("infrangere","infranto,infranta,infranti,infrante")
_vb("credere","credermi,crederci,crederlo,crederla,crederti")
_vb("provare","provaci,provami,provarlo,provarmi,provarti")
_vb("fermare","fermarsi,fermarti,fermarmi,fermarci,fermarvi")
_vb("presentare","presentarti,presentarmi,presentarci,presentarlo,presentarla")
_vb("rendere","renda,rendano,rendo")
IRREG["accorta"] = ("accorto", "adjective")
IRREG["simpatica"] = ("simpatico", "adjective")
IRREG["simpatici"] = ("simpatico", "adjective")
IRREG["simpatiche"] = ("simpatico", "adjective")
IRREG["solitario"] = ("solitario", "adjective")
IRREG["solitaria"] = ("solitario", "adjective")
IRREG["ovvero"] = ("ovvero", "adverb")
IRREG["rivoglio"] = ("rivolgere", "verb")
IRREG["rinchiuso"] = ("rinchiudere", "verb")
IRREG["utili"] = ("utile", "adjective")
IRREG["raggi"] = ("raggio", "noun")
IRREG["uffici"] = ("ufficio", "noun")
IRREG["scimmie"] = ("scimmia", "noun")
IRREG["lenzuola"] = ("lenzuolo", "noun")
IRREG["alquanto"] = ("alquanto", "adverb")
IRREG["indiana"] = ("indiano", "adjective")
IRREG["perfetti"] = ("perfetto", "adjective")
IRREG["perfetta"] = ("perfetto", "adjective")
IRREG["perfette"] = ("perfetto", "adjective")
# Delete foreign names/junk that slipped through
for w in ["louise","avenue","eve"]:
    IRREG[w] = None
# More specific edge-case fixes
IRREG["spirito"] = ("spirito", "noun")
IRREG["terzo"] = ("terzo", "number")
IRREG["quarto"] = ("quarto", "number")
IRREG["quinto"] = ("quinto", "number")
IRREG["usata"] = ("usato", "adjective")
IRREG["usate"] = ("usato", "adjective")
IRREG["usati"] = ("usato", "adjective")
IRREG["pulita"] = ("pulito", "adjective")
IRREG["pulite"] = ("pulito", "adjective")
IRREG["puliti"] = ("pulito", "adjective")
IRREG["pazzesco"] = ("pazzesco", "adjective")
IRREG["contrario"] = ("contrario", "adjective")
IRREG["contraria"] = ("contrario", "adjective")
IRREG["contrari"] = ("contrario", "adjective")
IRREG["contrarie"] = ("contrario", "adjective")
IRREG["massimo"] = ("massimo", "adjective")
IRREG["massima"] = ("massimo", "adjective")
IRREG["massimi"] = ("massimo", "adjective")
IRREG["massime"] = ("massimo", "adjective")
_vb("disporre","dispone,dispongono,disponiamo,disponete,disposta,disposti,disposte,disponendo")

# ── Irregular nouns ──
_nn("uomini","uomo")
_nn("anni,ore,minuti,giorni,mesi,piedi,occhi,mani,denti,braccia,gambe",
    "anno")  # placeholder — handle individually below

# Handle each individually
for pair in [
    ("uomini","uomo"),("anni","anno"),("giorni","giorno"),("mesi","mese"),
    ("ore","ora"),("minuti","minuto"),("nomi","nome"),("numeri","numero"),
    ("problemi","problema"),("sistemi","sistema"),("piani","piano"),
    ("programmi","programma"),("ragazzi","ragazzo"),("ragazze","ragazza"),
    ("persone","persona"),("donne","donna"),("bambini","bambino"),
    ("bambine","bambina"),("figli","figlio"),("figlie","figlia"),
    ("amici","amico"),("occhi","occhio"),("mani","mano"),
    ("genitori","genitore"),("fratelli","fratello"),("sorelle","sorella"),
    ("soldi","soldo"),("cose","cosa"),("morti","morto"),
    ("parole","parola"),("casi","caso"),("luoghi","luogo"),
    ("prove","prova"),("gambe","gamba"),("cani","cane"),
    ("cavalli","cavallo"),("soldati","soldato"),("animali","animale"),
    ("studenti","studente"),("paesi","paese"),("ospiti","ospite"),
    ("motivi","motivo"),("piedi","piede"),("braccia","braccio"),
    ("denti","dente"),("cittadini","cittadino"),("padri","padre"),
    ("madri","madre"),("dita","dito"),("baci","bacio"),
    ("amanti","amante"),("ospedale","ospedale"),
]:
    IRREG[pair[0]] = (pair[1], "noun")

# Invariable nouns
for w in "città cafè caffè tv gps video film bar bus hotel auto radio foto moto re goal computer internet euro".split():
    IRREG[w] = (w, "noun")

# ── Irregular adjectives ──
for forms, lem in [
    ("bella,belle,bei,belli,bel,bellissimo,bellissima,bellissimi,bellissime","bello"),
    ("buona,buone,buoni,buon,buonissimo,buonissima","buono"),
    ("nuova,nuove,nuovi","nuovo"),
    ("piccola,piccole,piccoli,piccolissimo,piccolissima","piccolo"),
    ("alta,alte,alti","alto"),
    ("bassa,basse,bassi","basso"),
    ("lunga,lunghe,lunghi","lungo"),
    ("corta,corte,corti","corto"),
    ("rossa,rosse,rossi","rosso"),
    ("bianca,bianche,bianchi","bianco"),
    ("nera,nere,neri","nero"),
    ("grande,grandi,grandissimo","grande"),
    ("felice,felici","felice"),
    ("giovane,giovani","giovane"),
    ("forte,forti","forte"),
    ("dolce,dolci","dolce"),
    ("povera,povere,poveri","povero"),
    ("cara,care,cari","caro"),
    ("duro,dura,duri,dure","duro"),
    ("grosso,grossa,grossi,grosse","grosso"),
    ("largo,larga,larghi,larghe","largo"),
    ("stretta,stretti,strette","stretto"),
    ("vera,vere,veri","vero"),
    ("sicura,sicure,sicuri","sicuro"),
    ("seria,serie,seri","serio"),
    ("brava,brave,bravi","bravo"),
    ("vecchia,vecchie,vecchi","vecchio"),
    ("fantastica,fantastici","fantastico"),
    ("calda,caldi,calde","caldo"),
    ("fredda,freddi,fredde","freddo"),
    ("cattiva,cattive,cattivi","cattivo"),
    ("migliore,migliori","migliore"),
    ("importante,importanti","importante"),
    ("difficile,difficili","difficile"),
    ("possibile,possibili","possibile"),
    ("speciale,speciali","speciale"),
    ("semplice","semplice"),
    ("arrabbiata,arrabbiati","arrabbiato"),
    ("pronta,pronti,pronte","pronto"),
    ("sbagliata,sbagliati,sbagliate","sbagliato"),
    ("stessa,stessi,stesse","stesso"),
    ("ultima,ultimi,ultime","ultimo"),
    ("prima,primi,prime","primo"),
    ("sola,soli,sole","solo"),
    ("ferma,fermi,ferme","fermo"),
    ("ricca,ricchi,ricche","ricco"),
    ("giusta,giusti,giuste","giusto"),
    ("nuova,nuove,nuovi","nuovo"),
    ("diversa,diversi,diverse","diverso"),
    ("strana,strani,strane","strano"),
    ("brutta,brutti,brutte","brutto"),
    ("verde,verdi","verde"),
    ("violenta,violenti","violento"),
    ("pericolosa,pericolosi,pericolose","pericoloso"),
    ("veloce,veloci","veloce"),
    ("seria,seri","serio"),
    ("stupida,stupidi,stupide","stupido"),
    ("incinta","incinta"),
    ("ubriaco,ubriaca","ubriaco"),
    ("calmo,calmi,calme","calmo"),
    ("nero,nera,neri,nere","nero"),
    # More adjectives that fell through to noun
    ("vero,vera,veri,vere","vero"),
    ("nuovo,nuova,nuovi,nuove","nuovo"),
    ("giusto,giusta,giusti,giuste","giusto"),
    ("sicuro,sicura,sicuri,sicure","sicuro"),
    ("bravo,brava,bravi,brave","bravo"),
    ("caro,cara,cari,care","caro"),
    ("cattivo,cattiva,cattivi,cattive","cattivo"),
    ("stanco,stanca,stanchi,stanche","stanco"),
    ("bianco,bianca,bianchi,bianche","bianco"),
    ("ricco,ricca,ricchi,ricche","ricco"),
    ("pazzo,pazza,pazzi,pazze","pazzo"),
    ("matto,matta,matti,matte","matto"),
    ("bianco,bianca,bianchi,bianche","bianco"),
    ("vivo,viva,vivi,vive","vivo"),
    ("furbo,furba,furbi,furbe","furbo"),
    ("cieco,cieca,ciechi,ceche","cieco"),
    ("muto,muta,muti,mute","muto"),
    ("solo,sola,soli,sole","solo"),
    (" diverso,diversa,diversi,diverse","diverso"),
]:
    for f in forms.split(","):
        f = f.strip()
        if f:
            IRREG[f] = (lem, "adjective")

# ── Adverbs ──
_adverb_list = [
    "bene","male","meglio","peggio","qui","qua","giu","su","sotto","sopra",
    "dentro","fuori","davanti","dietro","vicino","lontano","presto","tardi",
    "ormai","esattamente","probabilmente","certamente","veramente","davvero",
    "assolutamente","chiaramente","facilmente","lentamente","velocemente",
    "rapidamente","fortunatamente","purtroppo","naturalmente","ovviamente",
    "finalmente","recentemente","precisamente","completamente","totalmente",
    "particolarmente","generalmente","normalmente","solitamente",
    "improvvisamente","sinceramente","onestamente","letteralmente",
    "direttamente","indirettamente","semplicemente","immediatamente",
    "profondamente","adesso","subito","insieme","certo","appena",
]
for w in _adverb_list:
    IRREG[w.lower()] = (w.lower(), "adverb")

# ── Numbers ──
for w in ["uno","due","tre","quattro","cinque","sei","sette","otto","nove","dieci",
          "cento","mille","milione","miliardo"]:
    IRREG[w] = (w, "number")


# ═══════════════════════════════════════════════════════════════
# 3. RULE-BASED CLASSIFICATION
# ═══════════════════════════════════════════════════════════════

def classify(word):
    """
    Returns (lemma, pos) or None if should be deleted.
    """
    w = word.strip().lower()
    w_stripped = w.rstrip("'")

    # Check delete
    if w in DELETE or w_stripped in DELETE:
        return None

    # Check irregular dict first
    if w in IRREG:
        return IRREG[w]

    # ── Adverbs in -mente ──
    if w.endswith("mente") and len(w) > 6:
        return (w, "adverb")

    # ── Verb infinitives (-are, -ere, -ire) ──
    if len(w) > 4 and (w.endswith("are") or w.endswith("ere") or w.endswith("ire")):
        return (w, "verb")

    # ── Past participles ──
    # -ato → -are
    if w.endswith("ato") and len(w) > 4:
        return (w[:-3] + "are", "verb")
    # -ito → -ire
    if w.endswith("ito") and len(w) > 4:
        return (w[:-3] + "ire", "verb")
    # -uto → -ere
    if w.endswith("uto") and len(w) > 4:
        return (w[:-3] + "ere", "verb")

    # ── Gerunds ──
    if w.endswith("ando") and len(w) > 5:
        return (w[:-4] + "are", "verb")
    if w.endswith("endo") and len(w) > 5:
        # Could be -ere or -ire; default -ere (more common)
        return (w[:-4] + "ere", "verb")

    # ── Present 3pl: -ano, -ono ──
    if w.endswith("ano") and len(w) > 4:
        stem = w[:-3]
        if stem.endswith("isc"):  # -iscono → -ire
            return (stem[:-3] + "ire", "verb")
        return (stem + "are", "verb")  # default -ano → -are
    if w.endswith("ono") and len(w) > 4:
        stem = w[:-3]
        if stem.endswith("isc"):
            return (stem[:-3] + "ire", "verb")
        return (stem + "ere", "verb")  # -ono → -ere (or -ire)

    # ── Imperfect: -avo, -evo, -ivo ──
    if w.endswith("avano") and len(w) > 6:
        return (w[:-5] + "are", "verb")
    if w.endswith("evano") and len(w) > 6:
        return (w[:-5] + "ere", "verb")
    if w.endswith("ivano") and len(w) > 6:
        return (w[:-5] + "ire", "verb")
    if w.endswith("avo") and len(w) > 4:
        return (w[:-3] + "are", "verb")
    if w.endswith("evo") and len(w) > 4:
        return (w[:-3] + "ere", "verb")
    if w.endswith("ivo") and len(w) > 4:
        return (w[:-3] + "ire", "verb")

    # ── Future: -erò, -arò, -irò ──
    if w.endswith("erò") and len(w) > 4:
        return (w[:-3] + "ere", "verb")
    if w.endswith("arò") and len(w) > 4:
        return (w[:-3] + "are", "verb")
    if w.endswith("irò") and len(w) > 4:
        return (w[:-3] + "ire", "verb")

    # ── Conditional: -erei, -arei, -irei etc ──
    if w.endswith("erei") and len(w) > 5:
        return (w[:-4] + "ere", "verb")
    if w.endswith("arei") and len(w) > 5:
        return (w[:-4] + "are", "verb")
    if w.endswith("irei") and len(w) > 5:
        return (w[:-4] + "ire", "verb")
    if w.endswith("eremmo") and len(w) > 7:
        return (w[:-6] + "ere", "verb")
    if w.endswith("aremmo") and len(w) > 7:
        return (w[:-6] + "are", "verb")
    if w.endswith("iremmo") and len(w) > 7:
        return (w[:-6] + "ire", "verb")
    if w.endswith("ereste") and len(w) > 7:
        return (w[:-6] + "ere", "verb")
    if w.endswith("aresti") and len(w) > 7:
        return (w[:-6] + "are", "verb")
    if w.endswith("eresti") and len(w) > 7:
        return (w[:-6] + "ere", "verb")
    if w.endswith("iresti") and len(w) > 7:
        return (w[:-6] + "ire", "verb")

    # ── Present 1pl: -iamo ──
    if w.endswith("iamo") and len(w) > 5:
        stem = w[:-4]
        # Try -are first (most common)
        return (stem + "are", "verb")

    # ── Passato remoto: -ai, -etti, -ò ──
    if w.endswith("etti") and len(w) > 5:
        return (w[:-4] + "ere", "verb")
    if w.endswith("arono") and len(w) > 6:
        return (w[:-5] + "are", "verb")
    if w.endswith("erono") and len(w) > 6:
        return (w[:-5] + "ere", "verb")
    if w.endswith("irono") and len(w) > 6:
        return (w[:-5] + "ire", "verb")

    # ── Present 2sg/3sg: -i, -e ──
    # Too ambiguous for general rules. Skip.

    # ── Noun suffixes ──
    if w.endswith("zione") or w.endswith("zione"):
        return (w, "noun")
    if w.endswith("mento"):
        return (w, "noun")
    if w.endswith("tà") or w.endswith("tà"):
        return (w, "noun")
    if w.endswith("tore") or w.endswith("trice"):
        return (w, "noun")
    if w.endswith("enza") or w.endswith("anza"):
        return (w, "noun")
    if w.endswith("ismo") or w.endswith("ista"):
        return (w, "noun")
    if w.endswith("ità"):
        return (w, "noun")
    if w.endswith("aggio"):
        return (w, "noun")

    # ── Remaining words: use ending to guess POS ──
    # After all verb rules, what's left is likely a base-form noun or adjective.
    # Italian nouns ending in -e are ALREADY singular (e.g., parte, madre, nome, giorno).
    # Do NOT convert -e → -a or -i → -o — that was wrong.

    # Words ending in -o: likely m.sg noun or adjective — keep as-is
    if w.endswith("o") and len(w) > 2:
        return (w, "noun")

    # Words ending in -a: likely f.sg noun or adjective — keep as-is
    if w.endswith("a") and len(w) > 2:
        return (w, "noun")

    # Words ending in -e: likely already singular (nome, parte, madre, morte, etc.)
    if w.endswith("e") and len(w) > 2:
        return (w, "noun")

    # Words ending in -i: likely m.pl noun or adjective.
    # Most -i words in Italian are plurals → convert to -o for noun lemma.
    # Some are already base forms (crisi, analisi, pari) but those are in IRREG.
    if w.endswith("i") and len(w) > 2:
        return (w[:-1] + "o", "noun")

    # ── Fallback: keep as other ──
    return (w, "other")


# ═══════════════════════════════════════════════════════════════
# 4. MAIN: Read, process, deduplicate, write
# ═══════════════════════════════════════════════════════════════

def main():
    # Read first 6000 lines
    words = []
    with open(INPUT, "r", encoding="utf-8") as f:
        for i, line in enumerate(f, 1):
            if i > 6000:
                break
            line = line.strip()
            if line:
                words.append((i, line))

    print(f"Read {len(words)} words")

    # Process
    results = {}  # lemma → (rank, lemma, pos, original)
    stats = {"deleted": 0, "kept": 0, "pos": {}}

    for rank, word in words:
        result = classify(word)
        if result is None:
            stats["deleted"] += 1
            continue

        lemma, pos = result
        stats["kept"] += 1
        stats["pos"][pos] = stats["pos"].get(pos, 0) + 1

        # Dedup: keep lowest rank (most frequent)
        if lemma in results:
            if rank < results[lemma][0]:
                results[lemma] = (rank, lemma, pos, word)
        else:
            results[lemma] = (rank, lemma, pos, word)

    # Sort by rank
    sorted_results = sorted(results.values(), key=lambda x: x[0])

    # Write
    with open(OUTPUT, "w", encoding="utf-8", newline="\n") as f:
        for rank, lemma, pos, original in sorted_results:
            f.write(f"{rank}\t{lemma}\t{pos}\t{original}\n")

    print(f"\nDeleted: {stats['deleted']}")
    print(f"Unique lemmas: {len(sorted_results)}")
    print(f"\nPOS distribution:")
    for pos, cnt in sorted(stats["pos"].items(), key=lambda x: -x[1]):
        print(f"  {pos}: {cnt}")
    print(f"\nOutput: {OUTPUT}")


if __name__ == "__main__":
    main()
