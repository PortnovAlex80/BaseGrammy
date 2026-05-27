# PROMPT: Create One Italian Lesson

## YOUR TASK
Create ONE Italian lesson CSV file with 20-40 sentences for the assigned lesson.

## LESSON FORMAT
**First row:** `Lesson Code - Lesson Theme` (e.g., "A01 - Presente Indicativo")

**Subsequent rows:** `Russian sentence (with hints in parentheses);Italian translation`

Example:
```
они должны (dovere) заплатить (pagare) сумму (somma).;Loro devono pagare la somma.
у меня много (molto) свободного (libero) времени (tempo).;Io ho molto tempo libero.
```

## REQUIREMENTS
1. **20-40 sentences** per lesson
2. **Max B1 level** — keep vocabulary and grammar appropriate
3. **Word frequency check** — prioritize words from top 12500 Italian frequency list
4. **Hints in Russian** — put key Italian words in parentheses in Russian sentences
5. **Accurate Italian** — grammatically correct, natural phrasing

## PROCESS
1. Read your assigned lesson analysis from `lesson_analysis.csv`
2. Read the lesson description from `italian-course-program.csv`
3. Understand what grammar/vocab this lesson teaches
4. Create 20-40 sentence pairs practicing that content
5. Check word frequency (prioritize common words)
6. Save to: `D:\Development\BaseGrammy\docs\lesson-methodology\new_lessons\lesson_XX.csv`

## FREQUENCY CHECK
Reference: `D:\Development\BaseGrammy\data\it\it_12500_frequency.txt`
- Prioritize words from top 5000 


Report completion when done.
