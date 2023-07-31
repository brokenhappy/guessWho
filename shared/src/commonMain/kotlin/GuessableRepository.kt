interface GuessableRepository {
    suspend fun getAll(): List<Guessable>
    suspend fun add(guessable: Guessable)
    suspend fun getRandom(): Guessable
}

data class Guessable(val name: String)
