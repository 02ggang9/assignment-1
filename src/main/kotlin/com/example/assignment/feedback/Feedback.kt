package com.example.assignment.feedback

import com.example.assignment.chat.ChatRepository
import com.example.assignment.member.MemberRepository
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.util.Base64

@Entity
class Feedback(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val memberId: String,
    val chatId: String,
    val content: String,
    val isPositive: Boolean,
    val createAt: LocalDateTime,
    var status: String,
)

@RestController
class FeedbackController(
    private val feedbackService: FeedbackService,
) {

    @PostMapping("/feedback/{chatId}")
    fun saveFeedback(
        @PathVariable chatId: Long,
        @RequestHeader("token") jwtToken: String,
        @RequestBody requestDTO: SaveFeedbackRequestDTO
    ): Long {
        return feedbackService.save(requestDTO, jwtToken, chatId)
    }

    @PostMapping("/admin/feedback/{feedbackId}")
    fun updateFeedback(
        @PathVariable feedbackId: Long,
        @RequestHeader("token") jwtToken: String,
        @RequestBody requestDTO: UpdateFeedbackRequestDTO
    ) {
        return feedbackService.update(feedbackId, jwtToken, requestDTO)
    }

    data class SaveFeedbackRequestDTO(
        val isPositive: Boolean,
        val feedback: String,
    )

    data class UpdateFeedbackRequestDTO(
        val status: String = "",
    )
}

@Service
class FeedbackService(
    private val feedbackRepository: FeedbackRepository,
    private val chatRepository: ChatRepository,
    @Value("\${jwt.secret}")
    private val secretKey: String,
    private val memberRepository: MemberRepository,
) {

    private val key by lazy {
        Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretKey))
    }

    @Transactional
    fun save(
        requestDTO: FeedbackController.SaveFeedbackRequestDTO,
        jwtToken: String,
        chatId: Long,
    ): Long {
        val memberEmail = getClaim("email", jwtToken)
        val memberRole = getClaim("role", jwtToken)
        val findMember = memberRepository.findMemberByEmail(memberEmail) ?: throw IllegalArgumentException()
        val findChat = chatRepository.findByIdOrNull(chatId) ?: throw IllegalArgumentException()
        val isAuthor = findMember.email == memberEmail
        val isAdmin = memberRole == "admin"

        if (!isAdmin && !isAuthor) {
            throw IllegalArgumentException("권한이 없습니다.")
        }

        val feedback = Feedback(
            memberId = findMember.id.toString(),
            chatId = findChat.id.toString(),
            isPositive = requestDTO.isPositive,
            createAt = LocalDateTime.now(),
            status = "pending",
            content = requestDTO.feedback,
        )

        val saveFeedback = feedbackRepository.save(feedback)
        return saveFeedback.id!!
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

    @Transactional
    fun update(feedbackId: Long, jwtToken: String, requestDTO: FeedbackController.UpdateFeedbackRequestDTO) {
        val memberRole = getClaim("role", jwtToken)
        val isAdmin = memberRole == "admin"
        if (requestDTO.status != "pending" && requestDTO.status != "resolve") throw IllegalArgumentException()
        if (!isAdmin) throw IllegalArgumentException("어드민만 가능합니다.")

        val feedback =
            feedbackRepository.findByIdOrNull(feedbackId) ?: throw IllegalArgumentException("해당하는 Feedback이 없습니다.")

        feedback.status = requestDTO.status
    }

}

interface FeedbackRepository: JpaRepository<Feedback, Long> {

}
