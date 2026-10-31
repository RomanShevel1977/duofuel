package app.duofuel.gasoline.di

import android.content.Context
import app.duofuel.gasoline.data.local.createGasoLineRepository
import app.duofuel.gasoline.domain.repository.GasoLineRepository
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
    fun repository(@ApplicationContext context: Context): GasoLineRepository =
        createGasoLineRepository(context)
}
