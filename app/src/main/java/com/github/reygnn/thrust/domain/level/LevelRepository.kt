package com.github.reygnn.thrust.domain.level

import com.github.reygnn.thrust.domain.model.LevelConfig

interface LevelRepository {
    fun getLevel(id: Int): LevelConfig
    val totalLevels: Int
}

class LevelRepositoryImpl : LevelRepository {
    override fun getLevel(id: Int): LevelConfig = Levels.getById(id)
    override val totalLevels: Int = Levels.totalLevels
}
