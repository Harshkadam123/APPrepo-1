package com.harsh.jarvis.evolution

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.harsh.jarvis.tasks.JarvisDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EvolutionPersistenceTest {
    private lateinit var db: JarvisDatabase
    private lateinit var repository: EvolutionRepository

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JarvisDatabase::class.java).allowMainThreadQueries().build()
        repository = EvolutionRepository(db.evolutionDao())
    }

    @After fun close() = db.close()

    @Test fun profileSkillQuestSurviveDaoRoundTrip() = runBlocking {
        repository.ensureProfile()
        val skillId = repository.addSkill("Python", "INTELLIGENCE")
        val questId = repository.addQuest(EvolutionQuest(title = "Python practice", skillId = skillId, xpReward = 120))
        repository.completeQuest(questId)
        val dashboard = repository.dashboard()
        assertTrue(dashboard.profile.totalXp > 0)
        assertTrue(dashboard.skills.any { it.id == skillId })
        assertTrue(dashboard.achievements.any { it.key == "first_quest" })
    }
}
