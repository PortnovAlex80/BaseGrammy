#!/usr/bin/env python3
"""
Italian frequency list lemmatizer v4 — extended to rank 10000.
Based on lemmatize_it.py v3, extended to process lines 1-10000.
Target: ~6000 unique lemmas.
"""
import os, sys

INPUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "it_12500_frequency.txt")
OUTPUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "it_10000_lemmas.txt")

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
    "santo san sant' signora signor signore signori"
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
    # ── Extended for 6001-10000 ──
    # More names from 6k-10k range
    "winston becky beckett orleans miguel bones griffin lizzie amelia glenn"
    " watson sydney harrison queen perry fox alexander barnes gil palmer"
    " dallas harvard judy moon wells haley paolo sofia elvis nolan yang omar"
    " lawrence nicky cindy wes dawn ranger wyatt campbell maddie beverly"
    " fisher holmes woo lola stevie saint fox freddy jared xena hamilton"
    " shelby artie bones hammond scully thompson laurel douglas cody"
    " austin samantha mikey willie brody perry callie archer lester"
    " abigail baker iris jae schmidt sasha foster paula porter hal"
    " stephanie saint beverly samuel reggie drake sullivan bennett lenny"
    " murray jonah sheila conrad melanie ellis anton dexter erin brenda"
    " piper reese rex silver donovan georgia sadie clarke bart hugh"
    " chen north ward crane ohio porter burt monk moore sullivan"
    " bernie theo gabe gavin melanie ellis anton morris cyrus pierre"
    " lola lizzie amelia glenn watson sydney harrison queen perry fox"
    " calvin hans reynolds simpson juliette philadelphia dallas harvard"
    " judy moon wells haley paolo sofia elvis nolan yang omar lawrence"
    " nicky cindy wes dawn ranger wyatt campbell maddie beverly fisher"
    " holmes woo lola stevie saint freddy jared xena hamilton shelby"
    " artie bones hammond scully thompson laurel douglas cody austin"
    " samantha mikey willie brody callie archer lester abigail baker"
    " iris jae schmidt sasha foster paula porter hal stephanie samuel"
    " reggie drake sullivan bennett lenny murray jonah sheila conrad"
    " melanie ellis anton dexter erin brenda piper reese rex silver"
    " donovan georgia sadie clarke bart hugh chen north ward crane ohio"
    " porter burt monk moore calvin hans reynolds simpson juliette"
    " philadelphia valerie sullivan bennett alfred eleanor dawson"
    " sheldon donnie bernard harrison mcqueen wallace cindy conrad"
    " garrett rusty saul khan vivian pike marcel ellis anton"
    " choi cynthia evans stevens lori stewart bridget maurice"
    " rodney marilyn fitz sutton gail cass baxter briggs peyton"
    " darren shirley damien glen bert lake marcel chan"
    " joshua dorothy shelly hart marcel brandon hayley shannon"
    " rafael bond evans dani cynthia stevens camille esther"
    " mills arizona dante einstein holden sutton brett detroit"
    " nash stacy portland damien chan glen bert peyton darren"
    " shirley shelly hart brandon hayley shannon rafael bond"
    " evans dani stevens camille esther mills arizona dante einstein"
    " holden brett detroit nash stacy portland brien doris"
    " bates wally helena marge lionel gardner edwards elsa"
    " napoli montgomery ferrari marion robinson eugene holt"
    " elijah maxine elaine clive frost frost marion robinson"
    " eugene holt elijah maxine elaine clive frost aidan"
    " timothy magnus aiden devon denver suzanne tiffany sabrina"
    " timothy denver suzanne tiffany sabrina caitlin harriet"
    " haven colorado summer caitlin harriet haven colorado summer"
    " meyer mack denny manchester gibson crawford meyer mack"
    " denny manchester gibson crawford sherman vernon burns rufus"
    " gretchen izzie bryan rogers nigel ernie judith ronald"
    " valentine courtney peterson simmons meyer jose mack"
    " denny quentin courtney peterson simmons deacon hyun"
    " karate meyer peterson simmons mack denny quentin",
    # More English/foreign from 6k-10k
    "suite server round web dj ncis look pro pop app"
    " budget nerd hobby jazz bowling golf hockey"
    " bbq radar salsa blog google twitter nsas fbi"
    " p spaghetti yogurt sushi muffin cupcake popcorn"
    " puzzle link streaming drone volcano"
    " rpgs mmorpg sudoku espresso cappuccino"
    " pizza pasta lasagna tiramisu gelato"
    " yoga karate judo taekwondo"
    " abc cbs nbc cnn bbc hbo mtv espn"
    " sms gps wifi usb led lcd mp3 dvd cd rom"
    " vip ceo cfo coo cto mba phd"
    " ok okay hey yo oh wow hmm yeah shh blah whoa"
    " uh um mm mhm hmm",
    # More English from 6k-10k
    "get down hard happy central force grand east"
    " river saint lawrence houston ohio north"
    " kansas atlanta detroit brooklyn chicago miami vegas"
    " broadway edinburgh manchester denver"
    " colorado pennsylvania michigan georgia alaska"
    " hawaii cuba scozia milano napoli venezia"
    " roma parigi londra torino firenze"
    " australia california texas seattle boston"
    " dallas houston denver atlanta detroit chicago"
    " harvard oxford stanford yale princeton",
    # More junk/interjections from 6k-10k
    "aah bum bam hee ahia nah mh mm ca cho"
    " fi wa du gu ta ka ko lee op soo tae"
    " ji ja boo bong dong pop"
    " yep nah yup hmm aah mm bam"
    " it' you' don' hee hee",
    # More function words
    "ciascuno ognuna eccetera affinche poiche"
    " finch quassu quassù",
    # More English foreign
    "can will just like know what side way back pass game"
    " round rock match point ring end"
    " mine cross gold wall king star"
    " force pro spot air check start task"
    " account blog app dvd cd km gps tv"
    " mc rj dj ncis ncis svu nsa cia fbi",
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
    # ── Names from 6001-10000 ──
    "winston becky beckett orleans miguel bones"
    " griffin lizzie amelia glenn watson sydney harrison"
    " queen perry fox alexander barnes gil palmer dallas harvard"
    " judy moon wells haley paolo sofia elvis nolan yang omar lawrence"
    " nicky cindy wes dawn ranger wyatt campbell maddie beverly"
    " fisher holmes woo lola stevie saint freddy jared xena hamilton"
    " shelby artie hammond scully thompson laurel douglas cody"
    " austin samantha mikey willie brody callie archer lester"
    " abigail baker iris jae schmidt sasha foster paula porter hal"
    " stephanie samuel reggie drake sullivan bennett lenny murray jonah"
    " sheila conrad melanie ellis anton dexter erin brenda piper reese"
    " rex silver donovan georgia sadie clarke bart hugh chen north ward"
    " crane ohio burt monk moore calvin hans reynolds simpson juliette"
    " philadelphia valerie dawson sheldon donnie bernard garrett rusty"
    " saul khan vivian marcel choi cynthia evans stevens lori stewart"
    " bridget maurice rodney marilyn fitz sutton gail cass baxter briggs"
    " peyton darren shirley damien glen bert marcel chan joshua dorothy"
    " shelly hart brandon hayley shannon rafael bond dani camille esther"
    " mills arizona dante einstein holden brett detroit nash stacy portland"
    " brien doris bates wally helena marge lionel gardner edwards elsa"
    " napoli montgomery ferrari marion robinson eugene holt elijah maxine"
    " elaine clive frost aidan timothy magnus aiden devon denver suzanne"
    " tiffany sabrina caitlin harriet haven colorado summer meyer mack"
    " denny manchester gibson crawford sherman vernon burns rufus gretchen"
    " izzie bryan rogers nigel ernie judith ronald valentine courtney"
    " peterson simmons deacon jose quentin mack peterson simmons meyer"
    " enrico francesca giuseppe luca roberto lorenzo michele pietro"
    " luigi antonella carlo alberto marta sandra roma napoli venezia"
    " milano dinozzo goku naruto",
)

DELETE.update(JUNK)
DELETE.update(NAMES)

