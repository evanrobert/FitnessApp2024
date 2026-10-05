package Evan.Application.Fitness.Service;

import Evan.Application.Fitness.Model.MuscleGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a workout typed in a notes app, e.g.
 * <pre>
 * Incline dumbbell press
 * * 50 lbs 10 reps
 * * 50 lbs 10 reps
 * Bench press 3x8 @ 135
 * Push-ups 3x15
 * </pre>
 * A line without sets starts a new exercise; set lines below it belong to it. Runs entirely
 * in the app (nothing is sent anywhere) and never guesses silently: whatever it can't read
 * is returned as skipped, and the member reviews everything before saving.
 */
public final class WorkoutTextParser {
    public static final int MAX_TEXT = 10_000;
    public static final int MAX_EXERCISES = 30;
    /** The workout form numbers a block's rows 0–19. */
    public static final int MAX_SETS_PER_EXERCISE = 20;

    private static final double KG_TO_LB = 2.20462;
    private static final String NUM = "(\\d{1,4}(?:\\.\\d{1,2})?)";
    private static final String INT = "(\\d{1,3})";
    private static final String LB = "(?:lbs?|pounds?|#)";
    private static final String KG = "(?:kgs?|kilos?|kilograms?)";
    private static final String UNIT = "(" + LB + "|" + KG + ")";
    private static final String REPS = "(?:reps?|times|r)";
    private static final String BW = "(?:bw|body\\s*weight|bodyweight)";
    private static final String X = "\\s*(?:x|×|\\*)\\s*";

