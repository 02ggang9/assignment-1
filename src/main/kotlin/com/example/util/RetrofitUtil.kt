package com.example.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

inline fun <reified T> createRetrofitApi(
    baseUrl: String
): T {
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(
            JacksonConverterFactory.create(
                ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .registerModule(KotlinModule.Builder().build())
                    .registerModule(JavaTimeModule()) // Java 8 날짜/시간 타입 지원
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            )
        )
        .build()
        .create(T::class.java)
}
