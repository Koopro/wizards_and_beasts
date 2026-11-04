# Creature inventory (VR-0, 2026-11-04)

Measured from the files on `dev` d9f12242 by a read-only script: one row per registered creature (96 data-driven
`data/wizards_and_beasts/creatures/<id>.json` + 11 Java-defined = 107, all with a spawn egg). Geometry =
`geckolib/models/entity/<id>.geo.json`, animation = `geckolib/animations/entity/<id>.animation.json`, texture =
`textures/entity/<id>.png`; glow = a `<texture>_glowmask.png` exists. `size` is the rig's extent in model units
(16 = 1 block), `wh` the definition hitbox in blocks. **Roster**: A = alpha/shipped (natural spawning by default),
P = progression-critical. **Seen** = photographed in a client in VR-0 (`documentation/visual_renaissance/`).

Every creature has its geometry, animation and texture; no declared clip is missing from its animation file;
no `PLACEHOLDER box rig` marker remains (`CreatureRigGuardTest`). Generator = the PNG `Generator` chunk.

## AQUATIC (8)

| id | roster | seen | wh | scale | bones | cubes | size | clips | tex colours | generator | glow |
|---|---|---|---|---|---|---|---|---|---|---|---|
| grindylow |  |  | 0.65x0.75 |  | 16 | 17 | (9, 20, 5) | 7 | 17 | grindylow_model.py | G |
| hippocampus |  |  | 1.7x1.9 |  | 12 | 16 | (9, 31, 54) | 7 | 21 | hippocampus_model.py |  |
| kappa |  |  | 0.95x1.25 |  | 16 | 19 | (13, 24, 10) | 7 | 20 | kappa_model.py | G |
| kelpie |  | Y | 1.4x1.7 |  | 26 | 33 | (10, 34, 44) | 19 | 20 | kelpie_model.py |  |
| merperson |  | Y | 0.95x1.25 |  | 14 | 16 | (10, 26, 7) | 8 | 21 | merperson_model.py |  |
| plimpy |  |  | 0.65x0.75 |  | 6 | 9 | (10, 16, 13) | 7 | 21 | plimpy_model.py |  |
| ramora |  |  | 0.95x1.25 |  | 8 | 10 | (16, 14, 32) | 6 | 21 | ramora_model.py |  |
| shrake |  |  | 0.95x1.25 |  | 8 | 27 | (16, 16, 31) | 7 | 20 | shrake_model.py |  |

## ARTHROPOD_MULTILEG (6)

| id | roster | seen | wh | scale | bones | cubes | size | clips | tex colours | generator | glow |
|---|---|---|---|---|---|---|---|---|---|---|---|
| acromantula |  | Y | 2.2x1.6 |  | 48 | 50 | (118, 22, 57) | 11 | 23 | hand | G |
| blast_ended_skrewt |  |  | 1.7x1.9 |  | 38 | 38 | (42, 8, 40) | 8 | 21 | blast_ended_skrewt_model.py | G |
| chizpurfle |  |  | 0.45x0.45 |  | 33 | 27 | (17, 3, 5) | 7 | 11 | chizpurfle_model.py |  |
| fire_crab |  | Y | 0.65x0.75 |  | 23 | 22 | (25, 8, 16) | 8 | 17 | fire_crab_model.py | G |
| mackled_malaclaw |  |  | 0.65x0.75 |  | 41 | 37 | (22, 4, 25) | 7 | 9 | mackled_malaclaw_model.py |  |
| quintaped |  |  | 0.95x1.25 |  | 28 | 25 | (32, 14, 16) | 8 | 14 | quintaped_model.py | G |

## AVIAN (7)

