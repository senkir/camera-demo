package co.mirthandmalice.camerademo

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object CameraModule {

    @Provides
    fun provideScope(): CoroutineScope = CoroutineScope(Dispatchers.IO)

}