package com.example.assignment.log

import com.example.assignment.chat.ChatRepository
import com.fasterxml.jackson.dataformat.csv.CsvSchema
import com.opencsv.CSVWriter
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.io.StringWriter
import java.time.LocalDateTime
import java.util.Base64
import kotlin.math.log

@Entity
class Log(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(length = 3000)
    val logContent: String,
    val category: String,
    val createAt: LocalDateTime,
)

@RestController
class LogController(
    private val logService: LogService,
) {

    @GetMapping("/log")
    fun getMemberActivity(
        @RequestHeader("token") jwtToken: String,
        ): ResponseEntity<GetMemberActivityResponseDTO> {
        return ResponseEntity.ok(logService.getMemberActivity(jwtToken))
    }

    data class GetMemberActivityResponseDTO(
        val signUpCount: Long,
        val logInCount: Long,
        val chatCount: Long,
    )

    @GetMapping("/report")
    fun getCsvReport(
        @RequestHeader("token") jwtToken: String,
    ): ResponseEntity<String> {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "text/csv")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report.csv")
            .body(logService.getReport(jwtToken))

    }
}

@Service
class LogService(
    private val logRepository: LogRepository,
    @Value("\${jwt.secret}")
    private val secretKey: String,
    private val chatRepository: ChatRepository,
) {

    private val key by lazy {
        Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretKey))
    }

    fun getMemberActivity(jwtToken: String): LogController.GetMemberActivityResponseDTO {
        val memberRole = getClaim("role", jwtToken)
        if (memberRole != "admin") throw IllegalArgumentException("어드민만 가능합니다.")

        val now = LocalDateTime.now()
        val findLogs = logRepository.findByCreateAtBetween(now.minusDays(1), now)

        val signUpCount = findLogs.count { it.category == "signUp" }.toLong()
        val logInCount = findLogs.count { it.category == "logIn" }.toLong()
        val chatCount = findLogs.count { it.category == "chat" }.toLong()

        return LogController.GetMemberActivityResponseDTO(
            signUpCount = signUpCount,
            logInCount = logInCount,
            chatCount = chatCount
        )
    }

    private fun getClaim(claim: String, jwtToken: String): String {
        return Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(jwtToken)
            .body[claim]
            ?.toString()
            ?: throw IllegalArgumentException("잘못된 JWT TOKEN 입니다.")
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun log(
        category: String,
        logContent: String,
    ) {
        val log = Log(category = category, logContent = logContent, createAt = LocalDateTime.now())
        logRepository.save(log)
    }

    fun getReport(jwtToken: String): String? {
        val memberRole = getClaim("role", jwtToken)
        if (memberRole != "admin") throw IllegalArgumentException("어드민만 가능합니다.")

        val now = LocalDateTime.now()
        val findChat = chatRepository.findByCreateAtBetween(now.minusDays(1), now)

        val writer = StringWriter()
        val csvWriter = CSVWriter(writer)

        csvWriter.writeNext(arrayOf("chatQuestion", "chatAnswer", "memberEmail"))

        findChat.forEach {
            csvWriter.writeNext(arrayOf(it.question, it.answer, it.member?.email))
        }

        csvWriter.close()
        return writer.toString()
    }

}

interface LogRepository: JpaRepository<Log, Long> {

    fun findByCreateAtBetween(start: LocalDateTime, end: LocalDateTime): List<Log>

}
