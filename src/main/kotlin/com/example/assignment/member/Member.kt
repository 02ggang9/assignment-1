package com.example.assignment.member

import com.example.assignment.chat.Chat
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import java.time.LocalDateTime

@Entity
class Member(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long?,
    val email: String,
    val password: String,
    val name: String,
    val createAt: LocalDateTime,
    val role: String,
    @OneToMany(mappedBy = "member", cascade = [CascadeType.ALL], orphanRemoval = true)
    val chatList: MutableList<Chat> = mutableListOf(),
) {

    fun addChat(chat: Chat) {
        this.chatList.add(chat)
        chat.member = this
    }

}