    private static final Pattern BULLET = Pattern.compile("^(?:[*\\-•·–—>+]+\\s*|\\d{1,2}[.)]\\s+|set\\s*\\d{1,2}\\s*[:.)\\-–]?\\s*)");
    private static final Pattern SETS_PREFIX = Pattern.compile("^" + INT + "\\s*sets?\\s*(?:of|x|×)?\\s*(.+)$");
    private static final Pattern SETS_SUFFIX = Pattern.compile("^(.+?)\\s*(?:[,(]\\s*)?(?:(?:x|×)\\s*" + INT + "\\s*(?:sets?)?|" + INT + "\\s*sets?)\\s*\\)?$");
    /** 3x10 @ 135 lb, 3 x 10 135, 4x12 with 50 lbs */
    private static final Pattern SETS_REPS_WEIGHT = Pattern.compile("^" + INT + X + INT + "(?:\\s*" + REPS + ")?(?:\\s*(?:@|at|with|w/|,|-|–)\\s*|\\s+)" + NUM + "\\s*" + UNIT + "?$");
    /** 3x10 (sets x reps, no weight) */
    private static final Pattern SETS_REPS = Pattern.compile("^" + INT + X + INT + "\\s*" + REPS + "?$");
    /** 50 lbs 10 reps, 50lb x 10, 50 lbs for 10, 135 x 8 */
    private static final Pattern WEIGHT_REPS = Pattern.compile("^" + NUM + "(?:\\s*" + UNIT + ")?(?:\\s*(?:x|×|\\*|for|,|-|–|:)\\s*|\\s+)" + INT + "\\s*" + REPS + "?$");
    /** 50 lbs: 10, 10, 8 */
    private static final Pattern WEIGHT_REP_LIST = Pattern.compile("^" + NUM + "(?:\\s*" + UNIT + ")?(?:\\s*(?:x|×|\\*|for|-|–|:)\\s*|\\s+)(\\d{1,3}(?:\\s*[,/]\\s*\\d{1,3})+)\\s*" + REPS + "?$");
    /** 10 reps @ 50 lbs, 10 reps 50lb */
    private static final Pattern REPS_WEIGHT = Pattern.compile("^" + INT + "\\s*" + REPS + "\\s*(?:@|at|with|w/|x|of|,|-|–)?\\s*" + NUM + "\\s*" + UNIT + "$");
    /** 12 @ 135, 12 at 50 lbs */
    private static final Pattern REPS_AT_WEIGHT = Pattern.compile("^" + INT + "\\s*(?:@|at|with|w/)\\s*" + NUM + "\\s*" + UNIT + "?$");
    /** 12 reps, bodyweight x 12, bw 15 */
    private static final Pattern REPS_ONLY = Pattern.compile("^(?:" + BW + "\\s*(?:x|×|for|-|–|:)?\\s*)?" + INT + "\\s*" + REPS + "?(?:\\s*(?:x|×|@|-|–)?\\s*" + BW + ")?$");
    private static final Pattern BW_REPS = Pattern.compile("^" + BW + "\\s*(?:x|×|for|-|–|:)?\\s*" + INT + "\\s*" + REPS + "?$");
    private static final Pattern WORKOUT_PREFIX = Pattern.compile("^(?:today'?s\\s+)?workout(?:\\s+(?:today|for today|log))?\\b\\s*[:\\-–]?\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE = Pattern.compile("^(?:[a-z]{3,9},?\\s+)?(\\d{1,2})[/\\-.](\\d{1,2})(?:[/\\-.](\\d{2,4}))?$");

    private WorkoutTextParser() {
    }

    public record SetLine(Integer reps, Double weightLb) {
    }

    public record Block(String name, List<SetLine> sets) {
    }

    /** A date written on its own line, e.g. 10/3 or 10/3/2026 (year may be null). */
    public record WrittenDate(int month, int day, Integer year) {
    }

    public record Result(String title, WrittenDate date, List<Block> blocks, List<String> skipped) {
        public int setCount() {
            return blocks.stream().mapToInt(b -> b.sets().size()).sum();
        }
    }

    public static Result parse(String text) {
        List<Block> blocks = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        String title = null;
        WrittenDate date = null;
        Block current = null;
        String pendingHeader = null;
        if (text == null) {
            text = "";
        }
        if (text.length() > MAX_TEXT) {
            text = text.substring(0, MAX_TEXT);
        }
        for (String raw : text.split("\\R")) {
            String line = clean(raw);
            if (line.isEmpty()) {
                continue;
            }
            if (line.length() > 200) {
                skipped.add(line.substring(0, 60) + "…");
                continue;
            }
            WrittenDate written = parseDate(line);
            if (written != null) {
                if (date == null) {
                    date = written;
                }
                continue;
            }
            List<SetLine> sets = parseSets(line);
            if (sets != null) {
                if (current == null) {
                    if (pendingHeader == null) {
                        skipped.add(line);
                        continue;
                    }
                    current = startBlock(blocks, pendingHeader, skipped);
                    pendingHeader = null;
                    if (current == null) {
                        skipped.add(line);
                        continue;
                    }
                }
                addSets(current, sets, line, skipped);
                continue;
            }
            // A header line, possibly with its sets on the same line ("Bench press 3x8 @ 135").
            String header = line;
            List<SetLine> inline = null;
            Matcher firstDigit = Pattern.compile("\\d").matcher(line);
            if (firstDigit.find() && firstDigit.start() > 0) {
                String name = line.substring(0, firstDigit.start()).replaceAll("[\\s:\\-–—@,(]+$", "");
                List<SetLine> rest = parseSets(line.substring(firstDigit.start()));
                if (rest != null && name.matches(".*[A-Za-z].*")) {
                    header = name;
                    inline = rest;
                }
            }
            Matcher prefix = WORKOUT_PREFIX.matcher(header);
            if (prefix.find()) {
                header = header.substring(prefix.end()).trim();
                if (header.isEmpty()) {
                    continue;
                }
            }
            // The previous header had no sets: the first one is the workout's name, later ones are reported.
            if (pendingHeader != null) {
                if (blocks.isEmpty() && title == null) {
                    title = pendingHeader;
                } else {
                    skipped.add(pendingHeader);
                }
            }
            current = null;
            pendingHeader = header;
            if (inline != null) {
                current = startBlock(blocks, header, skipped);
                pendingHeader = null;
                if (current != null) {
                    addSets(current, inline, line, skipped);
                }
            }
        }
        if (pendingHeader != null) {
            if (blocks.isEmpty() && title == null) {
                title = pendingHeader;
            } else {
                skipped.add(pendingHeader);
            }
        }
        blocks.removeIf(b -> b.sets().isEmpty());
        if (title != null && title.length() > 120) {
            title = title.substring(0, 120);
        }
        return new Result(title, date, blocks, skipped);
    }

    private static Block startBlock(List<Block> blocks, String name, List<String> skipped) {
        if (blocks.size() >= MAX_EXERCISES) {
            skipped.add(name);
            return null;
        }
        Block block = new Block(name.length() > 100 ? name.substring(0, 100).trim() : name, new ArrayList<>());
        blocks.add(block);
        return block;
    }

    private static void addSets(Block block, List<SetLine> sets, String line, List<String> skipped) {
        for (SetLine set : sets) {
            if (block.sets().size() >= MAX_SETS_PER_EXERCISE) {
                skipped.add(block.name() + ": " + line);
                return;
            }
            block.sets().add(set);
        }
    }

    private static String clean(String raw) {
        String line = raw.replace('\u00A0', ' ').trim();
        // Drop bullets and list markers, possibly repeated ("- 1. ").
        for (int i = 0; i < 3; i++) {
            Matcher bullet = BULLET.matcher(line.toLowerCase(Locale.ROOT));
            if (!bullet.find() || bullet.end() == 0) {
                break;
            }
            line = line.substring(bullet.end()).trim();
        }
        return line.replaceAll("\\s+", " ").replaceAll("[.;]+$", "").trim();
    }

    /** The sets a line describes, or null when it isn't a set line. */
    static List<SetLine> parseSets(String line) {
        List<SetLine> sets = parseSetGroup(line);
        if (sets == null && line.matches(".*[,;].*")) {
            // "135 x 8, 135 x 8, 155 x 6": every part must be a set, or the line isn't one.
            List<SetLine> all = new ArrayList<>();
            for (String part : line.split("\\s*[,;]\\s*")) {
                List<SetLine> partSets = part.isBlank() ? null : parseSetGroup(part);
                if (partSets == null) {
                    return null;
                }
                all.addAll(partSets);
            }
            return all.size() > MAX_SETS_PER_EXERCISE ? null : all;
        }
        return sets;
    }

    private static List<SetLine> parseSetGroup(String line) {
        String s = line.toLowerCase(Locale.ROOT).replace("×", "x").trim();
        int count = 1;
        Matcher prefix = SETS_PREFIX.matcher(s);
        if (prefix.matches()) {
            count = Integer.parseInt(prefix.group(1));
            s = prefix.group(2).trim();
        } else {
            Matcher suffix = SETS_SUFFIX.matcher(s);
            if (suffix.matches() && one(suffix.group(1)) != null) {
                count = Integer.parseInt(suffix.group(2) != null ? suffix.group(2) : suffix.group(3));
                s = suffix.group(1).trim();
            }
        }
        Matcher m = SETS_REPS_WEIGHT.matcher(s);
        if (m.matches()) {
            return repeat(Integer.parseInt(m.group(1)) * count, new SetLine(Integer.parseInt(m.group(2)), weight(m.group(3), m.group(4))));
        }
        m = SETS_REPS.matcher(s);
        if (m.matches() && count == 1) {
            int a = Integer.parseInt(m.group(1));
            int b = Integer.parseInt(m.group(2));
            // "3x10" is 3 sets of 10; "135x8" is 135 lb for 8.
            if (a <= 10) {
                return repeat(a, new SetLine(b, null));
            }
            return repeat(1, new SetLine(b, (double) a));
        }
        m = WEIGHT_REP_LIST.matcher(s);
        if (m.matches() && count == 1) {
            Double w = weight(m.group(1), m.group(2));
            List<SetLine> sets = new ArrayList<>();
            for (String r : m.group(3).split("\\s*[,/]\\s*")) {
                sets.add(new SetLine(Integer.parseInt(r), w));
            }
            return sets.size() > MAX_SETS_PER_EXERCISE ? null : sets;
        }
        SetLine single = one(s);
        return single == null ? null : repeat(count, single);
    }

    private static SetLine one(String s) {
        Matcher m = WEIGHT_REPS.matcher(s);
        if (m.matches() && (m.group(2) != null || s.matches(".*\\d\\s*(?:x|\\*|for).*") || s.matches(".*" + REPS + "$"))) {
            return new SetLine(Integer.parseInt(m.group(3)), weight(m.group(1), m.group(2)));
        }
        m = REPS_WEIGHT.matcher(s);
        if (m.matches()) {
            return new SetLine(Integer.parseInt(m.group(1)), weight(m.group(2), m.group(3)));
        }
        m = REPS_AT_WEIGHT.matcher(s);
        if (m.matches()) {
            return new SetLine(Integer.parseInt(m.group(1)), weight(m.group(2), m.group(3)));
        }
        m = BW_REPS.matcher(s);
        if (m.matches()) {
            return new SetLine(Integer.parseInt(m.group(1)), null);
        }
        m = REPS_ONLY.matcher(s);
        if (m.matches() && (s.matches(".*" + REPS + ".*") || s.matches(".*" + BW + ".*"))) {
            return new SetLine(Integer.parseInt(m.group(1)), null);
        }
        return null;
    }

    private static List<SetLine> repeat(int count, SetLine set) {
        if (count < 1 || count > MAX_SETS_PER_EXERCISE) {
            return null;
        }
        List<SetLine> sets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            sets.add(set);
        }
        return sets;
    }

