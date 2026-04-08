package com.dealer.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.core.io.ResourceLoader

@Configuration
class FirebaseConfig(
    private val resourceLoader: ResourceLoader,
    private val firebaseProperties: FirebaseProperties
) {

    @Bean("firebaseApp")
    @ConditionalOnProperty(value = ["app.firebase.enabled"], havingValue = "true")
    fun firebaseApp(): FirebaseApp {
        val stream = resourceLoader.getResource("file:${firebaseProperties.messagingTokenPath!!}").inputStream
        val options =
            FirebaseOptions
                .builder()
                .setCredentials(GoogleCredentials.fromStream(stream))
                .build()

        return if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options)
        } else {
            FirebaseApp.getInstance()
        }
    }

    @Bean
    @DependsOn("firebaseApp")
    @ConditionalOnProperty(value = ["app.firebase.enabled"], havingValue = "true")
    fun firebaseMessaging(firebaseApp: FirebaseApp): FirebaseMessaging = FirebaseMessaging.getInstance(firebaseApp)
}
