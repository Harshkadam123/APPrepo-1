package com.harsh.jarvis.fitness

enum class SkillRank(val label: String, val multiplier: Int) {
    F("F", 1), E("E", 1), D("D", 2), C("C", 2), B("B", 3), A("A", 3),
    S("S", 4), SS("SS", 5), SSS("SSS", 6), SSS_PLUS("SSS+", 8)
}

data class SkillPrerequisite(val skillId: String, val minLevel: Int)

data class SkillDefinition(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val rank: SkillRank,
    val maxLevel: Int = 10,
    val xpPerLevel: Int = 100,
    val activityUnit: String = "completed practice session",
    val prerequisites: List<SkillPrerequisite> = emptyList(),
    val promotionId: String? = null,
    val promotionMessage: String = ""
)

data class SkillProgress(
    val level: Int = 0,
    val xp: Int = 0,
    val mastered: Boolean = false,
    val xpPerLevel: Int = 100
) {
    val progress: Float
        get() = if (mastered) 1f else ((xp % xpPerLevel).toFloat() / xpPerLevel.toFloat()).coerceIn(0f, 1f)
}

/**
 * JARVIS universal Hunter skill tree.
 *
 * Every skill has its own XP bar. Basic tracks cap at level 10 and pause
 * until their prerequisites are satisfied. The next skill then becomes
 * available. S/SS/SSS/SSS+ are game-style ranks, not certifications.
 */
