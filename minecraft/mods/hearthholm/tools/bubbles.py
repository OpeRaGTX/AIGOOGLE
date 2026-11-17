# Speech-bubble line pools: situation -> [(English, Russian)]. %1$s = settlement name, %2$s = owner name.
# Used by gen_lang.py (translation keys bubble.hearthholm.<situation>.<n>) and the bubbles.json count table.
B = {
 "greet": [
  ("Good day, %2$s!", "Добрый день, %2$s!"),
  ("Welcome back, %2$s.", "С возвращением, %2$s."),
  ("%2$s! Look how %1$s grows!", "%2$s! Смотри, как растёт %1$s!"),
  ("Hello, boss!", "Здравствуй, хозяин!"),
  ("Everything is in order, %2$s.", "Всё в порядке, %2$s."),
  ("Nice to see you!", "Рад тебя видеть!"),
  ("%1$s is lucky to have you.", "%1$s с тобой повезло."),
  ("Need anything, %2$s?", "Что-нибудь нужно, %2$s?"),
 ],
 "hungry": [
  ("My stomach is growling...", "В животе урчит..."),
  ("Is there any bread left?", "Хлеб ещё остался?"),
  ("I could eat a whole pie.", "Съел бы целый пирог."),
  ("Hungry. So hungry.", "Есть хочу. Очень."),
  ("When is lunch?", "Когда обед?"),
  ("Food... I need food.", "Еды бы... хоть кусочек."),
  ("The warehouse had bread, right?", "На складе же был хлеб?"),
 ],
 "no_bed": [
  ("I still have no bed...", "У меня всё ещё нет кровати..."),
  ("Sleeping on the floor again.", "Опять спать на полу."),
  ("Is there a free bed anywhere?", "Где бы найти свободную кровать?"),
  ("A bed would be nice.", "Кровать бы мне."),
  ("My back hurts from the floor.", "Спина болит от пола."),
  ("Build more houses, please!", "Постройте ещё домов, пожалуйста!"),
  ("%1$s needs more beds.", "%1$s нужны ещё кровати."),
 ],
 "night": [
  ("It is getting late.", "Уже поздно."),
  ("Look at those stars!", "Смотри, сколько звёзд!"),
  ("Time to get some sleep.", "Пора бы поспать."),
  ("The night is cold.", "Ночью холодно."),
  ("Hold the torch higher!", "Выше факел!"),
  ("Something moves out there...", "Там что-то шевелится..."),
 ],
 "rain": [
  ("Rain again...", "Опять дождь..."),
  ("Good for the crops!", "Урожаю на пользу!"),
  ("I am soaked to the bone.", "Промок до нитки."),
  ("Wish I had a hat.", "Мне бы шляпу."),
  ("Listen to the raindrops.", "Слушай, как капает."),
  ("Mud everywhere!", "Всюду грязь!"),
 ],
 "levelup": [
  ("I am getting better at this!", "У меня всё лучше получается!"),
  ("A new level! Hooray!", "Новый уровень! Ура!"),
  ("Practice makes perfect.", "Опыт приходит с практикой."),
  ("I feel stronger today.", "Чувствую себя сильнее."),
  ("Look what I can do now!", "Смотри, что я теперь умею!"),
  ("Hard work pays off.", "Труд приносит плоды."),
 ],
 "tasty": [
  ("Mmm, that was delicious!", "Ммм, вкуснотища!"),
  ("Best meal in ages!", "Лучший обед за долгое время!"),
  ("Compliments to the cook!", "Поварам моё почтение!"),
  ("Now that is real food.", "Вот это настоящая еда."),
  ("I could have another!", "Я бы ещё съел!"),
  ("My day just got better.", "Жизнь налаживается."),
 ],
 "festival": [
  ("Happy harvest, %1$s!", "С урожаем, %1$s!"),
  ("Dance with me!", "Танцуй со мной!"),
  ("What a wonderful day!", "Какой чудесный день!"),
  ("Music! Food! Friends!", "Музыка! Еда! Друзья!"),
  ("Long live %1$s!", "Да здравствует %1$s!"),
  ("Look at me go!", "Смотри, как я пляшу!"),
  ("Thank you, %2$s!", "Спасибо тебе, %2$s!"),
  ("Hey-ho, hey-ho!", "Эй-ха, эй-ха!"),
 ],
 "idle": [
  ("Nice weather today.", "Славная погода."),
  ("%1$s is a good place to live.", "В %1$s хорошо живётся."),
  ("I wonder what is for supper.", "Интересно, что на ужин."),
  ("Hmm hmm hmm...", "Хм-хм-хм..."),
  ("Did you see that cloud?", "Видел то облако?"),
  ("Just taking a breath.", "Просто перевожу дух."),
  ("I should visit the warehouse.", "Надо бы заглянуть на склад."),
  ("Quiet day.", "Тихий денёк."),
  ("A good village needs a good bell.", "Хорошей деревне нужен хороший колокол."),
  ("What a nice little town.", "Какой уютный городок."),
 ],
 "chat": [
  ("Remember last harvest?", "Помнишь прошлый урожай?"),
  ("How is your family?", "Как твои дела дома?"),
  ("You always make me laugh.", "Ты всегда меня смешишь."),
  ("Let us sit by the fire.", "Давай посидим у огня."),
  ("Did you hear the news?", "Слышал новости?"),
  ("I am glad we are friends.", "Рад, что мы друзья."),
  ("Tomorrow will be a good day.", "Завтра будет хороший день."),
  ("Tell me another story!", "Расскажи ещё историю!"),
 ],
 "quarrel": [
  ("You never listen!", "Ты никогда не слушаешь!"),
  ("Hmph! Leave me alone.", "Фу! Оставь меня в покое."),
  ("That was MY spot!", "Это было МОЁ место!"),
  ("I have had enough of you!", "С меня хватит!"),
  ("Mind your own business.", "Не лезь не в своё дело."),
  ("Always complaining!", "Вечно ты недоволен!"),
 ],
}