| id | roster | seen | wh | scale | bones | cubes | size | clips | tex colours | generator | glow |
|---|---|---|---|---|---|---|---|---|---|---|---|
| augurey |  |  |  |  | 16 | 21 | (7, 12, 15) | 4 | 22 | augurey_model.py |  |
| diricawl |  |  | 0.65x0.75 |  | 14 | 14 | (10, 16, 17) | 8 | 21 | diricawl_model.py |  |
| fwooper |  |  | 0.65x0.75 |  | 13 | 13 | (9, 15, 19) | 9 | 17 | fwooper_model.py |  |
| golden_snidget |  |  | 0.35x0.35 |  | 5 | 8 | (16, 6, 9) | 7 | 20 | golden_snidget_model.py | G |
| jobberknoll |  |  | 0.45x0.45 |  | 13 | 12 | (6, 7, 14) | 7 | 21 | jobberknoll_model.py |  |
| phoenix | A | Y |  |  | 36 | 40 | (59, 26, 46) | 11 | 40 | phoenix_model.py | G |
| thunderbird |  | Y | 1.7x1.9 | 1.4 | 20 | 22 | (15, 26, 41) | 9 | 23 | thunderbird_model.py | G |

## BIPED (2)

| id | roster | seen | wh | scale | bones | cubes | size | clips | tex colours | generator | glow |
|---|---|---|---|---|---|---|---|---|---|---|---|
| bowtruckle | A | Y |  |  | 14 | 18 | (5, 13, 3) | 12 | 15 | bowtruckle_model.py |  |
| hidebehind |  |  |  |  | 16 | 19 | (12, 33, 10) | 2 | 21 | hidebehind_model.py | G |

## BIPED_HUMANOID (13)

| id | roster | seen | wh | scale | bones | cubes | size | clips | tex colours | generator | glow |
|---|---|---|---|---|---|---|---|---|---|---|---|
| clabbert |  |  | 0.65x0.75 |  | 16 | 18 | (11, 18, 7) | 6 | 18 | clabbert_model.py | G |
| demiguise | P |  | 0.65x0.75 |  | 14 | 15 | (11, 17, 8) | 6 | 21 | demiguise_model.py |  |
| erkling |  |  | 0.65x0.75 |  | 18 | 21 | (14, 19, 8) | 7 | 17 | erkling_model.py |  |
| ghoul | A |  | 0.7x1.9 |  | 18 | 17 | (12, 29, 6) | 7 | 15 | ghoul_model.py | G |
| gnome |  |  | 0.65x0.75 |  | 14 | 17 | (11, 17, 9) | 6 | 19 | gnome_model.py |  |
| imp |  |  | 0.45x0.45 |  | 14 | 13 | (11, 10, 4) | 6 | 10 | imp_model.py | G |
| leprechaun |  |  | 0.65x0.75 |  | 15 | 17 | (10, 20, 7) | 7 | 19 | leprechaun_model.py | G |
| maledictus |  |  | 0.95x1.25 |  | 14 | 17 | (14, 25, 26) | 7 | 22 | maledictus_model.py | G |
| porlock |  |  | 0.65x0.75 |  | 12 | 13 | (11, 18, 7) | 6 | 21 | porlock_model.py |  |
| pukwudgie |  |  | 0.5x1.0 |  | 17 | 21 | (9, 25, 9) | 7 | 20 | pukwudgie_model.py |  |
| red_cap |  |  | 0.65x0.75 |  | 16 | 19 | (14, 24, 6) | 7 | 21 | red_cap_model.py | G |
| rougarou | P |  | 1.2x1.9 |  | 20 | 23 | (18, 32, 24) | 7 | 23 | rougarou_model.py | G |
| werewolf | A | Y | 1.7x1.9 |  | 26 | 25 | (11, 33, 22) | 7 | 19 | werewolf_model.py | G |

## BLOB_SPHERE (9)

