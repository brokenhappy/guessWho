import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlin.time.Duration.Companion.seconds

private val openAIClient by lazy {
    OpenAI(
        token = secrets.openApiKey, // If this code is red, you need to create DontShareSecrets.kt
        timeout = Timeout(socket = 60.seconds),
    )
}

class OpenAIBasedGuessWhoClient: GuessWhoClient {
    @OptIn(BetaOpenAI::class)
    override fun startGuessWhoSession(
        name: String,
    ): GuessWhoSession = object: GuessWhoSession {
        val chatSoFar = mutableListOf<ChatMessage>()
        var currentChat: StringBuilder? = null
        override suspend fun sendChat(chat: String): ChatStream = object: ChatStream {
            private val completions = openAIClient.chatCompletions(
                ChatCompletionRequest(
                    ModelId("gpt-3.5-turbo"),
                    messages = chatSoFar.also {
                        currentChat
                            ?.also {
                                currentChat = null
                                chatSoFar += ChatMessage(ChatRole.User, content = it.toString())
                            }
                        chatSoFar += ChatMessage(ChatRole.Assistant, content = chat)
                    },
                    maxTokens = 100,
                )
            )
            override val response: Flow<String>
                get() = completions.mapNotNull { completion ->
                    completion.choices.first().delta?.content
                        ?.also { currentChat = (currentChat ?: StringBuilder()).append(it) }
                }
        }
    }
}