    private static Double weight(String number, String unit) {
        double value = Double.parseDouble(number);
        if (unit != null && unit.matches(KG)) {
            value = Math.round(value * KG_TO_LB * 2) / 2.0;
        }
        return value;
    }

    private static WrittenDate parseDate(String line) {
        Matcher m = DATE.matcher(line.toLowerCase(Locale.ROOT));
        if (!m.matches()) {
            return null;
        }
        int month = Integer.parseInt(m.group(1));
        int day = Integer.parseInt(m.group(2));
        if (month < 1 || month > 12 || day < 1 || day > 31) {
            return null;
        }
        Integer year = null;
        if (m.group(3) != null) {
            year = Integer.parseInt(m.group(3));
            if (year < 100) {
                year += 2000;
            }
        }
        return new WrittenDate(month, day, year);
    }

    /**
     * Comparison key for exercise names, so "Low to high cable fly" finds "Low-to-High Cable Fly"
     * and "tricep push down" finds "Triceps Pushdown". Only spelling differences are ignored.
     */
    public static String matchKey(String name) {
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim()
                .replaceAll("\\b(push|pull) (down|up)s?\\b", "$1$2")
                .replaceAll("\\bdb\\b", "dumbbell").replaceAll("\\bbb\\b", "barbell")
                .replaceAll("\\bflye\\b", "fly").replaceAll("\\bflies\\b", "fly");
        StringBuilder key = new StringBuilder();
        for (String word : s.split(" ")) {
            if (word.length() > 3 && word.endsWith("s") && !word.endsWith("ss")) {
                word = word.substring(0, word.length() - 1);
            }
            key.append(word);
        }
        return key.toString();
    }