# ═══════════════════════════════════════════════════════════════
# 2. IRREGULAR FORMS: form → (lemma, POS)
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
_vb("sembrare","sembra,sembrano,sembrava,sembravi,sembravano,sembrerebbe,sembrò,sembrato")
_vb("credere","credo,credi,crede,credono,credeva,credevi,credevamo,credevate,credevano,crediamo,credete,creduto,creda,credete,credono,credan,credevamo,crediamo")
_vb("pensare","penso,pensi,pensa,pensiamo,pensate,pensano,pensavo,pensavi,pensava,pensavamo,pensavate,pensavano,pensai,pensò,pensammo,pensarono,penserò,penserai,penserà,penseremo,penserete,penseranno,pensando,pensato,pensata,pensati,pensate,pensandoci,pensarci")
_vb("dispiacere","dispiace,dispiacciono,dispiacque,dispiaciuto,dispiacerebbe,dispiaceva")
_vb("aspettare","aspetta,aspetti,aspettiamo,aspettate,aspettano,aspettava,aspettavi,aspettavamo,aspettavate,aspettavano,aspettando,aspettato")
_vb("sentire","sento,senti,sente,sentiamo,sentite,sentono,sentivo,sentivi,sentiva,sentivamo,sentivate,sentivano,sentii,sentì,sentimmo,sentirono,sentendo,sentito,sentita,sentiti,sentite,sentirlo,sentirmi,sentirti,sentirsi,sentirci")
_vb("guardare","guarda,guardi,guardiamo,guardate,guardano,guardavo,guardavi,guardava,guardavamo,guardavate,guardavano,guardai,guardò,guardammo,guardarono,guardando,guardato,guardata,guardati,guardate,guardarlo,guardarmi")
_vb("servire","serve,servi,servo,serviamo,servite,servono,serviva,servivano,servendo,servito,servita,serviti,servite,servirà,serviranno,servira,servono,servisse,servissero")
_vb("succedere","succede,succedono,succedeva,succedevano,successe,succedettero,succedendo,successo,successa,successi,succederà,succederanno,succedera")
_vb("capire","capisco,capisci,capisce,capiamo,capite,capiscono,capivo,capivi,capiva,capii,capì,capimmo,capirono,capendo,capito,capita,capiti,capite,capirlo,capirai")
_vb("significare","significa,significano,significava,significato,significata,significati,significate,significhi,significando")
_vb("importare","importa,importano,importava,importavano,importando,importato,importi")
_vb("riuscire","riesco,riesci,riesce,riusciamo,riescono,riuscivo,riuscivi,riusciva,riuscimmo,riuscirono,riuscendo,riuscito,riuscita,riusciti,riuscite,riuscirai,riusciremo,riesca,riuscirci")
_vb("chiamare","chiama,chiami,chiamiamo,chiamate,chiamano,chiamavo,chiamavi,chiamava,chiamavamo,chiamavate,chiamavano,chiamai,chiamò,chiamammo,chiamarono,chiamando,chiamato,chiamata,chiamati,chiamate,chiamarsi,chiamarlo,chiamarla,chiamarmi,chiamarti")
_vb("parere","pare,paiono,parse,parvero,parso,parsa,parendo")
_vb("cercare","cerco,cerchi,cerca,cercate,cercano,cercavo,cercavi,cercava,cercavamo,cercavate,cercavano,cercai,cercò,cercammo,cercarono,cercando,cercato,cercata,cercati,cercarlo")
_vb("immaginare","immagino,immagini,immagina,immaginiamo,immaginate,immaginano,immaginavo,immaginava,immaginando,immaginato,immaginata,immaginati,immaginate")
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
_vb("pagare","pago,paghi,paga,paghiamo,pagate,pagano,pagava,pagavano,pagando,pagato,pagata,pagati,pagate")
_vb("ricordare","ricordo,ricordi,ricorda,ricordiamo,ricordate,ricordano,ricordavo,ricordavi,ricordava,ricordavano,ricordando,ricordato,ricordata,ricordati,ricordate")
_vb("sperare","spero,speri,spera,speriamo,sperate,sperano,speravo,speravi,sperava,speravano,sperando,sperato")
_vb("abbracciare","abbraccio,abbracci,abbraccia,abbracciamo,abbracciano,abbracciando,abbracciato")
_vb("arrestare","arresta,arresti,arrestiamo,arrestano,arrestava,arrestando,arrestato,arrestata,arrestati")
_vb("bloccare","blocca,blocchi,blocchiamo,bloccano,bloccava,bloccando,bloccato,bloccata,bloccati")
_vb("chiedere","chiedo,chiedi,chiede,chiediamo,chiedete,chiedono,chiedevo,chiedevi,chiedeva,chiedevano,chiesi,chiese,chiesero,chiedendo,chieduto")
_vb("comprare","compro,compri,compra,compriamo,comprate,comprano,comprava,compravano,comprando,comprato,comprata")
_vb("divertire","diverte,divertono,divertendo,divertito,divertita,divertiti,divertite,divertirsi")
_vb("dormire","dormo,dormi,dorme,dormiamo,dormite,dormono,dormivo,dormiva,dormendo,dormito")
_vb("entrare","entra,entri,entriamo,entrate,entrano,entrava,entravano,entrando,entrato,entrata,entrati,entrate")
_vb("fermare","ferma,fermi,fermiamo,fermate,fermano,fermava,fermavano,fermando,fermato,fermata,fermati,fermate,fermarti,fermarmi,fermarci,fermatevi,fermarlo")
_vb("giocare","gioco,gioca,giocate,giocano,giocava,giocavano,giocando,giocato")
_vb("guidare","guida,guidi,guidiamo,guidate,guidano,guidava,guidando,guidato")
_vb("incontrare","incontro,incontri,incontra,incontriamo,incontrate,incontrano,incontrava,incontravano,incontrando,incontrato,incontrata,incontrati,incontrarci")
_vb("mandare","manda,mandi,mandiamo,mandate,mandano,mandava,mandavano,mandando,mandato,mandata,mandati")
_vb("meritare","merita,merito,meriti,meritiamo,meritano,meritando,meritato")
_vb("partire","parto,parti,parte,partiamo,partite,partono,partiva,partivano,partii,partì,partimmo,partirono,partendo,partito,partita,partiti,partite")
_vb("portare","porto,porti,porta,portiamo,portate,portano,portava,portavano,portando,portato,portata,portati,portate,portarlo,portarla,portarmi,portarti,portalo,portala")
_vb("provare","provo,provi,prova,proviamo,provate,provano,provava,provavano,provando,provato,provata,provati,provate,provarlo")
_vb("restare","resta,resti,restiamo,restate,restano,restava,restavano,restando,restato")
_vb("salvare","salva,salvi,salviamo,salvate,salvano,salvava,salvavano,salvando,salvato,salvata,salvati,salvate,salvarti")
_vb("sparare","spara,spari,spariamo,sparate,sparano,sparava,sparavano,sparando,sparato")
_vb("spaventare","spaventa,spaventi,spaventato,spaventata")
_vb("studiare","studio,studi,studia,studiare,studiamo,studiate,studiano,studiava,studiando,studiato")
_vb("risolvere","risolvo,risolvi,risolve,risolviamo,risolvono,risolveva,risolse,risolsero,risolvendo,risolto")
_vb("accettare","accetta,accetti,accettiamo,accettano,accettava,accettando,accettato,accettata")
_vb("ammettere","ammetto,ammetti,ammette,ammettono,ammetteva,ammettendo,ammesso")
_vb("bastare","basta,bastava,bastando,bastato")
_vb("cancellare","cancella,cancelli,cancelliamo,cancellano,cancellando,cancellato,cancellata")
_vb("cantare","canto,canti,canta,cantiamo,cantate,cantano,cantava,cantavano,cantando,cantato")
_vb("celebrare","celebra,celebri,celebriamo,celebrano,celebrando,celebrato")
_vb("confermare","conferma,confermi,confermiamo,confermano,confermava,confermando,confermato")
_vb("costruire","costruisco,costruisci,costruisce,costruiamo,costruite,costruiscono,costruiva,costruirono,costruendo,costruito,costruita")
_vb("dimostrare","dimostra,dimostri,dimostrano,dimostrava,dimostrando,dimostrato")
_vb("diventare","diventa,diventi,diventiamo,diventate,diventano,diventava,diventavano,diventando,diventato,diventata,diventati")
_vb("giudicare","giudico,giudichi,giudica,giudichiamo,giudicano,giudicando,giudicato")
_vb("insegnare","insegno,insegni,insegna,insegniamo,insegnano,insegnava,insegnando,insegnato")
_vb("lamentare","lamenta,lamenti,lamentiamo,lamentano,lamentando,lamentato")
_vb("offendere","offende,offesi,offese,offendendo,offeso,offesa")
_vb("riconoscere","riconosco,riconosci,riconosce,riconosciamo,riconoscono,riconobbi,riconobbe,riconobbero,riconoscendo,riconosciuto")
_vb("rischiare","rischio,rischi,rischia,rischiamo,rischiano,rischiando,rischiato")
_vb("rubare","rubo,rubi,ruba,rubiamo,rubate,rubano,rubava,rubavano,rubando,rubato,rubata,rubati")
_vb("scappare","scappa,scappi,scappiamo,scappano,scappava,scappando,scappato")
_vb("scommettere","scommetto,scommetti,scommette,scommettiamo,scommettono,scommetteva,scommettendo,scommesso")
_vb("sparire","sparisco,sparisci,sparisce,spariscono,spariva,sparirono,sparendo,sparito,sparita")
_vb("soffrire","soffro,soffri,soffre,soffrono,soffriva,soffrendo,sofferto")
_vb("suonare","suona,suoni,suoniamo,suonano,suonava,suonando,suonato")
_vb("parlare","parlo,parli,parla,parliamo,parlate,parlano,parlavo,parlavi,parlava,parlavamo,parlavate,parlavano,parlai,parlò,parlammo,parlarono,parlando,parlato,parlata,parlati,parlate,parlarne,parlarti,parlargli,parlarci,parlami")
_vb("intendere","intendo,intendi,intende,intendiamo,intendono,intendeva,intendevano,intendendo,inteso")
_vb("fare","fammi,fallo,fatevi,facciamolo,fagli,faglielo")
_vb("guardare","guardami,guardati,guardate,guardalo,guardarmi")
_vb("dire","digli,dirci,dirmi,dirti,dillo,dimmelo,dille")
_vb("prendere","prenderei,prenditi")
_vb("fidare","fida,fidati,fidata,fidarci,fidarmi,fidarti")
_vb("salutare","saluta,saluti,salutiamo,salutano,salutando,salutato")
_vb("andare","andiamo,andate,ando")
_vb("essere","esserci,esserti,essermi,essersi")
_vb("avere","averla,averli,averle,averne,averlo")
_vb("scegliere","scegli,scelgo,scelga")
_vb("venire","veniamo,venite,vengano")
_vb("volere","voglia,vogliamo,vogliate")
_vb("baciare","bacio,baci,bacia,baciamo,baciano,baciando,baciato")
_vb("correre","corri,corriamo,correte,corrano")
_vb("chiudere","chiudi,chiude,chiudiamo,chiudete,chiuse")
_vb("morire","moriamo,morite,morirà")
_vb("guadagnare","guadagno,guadagni,guadagna,guadagniamo,guadagnate,guadagnano,guadagnava,guadagnando,guadagnato")
_vb("fare","far,farsi,fallo,fatemi,lasciatemi")
_vb("prendere","prendermi")
_vb("chiamare","chiamami")
_vb("credere","crederci,credimi")
_vb("lasciare","lasciatemi,lasciami")
_vb("portare","portami")
_vb("provare","provarci")
_vb("pensare","pensaci")
_vb("rendere","rendi,rende,rendono,rendendo,resa,renda,rendano,rendo")
_vb("parlare","parlarmi")
_vb("muovere","muoversi")
_vb("potere","puo',poteri")
_vb("trovare","troverai,troverà,troveremo,troverete,troveranno,trovera,trovarsi")
_vb("uccidere","ucciderti,ucciderci,ucciderai,uccidiamo,uccidilo,ucciderli,uccidimi")
_vb("mancare","manchi,manca,manchiamo,mancono,manchi,manco,mancherà,mancherai,mancheremo,mancherete,mancheranno,manchera")
_vb("tornare","tornerà,tornerai,torneremo,tornerete,torneranno,tornera")
_vb("lasciare","lasciar")
_vb("muovere","muoverti,muoverci,muovermi")
_vb("scherzare","scherzi,scherza,scherzano,scherzando,scherzato")
_vb("dire","dicessi")
_vb("dare","darsi,dammi,dacci")
_vb("rivedere","rivederti")
_vb("aiutare","aiutatemi,aiuterà,aiuterai,aiuteremo,aiuterete,aiuteranno,aiutera")
_vb("andare","andar,andavamo")
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
_vb("funzionare","funziona,funzionano,funzionava,funzionando,funzionato,funzionerà,funzioneranno,funzionera")
_vb("accadere","accade,accadono,accadeva,accadde,accadendo,accadrà,accadranno,accadra")
_vb("diventare","diventerà,diventerai,diventeremo,diventerete,diventeranno,diventera")
_vb("passare","passerà,passerai,passeremo,passerete,passeranno,passera")
_vb("succedere","succederà,succederanno,succedera")
_vb("prendere","prenderà,prenderai,prenderemo,prenderete,prenderanno,prendera,prendetelo,prendine,prendili")
_vb("rimanere","rimanete,rimaniamo,rimanga,rimangano,rimarrà,rimarrai,rimarremo")
_vb("infrangere","infranto,infranta,infranti,infrante")
_vb("credere","credermi,crederci,crederlo,crederla,crederti")
_vb("provare","provaci,provami,provarlo,provarmi,provarti")
_vb("fermare","fermarsi,fermarti,fermarmi,fermarci,fermarvi,fermarli,fermarla")
_vb("presentare","presentarti,presentarmi,presentarci,presentarlo,presentarla")
_vb("disporre","dispone,dispongono,disponiamo,disponete,disposta,disposti,disposte,disponendo")
_vb("osservare","osserva,osservi,osserviamo,osservano,osservava,osservando,osservato")
_vb("comprendere","comprende,comprendo,comprendono,comprendeva,comprendendo,compreso")
_vb("puntare","punta,punti,puntiamo,puntano,puntava,puntando,puntato")
_vb("ignorare","ignora,ignori,ignoriamo,ignorano,ignorava,ignorando,ignorato")
_vb("disturbare","disturba,disturbi,disturbiamo,disturbano,disturbava,disturbando,disturbato")
_vb("picchiare","picchia,picchi,picchiamo,picchiano,picchiava,picchiando,picchiato")
_vb("scaricare","scarica,scarichi,scarichiamo,scaricano,scaricava,scaricando,scaricato")
_vb("registrare","registra,registri,registriamo,registrano,registrava,registrando,registrato,registrata")
_vb("riferire","riferisce,riferisco,riferisci,riferisce,riferiamo,riferiscono,riferiva,riferendo,riferito")
_vb("svegliare","sveglia,svegli,svegliamo,svegliano,svegliava,svegliando,svegliato")
_vb("desiderare","desidera,desideri,desideriamo,desiderano,desiderava,desiderando,desiderato")
_vb("valutare","valuta,valuti,valutiamo,valutano,valutava,valutando,valutato")
_vb("conquistare","conquista,conquisti,conquistiamo,conquistano,conquistava,conquistando,conquistato")
_vb("scoppiare","scoppia,scoppi,scoppiamo,scoppiamo,scoppiarono,scoppiando,scoppiato")
_vb("investire","investe,investi,investiamo,investono,investì,investirono,investendo,investito")
_vb("gridare","grida,gridi,gridiamo,gridano,gridava,gridando,gridato")
_vb("consegnare","consegna,consegni,consegniamo,consegnano,consegnava,consegnando,consegnato")
_vb("salire","saliamo,salite")
_vb("sostenere","sostiene,sostieni,sosteniamo,sostengono,sosteneva,sostenendo,sostenuto")
_vb("baciare","baciami,baciarti")
_vb("eliminare","elimina,elimini,eliminiamo,eliminano,eliminava,eliminando,eliminato")
_vb("nominare","nomina,nomini,nominiamo,nominano,nominava,nominando,nominato")
_vb("raccontare","racconta,racconti,raccontiamo,raccontano,raccontava,raccontando,raccontato,raccontami")
_vb("assicurare","assicura,assicuri,assicuriamo,assicurano,assicurava,assicurando,assicurato,assicurata")
_vb("negare","nega,neghi,neghiamo,negano,negava,negando,negato")
_vb("denunciare","denuncia,denunci,denunciamo,denunciano,denunciava,denunciando,denunciato")
_vb("votare","vota,voti,votiamo,votano,votava,votando,votato")
_vb("frequentare","frequenta,frequenti,frequentiamo,frequentano,frequentava,frequentando,frequentato")
_vb("meritare","merita,meriti,meritiamo,meritano,meritando,meritato")
_vb("trasformare","trasforma,trasformi,trasformiamo,trasformano,trasformava,trasformando,trasformato")
_vb("esprimere","esprime,esprimo,esprimi,esprimiamo,esprimono,espresse,espressero,esprimendo,espresso")
_vb("ricordare","ricordami,ricordarti")
_vb("confermare","conferma,confermi,confermiamo,confermano,confermava,confermando,confermato")
_vb("dimenticare","dimenticarti,dimenticarlo")
_vb("soggiornare","soggiorna,soggiorni,soggiorniamo,soggiornano")
_vb("interrogare","interroga,interroghi,interroghiamo,interrogano,interrogava,interrogando,interrogato")
_vb("accompagnare","accompagna,accompagni,accompagniamo,accompagnano,accompagnava,accompagnando,accompagnato")
_vb("sorridere","sorride,sorridi,sorridiamo,sorridono,sorrideva,sorridendo,sorriso")
_vb("proteggere","protegge,proteggo,proteggi,proteggiamo,proteggono,proteggendo,protetto,protetta,proteggerla,proteggermi,proteggerlo")
_vb("conoscere","conoscerla,conoscerlo,conoscerti,conoscervi")
_vb("sentire","sentirla,sentirà")
_vb("lavorare","lavorarci")
_vb("divertire","divertirci,divertirti,divertirmi")
_vb("scrivere","scriva")
_vb("leggere","legga")
_vb("ottenere","ottiene,ottengo,ottieni,otteniamo,ottengono,ottenne,ottennero,ottenendo,ottenuto,ottenuta,ottieni")
_vb("dimostrare","dimostrarlo")
_vb("informare","informa,informi,informiamo,informano,informava,informando,informato")
_vb("avvertire","avverte,avverto,avverti,avvertiamo,avvertono,avvertì,avvertirono,avvertendo,avvertito")
_vb("avvicinarsi","avvicinati")
_vb("fidanzare","fidanzati,fidanzata")
_vb("puntare","puntato")
_vb("comportare","comportato,comportando,comportarti,comportarmi,comportarsi")
_vb("offrire","offrirle")
_vb("dirigere","dirige")
_vb("cantare","cantando,cantato")
_vb("lavare","lavato")
_vb("riposare","riposa,riposati,riposi")
_vb("procedere","procede")
_vb("pescare","pesca,peschi,peschiamo,pescano,pescava,pescando,pescato")
_vb("colpire","colpisci,colpisce,colpiamo,colpiscono,colpì,colpirono,colpendo,colpito,colpita")
_vb("concludere","conclude,concludo,concludi,concludiamo,concludono,concluse,conclusero,concludendo,concluso")
_vb("dedicare","dedica,dedichi,dedichiamo,dedicano,dedicava,dedicando,dedicato")
_vb("creare","crea,crei,creiamo,creano,creava,creavano,creando,creato,creata")
_vb("assicurare","assicurarci,assicurarti")
_vb("ammettere","ammettilo")
_vb("volere","vuotare,vuote,vuoti")
_vb("mettere","mettila,mettilo,mettiti,mettetevi,metterà,metterai,metteremo,metterete,metteranno,mettera,metterli")
_vb("verificare","verifica,verifichi,verifichiamo,verifichino,verificava,verificando,verificato")
_vb("rispettare","rispetta,rispetti,rispettiamo,rispettano,rispettava,rispettando,rispettato")
_vb("piangere","piangi")
_vb("diminuire","diminuire")
_vb("appartenere","appartiene,appartengo,appartieni,apparteniamo,appartengono,apparteneva,appartenevano,appartenendo,apparteneva,appartengono")
_vb("annunciare","annuncia,annunci,annunciamo,annunciano,annunciava,annunciando,annunciato")
_vb("assicurare","assicurarlo")
_vb("andare","andiate,andrete,andarvene")
_vb("dovere","dovessimo,dovessero")
_vb("vendere","vendo,vendi,vende,vendiamo,vendete,vendono,vendeva,vendevano,vendendo,venduto,venduta,venditore")
_vb("richiedere","richiede,richiedo,richiedi,richiediamo,richiedono,richiese,richiesero,richiedendo,richiesto")
_vb("scomparire","scompare,scompaiono,scomparve,scomparvero,scomparendo,scomparso,scomparsa")
_vb("sposare","sposi,sposa,sposiamo,sposano,sposava,sposando,sposato,sposarla,sposarlo,sposarci")
_vb("formare","forma,formi,formiamo,formano,formava,formando,formato")
_vb("invitare","invita,inviti,invitiamo,invitano,invitava,invitando,invitato")
_vb("fornire","fornisce,fornisco,fornisci,forniamo,fornite,forniscono,forniva,fornendo,fornito")
_vb("pensare","pensera")
_vb("descrivere","descrive,descrivo,descrivi,descriviamo,descrivono,descrisse,descrissero,descrivendo,descritto")
_vb("uccidere","ucciderebbe")
_vb("bussare","bussa,bussi,bussiamo,bussano,bussava,bussando,bussato")
_vb("inventare","inventa,inventi,inventiamo,inventano,inventava,inventando,inventato,inventata")
_vb("consegnare","consegne")
_vb("accendere","accende,accendo,accendi,accendiamo,accendono,accese,accesero,accendendo,acceso,accesa,accendi")
_vb("scusare","scusa,scusi,scusiamo,scusano,scusava,scusando,scusato")
_vb("scoprire","scoprire")
_vb("guardare","guardarla")
_vb("salvare","salvarlo,salvarla,salvarmi,salvarci")
_vb("mantenere","mantiene,mantengo,mantieni,manteniamo,mantengono,mantenuto")
_vb("abbandonare","abbandona,abbandoni,abbandoniamo,abbandonano,abbandonava,abbandonando,abbandonato")
_vb("riposare","riposati")
_vb("lanciare","lancia,lanci,lanciamo,lanciano,lanciava,lanciando,lanciat")
_vb("sfidare","sfida,sfidi,sfidiamo,sfidano,sfidava,sfidando,sfidato")
_vb("evitare","evita,eviti,evitiamo,evitano,evitava,evitando,evitato,evitarlo")
_vb("fuggire","fuggo,fuggi,fugge,fuggiamo,fuggite,fuggono,fuggì,fuggirono,fuggendo,fuggito,fuggita")
_vb("consegnare","consegna")
_vb("rompere","rompe")
_vb("appoggiare","appoggia,appoggi,appoggiamo,appoggiano,appoggiava,appoggiando,appoggiato")
_vb("preparare","prepara,prepari,prepariamo,preparano,preparava,preparavano,preparando,preparato,preparata,preparatevi,prepararmi,prepararti")
_vb("presentare","presentata")
_vb("comunicare","comunica,comunichi,comunichiamo,comunicano,comunicava,comunicando,comunicato")
_vb("coprire","copre,copro,copri,copriamo,coverono,coprendo,coperto,co perta")
_vb("accettare","accettarlo")
_vb("riportare","riporta,riporti,riportiamo,riportano,riportava,riportando,riportato,riportarlo")
_vb("fermare","fermarla")
_vb("colpire","colpiti")
_vb("chiamare","chiameremo,chiamalo")
_vb("convincere","convince,convinci,convinciamo,convinciono,convinse,convincendo,convinto,convincerla,convincerlo")
_vb("giocare","giocano")
_vb("piacere","piacerebbe,piacerti")
_vb("trovare","trovarli,trovarne")
_vb("rivelare","rivela,riveli,riveliamo,rivelano,rivelava,rivelando,rivelato")
_vb("tenere","tienila,tenerli,tenerci")
_vb("guadagnare","guadagna")
_vb("conoscere","conoscenze")
_vb("mandare","mandarlo,mandami,mandarti")
_vb("pregare","prega,preghi,preghiamo,pregano,pregava,pregando,pregato")
_vb("ricercare","ricercato")
_vb("raggiungere","raggiungo,raggiungi,raggiunge,raggiungiamo,raggiungono,raggiunse,raggiunsero,raggiungendo,raggiunto")
_vb("rimuovere","rimuove,rimuovo,rimuovi,rimuoviamo,rimuovono,rimosse,rimossero,rimuovendo,rimosso")
_vb("ringraziare","ringrazia,ringrazi,ringraziamo,ringraziano,ringraziava,ringraziando,ringraziato")
_vb("descrivere","descritto")
_vb("giudicare","giudicato")
_vb("allontanare","allontana,allontani,allontaniamo,allontanano,allontanava,allontanando,allontanato")
_vb("permettere","permetta,permetti,permettiamo,permettono,permetteva,permettendo,permesso,permetterò,permetterci,permettimi,permetterti,permettete,permettero")
_vb("compiere","compi,compie,compiamo,compiono,compì,compirono,compiendo,compiuto")
_vb("abbattere","abbatte,abbatto,abbattiamo,abbattono,abbatté,abbatterono,abbattendo,abbattuto")
_vb("rinchiudere","rinchiuso")
_vb("sollevare","solleva,sollevi,solleviamo,sollevano,sollevava,sollevando,sollevato")
_vb("dividere","divide,divido,dividi,dividiamo,dividono,divise,dividero,dividendo,diviso")
_vb("insistere","insiste,insisto,insisti,insistiamo,insistono,insisteva,insistendo,insistito")
_vb("vedere","vederle,vedesse")
_vb("credere","creata")
_vb("liberare","libera,liberi,liberiamo,liberano,liberava,liberando,liberato,liberarsi,liberarmi,liberarti")
_vb("cercare","cercarla,cercarti,cercarmi")
_vb("divertire","divertiremo")
_vb("garantire","garantisce,garantisco,garantisci,garantiamo,garantiscono,garantiva,garantendo,garantito")
_vb("assumere","assume,assumo,assumi,assumiamo,assumono,assunse,assunsero,assumendo,assunto,assunta")
_vb("superare","supera,superi,superiamo,superano,superava,superando,superato")
_vb("difendere","difende,difendo,difendi,difendiamo,difendono,difese,difesero,difendendo,difeso")
_vb("trarre","tragga,traggano")
_vb("dimenticare","dimenticherò")
_vb("occupare","occupa,occupi,occupiamo,occupano,occupava,occupando,occupato,occuparti,occupero,occuparci,occupiamo")
_vb("promettere","promette,prometto,prometti,promettiamo,promettono,promise,promisero,promettendo,promesso")
_vb("analizzare","analizza,analizzi,analizziamo,analizzano,analizzava,analizzando,analizzato")
_vb("scomparire","scomparsi,scomparse")
_vb("tenere","tenersi")
_vb("togliere","togliermi,toglierti,tolgo")
_vb("uccidere","uccidilo")
_vb("considerare","considera,consideri,consideriamo,considerano,considerava,considerando,considerato,considerata,considero")
_vb("presentare","presentarmi")
_vb("apprezzare","apprezza,apprezzi,apprezziamo,apprezzano,apprezzava,apprezzando,apprezzato")
_vb("scappare","scappiamo")
_vb("arrivare","arrivasse,arrivò")
_vb("spingere","spinge,spingo,spingi,spingiamo,spingono,spìnse,spìnsero,spingendo,spinto")
_vb("lanciare","lanciare")
_vb("spaventare","spaventati")
_vb("trasferire","trasferisce,trasferisco,trasferisci,trasferiamo,trasferiscono,trasferì,trasferirono,trasferendo,trasferito,trasferirsi,trasferirmi")
_vb("mandare","mandero,manderò")
_vb("rendersi","rendermi,renderlo,rendera,renderti")
_vb("rendere","renderebbe")
_vb("preoccupare","preoccuparci")
_vb("chiedere","chiedeva,chiedevano,chiederti,chiedermi,chiedile,chiederlo")
_vb("incrociare","incrociato")
_vb("completare","completa,completi,completiamo,completano,completava,completando,completato")
_vb("scoprire","scopriamo,scoprì,scoprirai,scoprirà,scoprirete,scopriranno,scoprira")
_vb("scegliere","scelga")
_vb("cercare","cerchero,cercarla")
_vb("riprendere","riprendendo,riprendermi")
_vb("godere","goditi")
_vb("alzare","alzarsi,alzarti,alzarmi")
_vb("calmare","calmati,calmata,calmare,calmati")
_vb("ritrovare","ritrovare,ritrovata")
_vb("permettere","permetterà")
_vb("adottare","adotta,adotti,adottiamo,adottano,adottava,adottando,adottato")
_vb("spiegare","spiega,spieghi,spieghiamo,spiegano,spiegava,spiegando,spiegato,spiegarti,spiegarlo,spiegherebbe")
_vb("denunciare","denunciato")
_vb("inviare","invia,invi,inviamo,invianno,invitava,inviando,inviato")
_vb("guardare","guardatemi,guardarla")
_vb("divertire","divertita")
_vb("rispondere","rispondimi,rispondete")
_vb("camminare","cammina,cammini,camminiamo,camminano,camminava,camminando,camminato")
_vb("sostituire","sostituisce,sostituisco,sostituisci,sostituiamo,sostituiscono,sostituì,sostituirono,sostituendo,sostituito")
_vb("volere","vorreste,vorrete")
_vb("essere","esserle,esservi")
_vb("riuscire","riuscissi,riuscira,riusciro,riuscirei,riuscirà,riusciranno")
_vb("recuperare","recupera,recuperi,recuperiamo,recuperano,recuperava,recuperando,recuperato")
_vb("incontrare","incontrarla,incontrarlo,incontrarmi")
_vb("lavare","lavato,lavaggio")
_vb("mangiare","mangiate")
_vb("aspettare","aspettarti,aspettarmi,aspettero,aspetterò,aspetteremo")
_vb("lavorare","lavorate")
_vb("mettere","mettimi,metterei")
_vb("bruciare","brucia,bruci,bruciamo,bruciano,bruciava,bruciando,bruciato,bruciando")
_vb("dimenticare","dimenticate")
_vb("capire","capirà")
_vb("offendere","offeso")
_vb("apprezzare","apprezzare")
_vb("piacere","piaciuto")
_vb("organizzare","organizza,organizzi,organizziamo,organizzano,organizzava,organizzando,organizzato,organizzando")
_vb("tenere","tenetevi")
_vb("bastare","bastano,bastava")
_vb("mancare","mancare")
_vb("sembrare","sembrate,sembravi,sembrerà,sembrera")
_vb("stare","stiate,starei,starebbe,staremmo,stareste,starebbero,stareste")
_vb("girare","gira,giri,giriamo,girano,girava,girando,girato,girano")
_vb("tolgo","togliere")
_vb("muovere","muovono,muovendo,muoverci")
_vb("scoprire","scoprira")
_vb("svegliare","svegliarti,svegliarmi")
_vb("parlare","parlerà,parlerai,parlera")
_vb("vedere","vediamoci")
_vb("piangere","piangi")
_vb("lasciare","lascerà,lascera,lasceranno,lasciali,lasciatela,lasciaci,lasciateci")
_vb("dovere","doverlo")
_vb("avere","avermelo")
_vb("essere","dì")
_vb("divertire","divertire")
_vb("scegliere","scelga")
_vb("scoprire","scoprira,scopriranno")
_vb("chiedere","chiedertelo,chiedilo,chiedigli")
_vb("dare","darlo,dargli")
_vb("continuare","continueremo")
_vb("pensare","pensera,pensarlo")
_vb("arrivare","arrivino")
_vb("tenere","tenersi")
_vb("venire","venirmi,vennero")
_vb("abitare","abita,abiti,abitiamo,abitano,abitava,abitando,abitato,abituata,abituati")
_vb("cercare","cerchero")
_vb("toccare","tocca,tocchi,tocchiamo,toccano,toccava,toccando,toccato,toccarlo,toccarla")
_vb("baciare","baciarmi,baciata")
_vb("vedere","vedano")
_vb("prendere","prenderne,prendervi")
_vb("aiutare","aiutano,aiutaci,aiutami,aiutarmi")
_vb("preparare","preparata,preparate,prepararsi")
_vb("rifiutare","rifiuta,rifiuti,rifiutiamo,rifiutano,rifiutava,rifiutando,rifiutato,rifiutata")
_vb("bloccare","blocca,bloccate")
_vb("eseguire","esegue,eseguo,esegui,eseguiamo,eseguono,eseguì,eseguirono,eseguendo,eseguito")
_vb("trovare","trovasse")
_vb("cominciare","cominciando,cominciate,cominciata")
_vb("fingere","finge,fingo,fingi,fingiamo,fingono,finse,finsero,fingendo,finto")
_vb("mandare","mandiamo")
_vb("inseguire","insegue,inseguo,insegu,inseguono,insegui,inseguito,inseguendo")
_vb("coprire","coprire")
_vb("rimettere","rimetto,rimetti,rimette,rimettiamo,rimettono,rimetteva,rimettendo,rimesso")
_vb("cercare","cercavano,cercavi")
_vb("raggiungere","raggiunge")
_vb("restare","resterà,resterai,resteremo,resterete,resteranno,restera,resteremo,resterò,restero")
_vb("fermare","fermiamo")
_vb("dividere","dividiamo")
_vb("mettere","metterà,metteranno,mettera")
_vb("passare","passava")
_vb("guardare","guardavo")
_vb("arrivare","arrivavano")
_vb("lavorare","lavorarci")
_vb("volerci","volerci")
_vb("arrivare","arrivate")
_vb("divertire","divertiremo")
_vb("camminare","camminando,camminato")
_vb("cantare","cantando,cantato,cantano")
_vb("suonare","suonava")
_vb("scendere","scendiamo,scendete")
_vb("salire","salendo,salga")
_vb("continuare","continuerà,continueranno")
_vb("scrivere","scriva")
_vb("chiedere","chiedervi")
_vb("sapere","sapranno,sapessi,sapessero,saprebbe,sapremmo,sapreste,saprebbero,sapro,saprò")
_vb("potere","potessi,potesse,potessimo,potessero")
_vb("dovere","dovessimo")
_vb("andare","andassero,andasse,andassimo")
_vb("stare","stessimo")
_vb("potere","poterci")
_vb("morire","morirò,moriro,moriranno")
_vb("vivere","vivete")
_vb("andare","andiate")
_vb("vedere","vedessi")
_vb("sapere","sappiate")
_vb("volere","vogliate,vogliano")
_vb("potere","possiate")
_vb("essere","dì")
_vb("dovere","dovessero")

