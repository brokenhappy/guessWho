import com.aallam.openai.api.completion.CompletionRequest
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.seconds

private val openAIClient by lazy {
    OpenAI(
        token = secrets.openApiKey, // If this code is red, you need to create DontShareSecrets.kt
        timeout = Timeout(socket = 60.seconds),
    )
}

class OpenAIBasedGuessWhoClient: GuessWhoClient {
    override suspend fun startGuessWhoSession(
        name: String,
    ): GuessWhoSession = object: GuessWhoSession {
        private val completions = openAIClient.completions(
            CompletionRequest(
                ModelId("gpt-3.5-turbo"),
                prompt = """
                    We are playing Guess Who. You are now $name. Talk and act exactly like you are, 
                    but do NOT say exactly who you are!
                """.trimIndent(),
                maxTokens = 100,
            )
        )

        override suspend fun sendChat(chat: String): ChatStream = object: ChatStream {
            override val response: Flow<String>
                get() = completions.map { completion ->
                    completion.choices.first().text
                }
        }
    }
}