# work lines per profession path
W = {
 "miner": [("Another vein of iron!", "Ещё одна жила железа!"), ("Watch your head down here.", "Береги голову внизу."), ("Stone, stone and more stone.", "Камень, камень и ещё раз камень."), ("My pickaxe sings today.", "Кирка сегодня поёт.")],
 "lumberjack": [("Timber!", "Поберегись, падает!"), ("This oak is a giant.", "Этот дуб просто великан."), ("Plant a sapling, take a log.", "Сорвал бревно, посади саженец."), ("My axe is sharp today.", "Топор сегодня остёр.")],
 "farmer": [("The wheat looks golden.", "Пшеница так и золотится."), ("Water, sun and patience.", "Вода, солнце и терпение."), ("Good soil makes good bread.", "Хорошая земля даёт хороший хлеб."), ("The crops are growing well.", "Урожай растёт отлично.")],
 "fisher": [("Something is biting!", "Клюёт!"), ("Patience catches fish.", "Терпение рыбу ловит."), ("The water is calm today.", "Вода сегодня спокойная."), ("Fresh fish for supper!", "Свежая рыба на ужин!")],
 "builder": [("Measure twice, build once.", "Семь раз отмерь, один раз построй."), ("A strong house for %1$s!", "Крепкий дом для %1$s!"), ("Another block in the wall.", "Ещё один блок в стене."), ("I need more planks.", "Мне бы ещё досок.")],
 "guard": [("All quiet on my watch.", "На посту всё тихо."), ("Nothing gets past me.", "Мимо меня и мышь не проскочит."), ("Eyes open, spear ready.", "Глаза открыты, копьё наготове."), ("I keep %1$s safe.", "Я охраняю %1$s.")],
 "warrior": [("Let them come!", "Пусть только попробуют!"), ("My blade is ready.", "Мой клинок наготове."), ("For %1$s!", "За %1$s!"), ("Stand tall, stand strong.", "Держись прямо и крепко.")],
 "carpenter": [("Smell that fresh sawdust.", "Чуешь свежие опилки?"), ("A bench from honest planks.", "Лавка из честных досок."), ("Smooth as silk.", "Гладко, как шёлк."), ("Planks, planks, planks.", "Доски, доски, доски.")],
 "mason": [("Stone by stone.", "Камень за камнем."), ("Straight lines, steady hands.", "Ровные линии, твёрдая рука."), ("Tap, tap, tap.", "Тук, тук, тук."), ("A fine cut!", "Славный скол!")],
 "smith": [("Hot iron, hard hands.", "Горячее железо, крепкие руки."), ("The anvil rings true.", "Наковальня звенит чисто."), ("This will hold a hundred years.", "Прослужит сто лет."), ("Hammer and heat!", "Молот и жар!")],
 "cook": [("Something smells delicious!", "Пахнет просто восхитительно!"), ("A pinch of salt...", "Щепотку соли..."), ("Supper is almost ready.", "Ужин почти готов."), ("Hot bread, fresh from the oven!", "Горячий хлеб, прямо из печи!")],
 "hauler": [("Heavy crates today.", "Сегодня тяжёлые ящики."), ("To the warehouse we go!", "На склад, друзья!"), ("Back and forth, back and forth.", "Туда-сюда, туда-сюда."), ("Everything in its place.", "Всё на своём месте.")],
 "unemployed": [("I could use some work.", "Мне бы работу."), ("Idle hands...", "Руки без дела..."), ("Give me a job, boss!", "Дай мне работу, хозяин!"), ("What should I do today?", "Чем бы сегодня заняться?")],
}

