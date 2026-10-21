# Education — O.W.L.s, N.E.W.T.s and the road to a profession

Revision 2026-10-14. Plan and review: `tasks/todo_education_2026_10_14.md`. Earlier history: `PROGRESSION_MAP.md` §9
(evidence grading, resits, careers), `PROFESSIONS.md` (career tracks).

```
EDUCATION   play is coursework: practise, brew, grow, study creatures, read tomes, chart the sky
            + keeper lessons (one a day, up to A)                         ── Character Sheet ▸ Education
   ▼
O.W.L.      sitting at an examination desk: coursework + 10-minute practical / written paper
   ▼  (O.W.L. Exceeds Expectations in a subject opens its seat)
N.E.W.T.    same desk, harder ladders, no lesson credit
   ▼
PROFESSION  ordinary careers enter on O.W.L.s · elite careers need N.E.W.T.s · track top ranks need a N.E.W.T.
            · advanced brews and the skill web's capstones need N.E.W.T.s
```

Nothing in ordinary play asks for a grade: casting, creatures, everyday brews, exploration and the web below its
capstones are open to everyone. With the O.W.L. module off, every grade gate is open (fail-open, like the
apothecary's Potions E rank and a fee with Gringotts closed).

## 1. Subjects

| Subject | Coursework (what counts) | Practical in the sitting | N.E.W.T. | Keeper lessons |
|---|---|---|---|---|
| Charms | Utility spells practised: Proficient 1, Mastered 2 | a charm cast that counts as practice | 16/22/28 | Ollivanders |
| Defence Against the Dark Arts | Combat + Defence spells practised (never Dark Arts) | a duelling/defensive cast that counts | 12/16/20 | Ministry |
| Transfiguration | Animagus complete 1, registered 2 | take the Animagus form | 1/2/3 (O only via the practical) | — (cannot be taught) |
| Potions | brew points collected | collect a brew, or hand one in at the desk | 50/80/120 | apothecary |
| Herbology | Mandrakes 1 each, Herbology nodes 3 each | harvest a mature crop, or hand in a specimen (`#exam_herbology_specimens`) | 30/45/65 | apothecary |
| Care of Magical Creatures | pages STUDIED 1, KNOWN +1, research complete +1 | feed a bonded beast / deepen a page | 16/30/50 | apothecary |
| Ancient Runes | Wandlore nodes | finish a wand at the bench | 8/10/13 | Ollivanders |
| History of Magic | lore tomes studied | written section: questions on the tomes read | none | Ministry |
| Astronomy (**new**) | telescope: each moon phase + each constellation once, one observation a night (max 20) | written section: the constellations charted | 9/14/20 | — |
| Arithmancy, Divination, Muggle Studies | — | — | — | not examined (no honest content) |

O.W.L. ladders are unchanged except Astronomy (2/5/9). Ladders live in `owl/OWLGradeRules`; `OWLGradeRulesTest`
joins them with shipped content (every subject reaches O at both levels; Transfiguration's N.E.W.T. O needs the
practical).

**Lessons** (`education/LessonServices`): location services `lesson_<subject>`, data-offered (apothecary, Ollivanders,
Ministry), 5 Sickles, one lesson a day across all subjects. Three lessons are worth the subject's A threshold and no
more (`OWLGradeRules.lessonCredit` caps below E, so Transfiguration is unteachable). N.E.W.T.s never count lessons.

**Tomes teach now.** Studying a tome prints its three passages (every read — re-reading is revision). The History
paper only asks about these passages (`education/ExamQuestions`).

**Telescope** (`education/TelescopeItem`, the canon stub given behaviour; recipe under the OWLS module): three seconds
at the eyepiece on a clear night under open sky charts the moon's phase and the night's constellation, with a
one-line fact. Constellation = night × 5 mod 12, so twelve nights show all twelve.

## 2. The sitting (`education/ExamSittings`, rules `education/ExamRules`)

1. At the desk: **Sit the O.W.L.** / **Sit the N.E.W.T.** (server checks module, desk within 6 blocks, no open sitting,
   resit wait of 7 in-game days per level, N.E.W.T. seat).
2. A 10-minute clock starts (HUD line at the top of the screen). Each paper owes one practical; it is credited where
   the work already happens: `CareerWork.record` (casts, brews, harvests, creature care, wandcraft),
   `AnimagusTransformService.toggleTransform`, and items used on the desk (`ExaminationDeskBlock.useItemOn`).
3. The written paper (History + Astronomy, ≤ 2 questions each, drawn only from what was read/charted) is answered once
   at the desk; a section passes at half right.
4. **Hand in** at the desk, or the clock runs out (checked once a second, also after a relog).
5. Marks: coursework grade per paper; a done practical / passed section lifts it **one** grade (never from Troll);
   each subject keeps the best grade ever earned. The career choice reopens after every sitting at either level.

The sitting is saved on the player (`education_data` attachment), so a relog keeps the clock and the practicals.

## 3. What grades unlock

| Gate | Requirement | Where |
|---|---|---|
| Ordinary careers | O.W.L.s (unchanged) | `ProfessionEligibility` |
| Auror | + N.E.W.T. Defence E, Charms A | same |
| Healer | + N.E.W.T. Potions E, Herbology A | same |
| Unspeakable | + N.E.W.T. Charms E, Ancient Runes E | same |
| Hit Wizard | + N.E.W.T. Defence E | same |
| Curse-Breaker | + N.E.W.T. Ancient Runes A (goblins: none — Gringotts trains its own) | same |
| Master Magizoologist (track top rank) | + N.E.W.T. Care of Magical Creatures A | `career_tracks/magizoologist.json`, goal `newt_grade` |
| Ministry "commended" standing | + N.E.W.T. Defence A | `locations/ministry.json` |
| Felix Felicis, Amortentia, Draught of Living Death | N.E.W.T. Potions A | `qualification_gates/advanced_study.json` |
| Veritaserum, Wolfsbane | N.E.W.T. Potions E | same |
| Polyjuice | O.W.L. Potions E | same |
| Build capstones (Duelist, Healer, Beastmaster, Scholar, Wandmaster, Explorer) | N.E.W.T. in the discipline, A | same; checked in `SkillSystemAPI.evaluateUnlock` (`qualification_unmet`) |

Existing O.W.L.-grade gates (apothecary/Ollivanders/Ministry ranks, licences) are unchanged.

## 4. Character Sheet ▸ Education

Server-built `EducationView` (`education/EducationService`, payload `education_view`): sittings at each level and
when each may next be sat, a running sitting's clock, per subject the projected grade, progress to the next grade,
the O.W.L./N.E.W.T. on record, lessons taken and the N.E.W.T. seat; the last sitting's marks (lifts shown A→E); the
chosen career's requirements (same rule as the server); "Next" — the most useful next step (first O.W.L., a resit
that would mark higher, a N.E.W.T. seat to earn or sit, the nearest career). Hover a subject for what counts and its
practical.

