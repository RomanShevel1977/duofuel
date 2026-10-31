package app.duofuel.di

import android.content.Context
import app.duofuel.data.local.createDuoFuelRepository
import app.duofuel.domain.repository.DuoFuelRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun repository(@ApplicationContext context: Context): DuoFuelRepository =
        createDuoFuelRepository(context)
}
