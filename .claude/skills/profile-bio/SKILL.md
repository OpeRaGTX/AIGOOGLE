---
name: profile-bio
description: Write a dating-profile "about me" for a girl in English, first person, casual slang, 500-600 characters, with a Russian translation placed before it. Use when the user asks for "описание для анкеты", "био для анкеты", "about me", "profile description" and gives a name, job and hobbies. Text must read as written by a real person, not AI.
---

# Profile bio (EN + RU)

## Inputs

- Name, job, 2-4 hobbies. Optional: age, city, vibe (sweet / sassy / shy / sporty), what she's looking for.
- Missing name, job or hobbies → ask once in one line. Everything else: pick a sensible default.

## Output (exactly this, nothing else)

```
<Имя> - <профессия>

RU (перевод):
<русский перевод>

EN (<N> chars):
<english text>
```

For several profiles, repeat the block. Russian goes FIRST, English second, same content.

## Rules for the English text

- First person, 500-600 characters with spaces and emojis. Count with `python3 -c "print(len('''...'''))"` and fix if out of range.
- Casual slang, sparingly: lol, lowkey, kinda, gonna, tbh, ngl, vibe, "slide into my messages". Two or three per bio, not every sentence.
- Each hobby gets ONE concrete, slightly self-ironic detail ("pretending I'm flexible on a yoga mat", "baking way too many cinnamon rolls"). No bare list of hobbies.
- The job appears through a small funny or warm detail, not a CV line.
- End with an easy open question to the reader (what / where / how), so he wants to write first.
- 0-2 emojis. No hashtags.
- Clean: no sexual content, no body descriptions. Light double meaning is fine.
- No insults, no money talk, no requests for help, no sad-story pressure.
- NO mention of meeting in any form: no "if we meet", dates, "together", "take me / you'd bring me", "feed you", "see you", offline plans. Even the work word "meetings" is out (use "calls"). The closing question is about him, his tastes or stories, never about a shared plan.

## Humanizer bans (from `humanizer` / `humanizer-ru`)

- No em/en dashes (— –). Use commas, periods, parentheses.
- No "not just X but Y" / "it's not X, it's Y".
- No triads ("smart, kind and funny"), no lists of three adjectives.
- No AI words: passionate, vibrant, journey, embrace, thrive, curate, adventure-seeker, "love to laugh", "partner in crime", "live, laugh, love".
- No staged openers ("Let me tell you about myself", "A little about me:").
- Vary sentence length; one sentence can be just 3-4 words.
- Every bio for a batch must be structurally different: different first line, different order of job/hobbies, different closing question.

## Russian translation

Natural spoken Russian, same meaning and tone, slang rendered by equivalent ("lol" → «ахах», "lowkey" → «если честно»). Hyphen «-» instead of a long dash. Not counted toward the limit.

## Example

Input: Anna, nurse, yoga / baking / travel.

```
Anna - медсестра

RU (перевод):
Привет, я Анна 👋 Днём я медсестра, так что слышала все отговорки, почему можно не идти к врачу, даже не пытайся ахах. После смены я либо на коврике для йоги делаю вид, что гибкая, либо на кухне пеку слишком много булочек с корицей (соседи в восторге). Коплю на поездку в Португалию и, если честно, мне нужен кто-то, кто подскажет, какой пляж того стоит. Я довольно спокойная и немного дурашливая, смеюсь над своими шутками ещё до того, как их закончу. Есть хорошая история или рецепт получше? Пиши. Какое место последним заставило тебя сказать «вау»? 😊

EN (539 chars):
Hey, I'm Anna 👋 Nurse by day, so I've heard every excuse for skipping a doctor's visit, don't even try lol. Off shift I'm on a yoga mat pretending I'm flexible, or in my kitchen baking way too many cinnamon rolls (my neighbors are big fans). I'm saving up for a trip to Portugal and lowkey need someone to tell me which beach is actually worth it. I'm pretty chill and kinda goofy, I laugh at my own jokes before I even finish them. Got a good story or a better recipe? Slide into my messages. What's the last place that made you go wow? 😊
```