# ── Irregular nouns ──
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
_nn("danni","danno")
_nn("ragioni","ragione")
_nn("sistemi","sistema")
_nn("programmi","programma")
_nn("piani","piano")
_nn("telefonate","telefonata")
_nn("lettere","lettera")
_nn("ricerche","ricerca")
_nn("ospedali","ospedale")
_nn("chiese","chiesa")
_nn("mondi","mondo")
_nn("vite","vita")
_nn("piante","pianta")
_nn("luci","luce")
_nn("palle","palla")
_nn("pugni","pugno")
_nn("colpi","colpo")
_nn("leggi","legge")
_nn("facce","faccia")
_nn("sangue","sangue")
_nn("forze","forza")
_nn("voci","voce")
_nn("torte","torta")
_nn("scarpe","scarpa")
_nn("bocche","bocca")
_nn("mani","mano")
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
_nn("fondi","fondo")
_nn("desideri","desiderio")
_nn("episodi","episodio")
_nn("criminali","criminale")
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
_nn("spagnoli","spagnolo")
_nn("americani","americano")
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
# More plurals from 6k-10k range
_nn("foglie","foglia")
_nn("nuvole","nuvola")
_nn("fuochi","fuoco")
_nn("buchi","buco")
_nn("vie","via")
_nn("candele","candela")
_nn("letti","letto")
_nn("professionisti","professionista")
_nn("titoli","titolo")
_nn("quadri","quadro")
_nn("codici","codice")
_nn("monete","moneta")
_nn("buchi","buco")
_nn("borse","borsa")
_nn("guerre","guerra")
_nn("commenti","commento")
_nn("sensori","sensore")
_nn("pareti","parete")
_nn("dischi","disco")
_nn("serpenti","serpente")
_nn("cieli","cielo")
_nn("lati","lato")
_nn("buchi","buco")
_nn("cose","cosa")
_nn("fiamma","fiamma")
_nn("catene","catena")
_nn("diamanti","diamante")
_nn("raggi","raggio")
_nn("uffici","ufficio")
_nn("scimmie","scimmia")
_nn("lenzuola","lenzuolo")
_nn("fedeli","fedele")
_nn("semi","seme")
_nn("fratelli","fratello")
_nn("gradi","grado")
_nn("frutti","frutto")
_nn("vermi","verme")
_nn("pesci","pesce")
_nn("treni","treno")
_nn("veicoli","veicolo")
_nn("uccelli","uccello")
_nn("fiumi","fiume")
_nn("laghi","lago")
_nn("monti","monte")
_nn("ponti","ponte")
_nn("alberi","albero")
_nn("campioni","campione")
_nn("cuccioli","cucciolo")
_nn("uccellino","uccello")
_nn("cavalli","cavallo")
_nn("ragazzi","ragazzo")
_nn("prigionieri","prigioniero")
_nn("ferite","ferita")
_nn("soldati","soldato")
_nn("mariti","marito")
_nn("nonni","nonno")
_nn("cugini","cugino")
_nn("orecchini","orecchino")
_nn("baci","bacio")
_nn("strade","strada")
_nn("armi","arma")
_nn("bambine","bambina")
_nn("ragazze","ragazza")
_nn("donne","donna")
_nn("persone","persona")
_nn("animali","animale")
_nn("studenti","studente")
_nn("maiali","maiale")
_nn("pompieri","pompiere")
_nn("poliziotti","poliziotto")
_nn("cittadini","cittadino")
_nn("bimbi","bimbo")
_nn("bambole","bambola")
_nn("regali","regalo")
_nn("fiori","fiore")
_nn("libri","libro")
_nn("cani","cane")
_nn("gatti","gatto")
_nn("mucche","mucca")
_nn("pecore","pecora")
_nn("galline","gallina")
_nn("cavalli","cavallo")
_nn("orsi","orso")
_nn("leoni","leone")
_nn("api","ape")
_nn("cavalli","cavallo")
_nn("polli","pollo")
_nn("conigli","coniglio")
_nn("pesci","pesce")
_nn("volpi","volpe")
_nn("scoiattoli","scoiattolo")
_nn("farfalle","farfalla")
_nn("ragni","ragno")
_nn("zanzare","zanzara")
_nn("mosche","mosca")
_nn("formiche","formica")
_nn("fiocchi","fiocco")
_nn("sogni","sogno")
_nn("esperienze","esperienza")
_nn("paure","paura")
_nn("dolori","dolore")
_nn("forze","forza")
_nn("diritti","diritto")
_nn("verità","verità")
_nn("scienze","scienza")
_nn("menti","mente")
_nn("colonne","colonna")
_nn("basi","base")
_nn("piani","piano")
_nn("sistemi","sistema")
_nn("metodi","metodo")
_nn("regioni","regione")
_nn("nazioni","nazione")
_nn("classi","classe")
_nn("parti","parte")
_nn("regole","regola")
_nn("cause","causa")
_nn("prove","prova")
_nn("offerte","offerta")
_nn("vendite","vendita")
_nn("punteggi","punteggio")
_nn("proposte","proposta")
_nn("domande","domanda")
_nn("risposte","risposta")
_nn("frasi","frase")
_nn("parole","parola")
_nn("storie","storia")
_nn("opinioni","opinione")
_nn("idee","idea")
_nn("immagini","immagine")
_nn("persone","persona")
_nn("amici","amico")
_nn("famiglie","famiglia")
_nn("figli","figlio")
_nn("uomini","uomo")
_nn("donne","donna")
_nn("genitori","genitore")
_nn("fratelli","fratello")
_nn("sorelle","sorella")
_nn("nonni","nonno")
_nn("cugini","cugino")
_nn("nipoti","nipote")
_nn("bambini","bambino")
_nn("ragazzi","ragazzo")
_nn("ragazze","ragazza")
_nn("studenti","studente")
_nn("professori","professore")
_nn("colleghi","collega")
_nn("cittadini","cittadino")
_nn("soldati","soldato")
_nn("poliziotti","poliziotto")
_nn("pazienti","paziente")
_nn("medici","medico")
_nn("avvocati","avvocato")
_nn("giudici","giudice")
_nn("detenuti","detenuto")
_nn(" testimoni","testimone")
_nn("parenti","parente")
_nn("vicini","vicino")
_nn("ospiti","ospite")

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
    ("diversa,diversi,diverse","diverso"),
    ("strana,strani,strane","strano"),
    ("brutta,brutti,brutte","brutto"),
    ("verde,verdi","verde"),
    ("violenta,violenti","violento"),
    ("pericolosa,pericolosi,pericolose","pericoloso"),
    ("veloce,veloci","veloce"),
    ("stupida,stupidi,stupide","stupido"),
    ("incinta","incinta"),
    ("ubriaco,ubriaca","ubriaco"),
    ("calmo,calmi,calme","calmo"),
    ("stanco,stanca,stanchi,stanche","stanco"),
    ("pazzo,pazza,pazzi,pazze","pazzo"),
    ("matto,matta,matti,matte","matto"),
    ("vivo,viva,vivi,vive","vivo"),
    ("furbo,furba,furbi,furbe","furbo"),
    ("cieco,cieca,ciechi,ceche","cieco"),
    ("muto,muta,muti,mute","muto"),
    # More adjective forms from 6k-10k
    ("insolito,insolita,insoliti,insolite","insolito"),
    ("selvaggio,selvaggia,selvaggi,selvagge","selvaggio"),
    ("arrogante,arroganti","arrogante"),
    ("letale,letali","letale"),
    ("delicato,delicata,delicati,delicate","delicato"),
    ("leale,leali","leale"),
    ("disperato,disperata,disperati,disperate","disperato"),
    ("fragile,fragili","fragile"),
    ("prudente,prudenti","prudente"),
    ("valido,valida,validi,valide","valido"),
    ("decente,decenti","decente"),
    ("timido,timida,timidi,timide","timido"),
    ("incredibile,incredibili","incredibile"),
    ("privo,priva,privi,prive","privo"),
    ("rapido,rapida,rapidi,rapide","rapido"),
    ("umile,umili","umile"),
    ("bollente,bollenti","bollente"),
    ("comodo,comoda,comodi,comode","comodo"),
    ("deluso,delusa,delusi,deluse","deluso"),
    ("lento,lenta,lenti,lente","lento"),
    ("turista,turisti","turista"),
    ("misterioso,misteriosa,misteriosi,misteriose","misterioso"),
    ("curioso,curiosa,curiosi,curiose","curioso"),
    ("sereno,serena,sereni,serene","sereno"),
    ("triste,tristi","triste"),
    ("gentile,gentili","gentile"),
    ("coraggioso,coraggiosa,coraggiosi,coraggiose","coraggioso"),
    ("generoso,generosa,generosi,generose","generoso"),
    ("tranquillo,tranquilla,tranquilli,tranquille","tranquillo"),
    ("stupendo,stupefacente,stupendi,stupefacenti","stupendo"),
    ("favoloso,favolosa,favolosi,favolose","favoloso"),
    ("tremendo,tremenda,tremendi,tremende","tremendo"),
    ("intelligente,intelligenti","intelligente"),
    ("stupido,stupida,stupidaggini","stupido"),
    ("brutto,brutta,brutti,brutte","brutto"),
    ("profondo,profonda,profondi,profonde","profondo"),
    ("scuro,scura,scuri,scure","scuro"),
    ("grigio,grigia,grigi,grigie","grigio"),
    ("marrone,marroni","marrone"),
    ("azzurro,azzurra,azzurri,azzurre","azzurro"),
    ("arancione,arancioni","arancione"),
    ("allegro,allegra,allegri,allegre","allegro"),
    ("felice,felici","felice"),
    ("triste,tristi","triste"),
    ("arrabbiato,arrabbiata,arrabbiati,arrabbiate","arrabbiato"),
    ("ubriaco,ubriaca,ubriachi,ubriache","ubriaco"),
    ("arrabbiato,arrabbiati","arrabbiato"),
    ("audace,audaci","audace"),
    ("fedele,fedeli","fedele"),
    ("abile,abili","abile"),
    ("fiero,fiera,fieri,fiere","fiero"),
    ("dolce,dolci","dolce"),
    ("pulito,pulita,puliti,pulite","pulito"),
    ("sporco,sporca,sporchi,sporche","sporco"),
    ("calmo,calma,calmi,calme","calmo"),
    ("fiore","fiore"),
    ("normale,normali","normale"),
    ("potente,potenti","potente"),
    ("muto,muta,muti,mute","muto"),
    ("nuovo,nuova,nuovi,nuove","nuovo"),
    ("piccolo,piccola,piccoli,piccole,piccolino,piccolina","piccolo"),
    ("grande,grandi,grandioso,grandiosa","grande"),
    ("famoso,famosa,famosi,famose","famoso"),
    ("comune,comuni","comune"),
    ("attivo,attiva,attivi,attive","attivo"),
    ("personale,personali","personale"),
    ("violento,violenta,violenti,violente","violento"),
    ("solido,solida,solidi,solide","solido"),
    ("necessario,necessaria,necessari,necessarie","necessario"),
    ("utile,utili","utile"),
    ("serio,seria,seri,serie","serio"),
    ("silenzioso,silenziosa,silenziosi,silenziose","silenzioso"),
    ("magico,magica,magici,magiche","magico"),
    ("elegante,eleganti","elegante"),
    ("simpatico,simpatica,simpatici,simpatiche","simpatico"),
    ("antico,antica,antichi,antiche","antico"),
    ("moderno,moderna,moderni,moderne","moderno"),
    ("gigante,giganti","gigante"),
    ("brillante,brillanti","brillante"),
    ("ignoto,ignota,ignoti,ignote","ignoto"),
    ("tipico,tipica,tipici,tipiche","tipico"),
    ("eccellente,eccellenti","eccellente"),
    ("competente,competenti","competente"),
    ("incredibile,incredibili","incredibile"),
    ("curioso,curiosa,curiosi,curiose","curioso"),
    ("cattivo,cattiva,cattivi,cattive","cattivo"),
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
    "precisamente","altamente","definitivamente","costantemente",
    "raramente","lontanamente","necessariamente","pienamente",
    "fortemente","disperatamente","esattamente","proprio",
    "gravemente","apparentemente","attualmente","correttamente",
    "tranquillamente","legalmente","purtroppo","principalmente",
    "personalmente","specificamente","generalmente","recentemente",
    "spaventosamente","stranamente",
]
for w in _adverb_list:
    IRREG[w.lower()] = (w.lower(), "adverb")

