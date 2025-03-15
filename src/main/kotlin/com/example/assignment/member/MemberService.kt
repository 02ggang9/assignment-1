package com.example.assignment.member

import com.example.assignment.log.Log
import com.example.assignment.log.LogRepository
import com.example.assignment.log.LogService
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.Base64
import java.util.Date
import kotlin.math.log

@Service
class MemberService(
    @Value("\${jwt.secret}")
    private val secretKey: String,
    private val memberRepository: MemberRepository,
    private val logService: LogService,
) {

    private val key by lazy {
        Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretKey))
    }

    @Transactional
    fun saveMember(requestDTO: SaveMemberRequestDTO): Long {
        logService.log("signUp", "회원가입 시도")
        val findMember = memberRepository.findMemberByEmail(requestDTO.email)
        if (findMember != null) throw IllegalArgumentException("이미 회원이 존재합니다.")

        val member = Member(
            id = null,
            email = requestDTO.email,
            password = requestDTO.password,
            name = requestDTO.name,
            createAt = LocalDateTime.now(),
            role = requestDTO.role,
        )


        return memberRepository.save(member).id ?: throw IllegalArgumentException()
    }

    fun loginMember(requestDTO: LoginMemberRequestDTO): String {
        logService.log("logIn", "로그인 시도")
        val findMember= memberRepository.findMemberByEmail(requestDTO.email) ?: throw IllegalArgumentException("잘못된 email")

        // TODO: Spring Security Password Encoder 적용
        if (findMember.password != requestDTO.password) throw IllegalArgumentException("잘못된 password 입니다.")

        return Jwts.builder()
            .setSubject(requestDTO.email)
            .claim("email", requestDTO.email)
            .claim("role", findMember.role)
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + EXPIRATION_TIME))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()

    }

    companion object {
        private const val EXPIRATION_TIME = 1000 * 60 * 60 * 10 // 10시간
    }

}
