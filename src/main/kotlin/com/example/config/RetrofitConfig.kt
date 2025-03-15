package com.example.config

import com.example.util.createRetrofitApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

@Configuration
class RetrofitConfig(
    @Value("") private val baseUrl: String,
) {

    @Bean
    fun apiService(): AiApiService {
        return createRetrofitApi<AiApiService>(baseUrl)
    }
}

interface AiApiService {

    @POST("/")
    fun aiInfo(
        @Header("apiKey") apiKey: String,
        @Body body: TestRequestDTO
    )

    data class TestRequestDTO(
        val text: String
    )

}