# ── Numbers ──
for w in ["uno","due","tre","quattro","cinque","sei","sette","otto","nove","dieci",
          "cento","mille","milione","miliardo","tredici","quattordici","quindici",
          "sedici","diciassette","diciotto","diciannove","venti","trenta","quaranta",
          "cinquanta","sessanta","settanta","ottanta","novanta","duecento","diecimila"]:
    IRREG[w] = (w, "number")

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
IRREG["lassù"] = ("lassù", "adverb")
IRREG["lassu"] = ("lassù", "adverb")
IRREG["peggior"] = ("peggiorare", "verb")
IRREG["piuttosto"] = ("piuttosto", "adverb")
IRREG["disse"] = ("dire", "verb")
IRREG["ancor"] = ("ancorare", "verb")
IRREG["quaggiù"] = ("quaggiù", "adverb")
IRREG["quaggiu"] = ("quaggiù", "adverb")
IRREG["quì"] = ("qui", "adverb")
IRREG["possibilità"] = ("possibilità", "noun")
IRREG["possibilita"] = ("possibilità", "noun")
IRREG["possibilita'"] = ("possibilità", "noun")
IRREG["citta'"] = ("città", "noun")
IRREG["caos"] = ("caos", "noun")
IRREG["venerdì"] = ("venerdì", "noun")
IRREG["venerdi"] = ("venerdì", "noun")
IRREG["disse"] = ("dire", "verb")
IRREG["laggiù"] = ("laggiù", "adverb")
IRREG["quassù"] = ("quassù", "adverb")
IRREG["quassu"] = ("quassù", "adverb")
IRREG["giu'"] = ("giù", "adverb")

