package com.pockettrainer

import android.app.Application
import com.pockettrainer.data.ModelRepository
import com.pockettrainer.data.DatasetRepository

class PocketTrainerApp : Application() {
    lateinit var modelRepository: ModelRepository
    lateinit var datasetRepository: DatasetRepository

    override fun onCreate() {
        super.onCreate()
        instance = this
        modelRepository = ModelRepository(this)
        datasetRepository = DatasetRepository(this)
    }

    companion object {
        lateinit var instance: PocketTrainerApp
    }
}