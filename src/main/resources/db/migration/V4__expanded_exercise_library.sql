-- Expand the built-in exercise library so every muscle group has a real choice
-- of movements across equipment types (37 -> 125 library exercises).

INSERT INTO exercise (owner_id, name, muscle_group, equipment) VALUES
    -- Chest
    (NULL, 'Incline Dumbbell Press', 'CHEST', 'DUMBBELL'),
    (NULL, 'Decline Bench Press', 'CHEST', 'BARBELL'),
    (NULL, 'Floor Press', 'CHEST', 'BARBELL'),
    (NULL, 'Dumbbell Fly', 'CHEST', 'DUMBBELL'),
    (NULL, 'Cable Crossover', 'CHEST', 'CABLE'),
    (NULL, 'Low-to-High Cable Fly', 'CHEST', 'CABLE'),
    (NULL, 'Machine Chest Press', 'CHEST', 'MACHINE'),
    (NULL, 'Pec Deck', 'CHEST', 'MACHINE'),
    -- Back
    (NULL, 'Pendlay Row', 'BACK', 'BARBELL'),
    (NULL, 'T-Bar Row', 'BACK', 'BARBELL'),
    (NULL, 'Chest-Supported Row', 'BACK', 'DUMBBELL'),
    (NULL, 'Machine Row', 'BACK', 'MACHINE'),
    (NULL, 'Inverted Row', 'BACK', 'BODYWEIGHT'),
    (NULL, 'Straight-Arm Pulldown', 'BACK', 'CABLE'),
    (NULL, 'Rack Pull', 'BACK', 'BARBELL'),
    (NULL, 'Back Extension', 'BACK', 'BODYWEIGHT'),
    (NULL, 'Barbell Shrug', 'BACK', 'BARBELL'),
    (NULL, 'Dumbbell Shrug', 'BACK', 'DUMBBELL'),
    -- Shoulders
    (NULL, 'Arnold Press', 'SHOULDERS', 'DUMBBELL'),
    (NULL, 'Push Press', 'SHOULDERS', 'BARBELL'),
    (NULL, 'Landmine Press', 'SHOULDERS', 'BARBELL'),
    (NULL, 'Machine Shoulder Press', 'SHOULDERS', 'MACHINE'),
    (NULL, 'Front Raise', 'SHOULDERS', 'DUMBBELL'),
    (NULL, 'Cable Lateral Raise', 'SHOULDERS', 'CABLE'),
    (NULL, 'Rear Delt Fly', 'SHOULDERS', 'DUMBBELL'),
    (NULL, 'Reverse Pec Deck', 'SHOULDERS', 'MACHINE'),
    (NULL, 'Upright Row', 'SHOULDERS', 'BARBELL'),
    (NULL, 'Band Pull-Apart', 'SHOULDERS', 'BAND'),
    -- Biceps
    (NULL, 'EZ-Bar Curl', 'BICEPS', 'BARBELL'),
    (NULL, 'Preacher Curl', 'BICEPS', 'MACHINE'),
    (NULL, 'Incline Dumbbell Curl', 'BICEPS', 'DUMBBELL'),
    (NULL, 'Cable Curl', 'BICEPS', 'CABLE'),
    (NULL, 'Concentration Curl', 'BICEPS', 'DUMBBELL'),
    (NULL, 'Spider Curl', 'BICEPS', 'DUMBBELL'),
    (NULL, 'Reverse Curl', 'BICEPS', 'BARBELL'),
    (NULL, 'Bayesian Cable Curl', 'BICEPS', 'CABLE'),
    -- Triceps
    (NULL, 'Close-Grip Bench Press', 'TRICEPS', 'BARBELL'),
    (NULL, 'Overhead Triceps Extension', 'TRICEPS', 'DUMBBELL'),
    (NULL, 'Cable Overhead Extension', 'TRICEPS', 'CABLE'),
    (NULL, 'Rope Pushdown', 'TRICEPS', 'CABLE'),
    (NULL, 'Dumbbell Kickback', 'TRICEPS', 'DUMBBELL'),
    (NULL, 'JM Press', 'TRICEPS', 'BARBELL'),
    (NULL, 'Bench Dip', 'TRICEPS', 'BODYWEIGHT'),
    (NULL, 'Diamond Push-Up', 'TRICEPS', 'BODYWEIGHT'),
    -- Quads
    (NULL, 'Goblet Squat', 'QUADS', 'DUMBBELL'),
    (NULL, 'Hack Squat', 'QUADS', 'MACHINE'),
    (NULL, 'Smith Machine Squat', 'QUADS', 'MACHINE'),
    (NULL, 'Box Squat', 'QUADS', 'BARBELL'),
    (NULL, 'Pause Squat', 'QUADS', 'BARBELL'),
    (NULL, 'Reverse Lunge', 'QUADS', 'DUMBBELL'),
    (NULL, 'Step-Up', 'QUADS', 'DUMBBELL'),
    (NULL, 'Sissy Squat', 'QUADS', 'BODYWEIGHT'),
    (NULL, 'Pistol Squat', 'QUADS', 'BODYWEIGHT'),
    -- Hamstrings
    (NULL, 'Seated Leg Curl', 'HAMSTRINGS', 'MACHINE'),
    (NULL, 'Lying Leg Curl', 'HAMSTRINGS', 'MACHINE'),
    (NULL, 'Stiff-Leg Deadlift', 'HAMSTRINGS', 'BARBELL'),
    (NULL, 'Single-Leg Romanian Deadlift', 'HAMSTRINGS', 'DUMBBELL'),
    (NULL, 'Good Morning', 'HAMSTRINGS', 'BARBELL'),
    (NULL, 'Nordic Curl', 'HAMSTRINGS', 'BODYWEIGHT'),
    (NULL, 'Glute-Ham Raise', 'HAMSTRINGS', 'MACHINE'),
    (NULL, 'Stability Ball Leg Curl', 'HAMSTRINGS', 'OTHER'),
    -- Glutes
    (NULL, 'Glute Bridge', 'GLUTES', 'BODYWEIGHT'),
    (NULL, 'Single-Leg Hip Thrust', 'GLUTES', 'BODYWEIGHT'),
    (NULL, 'Sumo Deadlift', 'GLUTES', 'BARBELL'),
    (NULL, 'Cable Kickback', 'GLUTES', 'CABLE'),
    (NULL, 'Hip Abduction Machine', 'GLUTES', 'MACHINE'),
    (NULL, 'Reverse Hyperextension', 'GLUTES', 'MACHINE'),
    (NULL, 'Curtsy Lunge', 'GLUTES', 'DUMBBELL'),
    (NULL, 'Banded Lateral Walk', 'GLUTES', 'BAND'),
    -- Calves
    (NULL, 'Seated Calf Raise', 'CALVES', 'MACHINE'),
    (NULL, 'Leg Press Calf Raise', 'CALVES', 'MACHINE'),
    (NULL, 'Single-Leg Calf Raise', 'CALVES', 'BODYWEIGHT'),
    (NULL, 'Donkey Calf Raise', 'CALVES', 'MACHINE'),
    (NULL, 'Smith Machine Calf Raise', 'CALVES', 'MACHINE'),
    (NULL, 'Tibialis Raise', 'CALVES', 'OTHER'),
    -- Core
    (NULL, 'Ab Wheel Rollout', 'CORE', 'OTHER'),
    (NULL, 'Side Plank', 'CORE', 'BODYWEIGHT'),
    (NULL, 'Dead Bug', 'CORE', 'BODYWEIGHT'),
    (NULL, 'Hollow Hold', 'CORE', 'BODYWEIGHT'),
    (NULL, 'Pallof Press', 'CORE', 'CABLE'),
    (NULL, 'Cable Woodchop', 'CORE', 'CABLE'),
    (NULL, 'Russian Twist', 'CORE', 'BODYWEIGHT'),
    (NULL, 'Bicycle Crunch', 'CORE', 'BODYWEIGHT'),
    (NULL, 'Decline Sit-Up', 'CORE', 'BODYWEIGHT'),
    (NULL, 'Suitcase Carry', 'CORE', 'DUMBBELL'),
    -- Full body
    (NULL, 'Trap Bar Deadlift', 'FULL_BODY', 'BARBELL'),
    (NULL, 'Hang Clean', 'FULL_BODY', 'BARBELL'),
    (NULL, 'Clean and Jerk', 'FULL_BODY', 'BARBELL'),
    (NULL, 'Snatch', 'FULL_BODY', 'BARBELL'),
    (NULL, 'Thruster', 'FULL_BODY', 'BARBELL'),
    (NULL, 'Kettlebell Turkish Get-Up', 'FULL_BODY', 'KETTLEBELL'),
    (NULL, 'Sled Push', 'FULL_BODY', 'OTHER'),
    (NULL, 'Wall Ball', 'FULL_BODY', 'OTHER'),
    (NULL, 'Burpee', 'FULL_BODY', 'BODYWEIGHT'),
    (NULL, 'Battle Ropes', 'FULL_BODY', 'OTHER');