# trait flavoured lines (trait path -> lines)
T = {
 "grumpy": [("Bah! Everything is wrong.", "Бах! Всё не так."), ("Nobody does it right.", "Никто ничего не умеет."), ("Grumble, grumble...", "Бу-бу-бу..."), ("Don't talk to me.", "Не заговаривай со мной.")],
 "cheerful": [("What a beautiful day!", "Какой прекрасный день!"), ("La la la!", "Ля-ля-ля!"), ("Smile, friends!", "Улыбайтесь, друзья!"), ("Life is wonderful!", "Жизнь прекрасна!")],
 "lazy": [("Is it time for a nap yet?", "Не пора ли вздремнуть?"), ("Why run when you can walk?", "Зачем бежать, если можно идти?"), ("Tomorrow is also a day.", "Завтра тоже будет день."), ("*yawn*", "*зевок*")],
 "hardworking": [("No rest for the diligent!", "Усердным отдыхать некогда!"), ("One more task, then another.", "Ещё одно дело, и ещё одно."), ("Work is its own reward.", "Труд сам себе награда."), ("Busy, busy, busy!", "Занят, занят, занят!")],
 "glutton": [("Is there a second helping?", "А добавка есть?"), ("I love a full table.", "Люблю полный стол."), ("Thinking about pie again...", "Опять думаю о пироге..."), ("One more bite won't hurt.", "Ещё кусочек не повредит.")],
 "frugal": [("A crust is plenty for me.", "Мне и корки хватит."), ("Waste not, want not.", "Береги копейку."), ("I need so little.", "Мне так мало нужно."), ("Save the crumbs!", "Крошки сберегите!")],
 "brave": [("I fear nothing!", "Я ничего не боюсь!"), ("Let the monsters come.", "Пусть монстры приходят."), ("Courage is a habit.", "Смелость — это привычка."), ("Stand behind me!", "Встаньте за мной!")],
 "coward": [("Did you hear that noise?!", "Ты слышал этот шорох?!"), ("I will stay close to the houses.", "Я лучше поближе к домам."), ("Is it safe? Is it?", "Это безопасно? Точно?"), ("Eek! Just a shadow.", "Ай! Просто тень.")],
 "nimble": [("Catch me if you can!", "Догоняй!"), ("Quick as a fox!", "Быстрый, как лиса!"), ("Hop, skip, jump.", "Скок-поскок."), ("No time to waste!", "Некогда терять время!")],
 "sturdy": [("I can lift that myself.", "Я и один подниму."), ("Strong as an ox.", "Силён, как бык."), ("A little bruise? Nonsense.", "Синяк? Ерунда."), ("Built to last!", "Сделан на века!")],
 "lucky": [("I found a coin today!", "Я нашёл монетку!"), ("Luck is on my side.", "Удача на моей стороне."), ("Fortune smiles on %1$s.", "Фортуна улыбается %1$s."), ("Lucky me again!", "Мне опять повезло!")],
 "night_owl": [("The night is my friend.", "Ночь — мой друг."), ("Mornings are overrated.", "Утро переоценено."), ("Who needs sleep?", "Кому нужен сон?"), ("I think best by moonlight.", "Лучше всего думается при луне.")],
}

def all_pools():
    out = dict(B)
    for k, v in W.items(): out["work_" + k] = v
    for k, v in T.items(): out["trait_" + k] = v
    return out