| id | roster | seen | wh | scale | bones | cubes | size | clips | tex colours | generator | glow |
|---|---|---|---|---|---|---|---|---|---|---|---|
| boggart |  |  | 0.95x1.25 |  | 7 | 6 | (14, 23, 12) | 8 | 7 | boggart_model.py | G |
| bundimun |  |  | 0.5x0.4 |  | 10 | 15 | (12, 8, 8) | 6 | 12 | bundimun_model.py | G |
| lethifold |  |  | 0.95x1.25 |  | 6 | 5 | (14, 25, 11) | 7 | 10 | lethifold_model.py |  |
| lobalug |  |  | 0.7x0.7 |  | 3 | 7 | (10, 10, 20) | 6 | 13 | lobalug_model.py |  |
| obscurus | A |  | 0.95x1.25 |  | 27 | 65 | (15, 27, 16) | 7 | 29 | obscurus_model.py | G |
| pogrebin |  |  | 0.65x0.75 |  | 12 | 11 | (9, 14, 8) | 8 | 20 | pogrebin_model.py | G |
| puffskein |  |  | 0.45x0.45 |  | 5 | 7 | (10, 10, 12) | 7 | 24 | puffskein_model.py |  |
| pygmy_puff |  |  | 0.45x0.45 |  | 5 | 7 | (8, 8, 10) | 7 | 15 | pygmy_puff_model.py |  |
| toad |  |  | 0.45x0.45 |  | 8 | 9 | (10, 6, 12) | 7 | 19 | toad_model.py |  |

## FLYING (1)

| id | roster | seen | wh | scale | bones | cubes | size | clips | tex colours | generator | glow |
|---|---|---|---|---|---|---|---|---|---|---|---|
| cornish_pixie |  |  |  |  | 11 | 14 | (14, 17, 13) | 4 | 19 | cornish_pixie_model.py |  |

## HUMANOID (1)

| id | roster | seen | wh | scale | bones | cubes | size | clips | tex colours | generator | glow |
|---|---|---|---|---|---|---|---|---|---|---|---|
| goblin_teller |  |  |  |  | 18 | 38 | (13, 24, 9) | 3 | 19 | goblin_model.py |  |

## INSECTOID_FLYER (5)

| id | roster | seen | wh | scale | bones | cubes | size | clips | tex colours | generator | glow |
|---|---|---|---|---|---|---|---|---|---|---|---|
| billywig |  |  | 0.45x0.45 |  | 3 | 13 | (8, 6, 15) | 7 | 20 | billywig_model.py | G |
| doxy |  |  | 0.45x0.45 |  | 11 | 11 | (9, 12, 6) | 7 | 16 | doxy_model.py |  |
| fairy |  |  | 0.45x0.45 |  | 11 | 12 | (11, 11, 6) | 7 | 15 | fairy_model.py | G |
| glumbumble |  |  | 0.45x0.45 |  | 5 | 19 | (10, 9, 15) | 7 | 21 | glumbumble_model.py | G |
| swooping_evil |  |  | 0.95x1.25 |  | 10 | 18 | (33, 8, 26) | 8 | 21 | swooping_evil_model.py | G |

## INVERTEBRATE (1)

| id | roster | seen | wh | scale | bones | cubes | size | clips | tex colours | generator | glow |
|---|---|---|---|---|---|---|---|---|---|---|---|
| streeler |  |  |  |  | 6 | 9 | (7, 16, 20) | 4 | 21 | streeler_model.py |  |

## LARGE_HUMANOID (3)

| id | roster | seen | wh | scale | bones | cubes | size | clips | tex colours | generator | glow |
|---|---|---|---|---|---|---|---|---|---|---|---|
| giant |  | Y | 2.7x2.9 | 1.8 | 16 | 20 | (35, 48, 14) | 8 | 22 | giant_model.py |  |
| troll |  | Y | 2.7x2.9 | 1.25 | 17 | 25 | (33, 56, 26) | 8 | 23 | troll_model.py |  |
| yeti |  |  | 1.7x2.3 |  | 16 | 17 | (26, 38, 12) | 8 | 13 | yeti_model.py | G |

## QUADRUPED (25)

