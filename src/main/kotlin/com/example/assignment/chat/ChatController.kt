package com.example.assignment.chat

import com.example.assignment.log.LogService
import com.example.assignment.member.Member
import com.example.assignment.member.MemberRepository
import com.example.util.createRetrofitApi
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.time.Duration
import java.time.LocalDateTime
import java.util.Base64

@RestController
class ChatController(
    private val chatService: ChatService,
) {

    @PostMapping("/chat/thread")
    fun chatWithGPT(
        @RequestBody requestDTO: createChatRequestDTO,
        @RequestHeader("token") jwtToken: String,
    ): String {
        return chatService.chat(requestDTO, jwtToken)
    }

    @DeleteMapping("/chat/thread/{threadId}")
    fun deleteThread(
        @PathVariable threadId: Long,
        @RequestHeader("token") jwtToken: String,
    ): Long {
        return chatService.deleteThread(threadId, jwtToken)
    }

    @GetMapping("/chat/thread")
    fun getThread(
        @RequestHeader("token") jwtToken: String,
        @RequestParam email: String,
        @RequestParam(defaultValue = "0") page: Int,      // 페이지 번호 (기본값: 0)
        @RequestParam(defaultValue = "10") size: Int,     // 페이지 크기 (기본값: 10)
        @RequestParam(defaultValue = "desc") sort: String // 정렬 방향 (기본값: desc)
    ): Page<Chat> {
        return chatService.getThread(jwtToken, email, page, size, sort)
    }
}

data class GetThreadResponseDTO(
    val id: Long,
    val question: String,
    val answer: String,
    val createAt: LocalDateTime,
    val memberId: Long
)

data class createChatRequestDTO(
    val model: String = "gpt-3.5-turbo",
    val isStreaming: Boolean? = false,
    val prompt: String,
)

data class Message(
    val role: String,
    val content: String,
)

@Service
class ChatService(
    @Value("\${gpt.apiKey}")
    private val apiKey: String,
    @Value("\${jwt.secret}")
    private val secretKey: String,
    private val chatRepository: ChatRepository,
    private val memberRepository: MemberRepository,
    private val logService: LogService,
) {

    private val key by lazy {
        Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretKey))
    }

    @Transactional
    fun chat(requestDTO: createChatRequestDTO, jwtToken: String): String {
        logService.log("chat", "대화 생성 시도")
        // JWT 토큰 안에 member Email 으로 찾도록
        val memberEmail = Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(jwtToken)
            .body["email"]
            ?.toString()

        val findMember = memberRepository.findMemberByEmail(email = memberEmail!!) ?: throw IllegalArgumentException()

        val chatGPTRequest = ChatGPTRequest(model = requestDTO.model, listOf(Message("user", requestDTO.prompt)))
        val createRetrofitApi = createRetrofitApi<OpenAiApi>(baseUrl = "https://api.openai.com/")
        val response = createRetrofitApi.createChatCompletion(
            apiKey = "Bearer $apiKey",
            request = chatGPTRequest
        )
            .execute()
            .body()!!
            .choices[0]
            .message
            .content

        // 해당 유저의 첫 질문인가? or 30분이 지났는가?
        val lastChat = chatRepository.findTopByMemberOrderByCreateAtDesc(findMember)
        if (lastChat == null || findMember.chatList.isEmpty() || isOver30Min(lastChat.createAt)) {
            val chat = Chat(
                id = null,
                question = requestDTO.prompt,
                answer = response,
                createAt = LocalDateTime.now()
            )

            val saveChat = chatRepository.save(chat)
            findMember.addChat(saveChat)
        } else { // 30분 이내에 다시 질문할 경우
            lastChat.updateChat(
                question = requestDTO.prompt,
                answer = response,
                createAt = LocalDateTime.now()
            )
        }

        return response
    }

    private fun isOver30Min(chatCreateAt: LocalDateTime): Boolean {
        return Duration.between(chatCreateAt, LocalDateTime.now()).toMinutes() > 30
    }

    @Transactional
    fun deleteThread(threadId: Long, jwtToken: String): Long {
        val findChat = chatRepository.findByIdOrNull(threadId) ?: throw IllegalArgumentException("해당하는 id가 없습니다.")
        val memberEmail = getClaim("email", jwtToken)
        val memberRole = getClaim("role", jwtToken)

        if (findChat.member!!.email == memberEmail || memberRole == "admin") {
            chatRepository.deleteById(findChat.id!!)
            return findChat.id!!
        }

        throw IllegalArgumentException("해당 member는 삭제할 권한이 없습니다.")
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

    fun getThread(
        jwtToken: String,
        email: String,
        page: Int,
        size: Int,
        sort: String
    ): Page<Chat> {
        val findMember = memberRepository.findMemberByEmail(email) ?: throw IllegalArgumentException()
        val memberEmail = getClaim("email", jwtToken)
        val memberRole = getClaim("role", jwtToken)

        val isAdmin = memberRole == "admin"
        val isAuthor = findMember.email == memberEmail
        if (!isAdmin && !isAuthor) {
            throw IllegalArgumentException("권한이 없습니다.")
        }

        val sortOrder = if (sort.equals("desc", ignoreCase = true)) {
            Sort.by(Sort.Direction.DESC, "createAt")
        } else {
            Sort.by(Sort.Direction.ASC, "createAt")
        }

        val pageable: Pageable = PageRequest.of(page, size, sortOrder)
        return chatRepository.findChatByMember(findMember, pageable)
    }

}

data class ChatGPTRequest(
    val model: String,
    val messages: List<Message>
)

data class ChatGPTResponse(
    val choices: List<Choice> = emptyList()
) {
    data class Choice(
        val index: Int = 0,
        val message: Message = Message("", "")
    )
}

interface ChatRepository : JpaRepository<Chat, Long> {
    fun findTopByMemberOrderByCreateAtDesc(member: Member): Chat?
    fun findChatByMember(member: Member, pageable: Pageable): Page<Chat>
    fun findByCreateAtBetween(start: LocalDateTime, end: LocalDateTime): List<Chat>
}

interface OpenAiApi {
    @POST("v1/chat/completions")
    fun createChatCompletion(
        @Header("Authorization") apiKey: String,
        @Body request: ChatGPTRequest
    ): Call<ChatGPTResponse>
}