-- Members may already have created a personal exercise with a name that is now
-- in the library. Move that history onto the library exercise (same movement,
-- same name) and remove the duplicate so pickers, records and trends show one entry.
UPDATE exercise_set
SET exercise_id = (SELECT lib.id FROM exercise lib
                   WHERE lib.owner_id IS NULL
                     AND LOWER(lib.name) = (SELECT LOWER(p.name) FROM exercise p WHERE p.id = exercise_set.exercise_id))
WHERE exercise_id IN (SELECT p.id FROM exercise p
                      WHERE p.owner_id IS NOT NULL
                        AND EXISTS (SELECT 1 FROM exercise lib WHERE lib.owner_id IS NULL AND LOWER(lib.name) = LOWER(p.name)));

UPDATE goal
SET exercise_id = (SELECT lib.id FROM exercise lib
                   WHERE lib.owner_id IS NULL
                     AND LOWER(lib.name) = (SELECT LOWER(p.name) FROM exercise p WHERE p.id = goal.exercise_id))
WHERE exercise_id IN (SELECT p.id FROM exercise p
                      WHERE p.owner_id IS NOT NULL
                        AND EXISTS (SELECT 1 FROM exercise lib WHERE lib.owner_id IS NULL AND LOWER(lib.name) = LOWER(p.name)));

DELETE FROM exercise
WHERE owner_id IS NOT NULL
  AND LOWER(name) IN (SELECT lib_name FROM (SELECT LOWER(name) AS lib_name FROM exercise WHERE owner_id IS NULL) lib);