| id | roster | seen | wh | scale | bones | cubes | size | clips | tex colours | generator | glow |
|---|---|---|---|---|---|---|---|---|---|---|---|
| centaur |  | Y | 1.7x1.9 | 1.2 | 24 | 28 | (13, 36, 20) | 9 | 23 | centaur_model.py |  |
| chimaera |  |  | 1.7x1.9 |  | 21 | 29 | (14, 32, 58) | 9 | 23 | chimaera_model.py | G |
| crup |  |  | 0.65x0.75 |  | 18 | 19 | (8, 12, 25) | 9 | 20 | crup_model.py |  |
| dugbog |  |  | 0.95x1.25 |  | 15 | 21 | (13, 14, 48) | 9 | 22 | dugbog_model.py |  |
| erumpent |  |  | 1.7x1.9 |  | 20 | 26 | (20, 33, 64) | 9 | 22 | erumpent_model.py | G |
| graphorn |  |  | 1.7x1.9 |  | 21 | 26 | (17, 42, 41) | 9 | 21 | graphorn_model.py |  |
| hodag |  |  | 0.95x1.25 |  | 16 | 26 | (12, 18, 36) | 9 | 21 | hodag_model.py | G |
| jarvey |  |  | 0.65x0.75 |  | 15 | 18 | (6, 10, 33) | 9 | 21 | jarvey_model.py |  |
| knarl |  |  | 0.65x0.75 |  | 13 | 43 | (12, 10, 19) | 8 | 21 | knarl_model.py |  |
| kneazle |  |  | 0.65x0.75 |  | 18 | 19 | (7, 16, 30) | 9 | 21 | kneazle_model.py | G |
| matagot |  |  | 0.6x0.7 |  | 16 | 17 | (5, 17, 30) | 9 | 10 | matagot_model.py | G |
| moke |  |  | 0.65x0.75 |  | 15 | 16 | (7, 4, 27) | 8 | 21 | moke_model.py |  |
| mooncalf | A | Y |  |  | 19 | 38 | (11, 28, 20) | 5 | 32 | mooncalf_model.py | G |
| murtlap |  |  | 0.65x0.75 |  | 15 | 25 | (6, 13, 29) | 8 | 20 | murtlap_model.py |  |
| niffler | A | Y |  |  | 12 | 17 | (9, 10, 24) | 10 | 18 | niffler_model.py |  |
| nogtail |  |  | 0.65x0.75 |  | 19 | 20 | (6, 14, 20) | 9 | 16 | nogtail_model.py |  |
| nundu |  |  | 2.7x2.9 |  | 22 | 26 | (24, 51, 84) | 9 | 23 | nundu_model.py | G |
| qilin |  |  | 0.95x1.25 |  | 21 | 25 | (6, 26, 27) | 8 | 18 | qilin_model.py | G |
| reem |  |  | 2.7x2.9 |  | 23 | 26 | (25, 47, 82) | 9 | 23 | reem_model.py |  |
| salamander |  |  | 0.65x0.75 |  | 15 | 16 | (7, 4, 26) | 8 | 20 | salamander_model.py | G |
| sphinx |  |  | 1.7x1.9 |  | 21 | 25 | (13, 29, 44) | 9 | 21 | sphinx_model.py |  |
| tebo |  |  | 1.7x1.9 |  | 16 | 24 | (16, 25, 56) | 9 | 21 | tebo_model.py |  |
| unicorn | A | Y | 1.4x1.6 |  | 23 | 26 | (9, 36, 23) | 8 | 22 | unicorn_model.py | G |
| wampus_cat |  |  | 1.7x1.9 |  | 19 | 22 | (12, 26, 67) | 9 | 21 | wampus_cat_model.py | G |
| zouwu |  |  | 2.7x2.9 |  | 21 | 26 | (23, 43, 89) | 9 | 22 | zouwu_model.py | G |

## SERPENTINE (6)

| id | roster | seen | wh | scale | bones | cubes | size | clips | tex colours | generator | glow |
|---|---|---|---|---|---|---|---|---|---|---|---|
| ashwinder |  |  | 0.65x0.75 |  | 8 | 7 | (3, 2, 27) | 8 | 9 | ashwinder_model.py | G |
| basilisk | A | Y | 2.7x2.2 | 1.4 | 21 | 39 | (19, 17, 177) | 14 | 29 | basilisk_model.py | G |
| horned_serpent |  |  | 1.5x1.3 |  | 10 | 11 | (7, 16, 50) | 8 | 14 | horned_serpent_model.py | G |
| occamy | A | Y | 1.7x1.9 |  | 30 | 44 | (39, 34, 75) | 7 | 23 | occamy_model.py |  |
| runespoor |  |  |  |  | 16 | 17 | (4, 3, 39) | 2 | 13 | runespoor_model.py |  |
| sea_serpent |  |  | 2.7x2.9 |  | 10 | 14 | (12, 16, 88) | 8 | 13 | sea_serpent_model.py |  |