## 5. Multiplayer and authority

Every action is a C2S intent (`education_action`: VIEW, BEGIN_OWL, BEGIN_NEWT, ANSWER, HAND_IN) rate-limited per
player; the server checks module, desk reach, timing and seats. The right answer of a question never leaves the
server. Practicals are credited only on the acting player's own sitting (`EducationTests.twoCandidates`). The
goblin allowance is now synced, so the career screen greys exactly what the server would refuse.

## 6. Saves

All additive: `PlayerOWLData` gains `newtGrades`, `newtSittings`, `newtLastSatTick` (old saves: no N.E.W.T.s); new
attachment `education_data` (empty by default). A career chosen under old rules counted O.W.L. sittings only; the
sitting counter is now O.W.L. + N.E.W.T., which only ever reopens a choice.

## 7. Tests

| Kind | Test | Pins |
|---|---|---|
| unit | `OWLGradeRulesTest` (+7) | Astronomy examined; every N.E.W.T. reaches O; lessons stop at A, never at N.E.W.T.; elite careers need N.E.W.T.s; N.E.W.T. resit keeps best |
| unit | `education.ExamRulesTest` | lift rules, written pass, marking, clock, every practical has a seam, papers per level, resit timing per level |
| unit | `education.AstronomyAndPaperTest` | sky cycle, night boundaries, tomes ↔ questions ↔ lang join, facts don't give names away, draws only from what was read |
| unit | `education.EducationPersistenceTest` | sitting round-trip, old saves load empty |
| unit | `education.QualificationDataTest` | gates name real brews/capstones and sat papers; everyday brews ungated; lessons offered are registered and named; N.E.W.T. only at the track's top |
| unit | `education.EducationViewTest` | view stream codec, "next" lines |
| game | `EducationTests` (8) | practical lifts (hand-in + harvest), clock closes, resit wait + best paper, N.E.W.T. seat + Auror gate, brew/capstone gates + module fail-open, two candidates apart, relog, lessons |

Not automated: the screens in a running client (desk, paper, Education tab, HUD clock); the telescope under a real sky.