object SkillCatalog {
    val skills: List<SkillDefinition> = listOf(
        // Fitness / movement
        SkillDefinition("swimming", "Swimming", "Fitness", "Distance, technique and endurance practice.", SkillRank.F, activityUnit = "100 m completed", xpPerLevel = 100),
        SkillDefinition("strength", "Strength", "Fitness", "Progressive resistance and bodyweight strength.", SkillRank.F, activityUnit = "completed working set", xpPerLevel = 100),
        SkillDefinition("mobility", "Mobility", "Fitness", "Range of motion, control and movement quality.", SkillRank.F, activityUnit = "mobility session", xpPerLevel = 100),

        // Data / AI chain
        SkillDefinition("data_cleaning", "Data Cleaning", "Data", "Cleaning, validation, missing values, duplicates and reproducible preprocessing.", SkillRank.F, activityUnit = "completed dataset-work unit", promotionId = "ml_concepts", promotionMessage = "Data Cleaning mastered. Complete SQL and Math prerequisites to unlock S-rank ML Concepts."),
        SkillDefinition("sql", "SQL", "Data", "Queries, joins, aggregation, modeling and practical database work.", SkillRank.F, activityUnit = "completed SQL exercise", xpPerLevel = 100),
        SkillDefinition("math", "Math Foundations", "STEM", "Algebra, probability, statistics and quantitative reasoning.", SkillRank.F, activityUnit = "completed problem set", xpPerLevel = 100),
        SkillDefinition("ml_concepts", "ML Concepts", "AI", "Machine learning concepts, evaluation, features, bias and model selection.", SkillRank.S, xpPerLevel = 150, activityUnit = "completed ML concept/problem", prerequisites = listOf(SkillPrerequisite("data_cleaning", 10), SkillPrerequisite("sql", 7), SkillPrerequisite("math", 7)), promotionId = "deep_learning", promotionMessage = "S-rank ML Concepts mastered. Complete the advanced prerequisites to unlock SS-rank Deep Learning."),
        SkillDefinition("deep_learning", "Deep Learning", "AI", "Neural networks, representation learning, optimization and evaluation.", SkillRank.SS, xpPerLevel = 200, activityUnit = "completed DL experiment", prerequisites = listOf(SkillPrerequisite("ml_concepts", 10), SkillPrerequisite("math", 9), SkillPrerequisite("sql", 8)), promotionId = "real_world_projects", promotionMessage = "SS-rank Deep Learning mastered. Build completed real-world projects to unlock SSS."),
        SkillDefinition("real_world_projects", "Real-World ML Projects", "Career", "End-to-end ML projects with data, modeling, evaluation, deployment and documentation.", SkillRank.SSS, xpPerLevel = 250, activityUnit = "completed project milestone", prerequisites = listOf(SkillPrerequisite("deep_learning", 10), SkillPrerequisite("data_cleaning", 10), SkillPrerequisite("sql", 10), SkillPrerequisite("math", 10)), promotionId = "competition_wins", promotionMessage = "SSS project mastery achieved. Genuine competition achievements can unlock SSS+."),
        SkillDefinition("competition_wins", "Competitive ML", "Career", "Evidence-backed competition achievements and reproducible competition projects.", SkillRank.SSS_PLUS, maxLevel = 1, xpPerLevel = 500, activityUnit = "evidence-backed competition achievement", prerequisites = listOf(SkillPrerequisite("real_world_projects", 10)), promotionMessage = "SSS+ is reserved for genuine competition achievements."),

        // Chess chain
        SkillDefinition("chess_basics", "Chess Fundamentals", "Chess", "Rules, legal moves, check, checkmate, notation and basic principles.", SkillRank.F, activityUnit = "completed chess lesson/puzzle", promotionId = "chess_tactics", promotionMessage = "Chess fundamentals mastered. Tactics is now the active skill."),
        SkillDefinition("chess_tactics", "Chess Tactics", "Chess", "Forks, pins, skewers, discovered attacks, mating patterns and calculation.", SkillRank.D, activityUnit = "completed tactical puzzle", prerequisites = listOf(SkillPrerequisite("chess_basics", 10)), promotionId = "chess_strategy", promotionMessage = "Tactics mastered. Strategy is now the active skill."),
        SkillDefinition("chess_strategy", "Chess Strategy", "Chess", "Pawn structure, plans, positional play, endgames and opening principles.", SkillRank.A, activityUnit = "annotated game/strategy exercise", prerequisites = listOf(SkillPrerequisite("chess_tactics", 10)), promotionId = "chess_competitive", promotionMessage = "Strategy mastered. Competitive Chess is now available."),
        SkillDefinition("chess_competitive", "Competitive Chess", "Chess", "Serious games, analysis, time management and tournament preparation.", SkillRank.S, xpPerLevel = 150, activityUnit = "analyzed serious game", prerequisites = listOf(SkillPrerequisite("chess_strategy", 10)), promotionId = "chess_mastery", promotionMessage = "Competitive chess mastered. Chess Mastery is now available."),
        SkillDefinition("chess_mastery", "Chess Mastery", "Chess", "High-level analysis, preparation and completed competitive performance.", SkillRank.SS, xpPerLevel = 200, activityUnit = "completed high-level milestone", prerequisites = listOf(SkillPrerequisite("chess_competitive", 10))),

        // Physics chain
        SkillDefinition("physics_foundations", "Physics Foundations", "Physics", "Units, vectors, graphs, equations, measurement and scientific reasoning.", SkillRank.F, activityUnit = "completed physics problem", promotionId = "mechanics", promotionMessage = "Physics foundations mastered. Mechanics is now active."),
        SkillDefinition("mechanics", "Mechanics", "Physics", "Motion, forces, energy, momentum, rotation and problem solving.", SkillRank.D, activityUnit = "completed mechanics problem", prerequisites = listOf(SkillPrerequisite("physics_foundations", 10)), promotionId = "advanced_physics", promotionMessage = "Mechanics mastered. Advanced Physics is now active."),
        SkillDefinition("advanced_physics", "Advanced Physics", "Physics", "Electricity, waves, thermodynamics, fields and deeper mathematical modeling.", SkillRank.S, xpPerLevel = 150, activityUnit = "completed advanced problem/lab", prerequisites = listOf(SkillPrerequisite("mechanics", 10), SkillPrerequisite("math", 7)), promotionId = "physics_projects", promotionMessage = "Advanced Physics mastered. Applied Physics Projects are now active."),
        SkillDefinition("physics_projects", "Applied Physics Projects", "Physics", "Experiment design, simulation, measurement and real-world physics projects.", SkillRank.SS, xpPerLevel = 200, activityUnit = "completed project milestone", prerequisites = listOf(SkillPrerequisite("advanced_physics", 10), SkillPrerequisite("math", 9)), promotionId = "physics_research", promotionMessage = "Applied Physics mastery achieved. Research-level work is now active."),
        SkillDefinition("physics_research", "Physics Research", "Physics", "Independent investigation, modeling, reproducibility and research communication.", SkillRank.SSS, xpPerLevel = 250, activityUnit = "completed research milestone", prerequisites = listOf(SkillPrerequisite("physics_projects", 10), SkillPrerequisite("math", 10))),

        // English communication chain
        SkillDefinition("english_foundations", "English Foundations", "Communication", "Grammar, vocabulary, sentence construction and comprehension.", SkillRank.F, activityUnit = "completed language exercise", promotionId = "english_speaking", promotionMessage = "English foundations mastered. Speaking is now active."),
        SkillDefinition("english_speaking", "English Speaking", "Communication", "Fluency, pronunciation, listening response and everyday conversation.", SkillRank.D, activityUnit = "completed speaking session", prerequisites = listOf(SkillPrerequisite("english_foundations", 10)), promotionId = "english_professional", promotionMessage = "Speaking mastered. Professional Communication is now active."),
        SkillDefinition("english_professional", "Professional Communication", "Communication", "Emails, presentations, meetings, interviews and technical explanation.", SkillRank.A, activityUnit = "completed communication task", prerequisites = listOf(SkillPrerequisite("english_speaking", 10)), promotionId = "english_public_speaking", promotionMessage = "Professional communication mastered. Public Speaking is now active."),
        SkillDefinition("english_public_speaking", "Public Speaking", "Communication", "Structured talks, persuasion, storytelling, Q&A and confident delivery.", SkillRank.S, xpPerLevel = 150, activityUnit = "recorded/completed speaking task", prerequisites = listOf(SkillPrerequisite("english_professional", 10)), promotionId = "english_mastery", promotionMessage = "Public speaking mastered. Communication Mastery is now active."),
        SkillDefinition("english_mastery", "Communication Mastery", "Communication", "High-level professional communication, leadership and clear technical storytelling.", SkillRank.SS, xpPerLevel = 200, activityUnit = "completed high-level communication milestone", prerequisites = listOf(SkillPrerequisite("english_public_speaking", 10))),

        // Programming / career chain
        SkillDefinition("programming_basics", "Programming Fundamentals", "Programming", "Variables, control flow, functions, data structures and debugging.", SkillRank.F, activityUnit = "completed coding problem", promotionId = "software_engineering", promotionMessage = "Programming fundamentals mastered. Software Engineering is now active."),
        SkillDefinition("software_engineering", "Software Engineering", "Programming", "Testing, Git, architecture, APIs, databases and maintainable code.", SkillRank.A, activityUnit = "completed engineering task", prerequisites = listOf(SkillPrerequisite("programming_basics", 10)), promotionId = "production_projects", promotionMessage = "Software engineering mastered. Production Projects are now active."),
        SkillDefinition("production_projects", "Production Projects", "Career", "Complete applications with testing, documentation, deployment and maintenance.", SkillRank.SS, xpPerLevel = 200, activityUnit = "completed project milestone", prerequisites = listOf(SkillPrerequisite("software_engineering", 10)), promotionId = "engineering_competition", promotionMessage = "Production project mastery achieved. Competitive engineering is now active."),
        SkillDefinition("engineering_competition", "Competitive Engineering", "Career", "Completed hackathon/competition results and difficult real-world engineering challenges.", SkillRank.SSS, xpPerLevel = 250, activityUnit = "completed competition milestone", prerequisites = listOf(SkillPrerequisite("production_projects", 10)), promotionMessage = "SSS engineering achieved; SSS+ can be reserved for completed exceptional competition wins.")
    )

    fun get(id: String): SkillDefinition? = skills.firstOrNull { it.id == id }
}