## SESSILE (1)

| id | roster | seen | wh | scale | bones | cubes | size | clips | tex colours | generator | glow |
|---|---|---|---|---|---|---|---|---|---|---|---|
| horklump |  |  | 0.65x0.75 |  | 7 | 7 | (10, 12, 13) | 3 | 20 | horklump_model.py |  |

## WINGED_QUADRUPED (18)

| id | roster | seen | wh | scale | bones | cubes | size | clips | tex colours | generator | glow |
|---|---|---|---|---|---|---|---|---|---|---|---|
| abraxan |  |  | 2.7x2.9 |  | 23 | 29 | (20, 51, 60) | 9 | 21 | abraxan_model.py |  |
| aethonan |  |  | 1.7x1.9 |  | 23 | 29 | (12, 33, 41) | 9 | 21 | aethonan_model.py |  |
| antipodean_opaleye | A |  | 2.7x2.9 | 1.3 | 30 | 62 | (112, 36, 140) | 8 | 31 | dragon_model.py | G |
| chinese_fireball | A |  | 2.7x2.9 | 1.3 | 34 | 65 | (97, 38, 135) | 8 | 25 | dragon_model.py |  |
| common_welsh_green | A | Y | 2.7x2.9 | 1.25 | 34 | 56 | (90, 33, 128) | 8 | 25 | dragon_model.py |  |
| granian |  |  | 1.7x1.9 |  | 23 | 29 | (12, 33, 43) | 9 | 21 | granian_model.py |  |
| griffin |  | Y | 1.7x1.9 |  | 28 | 54 | (14, 40, 48) | 9 | 21 | griffin_model.py |  |
| hebridean_black | A |  | 2.7x2.9 | 1.35 | 30 | 59 | (126, 41, 158) | 8 | 24 | dragon_model.py | G |
| hippogriff | A | Y | 1.7x1.9 |  | 36 | 43 | (74, 37, 49) | 16 | 39 | hippogriff_model.py |  |
| hungarian_horntail | A | Y | 2.7x2.9 | 1.5 | 28 | 78 | (135, 44, 170) | 8 | 27 | dragon_model.py |  |
| manticore |  |  | 1.7x1.9 |  | 24 | 28 | (14, 38, 65) | 9 | 23 | manticore_model.py | G |
| norwegian_ridgeback | A |  | 2.7x2.9 | 1.35 | 28 | 56 | (117, 41, 145) | 8 | 25 | dragon_model.py | G |
| peruvian_vipertooth | A |  | 1.7x1.9 |  | 34 | 56 | (74, 27, 102) | 8 | 23 | dragon_model.py |  |
| romanian_longhorn | A |  | 2.7x2.9 | 1.35 | 36 | 58 | (110, 39, 151) | 8 | 24 | dragon_model.py |  |
| snallygaster |  |  | 1.7x1.9 |  | 22 | 28 | (13, 31, 61) | 9 | 17 | snallygaster_model.py |  |
| swedish_short_snout | A |  | 2.7x2.9 | 1.3 | 36 | 58 | (100, 38, 139) | 8 | 24 | dragon_model.py | G |
| thestral | A | Y |  |  | 34 | 47 | (76, 37, 43) | 14 | 27 | thestral_model.py | G |
| ukrainian_ironbelly | A |  | 2.7x2.9 | 1.5 | 32 | 60 | (155, 48, 171) | 8 | 27 | dragon_model.py | G |

## WORM_LARVA (1)

| id | roster | seen | wh | scale | bones | cubes | size | clips | tex colours | generator | glow |
|---|---|---|---|---|---|---|---|---|---|---|---|
| flobberworm |  |  | 0.45x0.45 |  | 6 | 5 | (3, 3, 14) | 4 | 9 | flobberworm_model.py |  |