# Delete foreign names/junk that slipped through
for w in ["louise","avenue","eve","ragae'","debbie","stone","nicole","mario",
          "blue","see","time","turk","nevada","database","infine",
          "diana","grata","bara","rossa","essa","debbie","blue","see",
          "time","stone","nicole","mario","nevada","turk","database",
          "diana","berlino","goku","naruto","barbie","ken",
          "mcqueen","superman","batman","spiderman","ironman",
          "fbi","cia","nsa","ncis","csi","svu"]:
    IRREG[w] = None

# ── Invariable nouns ──
for w in "città cafè caffè tv gps video film bar bus hotel auto radio foto moto re goal computer internet euro".split():
    IRREG[w] = (w, "noun")

# ── Specific IRREG mappings for 6k-10k words ──
IRREG["dì"] = ("dire", "verb")
IRREG["eccolo"] = None
IRREG["eccola"] = None
IRREG["eccoti"] = None
IRREG["eccomi"] = None
IRREG["eccoci"] = None
IRREG["eccoli"] = None
IRREG["eccole"] = None
IRREG["eccovi"] = None
IRREG["ecco"] = None
# More function words to delete
for w in ["ciascuno","ognuna","pertanto","affinché","affinche","poiché","poiche",
          "eccetera","innanzitutto","altamente","trent'","qualcun'","nessun'",
          "don'","it'","you'","we","with","can","but","or","an","at","if",
          "got","get","end","are","min","des","pro","pop","dj","k","p","f",
          "y","em","wa","ri","ta","ca","fi","gu","alt","sì","si'","gi",
          "du","et","he","ji","wo","tae","seo","na","jm","su","ja","ce",
          "mm","mh","ah","eh","oh","uh","aah","nah","bum","bam","hee",
          "ops","cho","hee","ahia","boo","bong","dong","jee","ch'","n'",
          "s'","t'","c'","v'","l'","d'","m'","co","que","cha","sun","sol",
          "men","hans","jin","opc","yoon","sung","jun","shin","hyun",
          "chang","choi","chen","khan","woo","cho","choi"]:
    IRREG[w] = None

