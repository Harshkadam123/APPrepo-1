package com.harsh.jarvis.fitness

data class FitnessProfile(
    val level: Int = 1,
    val xp: Int = 0,
    val goal: String = "General fitness",
    val mode: String = "Home",
    val minutes: Int = 30,
    val trainingDays: Int = 4,
    val equipment: String = "Bodyweight"
)

data class Exercise(
    val name: String,
    val category: String,
    val difficulty: String,
    val sets: Int,
    val reps: String,
    val restSeconds: Int,
    val notes: String
)

data class WorkoutPlan(
    val title: String,
    val mode: String,
    val exercises: List<Exercise>
)

object FitnessCatalog {
    val home = WorkoutPlan("Hunter Home — Full Body", "Home", listOf(
        Exercise("Bodyweight Squat", "Legs", "Beginner", 3, "12–20", 60, "Keep knees tracking over toes."),
        Exercise("Push-up", "Push", "Beginner", 3, "6–15", 75, "Use an incline or knee variation if needed."),
        Exercise("Reverse Lunge", "Legs", "Beginner", 3, "8/side", 60, "Control the lowering phase."),
        Exercise("Pike Push-up", "Shoulders", "Intermediate", 3, "5–12", 90, "Keep hips high and move under control."),
        Exercise("Glute Bridge", "Posterior chain", "Beginner", 3, "12–20", 60, "Pause briefly at the top."),
        Exercise("Plank", "Core", "Beginner", 3, "30–60 sec", 45, "Brace the abdomen; do not hold your breath.")
    ))

    val calisthenics = WorkoutPlan("Hunter Calisthenics — Push/Pull/Legs", "Calisthenics", listOf(
        Exercise("Push-up", "Push", "Beginner", 4, "6–15", 90, "Progress to decline or archer only when current form is strong."),
        Exercise("Australian Row", "Pull", "Beginner", 4, "6–15", 90, "Use a stable bar; keep the body rigid."),
        Exercise("Split Squat", "Legs", "Beginner", 3, "8–15/side", 75, "Keep the front foot planted."),
        Exercise("Pike Push-up", "Push", "Intermediate", 3, "5–12", 90, "Progress toward elevated pike work."),
        Exercise("Hanging Knee Raise", "Core", "Intermediate", 3, "6–12", 75, "Avoid swinging."),
        Exercise("L-sit Tuck", "Core", "Intermediate", 4, "10–30 sec", 60, "Use parallettes or sturdy supports.")
    ))

    val gym = WorkoutPlan("Hunter Gym — Full Body", "Gym", listOf(
        Exercise("Goblet Squat", "Legs", "Beginner", 3, "8–12", 90, "Choose a load that leaves controlled reps in reserve."),
        Exercise("Bench Press", "Push", "Intermediate", 3, "6–10", 120, "Use a spotter/safety setup for challenging loads."),
        Exercise("Lat Pulldown", "Pull", "Beginner", 3, "8–12", 90, "Pull toward the upper chest without swinging."),
        Exercise("Romanian Deadlift", "Posterior chain", "Intermediate", 3, "6–10", 120, "Keep a neutral spine and controlled hinge."),
        Exercise("Cable Row", "Pull", "Beginner", 3, "8–12", 90, "Pause briefly when handles reach the torso."),
        Exercise("Farmer Carry", "Conditioning", "Intermediate", 3, "30–60 sec", 75, "Walk tall with controlled steps.")
    ))

    fun plan(mode: String): WorkoutPlan = when (mode.lowercase()) {
        "gym" -> gym
        "calisthenics" -> calisthenics
        else -> home
    }
}
