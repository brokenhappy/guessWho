import kotlinx.coroutines.flow.Flow

interface GuessWhoClient {
    suspend fun startGuessWhoSession(name: String): GuessWhoSession
}

interface GuessWhoSession {
    suspend fun sendChat(chat: String): ChatStream
}

interface ChatStream {
    val response: Flow<String>
}