    /** A sensible body part for an exercise the member names for the first time (they can change it). */
    public static MuscleGroup guessMuscle(String name) {
        String n = " " + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", " ") + " ";
        if (has(n, "tricep", "skull", "pushdown", "push down", " dip")) return MuscleGroup.TRICEPS;
        if (has(n, "leg curl", "hamstring", " rdl", "romanian", "good morning")) return MuscleGroup.HAMSTRINGS;
        if (has(n, "calf", "calves")) return MuscleGroup.CALVES;
        if (has(n, "curl", "bicep")) return MuscleGroup.BICEPS;
        if (has(n, "squat", "leg press", "leg extension", "lunge", "quad", "step up")) return MuscleGroup.QUADS;
        if (has(n, "glute", "hip thrust", "abduct")) return MuscleGroup.GLUTES;
        if (has(n, " row", "pulldown", "pull down", "pull up", "pullup", "chin", " lat ", "deadlift", "shrug")) return MuscleGroup.BACK;
        if (has(n, "shoulder", "lateral", "delt", "overhead press", "military", "raise", "arnold")) return MuscleGroup.SHOULDERS;
        if (has(n, "chest", "bench", " fly", " flye", "pec", "push up", "pushup", "press")) return MuscleGroup.CHEST;
        if (has(n, " ab ", " abs ", "crunch", "plank", "core", "sit up", "situp")) return MuscleGroup.CORE;
        return null;
    }

    private static boolean has(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