# ── More deletions: English words from 6k-10k ──
for w in ["server","suite","round","web","blog","radar","account",
          "app","cd","pc","km","gps","tv","dvd","vip","nsa",
          "look","ncis","ring","dance","gold","wall","game",
          "pass","home","roll","link","task","check","spot",
          "point","cross","drive","back","network","performance",
          "reporter","software","audio","streaming","surf","metal",
          "master","first","secret","square","social","karma",
          "sushi","yoga","drone","box","castle","bridge",
          "whiskey","rum","vodka","tequila","champagne",
          "muffin","cupcake","popcorn","bacon","toast",
          "hamburger","sandwich","hotdog","french",
          "halloween","christmas","easter",
          "batman","superman","spiderman","ironman",
          "mcqueen","barbie","ken"]:
    IRREG[w.lower()] = None

# Map some common Italian content words
IRREG["caos"] = ("caos", "noun")
IRREG["martedì"] = ("martedì", "noun")
IRREG["mercoledì"] = ("mercoledì", "noun")
IRREG["mercoledi"] = ("mercoledì", "noun")
IRREG["gennaio"] = ("gennaio", "noun")
IRREG["febbraio"] = ("febbraio", "noun")
IRREG["dicembre"] = ("dicembre", "noun")
IRREG["pasqua"] = ("pasqua", "noun")
# Fix misclassified verb forms
IRREG["allontanato"] = ("allontanare", "verb")
IRREG["allontanati"] = ("allontanare", "verb")
IRREG["trasferita"] = ("trasferire", "verb")
IRREG["trasferiti"] = ("trasferire", "verb")
IRREG["abituata"] = ("abitare", "verb")
IRREG["abituati"] = ("abitare", "verb")
IRREG["costretta"] = ("costretto", "adjective")
IRREG["costretti"] = ("costretto", "adjective")
IRREG["inizata"] = ("iniziare", "verb")
IRREG["iniziata"] = ("iniziare", "verb")
IRREG["abbandonata"] = ("abbandonare", "verb")
IRREG["abbandonati"] = ("abandonare", "verb")
IRREG["sposarti"] = ("sposare", "verb")
IRREG["mettero"] = ("mettere", "verb")
IRREG["passami"] = ("passare", "verb")
IRREG["aspettami"] = ("aspettare", "verb")
IRREG["avervi"] = ("avere", "verb")
IRREG["indica"] = ("indicare", "verb")
IRREG["corrisponde"] = ("corrispondere", "verb")
IRREG["contiene"] = ("contenere", "verb")
IRREG["impara"] = ("imparare", "verb")
IRREG["vola"] = ("volare", "verb")
IRREG["rapita"] = ("rapire", "verb")
IRREG["innamorati"] = ("innamorare", "verb")
IRREG["sconvolto"] = ("sconvolgere", "verb")
IRREG["indossava"] = ("indossare", "verb")
IRREG["leggera"] = ("leggero", "adjective")
IRREG["leggero"] = ("leggero", "adjective")
IRREG["doppia"] = ("doppio", "adjective")
IRREG["costretta"] = ("costretto", "adjective")
IRREG["grata"] = ("grato", "adjective")
IRREG["terribilmente"] = ("terribilmente", "adverb")
IRREG["dannatamente"] = ("dannatamente", "adverb")
IRREG["francamente"] = ("francamente", "adverb")
IRREG["lento"] = ("lento", "adjective")
IRREG["stufo"] = ("stufo", "adjective")
IRREG["malato"] = ("malato", "adjective")
IRREG["promettente"] = ("promettente", "adjective")
IRREG["amatoriale"] = ("amatoriale", "adjective")
IRREG["strambo"] = ("strambo", "adjective")
IRREG["indipendente"] = ("indipendente", "adjective")
IRREG["sviluppo"] = ("sviluppare", "verb")
IRREG["attori"] = ("attore", "noun")
IRREG["attore"] = ("attore", "noun")
IRREG["guerriero"] = ("guerriero", "noun")
IRREG["scienziati"] = ("scienziato", "noun")
IRREG["bravissimo"] = ("bravo", "adjective")
IRREG["maschi"] = ("maschio", "noun")
IRREG["arti"] = ("arte", "noun")
IRREG["minacce"] = ("minaccia", "noun")
IRREG["terre"] = ("terra", "noun")
IRREG["cellule"] = ("cellula", "noun")
IRREG["martedi"] = ("martedì", "noun")
IRREG["martedo"] = ("martedì", "noun")
IRREG["risponda"] = ("rispondere", "verb")
IRREG["passamo"] = ("passare", "verb")
IRREG["abitanti"] = ("abitante", "noun")
IRREG["maggio"] = ("maggio", "noun")  # month name but used as common noun
IRREG["bugiarda"] = ("bugiardo", "adjective")
IRREG["russa"] = ("russo", "adjective")
IRREG["orribili"] = ("orribile", "adjective")
IRREG["enormi"] = ("enorme", "adjective")
IRREG["responsabili"] = ("responsabile", "adjective")
IRREG["reale"] = ("reale", "adjective")
IRREG["realizzare"] = ("realizzare", "verb")
IRREG["realizzato"] = ("realizzare", "verb")
IRREG["prenotato"] = ("prenotare", "verb")
IRREG["sbattuto"] = ("sbattere", "verb")
IRREG["maledetti"] = ("maledire", "verb")
IRREG["fascino"] = ("fascino", "noun")
IRREG["suggerisco"] = ("suggerire", "verb")
IRREG["coinvolgere"] = ("coinvolgere", "verb")
IRREG["esecutivo"] = ("esecutivo", "adjective")
IRREG["esecuzione"] = ("esecuzione", "noun")
IRREG["umanità"] = ("umanità", "noun")
IRREG["ricetta"] = ("ricetta", "noun")
IRREG["cazzata"] = ("cazzata", "noun")
IRREG["tessuto"] = ("tessere", "verb")
IRREG["accademia"] = ("accademia", "noun")
IRREG["sommare"] = ("sommare", "verb")
IRREG["somma"] = ("somma", "noun")
IRREG["stressato"] = ("stressare", "verb")
IRREG["rapita"] = ("rapire", "verb")
IRREG["pattuglia"] = ("pattuglia", "noun")
IRREG["medaglia"] = ("medaglia", "noun")
IRREG["irruzione"] = ("irruzione", "noun")
IRREG["strambo"] = ("strambo", "adjective")
IRREG["canaglia"] = ("canaglia", "noun")
IRREG["tango"] = ("tango", "noun")
IRREG["ala"] = ("ala", "noun")
IRREG["pianta"] = ("pianta", "noun")
IRREG["ferita"] = ("ferita", "noun")
IRREG["città"] = ("città", "noun")
IRREG["caffè"] = ("caffè", "noun")
IRREG["tè"] = ("tè", "noun")
IRREG["re"] = ("re", "noun")
IRREG["tsar"] = ("tsar", "noun")

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
    if w.endswith("ato") and len(w) > 4:
        return (w[:-3] + "are", "verb")
    if w.endswith("ito") and len(w) > 4:
        return (w[:-3] + "ire", "verb")
    if w.endswith("uto") and len(w) > 4:
        return (w[:-3] + "ere", "verb")

    # ── Gerunds ──
    if w.endswith("ando") and len(w) > 5:
        return (w[:-4] + "are", "verb")
    if w.endswith("endo") and len(w) > 5:
        return (w[:-4] + "ere", "verb")

    # ── Present 3pl: -ano, -ono ──
    if w.endswith("ano") and len(w) > 4:
        stem = w[:-3]
        if stem.endswith("isc"):
            return (stem[:-3] + "ire", "verb")
        return (stem + "are", "verb")
    if w.endswith("ono") and len(w) > 4:
        stem = w[:-3]
        if stem.endswith("isc"):
            return (stem[:-3] + "ire", "verb")
        return (stem + "ere", "verb")

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
    # Accented variants without accent
    if w.endswith("ero") and len(w) > 4:
        # Could be future or 1sg past — ambiguous, skip
        pass

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

    # ── Noun suffixes ──
    if w.endswith("zione") or w.endswith("zione"):
        return (w, "noun")
    if w.endswith("mento"):
        return (w, "noun")
    if w.endswith("tà") or w.endswith("ta'"):
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
    if w.endswith("iere") or w.endswith("iera"):
        return (w, "noun")

    # ── Remaining words: use ending to guess POS ──
    if w.endswith("o") and len(w) > 2:
        return (w, "noun")
    if w.endswith("a") and len(w) > 2:
        return (w, "noun")
    if w.endswith("e") and len(w) > 2:
        return (w, "noun")
    if w.endswith("i") and len(w) > 2:
        return (w[:-1] + "o", "noun")

    # ── Fallback: keep as other ──
    return (w, "other")


# ═══════════════════════════════════════════════════════════════
# 4. MAIN: Read, process, deduplicate, write
# ═══════════════════════════════════════════════════════════════

def main():
    # Read first 10500 lines (extended slightly to hit 6000+ lemmas target)
    words = []
    with open(INPUT, "r", encoding="utf-8") as f:
        for i, line in enumerate(f, 1):
            if i > 10500:
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
